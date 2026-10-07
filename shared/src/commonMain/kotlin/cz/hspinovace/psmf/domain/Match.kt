package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class MatchStatus {
    /** Header and lineups being filled in, before kickoff. */
    SETUP,

    /** The whistle has gone. This is the state that must survive a kill. */
    IN_PROGRESS,

    /** Full time; assessment and confirmations may still be outstanding. */
    FINISHED,

    /** Both captains and the referee have confirmed. */
    CONFIRMED,
}

/**
 * One match report in progress — the aggregate the whole app edits.
 *
 * Structured to mirror the paper form rather than to be tidy: goals and
 * personal punishments are separate blocks on page 2, so they are separate
 * fields here. [timeline] merges them for the live console, which is the
 * only place a single chronological list is wanted.
 *
 * Most fields are nullable because a report genuinely is incomplete for
 * most of its life. What makes it *exportable* is not an invariant of this
 * type but a check: see [ReportReadiness].
 */
@Serializable
data class Match(
    val id: MatchId,
    val fixtureId: FixtureId,
    val groupId: GroupId,
    val status: MatchStatus = MatchStatus.SETUP,
    val officials: RefereeAssignment? = null,
    val homeLineup: Lineup? = null,
    val awayLineup: Lineup? = null,
    /**
     * When the whistle went. **The clock is a computation, not a process**:
     * elapsed time is derived from this on demand, so nothing has to tick
     * in the background. iOS cannot run a background timer at all, and a
     * derived clock additionally cannot drift, cannot be killed, and
     * survives a reboot (TECH_STACK section 3).
     *
     * This instant and [periodBreaks] are the *whole* clock. **There is no
     * stoppage during play** (analysis section 2.6): inside a period the
     * clock runs continuously and the referee adds time, so there is
     * deliberately no accumulated-time or paused-at field to go with it.
     * What there is, is **a recorded interval between periods** -- each
     * boundary stored once as an instant, never as a pause. See [clock].
     */
    val kickoffAt: Instant? = null,
    val goals: List<GoalEvent> = emptyList(),
    /**
     * Null means the referee has not yet accounted for the `Osobní tresty`
     * block at all — which is different from having affirmed that no cards
     * were issued. See [CardsSection].
     */
    val cards: CardsSection? = null,
    val assessment: Assessment = Assessment(),
    val result: MatchResult? = null,
    val confirmations: List<Confirmation> = emptyList(),
    /**
     * Ten minutes of play a side spends a player short after a dismissal.
     *
     * **The only timer in the match with a start and a finish.** The match
     * clock itself has no stoppage during play — see `MatchClock.kt`, which
     * holds both and explains why one of them finishes and the other
     * cannot be stopped.
     */
    val powerPlays: List<PowerPlay> = emptyList(),
    /**
     * Every period boundary crossed so far.
     *
     * Grows by one entry each time the referee ends a period; that entry's
     * [PeriodBreak.nextStartedAt] is filled in when they start the next
     * one. **Not a second clock and not a pause** -- the record of where
     * the gaps are, so elapsed play can be computed by skipping them rather
     * than counting through a break that analysis section 2.6 says is not
     * part of the sixty minutes. See [PlayClock].
     */
    val periodBreaks: List<PeriodBreak> = emptyList(),
) {
    /** The clock these instants make. A value, worked out on demand; nothing ticks. */
    val clock: PlayClock get() = PlayClock(kickoffAt, periodBreaks)

    fun lineup(side: TeamSide): Lineup? =
        when (side) {
            TeamSide.HOME -> homeLineup
            TeamSide.AWAY -> awayLineup
        }

    /** Every card issued, empty if none were or the block is unaccounted for. */
    val cardEvents: List<CardEvent> get() = cards?.cards().orEmpty()

    /**
     * Goals and cards merged and ordered, for the live console's log sheet.
     *
     * By minute -- `30´+` between 29´ and the second half's 30´, `60´+`
     * last; see [Minute] -- and within one minute by recording order, so the
     * last thing written down at a minute is the last thing listed at it.
     */
    fun timeline(): List<MatchEvent> =
        (goals + cardEvents).sortedWith(
            compareBy<MatchEvent> { it.minute }.thenBy { it.sequence ?: Int.MIN_VALUE },
        )

    /**
     * The number the referee's next recording action gets; see
     * [MatchEvent.sequence]. One more than anything recorded so far.
     */
    fun nextSequence(): Int =
        (
            goals.mapNotNull { it.sequence } + cardEvents.mapNotNull { it.sequence } +
                powerPlays.mapNotNull { it.sequence }
        ).maxOrNull()
            ?.plus(1) ?: 1

    /** Looks an appearance up in either lineup. */
    fun appearance(id: AppearanceId): Appearance? = homeLineup?.appearance(id) ?: awayLineup?.appearance(id)

    /**
     * Records that a party has confirmed the report.
     *
     * **Confirming changes nothing but the confirmations.** Captains
     * acknowledge what the referee wrote; they do not contribute content,
     * and there is deliberately no way for them to. Anything else would
     * invent the reconciliation problem the paper process does not have
     * (analysis section 6).
     */
    fun confirmedBy(confirmation: Confirmation): Match =
        copy(confirmations = confirmations.filterNot { it.party == confirmation.party } + confirmation)

    fun hasConfirmationFrom(party: ConfirmingParty): Boolean = confirmations.any { it.party == party }

    /** The score implied by the recorded goals, for cross-checking `Stav`. */
    fun scoreFromGoals(): Score =
        goals.sortedBy { it.minute }.fold(Score.GOALLESS) { running, goal -> running.scoredBy(goal.side) }

    /**
     * `poločas`: the score once the first period ended.
     *
     * Null until the first period actually has -- there is no moment to
     * read a half-time score from before that, and pretending otherwise
     * would be a fabrication, not a suggestion. Every goal up to and
     * including the interval carries a minute no later than
     * [Minute.HalfTime], and every goal after it one no earlier than the
     * second half's `30´` -- the console's own clock guarantees both, see
     * [PlayClock.minuteAt] -- so the cut is a comparison on
     * [GoalEvent.minute], not on when the goal happened to be saved.
     */
    fun scoreAtEndOfFirstPeriod(): Score? {
        if (periodBreaks.isEmpty()) return null
        return goals
            .sortedBy { it.minute }
            .filter { it.minute <= Minute.HalfTime }
            .fold(Score.GOALLESS) { running, goal -> running.scoredBy(goal.side) }
    }
}

/**
 * When a period ended, and -- once pressed -- when the next one started.
 *
 * The match is in the interval between periods whenever the last entry in
 * [Match.periodBreaks] has no [nextStartedAt] yet: the minute holds there
 * rather than counting through a break that is not part of the match.
 */
@Serializable
data class PeriodBreak(
    val endedAt: Instant,
    val nextStartedAt: Instant? = null,
)
