package cz.hspinovace.psmf.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.isPastTheFinalWhistle
import cz.hspinovace.psmf.domain.isSigned
import cz.hspinovace.psmf.domain.nextUndoStep
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.CardColour
import cz.hspinovace.psmf.usecase.CardDraft
import cz.hspinovace.psmf.usecase.CardProblem
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.EndPeriod
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.GoalDraft
import cz.hspinovace.psmf.usecase.GoalProblem
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogCorrections
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.MinuteDraft
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.StartNextPeriod
import cz.hspinovace.psmf.usecase.UndoLastEvent
import cz.hspinovace.psmf.usecase.cardCorrectionChangesNothing
import cz.hspinovace.psmf.usecase.keepsItsPlayer
import cz.hspinovace.psmf.usecase.secondYellowStandingOn
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
    /** Open only while a card is being written, or one in the log corrected. */
    val card: CardDraft? = null,
    val cardProblems: List<CardProblem> = emptyList(),
    /** The card the open sheet corrects is to be removed, waiting for the referee to say yes. */
    val removingCard: Boolean = false,
    /** The card the open sheet corrects is a first yellow its `2. ŽK` stands on: it cannot go yet. */
    val cardRemovalRefused: Boolean = false,
    /**
     * Open only while a goal is being written with a minute of its own: one
     * the referee forgot, or any goal after the final whistle.
     */
    val goal: GoalDraft? = null,
    val goalProblems: List<GoalProblem> = emptyList(),
    /** The goal the open sheet corrects is to be removed, waiting for the referee to say yes. */
    val removingGoal: Boolean = false,
    /**
     * A whistle that Undo would take back, waiting for the referee to say
     * yes: taking it back changes the clock, so it asks first.
     */
    val takeBack: UndoStep.TakesBackWhistle? = null,
    /** The report has its final confirmations: a change to what they attest takes them back. */
    val signed: Boolean = false,
    /**
     * A change waiting for the referee to say yes, because the report is signed
     * and the change would take the signatures back (prompt 13, part 4).
     */
    val withdrawing: ConsoleEvent? = null,
    /** The final whistle has gone and the referee is moving on. */
    val readyToContinue: Boolean = false,
) {
    val selected get() = entry?.side(selectedSide)

    fun cardProblem(kind: CardProblem): Boolean = kind in cardProblems

    fun goalProblem(kind: GoalProblem): Boolean = kind in goalProblems

    /** The goal in the log the open sheet corrects; null for a new goal, or with no sheet open. */
    val correcting: GoalEvent?
        get() {
            val sequence = goal?.corrects ?: return null
            return entry?.log?.firstOrNull { it is GoalEvent && it.sequence == sequence } as? GoalEvent
        }

    /**
     * The player the open card is for already has a yellow in this match.
     * Read from the row, the same fact `LogCard` reads from the match.
     */
    val cardSubjectBooked: Boolean
        get() {
            val appearance = card?.appearance ?: return false
            return (entry?.row(appearance)?.yellowsInThisMatch ?: 0) > 0
        }

    /**
     * Saving the open card sends a booked player off for a second yellow. Never for a card
     * in the log being corrected: its colour and kind are what they were.
     */
    val cardSendsOff: Boolean
        get() = card?.corrects == null && card?.sendsOffForSecondYellow(cardSubjectBooked) == true

    /** Whether the open red may be `2. ŽK`; for an unbooked player it can only be straight. */
    val cardOffersSecondYellowKind: Boolean get() = card?.offersSecondYellowKind(cardSubjectBooked) == true

    /** The kind the open red would be saved as, or null until one is chosen. */
    val cardDismissalKind: Dismissal? get() = card?.dismissalKind(cardSubjectBooked)

    /** The card in the log the open sheet corrects -- a second yellow's red -- or null. */
    val correctingCard: CardEvent?
        get() {
            val sequence = card?.corrects ?: return null
            val action =
                entry
                    ?.log
                    .orEmpty()
                    .filterIsInstance<CardEvent>()
                    .filter { it.sequence == sequence }
            return action.firstOrNull { it is RedCard } ?: action.firstOrNull()
        }

    /**
     * Who the card the open sheet corrects can be given to: its own player and, unless it
     * keeps its player (a second yellow, or a first yellow one stands on), anyone of the
     * same team with no card in this match. Empty for a new card, and for someone not in
     * the lineup, whose name is corrected as written.
     */
    val cardPlayerChoices: List<ConsoleRow>
        get() {
            val draft = card ?: return emptyList()
            val sequence = draft.corrects ?: return emptyList()
            val entry = entry ?: return emptyList()
            val cards = entry.log.filterIsInstance<CardEvent>()
            val own = (cards.firstOrNull { it.sequence == sequence }?.subject as? CardSubject.Player)?.appearance
            val others = own != null && !cards.keepsItsPlayer(sequence)
            return entry.side(draft.side).rows.filter { row ->
                row.appearanceId == own || (others && row.yellowsInThisMatch == 0 && !row.dismissed)
            }
        }
}

sealed interface ConsoleEvent {
    data object StartPressed : ConsoleEvent

    data object FinishPressed : ConsoleEvent

    /** Not the whole match -- see [EndPeriod]. */
    data object EndPeriodPressed : ConsoleEvent

    data object StartNextPeriodPressed : ConsoleEvent

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

    /** A goal with a minute of its own: the sheet, for the team on screen. */
    data class GoalWithMinuteOpened(
        val side: TeamSide,
    ) : ConsoleEvent

    data class GoalEdited(
        val draft: GoalDraft,
    ) : ConsoleEvent

    data object GoalDismissed : ConsoleEvent

    data object GoalSubmitted : ConsoleEvent

    /** A goal in the log, tapped: the sheet, filled in with it, to correct or remove it (0.4.0). */
    data class GoalInLogTapped(
        val sequence: Int,
    ) : ConsoleEvent

    /** The sheet's *Odebrat gól*: asks first. */
    data object GoalRemovePressed : ConsoleEvent

    data object GoalRemoveConfirmed : ConsoleEvent

    data object GoalRemoveDismissed : ConsoleEvent

    /** A card in the log, tapped: the card sheet, filled in with it, to correct or remove it (0.4.0). */
    data class CardInLogTapped(
        val sequence: Int,
    ) : ConsoleEvent

    /** The sheet's *Odebrat kartu*: asks first, or says why it cannot go yet. */
    data object CardRemovePressed : ConsoleEvent

    data object CardRemoveConfirmed : ConsoleEvent

    data object CardRemoveDismissed : ConsoleEvent

    /** Takes back the last thing recorded -- asking first when that is a whistle. */
    data object UndoPressed : ConsoleEvent

    data object TakeBackConfirmed : ConsoleEvent

    data object TakeBackDismissed : ConsoleEvent

    /** Yes: take the signatures back and make the change that was waiting. */
    data object WithdrawalConfirmed : ConsoleEvent

    data object WithdrawalDismissed : ConsoleEvent

    data object ContinuePressed : ConsoleEvent
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
    private val endPeriod: EndPeriod,
    private val startNextPeriod: StartNextPeriod,
    private val corrections: LogCorrections,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    private var match: Match? = null

    /** True only while a change the referee has just agreed to is being made. */
    private var withdrawalAgreed = false

    /** The report is signed and this change would take the signatures back: ask before making it. */
    private val asksFirst: Boolean get() = match?.isSigned == true && !withdrawalAgreed

    private val fromTheLog = FromTheLog()

    init {
        viewModelScope.launch { reload() }
    }

    fun now(): Instant = clock.now()

    fun continued() {
        _state.update { it.copy(readyToContinue = false) }
    }

    fun onEvent(event: ConsoleEvent) {
        if (handleLifecycleEvent(event)) return
        if (handleGoalEvent(event)) return
        if (handleCardEvent(event)) return
        if (fromTheLog.handle(event)) return
        when (event) {
            is ConsoleEvent.SideSelected -> {
                _state.update { it.copy(selectedSide = event.side) }
            }

            ConsoleEvent.ContinuePressed -> {
                _state.update { it.copy(readyToContinue = true) }
            }

            // The dialog that asked has already said, when it applies, that the
            // signatures go with the whistle: one question, not two.
            ConsoleEvent.TakeBackConfirmed -> {
                _state.update { it.copy(takeBack = null) }
                record { undoLastEvent(it) }
            }

            // Either question answered No: only one of them is ever open.
            ConsoleEvent.TakeBackDismissed, ConsoleEvent.WithdrawalDismissed -> {
                _state.update { it.copy(takeBack = null, withdrawing = null) }
            }

            ConsoleEvent.WithdrawalConfirmed -> {
                val pending = _state.value.withdrawing
                _state.update { it.copy(withdrawing = null) }
                if (pending != null) {
                    withdrawalAgreed = true
                    onEvent(pending)
                    withdrawalAgreed = false
                }
            }

            // Handled by the lifecycle, goal and card handlers, which already returned.
            else -> {}
        }
    }

    /**
     * The card sheet, for a new card or for one already in the log (opened by
     * [FromTheLog]). Split from [onEvent] as the goals were.
     */
    private fun handleCardEvent(event: ConsoleEvent): Boolean {
        when (event) {
            is ConsoleEvent.CardOpened -> {
                _state.update { it.withCardOpened(event, clock.now()) }
            }

            is ConsoleEvent.CardEdited -> {
                _state.update { it.copy(card = event.draft, cardProblems = emptyList(), cardRemovalRefused = false) }
            }

            ConsoleEvent.CardDismissed -> {
                _state.update { it.withCardClosed() }
            }

            ConsoleEvent.CardSubmitted -> {
                submitCard()
            }

            else -> {
                return false
            }
        }
        return true
    }

    /**
     * Start, finish, undo, and the two new period-boundary events -- every
     * event here is a single instant handed to a use case and nothing
     * else. Split from [onEvent] once the number of event types alone (not
     * their handling) pushed that function's complexity over the limit.
     */
    private fun handleLifecycleEvent(event: ConsoleEvent): Boolean {
        when (event) {
            ConsoleEvent.StartPressed -> {
                _state.guarded(asksFirst, event) { record { startMatch(it, clock.now()) } }
            }

            ConsoleEvent.FinishPressed -> {
                record { finishMatch(it, clock.now()) }
            }

            ConsoleEvent.EndPeriodPressed -> {
                _state.guarded(asksFirst, event) { record { endPeriod(it, clock.now()) } }
            }

            ConsoleEvent.StartNextPeriodPressed -> {
                _state.guarded(asksFirst, event) { record { startNextPeriod(it, clock.now()) } }
            }

            // Undo takes back the last thing recorded -- a goal, a card, or a
            // whistle, whichever came last. A goal or a card goes at once, as it
            // always did. A whistle asks first, because taking it back changes
            // the clock: the dialog says which, and nothing happens until it is
            // answered. Nothing here edits an instant; see Whistles.kt.
            ConsoleEvent.UndoPressed -> {
                when (val step = match?.nextUndoStep()) {
                    null -> {}

                    is UndoStep.TakesBackWhistle -> {
                        _state.update { it.copy(takeBack = step) }
                    }

                    UndoStep.Event -> {
                        _state.guarded(asksFirst, event) { record { undoLastEvent(it) } }
                    }
                }
            }

            else -> {
                return false
            }
        }
        return true
    }

    /**
     * The goal sheet, and the goals that cannot be one tap.
     *
     * A goal is one tap at the clock's minute while the match is on. After
     * the final whistle the clock has no minute to give -- `60´+` is not one
     * a goal can have -- so the same tap opens the sheet, with the scorer
     * already chosen and the minute left to write.
     */
    private fun handleGoalEvent(event: ConsoleEvent): Boolean {
        val tapped = { side: TeamSide, scorer: AppearanceId? ->
            val entry = _state.value.entry
            if (entry != null && entry.status.isPastTheFinalWhistle) {
                _state.update { it.withGoalOpened(GoalDraft(side, scorer)) }
            } else if (entry != null) {
                val now = clock.now()
                val minute = _state.value.minuteAt(now)
                _state.guarded(asksFirst, event) {
                    record { logGoal(it, side, scorer, minute, now, entry.halfLengthMinutes) }
                }
            }
        }
        when (event) {
            is ConsoleEvent.GoalScoredBy -> {
                _state.value.sideOf(event.appearanceId)?.let { tapped(it, event.appearanceId) }
            }

            is ConsoleEvent.GoalWithNoScorer -> {
                tapped(event.side, null)
            }

            is ConsoleEvent.GoalWithMinuteOpened -> {
                _state.update { it.withGoalOpened(GoalDraft(event.side)) }
            }

            is ConsoleEvent.GoalEdited -> {
                _state.update { it.copy(goal = event.draft, goalProblems = emptyList()) }
            }

            ConsoleEvent.GoalDismissed -> {
                _state.update { it.copy(goal = null, goalProblems = emptyList(), removingGoal = false) }
            }

            ConsoleEvent.GoalSubmitted -> {
                submitGoal()
            }

            else -> {
                return false
            }
        }
        return true
    }

    /**
     * A goal or a card already in the log (0.4.0): a row tapped opens its sheet filled in
     * with it, which saves a correction ([submitGoal], [submitCard]) or, after asking,
     * removes it. Only what has a place in the recording order is offered (see
     * [cz.hspinovace.psmf.usecase.CorrectGoal], [cz.hspinovace.psmf.usecase.CorrectCard]).
     * Its own class because the console's own functions reached detekt's limit with it.
     */
    private inner class FromTheLog {
        fun handle(event: ConsoleEvent): Boolean = goal(event) || card(event)

        private fun goal(event: ConsoleEvent): Boolean {
            val correcting = _state.value.goal?.corrects
            when (event) {
                is ConsoleEvent.GoalInLogTapped -> {
                    match?.goals?.firstOrNull { it.sequence == event.sequence }?.let { goal ->
                        val draft = GoalDraft(goal.side, goal.scorer, MinuteDraft.of(goal.minute), event.sequence)
                        _state.update { it.withGoalOpened(draft) }
                    }
                }

                ConsoleEvent.GoalRemovePressed -> {
                    if (correcting != null) _state.update { it.copy(removingGoal = true) }
                }

                ConsoleEvent.GoalRemoveDismissed -> {
                    _state.update { it.copy(removingGoal = false) }
                }

                // The question has already said, when it applies, that the signatures
                // go with the goal: one question, not two.
                ConsoleEvent.GoalRemoveConfirmed -> {
                    if (correcting != null) {
                        _state.update { it.copy(removingGoal = false, goal = null, goalProblems = emptyList()) }
                        record { corrections.removeGoal(it, correcting) }
                    }
                }

                else -> {
                    return false
                }
            }
            return true
        }

        /**
         * A second yellow opens as its red, since the two are one action. A first yellow its
         * `2. ŽK` stands on is not removed: the sheet says so instead of asking.
         */
        private fun card(event: ConsoleEvent): Boolean {
            val correcting = _state.value.card?.corrects
            when (event) {
                is ConsoleEvent.CardInLogTapped -> {
                    match?.cardEvents.orEmpty().correctionDraft(event.sequence)?.let { draft ->
                        _state.update { it.withCardClosed().copy(card = draft) }
                    }
                }

                ConsoleEvent.CardRemovePressed -> {
                    if (correcting != null) {
                        val refused = match?.cardEvents.orEmpty().secondYellowStandingOn(correcting) != null
                        _state.update { it.copy(removingCard = !refused, cardRemovalRefused = refused) }
                    }
                }

                ConsoleEvent.CardRemoveDismissed -> {
                    _state.update { it.copy(removingCard = false) }
                }

                // The question has already said, when it applies, that the signatures go
                // with the card: one question, not two.
                ConsoleEvent.CardRemoveConfirmed -> {
                    if (correcting != null) {
                        _state.update { it.withCardClosed() }
                        record { corrections.removeCard(it, correcting) }
                    }
                }

                else -> {
                    return false
                }
            }
            return true
        }
    }

    private fun submitGoal() {
        val draft = _state.value.goal ?: return
        val entry = _state.value.entry ?: return
        val now = clock.now()
        val problems = draft.problems(entry.latestGoalMinuteAt(now), entry.started)
        val minute = draft.toMinute()
        if (problems.isNotEmpty() || minute == null) {
            _state.update { it.copy(goalProblems = problems) }
            return
        }
        val current = match ?: return
        val corrects = draft.corrects
        // A correction that changes nothing is not a change: no question, nothing saved.
        if (draft.changesNothingIn(current, minute)) {
            _state.update { it.copy(goal = null, goalProblems = emptyList()) }
        } else {
            _state.guarded(asksFirst, ConsoleEvent.GoalSubmitted) {
                viewModelScope.launch {
                    match =
                        if (corrects != null) {
                            corrections.correctGoal(
                                current,
                                corrects,
                                draft.scorer,
                                minute,
                                now,
                                entry.halfLengthMinutes,
                            )
                        } else {
                            logGoal(current, draft.side, draft.scorer, minute, now, entry.halfLengthMinutes)
                        }
                    _state.update { it.copy(goal = null, goalProblems = emptyList()) }
                    reload()
                }
            }
        }
    }

    private fun submitCard() {
        val draft = _state.value.card ?: return
        val problems = draft.problems(_state.value.cardSubjectBooked)
        if (problems.isNotEmpty()) {
            _state.update { it.copy(cardProblems = problems) }
            return
        }
        val current = match ?: return
        val halfLength = _state.value.entry?.halfLengthMinutes ?: Minute.HALF_LENGTH
        val save = draft.saveIn(current)
        // A correction that changes nothing is not a change: no question, nothing saved.
        if (save == CardSave.Unchanged) {
            _state.update { it.withCardClosed() }
            return
        }
        _state.guarded(asksFirst, ConsoleEvent.CardSubmitted) {
            viewModelScope.launch {
                val now = clock.now()
                match =
                    when (save) {
                        // A second yellow records both cards; a dismissal starts ten
                        // minutes of play from the minute written. See LogCard.
                        CardSave.New -> {
                            logCard(current, draft, now, halfLength) ?: current
                        }

                        // A red's power play follows its minute. See CorrectCard.
                        is CardSave.Correction -> {
                            corrections.correctCard(
                                current,
                                save.sequence,
                                save.subject,
                                save.reason,
                                save.minute,
                                now,
                                halfLength,
                            )
                        }

                        CardSave.Unchanged -> {
                            current
                        }
                    }
                _state.update { it.withCardClosed() }
                reload()
            }
        }
    }

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
        _state.update { it.copy(loading = false, entry = buildConsoleEntry(current), signed = current.isSigned) }
    }
}

private fun ConsoleUiState.sideOf(appearanceId: AppearanceId): TeamSide? {
    val entry = entry ?: return null
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
private fun ConsoleUiState.minuteAt(now: Instant): Minute = entry?.minuteAt(now) ?: Minute.Played(0)

/**
 * A correction that would leave its goal as it is, or of a goal no longer there:
 * nothing to save, and nothing to ask a signed report about. False for a new goal.
 */
private fun GoalDraft.changesNothingIn(
    match: Match,
    minute: Minute,
): Boolean {
    val sequence = corrects ?: return false
    val goal = match.goals.firstOrNull { it.sequence == sequence } ?: return true
    return goal.scorer == scorer && goal.minute == minute
}

private fun ConsoleUiState.withGoalOpened(draft: GoalDraft): ConsoleUiState =
    copy(goal = draft, goalProblems = emptyList())

private fun ConsoleUiState.withCardOpened(
    event: ConsoleEvent.CardOpened,
    now: Instant,
): ConsoleUiState =
    copy(
        card =
            CardDraft(
                side = event.side,
                appearance = event.appearanceId,
                // Pre-filled from the clock; the referee can change it,
                // including to 30´+ or 60´+.
                minute = MinuteDraft.of(minuteAt(now)),
            ),
        cardProblems = emptyList(),
    )

private fun ConsoleUiState.withCardClosed(): ConsoleUiState =
    copy(card = null, cardProblems = emptyList(), removingCard = false, cardRemovalRefused = false)

/**
 * What saving the card sheet does: record a new card, correct one already in the log, or
 * nothing at all -- a correction that would leave its card as it is, or one whose draft
 * is not whole, which [CardDraft.problems] has already refused before this is asked.
 */
private sealed interface CardSave {
    data object New : CardSave

    data object Unchanged : CardSave

    data class Correction(
        val sequence: Int,
        val subject: CardSubject,
        val reason: CardReason,
        val minute: Minute,
    ) : CardSave
}

private fun CardDraft.saveIn(match: Match): CardSave {
    val sequence = corrects ?: return CardSave.New
    val subject = subject()
    val minute = minute.toMinute()
    if (subject == null || minute == null || reason.isBlank()) return CardSave.Unchanged
    val reason = CardReason(reason.trim())
    return if (match.cardCorrectionChangesNothing(sequence, subject, reason, minute)) {
        CardSave.Unchanged
    } else {
        CardSave.Correction(sequence, subject, reason, minute)
    }
}

/** The sheet's draft for the card in the log recorded as [sequence] -- a second yellow's red -- or null. */
private fun List<CardEvent>.correctionDraft(sequence: Int): CardDraft? {
    val action = filter { it.sequence == sequence }
    return (action.firstOrNull { it is RedCard } ?: action.firstOrNull())?.toCorrection(sequence)
}

/** The sheet's draft for a card already in the log: as it was recorded, and which one it corrects. */
private fun CardEvent.toCorrection(sequence: Int): CardDraft =
    CardDraft(
        side = side,
        appearance = (subject as? CardSubject.Player)?.appearance,
        namedPerson = (subject as? CardSubject.NamedPerson)?.name?.value.orEmpty(),
        colour = if (this is RedCard) CardColour.RED else CardColour.YELLOW,
        dismissal = (this as? RedCard)?.dismissal,
        reason = reason.text,
        minute = MinuteDraft.of(minute),
        corrects = sequence,
    )

/**
 * Makes [change], **or asks first** when [askFirst]: the report is signed and
 * the change would take the signatures back. The dialog says so, and nothing is
 * saved until it is answered.
 */
private fun MutableStateFlow<ConsoleUiState>.guarded(
    askFirst: Boolean,
    event: ConsoleEvent,
    change: () -> Unit,
) {
    if (askFirst) update { it.copy(withdrawing = event) } else change()
}
