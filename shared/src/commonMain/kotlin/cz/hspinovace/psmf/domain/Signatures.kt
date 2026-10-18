package cz.hspinovace.psmf.domain

import kotlin.time.Instant

// What the signatures attest, and what takes them back (prompt 13, part 4).
//
// The three final confirmations -- the home captain's, the away captain's and
// the referee's -- are given at the recap, on the finished report. They say
// "this is what the report says". So when what it says changes -- the header,
// a lineup, a goal, a card (the "no cards" affirmation included), a whistle --
// they no longer do, and they are taken back: the recap asks for them again.
//
// What does NOT take them back: the assessment, the commentary and the team
// notes. They stay the referee's to finish until export (A6), and the
// captains do not sign them. A lineup's own confirmation (the captain's, before
// kickoff) is a signature, not content.

/** True once anyone has given a final confirmation. */
val Match.isSigned: Boolean get() = confirmations.isNotEmpty()

/**
 * The final confirmations taken back, because what they attested has changed.
 * A CONFIRMED report is a finished one again, and the recap asks for the
 * three of them once more.
 */
fun Match.withConfirmationsWithdrawn(): Match =
    if (confirmations.isEmpty()) {
        this
    } else {
        copy(
            confirmations = emptyList(),
            status = if (status == MatchStatus.CONFIRMED) MatchStatus.FINISHED else status,
        )
    }

/**
 * This match, as it is after a change to [before]: with the final
 * confirmations taken back **if there were any and the change touched
 * something they attest**, and unchanged otherwise.
 *
 * Every use case that changes the report passes what it is about to save
 * through here. A save that changes nothing a signature attests -- the
 * assessment, the commentary, a team note, the same lineup written again --
 * leaves the signatures where they are.
 */
fun Match.afterAChangeFrom(before: Match): Match =
    if (before.isSigned && attested() != before.attested()) withConfirmationsWithdrawn() else this

/** Everything a signature attests, and nothing it does not. */
private data class Attested(
    val officials: RefereeAssignment?,
    val homeLineup: Lineup?,
    val awayLineup: Lineup?,
    val goals: List<GoalEvent>,
    val cards: CardsSection?,
    val powerPlays: List<PowerPlay>,
    val kickoffAt: Instant?,
    val periodBreaks: List<PeriodBreak>,
    val finishedAt: Instant?,
    val pastTheFinalWhistle: Boolean,
)

private fun Match.attested() =
    Attested(
        officials = officials,
        // The captain's own confirmation under a lineup is a signature, not
        // part of what is signed.
        homeLineup = homeLineup?.copy(confirmation = null),
        awayLineup = awayLineup?.copy(confirmation = null),
        goals = goals,
        cards = cards,
        powerPlays = powerPlays,
        kickoffAt = kickoffAt,
        periodBreaks = periodBreaks,
        finishedAt = finishedAt,
        pastTheFinalWhistle = status.isPastTheFinalWhistle,
    )

/**
 * `poločas` and `Konečný výsledek`, **from the goals** and never typed.
 *
 * Full time is the score the goals make. Half time is the score at the moment
 * the first period ended -- the goals written at a minute no later than
 * `30´+`, whenever they were written -- or, for a match that never reached the
 * break, the closest true statement: the score so far.
 */
fun Match.resultFromGoals(): MatchResult {
    val fullTime = scoreFromGoals()
    return MatchResult(halfTime = scoreAtEndOfFirstPeriod() ?: fullTime, fullTime = fullTime)
}

/**
 * The result the report says: the goals', except on a report that was
 * already CONFIRMED when it carries one of its own -- typed under the old
 * rules and signed under them -- which keeps what it said.
 */
fun Match.resultForReport(): MatchResult =
    if (status == MatchStatus.CONFIRMED) result ?: resultFromGoals() else resultFromGoals()
