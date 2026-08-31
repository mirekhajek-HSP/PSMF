package cz.hspinovace.psmf.ui.export

import androidx.compose.runtime.Composable
import cz.hspinovace.psmf.export.ZouDocument

/**
 * Writes the report somewhere the referee can open again without the app.
 *
 * A save step beside the send step, not instead of it (DEMO_SCOPE screen
 * 7). Today's flow writes the three files to app-private storage and
 * hands them to a mail client -- the referee can never get back to the
 * work their own name is on once that draft is dismissed.
 *
 * **The same [ZouDocument] list both paths are handed.** [ExportViewModel]
 * builds it once from one [cz.hspinovace.psmf.export.BuildZouReport]
 * value; sending and saving each write it out, and neither derives its
 * own copy. See [ZouDocument.bytes] for the encoding both of them share.
 */
interface ReportSaver {
    /** True once every document has a location written to. */
    suspend fun save(documents: List<ZouDocument>): Boolean
}

/**
 * For targets with no document picker to hand: the JVM test host, and iOS
 * until somebody builds it on a Mac.
 */
class UnavailableReportSaver : ReportSaver {
    override suspend fun save(documents: List<ZouDocument>): Boolean = false
}

/**
 * Builds the [ReportSaver] for the running platform.
 *
 * A composable rather than a Koin `single`, because Android's half of this
 * needs an [androidx.activity.ComponentActivity] -- specifically its
 * `activityResultRegistry` -- and Koin here is wired from the *Application*
 * context (see `androidContext()` in `PsmfApplication`), which cannot open
 * a document picker at all. [ReportSender] gets away with a plain
 * `Context` because launching a share sheet needs nothing back; asking the
 * user to choose *where a file goes* does.
 */
@Composable
expect fun rememberReportSaver(): ReportSaver
