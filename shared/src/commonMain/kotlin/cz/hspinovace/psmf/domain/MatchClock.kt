package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The two timers a match has, and the fact that only one of them stops.
//
// THE MATCH CLOCK NEVER PAUSES.
//
// Analysis section 2.6: "2 x 30 minutes gross time... The clock runs
// continuously; the referee may add time." There is NO STOPPAGE in maly
// fotbal -- not for injuries, not for the break in play, not for anything.
//
// golblok pauses its clock, and that behaviour must not be carried across.
// It is exactly the kind of thing that gets copied from a familiar codebase
// without anyone noticing it is wrong here, so it is written down rather
// than left to be inferred.
//
// The consequence is that there is deliberately NO PAUSE, STOP, RESUME OR
// ADJUST OPERATION ANYWHERE IN THIS FILE. Elapsed time is a subtraction from
// the kickoff instant, which additionally cannot drift, cannot be killed
// with the process, and survives a reboot -- the resolution TECH_STACK
// section 3 reaches for iOS, which cannot run a background timer at all.
//
// THE POWER PLAY DOES HAVE A LIFECYCLE. It is the one timer here that starts
// and finishes, and it runs alongside a match clock that never pauses.

/**
 * Time since the whistle, or null before kickoff.
 *
 * A pure function of [Match.kickoffAt], [Match.periodBreaks] and [now].
 * Nothing ticks and nothing is stored but instants, so no event in the
 * match can change the answer.
 *
 * **Holds during a period break rather than counting through it.**
 * Analysis section 2.6 is explicit that the interval between periods is
 * not part of the sixty minutes -- a half-time exists, even though the
 * clock inside each period still never pauses. The break is skipped by
 * walking the recorded boundaries rather than by subtracting a duration,
 * because a break still in progress has no end to subtract yet.
 */
fun Match.elapsedAt(now: Instant): Duration? {
    val start = kickoffAt ?: return null
    var total = Duration.ZERO
    var segmentStart = start
    for (brk in periodBreaks) {
        total += brk.endedAt - segmentStart
        val nextStart = brk.nextStartedAt ?: return total
        segmentStart = nextStart
    }
    return total + (now - segmentStart)
}

/** True once a period has ended and the next one has not started yet. */
val Match.inPeriodInterval: Boolean
    get() = periodBreaks.isNotEmpty() && periodBreaks.last().nextStartedAt == null

/**
 * Which period is running, 1-based. Null before kickoff, and **null during
 * the interval between periods** -- nothing is running on a break.
 */
fun Match.currentPeriodNumber(): Int? {
    if (kickoffAt == null) return null
    if (inPeriodInterval) return null
    return periodBreaks.size + 1
}

/**
 * `Čas` as the referee would write it: whole minutes elapsed, from 0.
 *
 * Null before kickoff. Note this is the *raw* elapsed minute and not a
 * [Minute] — half-time and post-whistle cards are recorded as `30´+` and
 * `60´+` by the referee, which is a judgement about which block of the
 * match they belong to, not a clock reading.
 */
fun Match.minutesPlayedAt(now: Instant): Int? = elapsedAt(now)?.inWholeMinutes?.toInt()

/**
 * A dismissed player's team plays a player short for ten minutes
 * (analysis section 2.6).
 *
 * Three properties, all of which are easy to get wrong and each of which
 * has a test:
 *
 * - **Ten minutes fixed.** It is *not* shortened by a goal, unlike the
 *   power play in ice hockey that the name is borrowed from.
 * - **Unaffected by further dismissals.** A second dismissal starts a
 *   second, independent period; it does not extend or restart the first.
 * - **It runs on the same continuous clock**, so it is derived from
 *   instants rather than from the match minute.
 */
@Serializable
data class PowerPlay(
    /** The side that is a player short — the side that had a player sent off. */
    val shortHandedSide: TeamSide,
    /** When the dismissal happened, on the same wall clock as the kickoff. */
    val startedAt: Instant,
    /**
     * The minute as the referee wrote it against the card, kept so the
     * console can show the two together. Not used in the arithmetic.
     */
    val dismissedAtMinute: Minute,
) {
    val endsAt: Instant get() = startedAt + LENGTH

    fun remainingAt(now: Instant): Duration = (endsAt - now).coerceAtLeast(Duration.ZERO)

    fun isRunningAt(now: Instant): Boolean = now >= startedAt && now < endsAt

    companion object {
        /** Ten minutes, fixed. Not shortened by a goal. */
        val LENGTH: Duration = 10.minutes
    }
}

/** The power plays in force at [now]; usually none, occasionally two. */
fun Match.powerPlaysRunningAt(now: Instant): List<PowerPlay> = powerPlays.filter { it.isRunningAt(now) }

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
