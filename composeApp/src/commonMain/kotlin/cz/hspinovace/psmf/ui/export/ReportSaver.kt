package cz.hspinovace.psmf.ui.export

import androidx.compose.runtime.Composable
import cz.hspinovace.psmf.export.ZouDocument

/** What happened when the referee pressed save. Each is named on screen. */
sealed interface SaveOutcome {
    /** Every document was written. */
    data object Saved : SaveOutcome

    /** The referee backed out of choosing a folder. Nothing was written. */
    data object Cancelled : SaveOutcome

    /**
     * A write failed. It stops at the first failure, so some files may be
     * written and others not; the referee is told it failed either way.
     */
    data object Failed : SaveOutcome
}

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
 *
 * **A folder is asked for once, not a file every time.** The first
 * [save] asks where; every one after that writes straight there, with no
 * dialog at all -- see `AndroidReportSaver`. [changeFolder] is the escape
 * hatch a referee who wants a different folder reaches from Settings.
 * iOS does the same with a remembered folder bookmark (`IosReportSaver`).
 */
interface ReportSaver {
    suspend fun save(documents: List<ZouDocument>): SaveOutcome

    /**
     * Asks where to save from now on, replacing whatever was chosen
     * before. True once a folder has been picked and stored; false if the
     * referee backs out of the picker, in which case the old folder (if
     * any) is left in force.
     */
    suspend fun changeFolder(): Boolean
}

/** For targets with no document picker to hand: the JVM test host. */
class UnavailableReportSaver : ReportSaver {
    override suspend fun save(documents: List<ZouDocument>): SaveOutcome = SaveOutcome.Failed

    override suspend fun changeFolder(): Boolean = false
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
