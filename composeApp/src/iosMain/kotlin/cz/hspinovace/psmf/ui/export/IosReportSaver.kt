package cz.hspinovace.psmf.ui.export

import cz.hspinovace.psmf.data.settings.SettingsRepository
import cz.hspinovace.psmf.export.ZouDocument
import cz.hspinovace.psmf.export.bytes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Saves the report on iOS into a folder the referee picks **once**, the
 * same as Android: every later save writes straight there, with no dialog.
 *
 * # Why this, of the three candidates
 *
 * - The document picker in export mode costs an interaction on **every**
 *   save, which is the mistake Android already made once with three
 *   dialogs and then corrected.
 * - The app's own Documents folder, shown in the Files app, costs none, but
 *   **the files go when the app is deleted.** That is the one outcome saving
 *   exists to prevent: the referee getting back to their report without the
 *   app.
 * - **A folder picked once and remembered** is Android parity, and the files
 *   live wherever the referee chose (On My iPhone, iCloud Drive or another
 *   provider), independent of the app. It also fits the existing Settings row,
 *   "Choose a folder" / "Change the folder", with no change to shared UI.
 *
 * # What the referee loses with it
 *
 * The first save asks for a folder, so it is one interaction more than the
 * Documents route, once. If the folder is deleted or its provider stops
 * granting access, the next save asks again, as on Android.
 *
 * # How it is remembered
 *
 * A bookmark to the picked folder, base64 in the settings value Android
 * uses for its tree URI (`exportFolderUri`). It is an opaque per-device
 * token either way; nothing reads it but this class. A stale bookmark (the
 * folder moved) is refreshed; one that no longer resolves to a folder is
 * treated as none, and the referee is asked again.
 *
 * Writes go through `NSFileCoordinator`, which iCloud Drive and other file
 * providers need in order to see a change made from outside them, and each
 * write replaces the file of the same name. Saving a match again updates
 * its three files instead of leaving copies beside them.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosReportSaver(
    private val settings: SettingsRepository,
) : ReportSaver {
    /** UIKit holds the picker's delegate weakly; see [IosReportSender]. */
    private var inFlight: Any? = null

    override suspend fun save(documents: List<ZouDocument>): SaveOutcome =
        withContext(Dispatchers.Main) {
            saveAll(
                folder = storedFolder(),
                pickFolder = { pickAndStoreFolder() },
                documents = documents,
                write = ::write,
            )
        }

    override suspend fun changeFolder(): Boolean = withContext(Dispatchers.Main) { pickAndStoreFolder() != null }

    private suspend fun storedFolder(): NSURL? {
        val token = settings.load().exportFolderUri ?: return null
        val bookmark = NSData.create(base64EncodedString = token, options = 0u) ?: return null
        val folder =
            memScoped {
                val stale = alloc<BooleanVar>()
                val resolved =
                    NSURL.URLByResolvingBookmarkData(
                        bookmark,
                        options = 0u,
                        relativeToURL = null,
                        bookmarkDataIsStale = stale.ptr,
                        error = null,
                    ) ?: return null
                if (stale.value) remember(resolved)
                resolved
            }
        return folder.takeIf { it.isUsableFolder() }
    }

    private fun NSURL.isUsableFolder(): Boolean =
        withAccess {
            val path = path ?: return@withAccess false
            NSFileManager.defaultManager.isWritableFileAtPath(path)
        }

    private suspend fun pickAndStoreFolder(): NSURL? {
        val picked = pickFolder() ?: return null
        remember(picked)
        return picked
    }

    /**
     * Stores [folder] for next time. If the bookmark cannot be made, this
     * save still goes ahead; the referee is simply asked again next time.
     */
    private suspend fun remember(folder: NSURL) {
        val bookmark =
            folder.withAccess {
                folder.bookmarkDataWithOptions(
                    options = 0u,
                    includingResourceValuesForKeys = null,
                    relativeToURL = null,
                    error = null,
                )
            } ?: return
        settings.setExportFolderUri(bookmark.base64EncodedStringWithOptions(0u))
    }

    private fun write(
        folder: NSURL,
        document: ZouDocument,
    ): Boolean =
        folder.withAccess {
            val target = folder.URLByAppendingPathComponent(document.fileName) ?: return@withAccess false
            var written = false
            NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
                target,
                options = NSFileCoordinatorWritingForReplacing,
                error = null,
            ) { url ->
                written = url != null && document.bytes().toNSData().writeToURL(url, atomically = true)
            }
            written
        }

    private suspend fun pickFolder(): NSURL? {
        val presenter = topViewController() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
            picker.allowsMultipleSelection = false
            val delegate =
                FolderPickerDelegate { folder ->
                    inFlight = null
                    if (continuation.isActive) continuation.resume(folder)
                }
            inFlight = delegate
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

    private class FolderPickerDelegate(
        private val onDone: (NSURL?) -> Unit,
    ) : NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            onDone(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onDone(null)
        }
    }
}

/**
 * Runs [block] inside the security scope a picked folder grants. Outside
 * it, a folder from the document picker cannot be read or written at all.
 */
private inline fun <T> NSURL.withAccess(block: () -> T): T {
    val accessing = startAccessingSecurityScopedResource()
    try {
        return block()
    } finally {
        if (accessing) stopAccessingSecurityScopedResource()
    }
}
