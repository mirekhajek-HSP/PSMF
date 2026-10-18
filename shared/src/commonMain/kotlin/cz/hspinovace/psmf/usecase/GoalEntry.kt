package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide

/**
 * A goal being written with a minute of its own, before it is one
 * (prompt 13, part 3).
 *
 * The one-tap goal is still one tap, at the clock's minute. This is for a
 * goal the referee forgot, during the match or after the final whistle, and
 * for **any goal after the final whistle**, which has no clock minute to be
 * written at. The minute is entered the way the card sheet takes it -- a
 * number, or `30´+` -- but there is no `60´+`: no goal has one.
 */
data class GoalDraft(
    val side: TeamSide,
    /** Null is a goal with no scorer: an own goal, or one nobody could name. */
    val scorer: AppearanceId? = null,
    val minute: MinuteDraft = MinuteDraft(),
) {
    /** The minute written, or null while it is empty, nonsense, or `60´+`. */
    fun toMinute(): Minute? =
        when (minute.mark) {
            MinuteMark.PLAYED -> minute.toMinute()
            MinuteMark.HALF_TIME -> Minute.HalfTime
            MinuteMark.AFTER_FINAL_WHISTLE -> null
        }

    /**
     * What stands in the way of writing it, given the latest minute the
     * clock allows ([latest]; see [cz.hspinovace.psmf.domain.PlayClock.latestGoalMinuteAt])
     * and whether the whistle has gone. **A minute later than the clock is
     * refused, not clamped**: a goal moved to another minute without being
     * asked is a goal in the wrong place on a report the referee signs.
     */
    fun problems(
        latest: Minute,
        started: Boolean,
    ): List<GoalProblem> {
        val written = toMinute()
        return when {
            !started -> listOf(GoalProblem.NOT_STARTED)
            written == null -> listOf(GoalProblem.NO_MINUTE)
            written > latest -> listOf(GoalProblem.LATER_THAN_THE_CLOCK)
            else -> emptyList()
        }
    }
}

enum class GoalProblem {
    /** The match has not kicked off, so there is no minute to have scored in. */
    NOT_STARTED,
    NO_MINUTE,

    /** The minute has not been played yet. */
    LATER_THAN_THE_CLOCK,
}
