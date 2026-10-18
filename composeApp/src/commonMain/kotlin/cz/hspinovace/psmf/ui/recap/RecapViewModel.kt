package cz.hspinovace.psmf.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.isSigned
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouSide
import cz.hspinovace.psmf.usecase.AffirmNoCards
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.WithdrawNoCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A change the recap itself can make to what the signatures attest. */
enum class RecapChange {
    AFFIRM_NO_CARDS,
    WITHDRAW_NO_CARDS,
}

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
    /**
     * **The name each tap signs under**, the one the button shows: the captain
     * marked in that team's lineup, and the referee from the header. Null when
     * there is none to sign under -- no captain marked, no referee named --
     * and then there is no button, because there is nothing to type instead.
     */
    val signsAs: Map<ConfirmingParty, String?> = emptyMap(),
    /** The final confirmations given so far, with their times. */
    val confirmed: Map<ConfirmingParty, Confirmation> = emptyMap(),
    /** True while the cards block has not been accounted for at all. */
    val cardsUnaccountedFor: Boolean = false,
    val noCardsAffirmed: Boolean = false,
    val problems: List<ReportProblem> = emptyList(),
    /**
     * A change waiting for the referee to say yes, because the report is
     * signed and the change takes the signatures back.
     */
    val withdrawing: RecapChange? = null,
    val readyToContinue: Boolean = false,
) {
    val signed: Boolean get() = confirmed.isNotEmpty()
}

sealed interface RecapEvent {
    /** One tap: the party's final confirmation, under the name the button showed. */
    data class ConfirmationGiven(
        val party: ConfirmingParty,
    ) : RecapEvent

    /** The referee striking the boxes through: no cards were issued. */
    data object NoCardsAffirmed : RecapEvent

    /** The same, taken back: the block is unaccounted for again. */
    data object NoCardsWithdrawn : RecapEvent

    data object WithdrawalConfirmed : RecapEvent

    data object WithdrawalDismissed : RecapEvent

    data object ContinuePressed : RecapEvent
}

/**
 * Screen 6. What the captains confirm, and what PSMF will receive.
 *
 * **Nothing here is typed.** The result comes from the goals, the signatures
 * are three taps under names that are already known, and the "no cards"
 * affirmation is a button that can be pressed again to take it back.
 */
@OptIn(ExperimentalTime::class)
class RecapViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildZouReport: BuildZouReport,
    private val confirmReport: ConfirmReport,
    private val affirmNoCards: AffirmNoCards,
    private val withdrawNoCards: WithdrawNoCards,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { reload() }
    }

    fun onEvent(event: RecapEvent) {
        when (event) {
            is RecapEvent.ConfirmationGiven -> {
                confirm(event.party)
            }

            RecapEvent.NoCardsAffirmed -> {
                change(RecapChange.AFFIRM_NO_CARDS)
            }

            RecapEvent.NoCardsWithdrawn -> {
                change(RecapChange.WITHDRAW_NO_CARDS)
            }

            RecapEvent.WithdrawalConfirmed -> {
                _state.value.withdrawing?.let { apply(it) }
            }

            RecapEvent.WithdrawalDismissed -> {
                _state.update { it.copy(withdrawing = null) }
            }

            RecapEvent.ContinuePressed -> {
                _state.update { it.copy(readyToContinue = true) }
            }
        }
    }

    fun continued() {
        _state.update { it.copy(readyToContinue = false) }
    }

    /** Asks first when the report is signed: the change takes the signatures back. */
    private fun change(change: RecapChange) {
        if (match?.isSigned == true) {
            _state.update { it.copy(withdrawing = change) }
        } else {
            apply(change)
        }
    }

    private fun apply(change: RecapChange) {
        _state.update { it.copy(withdrawing = null) }
        viewModelScope.launch {
            val current = match ?: return@launch
            match =
                when (change) {
                    RecapChange.AFFIRM_NO_CARDS -> affirmNoCards(current)
                    RecapChange.WITHDRAW_NO_CARDS -> withdrawNoCards(current)
                }
            reload()
        }
    }

    private fun confirm(party: ConfirmingParty) {
        val current = match ?: return
        // The name the button showed, and nothing typed: no name, no signature.
        val name = _state.value.signsAs[party]?.let { PersonName.orNull(it) } ?: return
        viewModelScope.launch {
            match = confirmReport(current, party, name, clock.now())
            reload()
        }
    }

    private suspend fun reload() {
        val current = matches.load(matchId) ?: return
        match = current
        val report = buildZouReport(current)
        _state.update {
            it.copy(
                loading = false,
                report = report,
                signsAs = report?.let { built -> signsAs(current, built) }.orEmpty(),
                confirmed = current.confirmations.associateBy { c -> c.party },
                cardsUnaccountedFor = current.cards == null,
                noCardsAffirmed = current.cards is CardsSection.NoneIssued,
                problems = current.reportProblems(),
            )
        }
    }

    private fun signsAs(
        match: Match,
        report: ZouReport,
    ): Map<ConfirmingParty, String?> =
        mapOf(
            ConfirmingParty.HOME_CAPTAIN to report.captainOf(ZouSide.HOME),
            ConfirmingParty.AWAY_CAPTAIN to report.captainOf(ZouSide.AWAY),
            ConfirmingParty.REFEREE to
                match.officials
                    ?.main
                    ?.name
                    ?.value,
        )

    private fun ZouReport.captainOf(side: ZouSide): String? =
        lineups
            .firstOrNull { it.side == side }
            ?.rows
            ?.firstOrNull { it.captain }
            ?.name
}
