package cz.hspinovace.psmf.ui.export

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import cz.hspinovace.psmf.export.ZouDocument
import cz.hspinovace.psmf.export.bytes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Saves each document through Android's own document picker, one file at
 * a time.
 *
 * `ACTION_CREATE_DOCUMENT` creates exactly one document per launch --
 * there is no batch form of it -- so saving all three formats means three
 * system dialogs in a row, each pre-filled with that format's own file
 * name. Noted rather than hidden: `ACTION_OPEN_DOCUMENT_TREE`, asked once
 * for a folder, would let all three land without asking again, and reads
 * smoother for a referee outdoors. Left as three dialogs because that is
 * the mechanism DEMO_SCOPE names; worth revisiting if this becomes more
 * than a demo.
 *
 * A save stops as soon as one document fails or is cancelled -- a partial
 * save (the text file landed, the spreadsheet did not) is worse than none,
 * because it looks complete from the file list alone.
 *
 * # Why this is not a Koin `single`
 *
 * `ActivityResultContracts.CreateDocument` needs the *Activity's* result
 * registry, and Koin here is wired from the Application context (see
 * `androidContext()` in `PsmfApplication`), which cannot open a picker at
 * all. This class is built from the real `ComponentActivity` instead, by
 * [rememberReportSaver] at the point it is used.
 *
 * # Why `register` is called directly rather than through
 * `registerForActivityResult`
 *
 * The usual Compose helper, `rememberLauncherForActivityResult`, has to be
 * called during composition so it can register before the activity
 * reaches `STARTED`. This class is built once and reused for every export
 * screen visited afterwards, long after that point -- exactly the case
 * the composable helper is not for. `ActivityResultRegistry.register`
 * without a `LifecycleOwner` has no such restriction: each save registers
 * a fresh, uniquely-keyed launcher, awaits its one result, and unregisters
 * it again, so nothing here has to happen before `onStart`.
 */
class AndroidReportSaver(
    private val activity: ComponentActivity,
) : ReportSaver {
    override suspend fun save(documents: List<ZouDocument>): Boolean {
        for (document in documents) {
            val uri = createDocument(document) ?: return false
            if (!write(uri, document)) return false
        }
        return true
    }

    private fun write(
        uri: Uri,
        document: ZouDocument,
    ): Boolean =
        runCatching {
            // "wt": truncate. Some providers otherwise append to a file
            // that already exists from an earlier, cancelled attempt.
            activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(document.bytes()) }
                ?: error("no output stream for $uri")
        }.isSuccess

    private suspend fun createDocument(document: ZouDocument): Uri? =
        suspendCancellableCoroutine { continuation ->
            val key = "psmf-save-${document.fileName}-${System.nanoTime()}"
            lateinit var launcher: ActivityResultLauncher<String>
            launcher =
                activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.CreateDocument(document.mimeType),
                ) { uri ->
                    launcher.unregister()
                    if (continuation.isActive) continuation.resume(uri)
                }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(document.fileName)
        }
}
