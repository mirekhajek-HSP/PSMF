package cz.hspinovace.psmf.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.usecase.AffirmNoCards
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.RecordResult
import cz.hspinovace.psmf.usecase.ResultDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RecapUiState(
    val loading: Boolean = true,
    /**
     * **The same value the export writes.**
     *
     * Not a summary built for the screen: on paper a captain signs one
     * sheet they can see in full, and here they confirm a screen — so
     * whatever is not on it is not being checked (analysis section 5.5).
     * Rendering the report itself is what makes that true by construction
     * rather than by diligence.
     */
    val report: ZouReport? = null,
    val result: ResultDraft = ResultDraft(),
    val confirmed: Set<ConfirmingParty> = emptySet(),
    /** True while the cards block has not been accounted for at all. */
    val cardsUnaccountedFor: Boolean = false,
    val noCardsAffirmed: Boolean = false,
    val problems: List<ReportProblem> = emptyList(),
    /** Open while somebody is being asked to confirm. */
    val confirming: ConfirmingParty? = null,
    val confirmingName: String = "",
    val confirmingAsDeputy: Boolean = false,
    val confirmingRejected: Boolean = false,
    val readyToContinue: Boolean = false,
)

sealed interface RecapEvent {
    data class ResultEdited(
        val draft: ResultDraft,
    ) : RecapEvent

    data class ConfirmationOpened(
        val party: ConfirmingParty,
    ) : RecapEvent

    data object ConfirmationDismissed : RecapEvent

    data class ConfirmingNameChanged(
        val name: String,
    ) : RecapEvent

    data class ConfirmingDeputyChanged(
        val asDeputy: Boolean,
    ) : RecapEvent

    data object ConfirmationSubmitted : RecapEvent

    /** The referee striking the boxes through: no cards were issued. */
    data object NoCardsAffirmed : RecapEvent

    data object ContinuePressed : RecapEvent
}

/** Screen 6. What the captains confirm, and what PSMF will receive. */
@OptIn(ExperimentalTime::class)
class RecapViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildZouReport: BuildZouReport,
    private val recordResult: RecordResult,
    private val confirmReport: ConfirmReport,
    private val affirmNoCards: AffirmNoCards,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { reload(firstLoad = true) }
    }

    fun onEvent(event: RecapEvent) {
        when (event) {
            is RecapEvent.ResultEdited -> {
                editResult(event.draft)
            }

            is RecapEvent.ConfirmationOpened -> {
                _state.update {
                    it.copy(confirming = event.party, confirmingName = "", confirmingRejected = false)
                }
            }

            RecapEvent.ConfirmationDismissed -> {
                _state.update { it.copy(confirming = null, confirmingRejected = false) }
            }

            is RecapEvent.ConfirmingNameChanged -> {
                _state.update { it.copy(confirmingName = event.name, confirmingRejected = false) }
            }

            is RecapEvent.ConfirmingDeputyChanged -> {
                _state.update { it.copy(confirmingAsDeputy = event.asDeputy) }
            }

            RecapEvent.ConfirmationSubmitted -> {
                submitConfirmation()
            }

            RecapEvent.NoCardsAffirmed -> {
                viewModelScope.launch {
                    match?.let { match = affirmNoCards(it) }
                    reload()
                }
            }

            RecapEvent.ContinuePressed -> {
                _state.update { it.copy(readyToContinue = true) }
            }
        }
    }

    fun continued() {
        _state.update { it.copy(readyToContinue = false) }
    }

    private fun editResult(draft: ResultDraft) {
        _state.update { it.copy(result = draft) }
        viewModelScope.launch {
            match?.let { match = recordResult(it, draft) }
            reload()
        }
    }

    private fun submitConfirmation() {
        val party = _state.value.confirming ?: return
        val current = match ?: return
        viewModelScope.launch {
            val updated =
                confirmReport(
                    match = current,
                    party = party,
                    by = _state.value.confirmingName,
                    asDeputy = _state.value.confirmingAsDeputy,
                    at = clock.now(),
                )
            if (updated == null) {
                _state.update { it.copy(confirmingRejected = true) }
                return@launch
            }
            match = updated
            _state.update {
                it.copy(confirming = null, confirmingName = "", confirmingAsDeputy = false)
            }
            reload()
        }
    }

    private suspend fun reload(firstLoad: Boolean = false) {
        val current = matches.load(matchId) ?: return
        match = current
        _state.update {
            it.copy(
                loading = false,
                report = buildZouReport(current),
                // Pre-filled from the goals the first time, then left alone:
                // the referee's number is the one that counts.
                result = if (firstLoad) initialResult(current) else it.result,
                confirmed = current.confirmations.map { c -> c.party }.toSet(),
                cardsUnaccountedFor = current.cards == null,
                noCardsAffirmed = current.cards is cz.hspinovace.psmf.domain.CardsSection.NoneIssued,
                problems = current.reportProblems(),
            )
        }
    }

    private fun initialResult(match: Match): ResultDraft =
        if (match.result != null) ResultDraft.from(match.result) else ResultDraft.suggestedFrom(match)
}
