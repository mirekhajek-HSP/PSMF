package cz.hspinovace.psmf.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// What the console's clock shows (prompt 13, part 3), worked out from the same
// stored instants as everything else on the clock and nothing more: no pause,
// stop, resume or adjust here either. A reading is a computation over the
// kickoff and the period boundaries, asked about any instant.

/** Where the console's clock is, for the words beside it. */
enum class ClockPhase {
    BEFORE_KICKOFF,
    PLAYING,

    /** The first period past its nominal length: still running, still `30´+`. */
    FIRST_HALF_ADDED_TIME,
    INTERVAL,
    FINISHED,
}

/** What the clock shows; see [readingAt]. */
data class ClockReading(
    /** Play so far for `mm:ss`; null before kickoff, or after full time when it is not known. */
    val shown: Duration?,
    /** The minute the next thing recorded gets, as the form writes it; null before kickoff. */
    val written: Minute?,
    val phase: ClockPhase,
)

/**
 * What the console's clock shows at [now]: play so far as minutes and
 * seconds, and the minute the next thing recorded would be written with.
 *
 * The two are different things and both stay on screen. **Play so far**
 * counts the first period on from 0:00 -- past `30:00` in its added time
 * -- holds in the interval, and starts the second period at
 * `halfLengthMinutes`:00 whatever the first added, because that is how
 * the form's minute restarts (see [PlayClock.minuteAt]). **The minute
 * written** is the form's: `12´` for 12:40, `30´+` for added time and the
 * interval, `60´+` once the final whistle has gone.
 *
 * After the final whistle the reading is frozen at the whistle when
 * [finishedAt] is known, and has no mm:ss when it is not (a match
 * finished before 0.3.0 kept no instant for it).
 */
fun PlayClock.readingAt(
    now: Instant,
    status: MatchStatus,
    finishedAt: Instant?,
    halfLengthMinutes: Int,
): ClockReading {
    if (kickoffAt == null) return ClockReading(shown = null, written = null, phase = ClockPhase.BEFORE_KICKOFF)
    if (status.isPastTheFinalWhistle) {
        return ClockReading(
            shown = finishedAt?.let { shownAt(it, halfLengthMinutes) },
            written = Minute.AfterFinalWhistle,
            phase = ClockPhase.FINISHED,
        )
    }
    val written = minuteAt(now, status, halfLengthMinutes)
    val phase =
        when {
            inPeriodInterval -> ClockPhase.INTERVAL
            written == Minute.HalfTime -> ClockPhase.FIRST_HALF_ADDED_TIME
            else -> ClockPhase.PLAYING
        }
    return ClockReading(shown = shownAt(now, halfLengthMinutes), written = written, phase = phase)
}

/** Period *k* starts the count at (*k*−1) × the half length; within it, play since it began. */
private fun PlayClock.shownAt(
    at: Instant,
    halfLengthMinutes: Int,
): Duration {
    val starts = listOfNotNull(kickoffAt) + periodBreaks.mapNotNull { it.nextStartedAt }
    val index = starts.indexOfLast { it <= at }.coerceAtLeast(0)
    val end = periodBreaks.getOrNull(index)?.endedAt
    val into = (minOf(at, end ?: at) - starts[index]).coerceAtLeast(Duration.ZERO)
    return (halfLengthMinutes * index).minutes + into
}

/**
 * The latest minute a goal can carry at [at]: the minute the clock reads,
 * as if the final whistle had not gone. Before kickoff there is no clock
 * and events land at minute 0, so that is the latest.
 *
 * `30´+` is the latest in the first period's added time and in the
 * interval, which is what keeps a second-half minute out of reach until
 * the second half has started.
 */
fun PlayClock.latestGoalMinuteAt(
    at: Instant,
    halfLengthMinutes: Int,
): Minute = minuteAt(at, MatchStatus.IN_PROGRESS, halfLengthMinutes) ?: Minute.Played(0)

/**
 * The latest minute a goal can carry now: the clock's own, frozen at the
 * final whistle when it is known. A match finished before 0.3.0 has no
 * instant for it, and the running clock is the best bound there is -- never
 * wrongly refusing a goal that was scored, though it refuses less.
 */
fun Match.latestGoalMinuteAt(
    now: Instant,
    halfLengthMinutes: Int,
): Minute = clock.latestGoalMinuteAt(finishedAt ?: now, halfLengthMinutes)
