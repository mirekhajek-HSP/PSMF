package cz.hspinovace.psmf.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.CardDraft
import cz.hspinovace.psmf.usecase.CardProblem
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.MinuteDraft
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.UndoLastEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class ConsoleUiState(
    val loading: Boolean = true,
    val entry: ConsoleEntry? = null,
    val selectedSide: TeamSide = TeamSide.HOME,
    /** Open only while a card is being written. */
    val card: CardDraft? = null,
    val cardProblems: List<CardProblem> = emptyList(),
) {
    val selected get() = entry?.side(selectedSide)

    fun cardProblem(kind: CardProblem): Boolean = kind in cardProblems
}

sealed interface ConsoleEvent {
    data object StartPressed : ConsoleEvent

    data object FinishPressed : ConsoleEvent

    data class SideSelected(
        val side: TeamSide,
    ) : ConsoleEvent

    /** One tap. The minute comes from the clock, which is always running. */
    data class GoalScoredBy(
        val appearanceId: AppearanceId,
    ) : ConsoleEvent

    /** `13´ — 2:1` in the worked example: a goal with no scorer. */
    data class GoalWithNoScorer(
        val side: TeamSide,
    ) : ConsoleEvent

    data class CardOpened(
        val appearanceId: AppearanceId?,
        val side: TeamSide,
    ) : ConsoleEvent

    data class CardEdited(
        val draft: CardDraft,
    ) : ConsoleEvent

    data object CardDismissed : ConsoleEvent

    data object CardSubmitted : ConsoleEvent

    data object UndoPressed : ConsoleEvent
}

/**
 * Screen 4.
 *
 * **The clock is not held here.** [now] is read from the [Clock] each time
 * something is logged, and the screen re-reads it once a second to draw
 * the minute. Nothing ticks: the elapsed time is a subtraction from the
 * stored kickoff instant, so it survives the process dying and cannot
 * drift — and iOS cannot run a background timer at all.
 */
@OptIn(ExperimentalTime::class)
class ConsoleViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildConsoleEntry: BuildConsoleEntry,
    private val startMatch: StartMatch,
    private val finishMatch: FinishMatch,
    private val logGoal: LogGoal,
    private val logCard: LogCard,
    private val undoLastEvent: UndoLastEvent,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { reload() }
    }

    fun now(): Instant = clock.now()

    fun onEvent(event: ConsoleEvent) {
        when (event) {
            ConsoleEvent.StartPressed -> {
                record { startMatch(it, clock.now()) }
            }

            ConsoleEvent.FinishPressed -> {
                record { finishMatch(it) }
            }

            ConsoleEvent.UndoPressed -> {
                record { undoLastEvent(it) }
            }

            is ConsoleEvent.SideSelected -> {
                _state.update { it.copy(selectedSide = event.side) }
            }

            is ConsoleEvent.GoalScoredBy -> {
                val side = sideOf(event.appearanceId) ?: return
                record { logGoal(it, side, event.appearanceId, currentMinute()) }
            }

            is ConsoleEvent.GoalWithNoScorer -> {
                record { logGoal(it, event.side, null, currentMinute()) }
            }

            is ConsoleEvent.CardOpened -> {
                openCard(event)
            }

            is ConsoleEvent.CardEdited -> {
                _state.update { it.copy(card = event.draft, cardProblems = emptyList()) }
            }

            ConsoleEvent.CardDismissed -> {
                _state.update { it.copy(card = null, cardProblems = emptyList()) }
            }

            ConsoleEvent.CardSubmitted -> {
                submitCard()
            }
        }
    }

    private fun openCard(event: ConsoleEvent.CardOpened) {
        _state.update {
            it.copy(
                card =
                    CardDraft(
                        side = event.side,
                        appearance = event.appearanceId,
                        // Pre-filled from the clock; the referee can change it,
                        // including to 30´+ or 60´+.
                        minute = MinuteDraft.of(currentMinute()),
                    ),
                cardProblems = emptyList(),
            )
        }
    }

    private fun submitCard() {
        val draft = _state.value.card ?: return
        val problems = draft.problems()
        if (problems.isNotEmpty()) {
            _state.update { it.copy(cardProblems = problems) }
            return
        }
        val current = match ?: return
        viewModelScope.launch {
            // A dismissal starts a power play from this instant; see LogCard.
            match = logCard(current, draft, clock.now()) ?: current
            _state.update { it.copy(card = null, cardProblems = emptyList()) }
            reload()
        }
    }

    private fun sideOf(appearanceId: AppearanceId): TeamSide? {
        val entry = _state.value.entry ?: return null
        return when {
            entry.home.row(appearanceId) != null -> TeamSide.HOME
            entry.away.row(appearanceId) != null -> TeamSide.AWAY
            else -> null
        }
    }

    /**
     * The minute an event gets, if the referee does not say otherwise.
     *
     * Before kickoff there is no clock, so events land at minute 0 rather
     * than at nothing — a goal before the whistle is a mistake to undo, not
     * a state to model.
     */
    private fun currentMinute() = _state.value.entry?.minuteAt(clock.now()) ?: Minute.Played(0)

    private fun record(change: suspend (Match) -> Match) {
        val current = match ?: return
        viewModelScope.launch {
            match = change(current)
            reload()
        }
    }

    private suspend fun reload() {
        val current = matches.load(matchId) ?: return
        match = current
        _state.update { it.copy(loading = false, entry = buildConsoleEntry(current)) }
    }
}
