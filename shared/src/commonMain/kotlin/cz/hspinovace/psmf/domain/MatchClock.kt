package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The two timers a match has, and how each of them runs.
//
// NO STOPPAGE DURING PLAY.
//
// Analysis section 2.6: "2 x 30 minutes gross time... The clock runs
// continuously; the referee may add time." Inside a period there is NO
// STOPPAGE in maly fotbal -- not for injuries, not for a break in play, not
// for anything. The referee adds time at the end instead.
//
// golblok pauses its clock, and that behaviour must not be carried across.
// It is exactly the kind of thing that gets copied from a familiar codebase
// without anyone noticing it is wrong here, so it is written down rather
// than left to be inferred.
//
// The consequence is that there is deliberately NO PAUSE, STOP, RESUME OR
// ADJUST OPERATION DURING PLAY ANYWHERE IN THIS FILE, and there must not be
// one.
//
// What the clock does have is A RECORDED INTERVAL BETWEEN PERIODS (2026-08-31:
// a half-time exists). Ending a period and starting the next are facts about
// the match, each stored once as an instant exactly like the kickoff, and
// never a pause: nothing here can stop a period that is running. The interval
// is not part of the sixty minutes, so play is counted by walking the
// recorded boundaries and skipping the gaps. Every answer is a computation
// over stored instants, which additionally cannot drift, cannot be killed
// with the process, and survives a reboot -- the resolution TECH_STACK
// section 3 reaches for iOS, which cannot run a background timer at all.
//
// THE POWER PLAY runs on that same clock: ten minutes OF PLAY. It holds
// through the interval rather than expiring in it, and it is the one timer
// here that starts and finishes.

/**
 * Where play stands, worked out from the instants a match stores: the
 * whistle, and where its intervals are.
 *
 * A value, not a process. Nothing in it ticks; ask it about any instant and
 * it answers from [kickoffAt] and [periodBreaks] alone.
 */
data class PlayClock(
    val kickoffAt: Instant?,
    val periodBreaks: List<PeriodBreak> = emptyList(),
) {
    /** True once a period has ended and the next one has not started yet. */
    val inPeriodInterval: Boolean
        get() = periodBreaks.isNotEmpty() && periodBreaks.last().nextStartedAt == null

    /**
     * Which period is running, 1-based. Null before kickoff, and **null
     * during the interval between periods** -- nothing is running on a break.
     */
    fun currentPeriodNumber(): Int? {
        if (kickoffAt == null) return null
        if (inPeriodInterval) return null
        return periodBreaks.size + 1
    }

    /**
     * Play elapsed by [at], all periods together, or null before kickoff.
     *
     * **Holds during an interval rather than counting through it**, and
     * answers for any instant, not only the present one: an instant before
     * a break counts only the play before it. Zero for an instant before the
     * whistle.
     */
    fun elapsedAt(at: Instant): Duration? {
        if (kickoffAt == null) return null
        return periods().fold(Duration.ZERO) { total, (start, end) ->
            total + (minOf(at, end ?: at) - start).coerceAtLeast(Duration.ZERO)
        }
    }

    /** Play elapsed when period [number] (1-based) kicked off; null if it has not. */
    fun elapsedAtStartOfPeriod(number: Int): Duration? =
        periods().getOrNull(number - 1)?.let { (start, _) -> elapsedAt(start) }

    /**
     * Every period that has kicked off, as its wall-clock start and -- once
     * it has ended -- its end. Only the last can still be open. Empty
     * before kickoff.
     */
    private fun periods(): List<Pair<Instant, Instant?>> {
        val kickoff = kickoffAt ?: return emptyList()
        val starts = listOf(kickoff) + periodBreaks.mapNotNull { it.nextStartedAt }
        return starts.mapIndexed { index, start -> start to periodBreaks.getOrNull(index)?.endedAt }
    }

    /**
     * `Čas` as the form writes it at [now].
     *
     * Period *k* restarts at (*k*−1) × [halfLengthMinutes], whatever added
     * time the period before it had: end the first half at 32 minutes and
     * the second still kicks off at `30´`. `30´+` already absorbs the first
     * half's added time, which only makes sense if the second restarts at
     * 30 (DECISIONS 2026-10-07; psmf.cz agrees).
     *
     * `30´+` is the first period past its nominal length and the interval
     * after it. `60´+` is the final whistle -- not merely the last period
     * running long, which stays an ordinary [Minute.Played].
     */
    fun minuteAt(
        now: Instant,
        status: MatchStatus,
        halfLengthMinutes: Int,
    ): Minute? {
        val period = currentPeriodNumber()
        return when {
            kickoffAt == null -> null

            status.isPastTheFinalWhistle -> Minute.AfterFinalWhistle

            // Kicked off and nothing running: the interval.
            period == null -> Minute.HalfTime

            else -> writtenMinute(period, now, halfLengthMinutes)
        }
    }

    private fun writtenMinute(
        period: Int,
        now: Instant,
        halfLengthMinutes: Int,
    ): Minute {
        val intoPeriod = (elapsedAt(now) ?: Duration.ZERO) - (elapsedAtStartOfPeriod(period) ?: Duration.ZERO)
        val minutes = intoPeriod.inWholeMinutes.toInt().coerceAtLeast(0)
        return if (period == 1 && minutes >= halfLengthMinutes) {
            Minute.HalfTime
        } else {
            Minute.Played((period - 1) * halfLengthMinutes + minutes)
        }
    }

    /**
     * The instant play stood at [elapsed], on the same wall clock as the
     * kickoff. Inverse of [elapsedAt] inside a period; a moment that falls
     * past the end of a period whose successor has not started yet is that
     * period's end. Null before kickoff.
     */
    fun instantAtElapsed(elapsed: Duration): Instant? {
        val all = periods()
        var remaining = elapsed.coerceAtLeast(Duration.ZERO)
        for ((start, end) in all) {
            val length = end?.let { it - start }
            if (length == null || remaining <= length) return start + remaining
            remaining -= length
        }
        return all.lastOrNull()?.second
    }

    /**
     * Where on this clock something written at [minute] happened, as near as
     * the minute says -- the start of it. Null when the form's notation does
     * not place it on the clock at all (`60´+`), or before kickoff.
     *
     * A played minute belongs to the period whose range holds it: under
     * [halfLengthMinutes] the first, then the second, and so on, capped at
     * the latest period that has started. `30´+` is the end of the first
     * period once it has ended, or nothing yet if it is still running.
     */
    fun instantOfMinute(
        minute: Minute,
        halfLengthMinutes: Int,
    ): Instant? {
        val elapsed =
            when (minute) {
                is Minute.Played -> elapsedAtPlayedMinute(minute.value, halfLengthMinutes)
                Minute.HalfTime -> periodBreaks.firstOrNull()?.let { elapsedAt(it.endedAt) }
                Minute.AfterFinalWhistle -> null
            } ?: return null
        return instantAtElapsed(elapsed)
    }

    private fun elapsedAtPlayedMinute(
        value: Int,
        halfLengthMinutes: Int,
    ): Duration? {
        if (kickoffAt == null || halfLengthMinutes <= 0) return null
        val startedPeriods = 1 + periodBreaks.count { it.nextStartedAt != null }
        val period = (value / halfLengthMinutes + 1).coerceAtMost(startedPeriods)
        val periodStart = elapsedAtStartOfPeriod(period) ?: return null
        val written = periodStart + (value - (period - 1) * halfLengthMinutes).minutes
        val periodEnd = periodBreaks.getOrNull(period - 1)?.let { elapsedAt(it.endedAt) }
        return if (periodEnd != null) written.coerceAtMost(periodEnd) else written
    }
}

/** The final whistle has gone: `60´+` from here, and nobody plays short any more. */
val MatchStatus.isPastTheFinalWhistle: Boolean
    get() = this == MatchStatus.FINISHED || this == MatchStatus.CONFIRMED

/**
 * Play elapsed at [now], or null before kickoff. A pure function of
 * [Match.kickoffAt], [Match.periodBreaks] and [now]; see [PlayClock].
 */
fun Match.elapsedAt(now: Instant): Duration? = clock.elapsedAt(now)

/** True once a period has ended and the next one has not started yet. */
val Match.inPeriodInterval: Boolean
    get() = clock.inPeriodInterval

/** Which period is running, 1-based; null before kickoff and during an interval. */
fun Match.currentPeriodNumber(): Int? = clock.currentPeriodNumber()

/**
 * Whole minutes of play so far, from 0, all periods together.
 *
 * Not what the form writes in the second half, which restarts at the half
 * length -- that is [PlayClock.minuteAt]. This is the raw amount of play.
 */
fun Match.minutesPlayedAt(now: Instant): Int? = elapsedAt(now)?.inWholeMinutes?.toInt()

/**
 * A dismissed player's team plays a player short for ten minutes of play
 * (analysis section 2.6).
 *
 * Four properties, all of which are easy to get wrong and each of which
 * has a test:
 *
 * - **Ten minutes fixed.** It is *not* shortened by a goal, unlike the
 *   power play in ice hockey that the name is borrowed from.
 * - **Unaffected by further dismissals.** A second dismissal starts a
 *   second, independent period; it does not extend or restart the first.
 * - **Ten minutes of play, not of the wall clock.** It holds through the
 *   interval between periods, so a red at 25´ leaves the side short at the
 *   start of the second half however long the break was. That is why it is
 *   measured with a [PlayClock] and has no `endsAt`: when it ends depends
 *   on breaks that may not have happened yet.
 * - **None after the final whistle, and none for someone not in the
 *   lineup** -- see `LogCard`, which decides whether one starts.
 */
@Serializable
data class PowerPlay(
    /** The side that is a player short — the side that had a player sent off. */
    val shortHandedSide: TeamSide,
    /**
     * Where the ten minutes of play began, as an instant on the same wall
     * clock as the kickoff. Usually the moment the card was saved; for a
     * red written up late, the start of the minute the referee wrote.
     */
    val startedAt: Instant,
    /**
     * The minute as the referee wrote it against the card, kept so the
     * console can show the two together. Not used in the arithmetic.
     */
    val dismissedAtMinute: Minute,
    /**
     * The recording action that started it, shared with the red card it
     * belongs to, so that undoing the card takes this back with it. Null for
     * a power play recorded before 0.2.0; see [MatchEvent.sequence].
     */
    val sequence: Int? = null,
) {
    /** Play still to go at [now]: ten minutes at the start, never negative. */
    fun remainingAt(
        clock: PlayClock,
        now: Instant,
    ): Duration {
        val played = (clock.elapsedAt(now) ?: Duration.ZERO) - (clock.elapsedAt(startedAt) ?: Duration.ZERO)
        return (LENGTH - played.coerceAtLeast(Duration.ZERO)).coerceAtLeast(Duration.ZERO)
    }

    /** In force at [now]: begun, and with play still to go. Holds through an interval. */
    fun isRunningAt(
        clock: PlayClock,
        now: Instant,
    ): Boolean = now >= startedAt && remainingAt(clock, now) > Duration.ZERO

    companion object {
        /** Ten minutes of play, fixed. Not shortened by a goal. */
        val LENGTH: Duration = 10.minutes
    }
}

/**
 * The power plays in force at [now]; usually none, occasionally two, and
 * **none at all once the final whistle has gone** -- nothing counts down
 * after the match.
 */
fun Match.powerPlaysRunningAt(now: Instant): List<PowerPlay> =
    if (status.isPastTheFinalWhistle) emptyList() else powerPlays.filter { it.isRunningAt(clock, now) }

/**
 * How many players a side is short at [now].
 *
 * Two concurrent dismissals mean two, which the 5+1 rules permit to happen
 * even though it is rare.
 */
fun Match.playersShortAt(
    side: TeamSide,
    now: Instant,
): Int = powerPlaysRunningAt(now).count { it.shortHandedSide == side }
