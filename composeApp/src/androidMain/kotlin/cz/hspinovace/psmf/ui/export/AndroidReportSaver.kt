package cz.hspinovace.psmf.ui.export

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import cz.hspinovace.psmf.data.settings.SettingsRepository
import cz.hspinovace.psmf.export.ZouDocument
import cz.hspinovace.psmf.export.bytes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Saves the report into a folder the referee is asked for once.
 *
 * This project's own earlier specification named an API
 * (`ACTION_CREATE_DOCUMENT`) where it should have named an outcome ("a
 * folder the referee can find the report in again"). `CREATE_DOCUMENT`
 * creates exactly one document per launch, so saving all three formats
 * meant three system dialogs in a row, every time. `OPEN_DOCUMENT_TREE`
 * asks for a folder instead: asked once, its URI persisted -- with a
 * *persistable* permission grant, so it survives the app being killed and
 * the device rebooting -- every export after the first writes straight
 * into it, no dialog at all.
 *
 * A save stops as soon as one document fails or the referee backs out of
 * the folder picker -- a partial save (the text file landed, the
 * spreadsheet did not) is worse than none, because it looks complete from
 * the file list alone.
 *
 * # Overwrite, not accumulate
 *
 * The three files share one name stem per match
 * ([cz.hspinovace.psmf.export.ZouReport.fileStem]), so saving the same
 * match again -- mid-match, then again after the recap is confirmed --
 * has to replace the earlier files rather than sit beside them as
 * "zapis (1).txt". [DocumentFile.findFile] before
 * [DocumentFile.createFile] is what keeps that true; opening the found
 * file `"wt"` is the same truncate-and-write [write] always used.
 *
 * # Why this is not a Koin `single`
 *
 * `ActivityResultContracts` need the *Activity's* result registry, and
 * Koin here is wired from the Application context (see `androidContext()`
 * in `PsmfApplication`), which cannot open a picker at all. This class is
 * built from the real `ComponentActivity` instead, by [rememberReportSaver]
 * at the point it is used. [SettingsRepository] rides along as a
 * constructor parameter for the same reason `AndroidReportSaver` itself
 * is not a `single` -- it needs to be handed to something built outside
 * Koin's own graph.
 *
 * # Why `register` is called directly rather than through
 * `registerForActivityResult`
 *
 * The usual Compose helper, `rememberLauncherForActivityResult`, has to be
 * called during composition so it can register before the activity
 * reaches `STARTED`. This class is built once and reused for every export
 * screen visited afterwards, long after that point -- exactly the case
 * the composable helper is not for. `ActivityResultRegistry.register`
 * without a `LifecycleOwner` has no such restriction: each pick registers
 * a fresh, uniquely-keyed launcher, awaits its one result, and unregisters
 * it again, so nothing here has to happen before `onStart`.
 */
class AndroidReportSaver(
    private val activity: ComponentActivity,
    private val settings: SettingsRepository,
) : ReportSaver {
    override suspend fun save(documents: List<ZouDocument>): Boolean {
        val folder = existingFolder() ?: pickAndStoreFolder() ?: return false
        for (document in documents) {
            if (!write(folder, document)) return false
        }
        return true
    }

    override suspend fun changeFolder(): Boolean = pickAndStoreFolder() != null

    /**
     * The stored folder, if there is one and it is still usable.
     *
     * `canWrite()` is what catches a folder that was moved, deleted, or
     * had its access revoked from outside the app (Android's own storage
     * settings, a file manager) since it was picked -- treated the same as
     * never having picked one, which sends [save] back through the picker
     * rather than failing silently against a folder that is gone.
     */
    private suspend fun existingFolder(): DocumentFile? {
        val stored = settings.load().exportFolderUri ?: return null
        val folder = DocumentFile.fromTreeUri(activity, Uri.parse(stored))
        return folder?.takeIf { it.canWrite() }
    }

    private suspend fun pickAndStoreFolder(): DocumentFile? {
        val picked = pickFolder() ?: return null
        activity.contentResolver.takePersistableUriPermission(
            picked,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setExportFolderUri(picked.toString())
        return DocumentFile.fromTreeUri(activity, picked)
    }

    private fun write(
        folder: DocumentFile,
        document: ZouDocument,
    ): Boolean =
        runCatching {
            val target =
                folder.findFile(document.fileName)
                    ?: folder.createFile(document.mimeType, document.fileName)
                    ?: error("could not create ${document.fileName}")
            // "wt": truncate. A file found from an earlier save must be
            // replaced, not appended to.
            activity.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(document.bytes()) }
                ?: error("no output stream for ${target.uri}")
        }.isSuccess

    private suspend fun pickFolder(): Uri? =
        suspendCancellableCoroutine { continuation ->
            val key = "psmf-export-folder-${System.nanoTime()}"
            lateinit var launcher: ActivityResultLauncher<Uri?>
            launcher =
                activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    launcher.unregister()
                    if (continuation.isActive) continuation.resume(uri)
                }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(null)
        }
}
