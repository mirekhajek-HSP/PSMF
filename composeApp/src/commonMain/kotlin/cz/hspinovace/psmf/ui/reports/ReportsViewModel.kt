package cz.hspinovace.psmf.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.usecase.DiscardReport
import cz.hspinovace.psmf.usecase.ObserveReports
import cz.hspinovace.psmf.usecase.ReportContents
import cz.hspinovace.psmf.usecase.ReportListing
import cz.hspinovace.psmf.usecase.ReportRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class ReportsUiState(
    /**
     * The reports on the device. Null only until the first answer lands:
     * the screen then draws nothing rather than *Žádný zápis*, which would
     * be a flash of something untrue.
     */
    val listing: ReportListing? = null,
    /** The question before a report is discarded, while it is being asked. */
    val discarding: DiscardQuestion? = null,
)

/** The question put before a report is discarded: the row it was asked from, and what would go. */
data class DiscardQuestion(
    val row: ReportRow,
    val contents: ReportContents,
)

sealed interface ReportsEvent {
    /** *Zahodit* on a row. Asks; removes nothing. */
    data class DiscardRequested(
        val row: ReportRow,
    ) : ReportsEvent

    /** The question answered *keep*, or dismissed by tapping beside it or going back. */
    data object DiscardDismissed : ReportsEvent

    /** Yes to the question on screen. */
    data object DiscardConfirmed : ReportsEvent
}

/**
 * The Zápis tab's first screen: the reports on the device.
 *
 * **A stream, not a read on entry** (TODO, "Known gaps"): a report started
 * from Zápasy, or moved on in the wizard, has to be in the list the next
 * time the referee looks at it, and the list may already be on screen.
 *
 * Built on [work], as Zápasy is: the first answer joins every report to the
 * league data, which may not have been parsed yet.
 *
 * **A report is discarded only by a yes to the question about it** (prompt
 * 17, Gate 2). *Zahodit* asks; the question is state, and the yes takes the
 * report that question is about, so a yes with no question on screen -- a
 * second tap on the answer -- has nothing to remove.
 */
class ReportsViewModel(
    observeReports: ObserveReports,
    private val discardReport: DiscardReport,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val question = MutableStateFlow<DiscardQuestion?>(null)

    val state: StateFlow<ReportsUiState> =
        combine(observeReports().flowOn(work), question) { listing, asked -> ReportsUiState(listing, asked) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE.inWholeMilliseconds),
                initialValue = ReportsUiState(),
            )

    private val _discarded = MutableStateFlow<MatchId?>(null)

    /** A report just discarded, until the route has told the navigator ([discardHandled]). */
    val discarded: StateFlow<MatchId?> = _discarded.asStateFlow()

    fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.DiscardRequested -> ask(event.row)
            ReportsEvent.DiscardDismissed -> question.value = null
            ReportsEvent.DiscardConfirmed -> discardWhatWasAskedAbout()
        }
    }

    private fun ask(row: ReportRow) {
        viewModelScope.launch {
            // What the question says is read when it is asked, not taken from
            // the row: the row has no goals or cards on it.
            val contents = discardReport.contentsOf(row.matchId) ?: return@launch
            question.value = DiscardQuestion(row, contents)
        }
    }

    private fun discardWhatWasAskedAbout() {
        val asked = question.value ?: return
        question.value = null
        viewModelScope.launch {
            discardReport(asked.contents)
            _discarded.value = asked.row.matchId
        }
    }

    fun discardHandled() {
        _discarded.value = null
    }

    private companion object {
        /** As the shell's: long enough to ride out a configuration change. */
        val SUBSCRIPTION_GRACE = 5.seconds
    }
}
