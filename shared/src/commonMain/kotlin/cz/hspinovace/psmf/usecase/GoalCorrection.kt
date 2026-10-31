package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.afterAChangeFrom
import cz.hspinovace.psmf.domain.latestGoalMinuteAt
import kotlin.time.Instant

// A goal already in the log, corrected or removed (0.4.0, Gate 5; the testers'
// point 5, analysis section 2.12). Undo takes back only the last thing recorded;
// these reach any goal further back.
//
// **A goal is addressed by its place in the recording order** ([GoalEvent.sequence]),
// so a goal from before 0.2.0, which has none, is never corrected or removed here:
// Undo still takes it back, by the timeline, as it always did (DECISIONS 2026-10-31,
// Gate 5). **This is not versioning**: the report says what the goals now are, and
// nothing keeps what they were (analysis section 5.3 is out of the demo).

/**
 * The scorer and the minute of a goal already recorded, changed in place.
 *
 * **Refused** -- the match comes back as it was, and nothing is saved -- when there
 * is no goal with that [sequence]; when the scorer is not in the goal's own team's
 * lineup (the team of a goal is not changed in place: it is removed and recorded
 * for the other); and when the minute breaks the rules a written minute has in
 * [LogGoal]: never `60´+`, and, if [now] is given, never later than the clock.
 * Refused rather than clamped, for [LogGoal]'s reason.
 *
 * **The goal keeps its place in the recording order**, so Undo takes back what it
 * would have before. `Stav` and the half-time score follow the minute through
 * [rescored]. A correction that changes nothing saves nothing, so it cannot take
 * a signed report's signatures back; one that changes something does, as any
 * change to a goal does ([afterAChangeFrom]).
 */
class CorrectGoal(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        sequence: Int,
        scorer: AppearanceId?,
        minute: Minute,
        now: Instant? = null,
        halfLengthMinutes: Int = Minute.HALF_LENGTH,
    ): Match {
        val goal = match.goalRecordedAs(sequence) ?: return match
        if (!match.mayBeWrittenAt(minute, now, halfLengthMinutes) || !match.mayHaveScored(goal, scorer)) return match
        if (goal.scorer == scorer && goal.minute == minute) return match

        val corrected = match.goals.map { if (it == goal) it.copy(scorer = scorer, minute = minute) else it }
        val updated = match.copy(goals = corrected.rescored()).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/**
 * A goal already recorded, removed: from the list, from `Stav` and from the
 * recording order. Anything else with the same number -- a card, a whistle -- is
 * not a goal and stays. Taken back with the signatures on a signed report, as
 * any change to a goal is.
 */
class RemoveGoal(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        sequence: Int,
    ): Match {
        val goal = match.goalRecordedAs(sequence) ?: return match
        val updated = match.copy(goals = (match.goals - goal).rescored()).afterAChangeFrom(match)
        matches.save(updated)
        return updated
    }
}

/** [LogGoal]'s rules for a written minute: never `60´+`, and never later than the clock at [now]. */
private fun Match.mayBeWrittenAt(
    minute: Minute,
    now: Instant?,
    halfLengthMinutes: Int,
): Boolean =
    minute != Minute.AfterFinalWhistle &&
        (now == null || minute <= latestGoalMinuteAt(now, halfLengthMinutes))

private fun Match.goalRecordedAs(sequence: Int): GoalEvent? = goals.firstOrNull { it.sequence == sequence }

/**
 * Nobody, someone in the goal's own team's lineup, or whoever it already names: a
 * scorer whose appearance has since left the lineup is kept while the minute is
 * corrected, and is not offered again once changed.
 */
private fun Match.mayHaveScored(
    goal: GoalEvent,
    scorer: AppearanceId?,
): Boolean =
    scorer == null ||
        scorer == goal.scorer ||
        lineup(goal.side)?.appearances.orEmpty().any { it.id == scorer }
