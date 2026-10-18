package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * What the console's clock shows at each boundary of a match (prompt 13, part
 * 3): play so far as `mm:ss`, and the minute the next event is written with.
 *
 * **Which minute a moment maps to has not changed**: 12:40 is `12´`, as it was
 * when only the minute was shown. These tests pin that, and the places the two
 * readings part company: the first half's added time, the interval, and the
 * second half starting at `30:00` whatever the first added.
 */
class ClockReadingTest {
    private fun reading(
        at: Instant,
        breaks: List<PeriodBreak> = emptyList(),
        status: MatchStatus = MatchStatus.IN_PROGRESS,
        finishedAt: Instant? = null,
    ) = PlayClock(KICKOFF, breaks).readingAt(at, status, finishedAt, halfLengthMinutes = 30)

    private val firstHalfOver = PeriodBreak(endedAt = KICKOFF + 31.minutes + 12.seconds)
    private val secondHalfStarted = firstHalfOver.copy(nextStartedAt = KICKOFF + 45.minutes)

    @Test
    fun beforeTheKickoffThereIsNoTimeAndNoMinute() {
        val before = PlayClock(null).readingAt(KICKOFF, MatchStatus.SETUP, null, 30)

        assertEquals(ClockPhase.BEFORE_KICKOFF, before.phase)
        assertNull(before.shown)
        assertNull(before.written)
    }

    @Test
    fun twelveFortyIsTwelveAsItAlwaysWas() {
        val at = reading(KICKOFF + 12.minutes + 40.seconds)

        assertEquals(12.minutes + 40.seconds, at.shown)
        assertEquals(Minute.Played(12), at.written)
        assertEquals(ClockPhase.PLAYING, at.phase)
    }

    @Test
    fun theMinuteIsTheWholeMinuteAtEverySecondOfIt() {
        assertEquals(Minute.Played(12), reading(KICKOFF + 12.minutes).written)
        assertEquals(Minute.Played(12), reading(KICKOFF + 12.minutes + 59.seconds).written)
        assertEquals(Minute.Played(13), reading(KICKOFF + 13.minutes).written)
        assertEquals(Minute.Played(29), reading(KICKOFF + 29.minutes + 59.seconds).written)
    }

    @Test
    fun atThirtyMinutesTheFirstHalfIsInItsAddedTimeAndTheClockCountsOn() {
        val atThirty = reading(KICKOFF + 30.minutes)
        val added = reading(KICKOFF + 31.minutes + 12.seconds)

        assertEquals(30.minutes, atThirty.shown)
        assertEquals(Minute.HalfTime, atThirty.written)
        assertEquals(ClockPhase.FIRST_HALF_ADDED_TIME, atThirty.phase)
        // Past 30:00 the clock goes on; what is written stays 30´+.
        assertEquals(31.minutes + 12.seconds, added.shown)
        assertEquals(Minute.HalfTime, added.written)
        assertEquals(ClockPhase.FIRST_HALF_ADDED_TIME, added.phase)
    }

    @Test
    fun inTheIntervalTheClockHoldsAtWhereTheHalfEndedAndTheMinuteIsStillThirtyPlus() {
        val early = reading(KICKOFF + 33.minutes, listOf(firstHalfOver))
        val late = reading(KICKOFF + 44.minutes, listOf(firstHalfOver))

        listOf(early, late).forEach {
            assertEquals(31.minutes + 12.seconds, it.shown)
            assertEquals(Minute.HalfTime, it.written)
            assertEquals(ClockPhase.INTERVAL, it.phase)
        }
    }

    @Test
    fun theSecondHalfStartsAtThirtyWhateverTheFirstAdded() {
        val kickedOff = reading(KICKOFF + 45.minutes, listOf(secondHalfStarted))
        val later = reading(KICKOFF + 47.minutes + 30.seconds, listOf(secondHalfStarted))

        // The interval showed 31:12; the second half begins at 30:00, because
        // the form's minute does and 30´+ already absorbed the added time.
        assertEquals(30.minutes, kickedOff.shown)
        assertEquals(Minute.Played(30), kickedOff.written)
        assertEquals(ClockPhase.PLAYING, kickedOff.phase)
        assertEquals(32.minutes + 30.seconds, later.shown)
        assertEquals(Minute.Played(32), later.written)
    }

    @Test
    fun theSecondHalfsOwnAddedTimeCountsPastSixtyAndIsWrittenAsPlayed() {
        // 31:05 into the second half is 61:05 on the clock and 61´ on the form.
        val added = reading(KICKOFF + 45.minutes + 31.minutes + 5.seconds, listOf(secondHalfStarted))

        assertEquals(61.minutes + 5.seconds, added.shown)
        assertEquals(Minute.Played(61), added.written)
        assertEquals(ClockPhase.PLAYING, added.phase)
    }

    @Test
    fun afterTheFinalWhistleTheClockIsFrozenAtTheWhistleAndTheMinuteIsSixtyPlus() {
        val whistle = KICKOFF + 45.minutes + 31.minutes + 7.seconds

        val over =
            reading(KICKOFF + 3.minutes * 40, listOf(secondHalfStarted), MatchStatus.FINISHED, finishedAt = whistle)

        assertEquals(61.minutes + 7.seconds, over.shown)
        assertEquals(Minute.AfterFinalWhistle, over.written)
        assertEquals(ClockPhase.FINISHED, over.phase)
    }

    @Test
    fun aMatchFinishedBeforeTheWhistleWasRecordedHasNoTimeToShowAfterIt() {
        val over = reading(KICKOFF + 100.minutes, listOf(secondHalfStarted), MatchStatus.FINISHED, finishedAt = null)

        assertNull(over.shown)
        assertEquals(Minute.AfterFinalWhistle, over.written)
        assertEquals(ClockPhase.FINISHED, over.phase)
    }

    @Test
    fun theLatestMinuteAGoalCanHaveIsTheClocksOwnAndFreezesAtTheWhistle() {
        val clock = PlayClock(KICKOFF, listOf(secondHalfStarted))

        assertEquals(Minute.Played(35), clock.latestGoalMinuteAt(KICKOFF + 50.minutes, 30))
        assertEquals(Minute.HalfTime, PlayClock(KICKOFF).latestGoalMinuteAt(KICKOFF + 33.minutes, 30))
        assertEquals(
            Minute.HalfTime,
            PlayClock(KICKOFF, listOf(firstHalfOver)).latestGoalMinuteAt(KICKOFF + 40.minutes, 30),
        )
        assertEquals(Minute.Played(0), PlayClock(null).latestGoalMinuteAt(KICKOFF, 30))
    }
}
