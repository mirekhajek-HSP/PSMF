package cz.hspinovace.psmf.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.ExportZou
import cz.hspinovace.psmf.export.PSMF_REPORT_ADDRESS
import cz.hspinovace.psmf.export.ZouDocument
import cz.hspinovace.psmf.export.ZouFormat
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val loading: Boolean = true,
    val report: ZouReport? = null,
    val documents: List<ZouDocument> = emptyList(),
    /**
     * What stands between this report and PSMF.
     *
     * **Non-empty means export is refused.** The fine for an incomplete,
     * incorrect or late report is charged to the delegating team, so
     * catching it here is the one thing the app does that paper cannot.
     */
    val problems: List<ReportProblem> = emptyList(),
    val selected: ZouFormat = ZouFormat.TEXT,
    val sendFailed: Boolean = false,
    val sent: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
) {
    val ready: Boolean get() = problems.isEmpty() && report != null

    val preview: ZouDocument? get() = documents.firstOrNull { it.format == selected }
}

sealed interface ExportEvent {
    data class FormatSelected(
        val format: ZouFormat,
    ) : ExportEvent

    data object SendPressed : ExportEvent

    /**
     * Beside send, not instead of it (DEMO_SCOPE screen 7). The referee
     * presses this to get the three files somewhere they can open again
     * without the app; pressing "send" still only opens a mail draft.
     */
    data object SavePressed : ExportEvent
}

/**
 * Screen 7.
 *
 * The report is built in all three formats whether or not it is ready, so
 * the referee can see what they have; only *sending* is refused while
 * something mandatory is missing.
 */
class ExportViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildZouReport: BuildZouReport,
    private val exportZou: ExportZou,
    private val reportSender: ReportSender,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /**
     * Set when the referee has asked to save and cleared once it is
     * handled -- the same one-shot shape as `FixturesViewModel.openMatch`:
     * the actual save needs a live [android.app.Activity] this ViewModel
     * has no business knowing about, so it only ever asks for one to
     * happen and is told the answer through [saveHandled].
     */
    private val _savePending = MutableStateFlow<List<ZouDocument>?>(null)
    val savePending: StateFlow<List<ZouDocument>?> = _savePending.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val match = matches.load(matchId) ?: return
        val report = buildZouReport(match)
        _state.value =
            ExportUiState(
                loading = false,
                report = report,
                documents = report?.let { exportZou(it) }.orEmpty(),
                problems = match.reportProblems(),
            )
    }

    fun onEvent(event: ExportEvent) {
        when (event) {
            is ExportEvent.FormatSelected -> {
                _state.update { it.copy(selected = event.format) }
            }

            ExportEvent.SendPressed -> {
                send()
            }

            ExportEvent.SavePressed -> {
                val current = _state.value
                if (current.ready) _savePending.value = current.documents
            }
        }
    }

    /** Called back once the platform save has finished, whichever way. */
    fun saveHandled(success: Boolean) {
        _savePending.value = null
        _state.update { it.copy(saved = success, saveFailed = !success) }
    }

    private fun send() {
        val current = _state.value
        val report = current.report ?: return
        if (!current.ready) return

        viewModelScope.launch {
            val delivered =
                reportSender.send(
                    ReportDelivery(
                        to = PSMF_REPORT_ADDRESS,
                        subject = subjectFor(report),
                        body = ZouText.format(report),
                        documents = current.documents,
                    ),
                )
            _state.update { it.copy(sent = delivered, sendFailed = !delivered) }
        }
    }

    /**
     * The subject is Czech, like the rest of the report: somebody at PSMF
     * reads it in a mailbox alongside a hundred others.
     */
    private fun subjectFor(report: ZouReport): String =
        "ZoU ${report.header.league} ${report.header.dateWritten} " +
            "${report.header.homeTeam} - ${report.header.awayTeam}"
}
