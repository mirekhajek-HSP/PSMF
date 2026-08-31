package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * RULE: **the match clock runs continuously and never pauses.**
 *
 * Analysis section 2.6: *"2 × 30 minutes gross time... The clock runs
 * continuously; the referee may add time."* There is no stoppage in malý
 * fotbal — not for injuries, not for breaks in play.
 *
 * golblok pauses its clock. That behaviour must not be carried across, and
 * these tests are what stops it being reintroduced by someone reasoning
 * from a familiar codebase rather than from the rules.
 */
class MatchClockTest {
    private val kickoff = Fixtures.kickoffAt

    private fun running() = Fixtures.matchInSetup().copy(status = MatchStatus.IN_PROGRESS, kickoffAt = kickoff)

    @Test
    fun thereIsNoClockUntilTheWhistleGoes() {
        assertNull(Fixtures.matchInSetup().elapsedAt(kickoff))
        assertNull(Fixtures.matchInSetup().minutesPlayedAt(kickoff))
    }

    @Test
    fun elapsedTimeIsJustTheDifferenceFromKickoff() {
        val match = running()
        assertEquals(0.minutes, match.elapsedAt(kickoff))
        assertEquals(23.minutes, match.elapsedAt(kickoff + 23.minutes))
        assertEquals(23, match.minutesPlayedAt(kickoff + 23.minutes + 40.seconds))
    }

    @Test
    fun nothingThatHappensInTheMatchStopsTheClock() {
        // THE RULE. Goals, cards and dismissals all pile up; the clock is
        // unmoved by every one of them, because it is a subtraction and not
        // a process. If anyone ever adds a pause, this fails.
        val quiet = running()
        val eventful =
            quiet.copy(
                goals =
                    listOf(
                        GoalEvent(Minute.Played(5), TeamSide.AWAY, null, Score(0, 1)),
                        GoalEvent(Minute.Played(13), TeamSide.HOME, null, Score(1, 1)),
                    ),
                cards =
                    CardsSection.Issued(
                        listOf(
                            RedCard(
                                Minute.Played(40),
                                TeamSide.AWAY,
                                CardSubject.Player(Fixtures.bacaAppearance.id),
                                CardReason("oplácení"),
                                Dismissal.STRAIGHT,
                            ),
                        ),
                    ),
                powerPlays =
                    listOf(PowerPlay(TeamSide.AWAY, kickoff + 40.minutes, Minute.Played(40))),
            )

        val at = kickoff + 55.minutes
        assertEquals(quiet.elapsedAt(at), eventful.elapsedAt(at))
        assertEquals(55.minutes, eventful.elapsedAt(at))
    }

    @Test
    fun theClockKeepsRunningPastFullTimeBecauseTheRefereeMayAddTime() {
        // 2 x 30 is 60 minutes, and the referee may add to it. Nothing stops
        // at 60, which is also why `60´+` is a valid Minute.
        val match = running()
        assertEquals(64.minutes, match.elapsedAt(kickoff + 64.minutes))
        assertEquals(Minute.FULL_LENGTH, Fixtures.group.fullLengthMinutes)
    }

    @Test
    fun aDerivedClockSurvivesTheProcessBecauseNothingIsHeldInMemory() {
        // Two independent Match values built from the same stored instant
        // agree, which is what "survives a kill" means for a clock that is
        // a computation rather than a service.
        val before = running()
        val afterRestart = Match(before.id, before.fixtureId, before.groupId, kickoffAt = before.kickoffAt)
        val at = kickoff + 31.minutes
        assertEquals(before.elapsedAt(at), afterRestart.elapsedAt(at))
    }
}

/**
 * RULE: **a half-time exists, and it is not part of the sixty minutes.**
 *
 * Analysis section 2.6: 2 x 30 with a break between them. The clock inside
 * a period still never pauses -- see [MatchClockTest] above -- but the
 * break itself is not play, which is what [Match.periodBreaks] and this
 * corrected [Match.elapsedAt] exist to say. Found by using the app: there
 * was no way to end the first half at all (2026-08-31 decision log).
 */
class PeriodBreakTest {
    private val kickoff = Fixtures.kickoffAt

    private fun running() = Fixtures.matchInSetup().copy(status = MatchStatus.IN_PROGRESS, kickoffAt = kickoff)

    @Test
    fun beforeAnyBreakThePeriodIsTheFirstOne() {
        val match = running()
        assertEquals(1, match.currentPeriodNumber())
        assertFalse(match.inPeriodInterval)
    }

    @Test
    fun endingAPeriodHoldsElapsedTimeAtTheMomentItEnded() {
        val ended = running().copy(periodBreaks = listOf(PeriodBreak(endedAt = kickoff + 32.minutes)))

        assertTrue(ended.inPeriodInterval)
        assertNull(ended.currentPeriodNumber())
        // Whatever real time passes on the break, elapsed play does not
        // move -- that is the whole point of recording where it is.
        assertEquals(32.minutes, ended.elapsedAt(kickoff + 32.minutes))
        assertEquals(32.minutes, ended.elapsedAt(kickoff + 40.minutes))
    }

    @Test
    fun startingTheNextPeriodResumesFromWhereTheBreakBegan() {
        val resumed =
            running().copy(
                periodBreaks =
                    listOf(PeriodBreak(endedAt = kickoff + 32.minutes, nextStartedAt = kickoff + 35.minutes)),
            )

        assertFalse(resumed.inPeriodInterval)
        assertEquals(2, resumed.currentPeriodNumber())
        // Gross time: the second period continues the same sixty minutes
        // rather than restarting the count from zero.
        assertEquals(32.minutes, resumed.elapsedAt(kickoff + 35.minutes))
        assertEquals(57.minutes, resumed.elapsedAt(kickoff + 35.minutes + 25.minutes))
    }

    @Test
    fun aSecondBreakIsHeldTheSameWayAsTheFirst() {
        // Nothing here assumes exactly two periods; a competition with
        // more reads the same way (Group.periods, not a hardcoded two).
        val match =
            running().copy(
                periodBreaks =
                    listOf(
                        PeriodBreak(endedAt = kickoff + 30.minutes, nextStartedAt = kickoff + 30.minutes),
                        PeriodBreak(endedAt = kickoff + 65.minutes),
                    ),
            )

        assertTrue(match.inPeriodInterval)
        assertEquals(65.minutes, match.elapsedAt(kickoff + 90.minutes))
    }
}

/**
 * RULE: **a dismissed player's team plays a player short for ten minutes**,
 * a period *not* shortened by a goal and unaffected by further dismissals
 * (analysis section 2.6).
 *
 * This is the only timer in the match with a lifecycle, and it runs
 * alongside a match clock that never pauses.
 */
class PowerPlayTest {
    private val kickoff = Fixtures.kickoffAt
    private val dismissal = kickoff + 40.minutes

    private fun powerPlay() = PowerPlay(TeamSide.AWAY, dismissal, Minute.Played(40))

    @Test
    fun itRunsForTenMinutesFromTheDismissal() {
        val play = powerPlay()
        assertEquals(10.minutes, PowerPlay.LENGTH)
        assertEquals(dismissal + 10.minutes, play.endsAt)
        assertEquals(10.minutes, play.remainingAt(dismissal))
        assertEquals(4.minutes, play.remainingAt(dismissal + 6.minutes))
    }

    @Test
    fun itIsRunningInsideTheWindowAndNotOutsideIt() {
        val play = powerPlay()
        assertFalse(play.isRunningAt(dismissal - 1.seconds))
        assertTrue(play.isRunningAt(dismissal))
        assertTrue(play.isRunningAt(dismissal + 9.minutes + 59.seconds))
        assertFalse(play.isRunningAt(dismissal + 10.minutes))
    }

    @Test
    fun aGoalDoesNotShortenIt() {
        // The difference from the ice-hockey power play the name is borrowed
        // from, and the mistake someone will make.
        val match =
            Fixtures.matchInSetup().copy(
                status = MatchStatus.IN_PROGRESS,
                kickoffAt = kickoff,
                powerPlays = listOf(powerPlay()),
            )
        val duringThePenalty = dismissal + 3.minutes

        val afterAGoal =
            match.copy(
                goals = listOf(GoalEvent(Minute.Played(43), TeamSide.HOME, null, Score(1, 0))),
            )

        assertEquals(
            match.powerPlays.single().remainingAt(duringThePenalty),
            afterAGoal.powerPlays.single().remainingAt(duringThePenalty),
        )
        assertEquals(7.minutes, afterAGoal.powerPlays.single().remainingAt(duringThePenalty))
    }

    @Test
    fun aSecondDismissalStartsASecondPeriodAndDoesNotExtendTheFirst() {
        val second = PowerPlay(TeamSide.AWAY, dismissal + 4.minutes, Minute.Played(44))
        val match =
            Fixtures.matchInSetup().copy(
                status = MatchStatus.IN_PROGRESS,
                kickoffAt = kickoff,
                powerPlays = listOf(powerPlay(), second),
            )

        // The first still ends when it always would have.
        assertEquals(dismissal + 10.minutes, match.powerPlays.first().endsAt)
        // Both run at once, so the side is two players short.
        val overlapping = dismissal + 6.minutes
        assertEquals(2, match.playersShortAt(TeamSide.AWAY, overlapping))
        assertEquals(0, match.playersShortAt(TeamSide.HOME, overlapping))

        // And after the first expires, only the second remains.
        val afterFirst = dismissal + 10.minutes + 1.seconds
        assertEquals(1, match.playersShortAt(TeamSide.AWAY, afterFirst))
        assertEquals(listOf(second), match.powerPlaysRunningAt(afterFirst))
    }

    @Test
    fun remainingTimeNeverGoesNegative() {
        assertEquals(kotlin.time.Duration.ZERO, powerPlay().remainingAt(dismissal + 30.minutes))
    }

    @Test
    fun aMatchWithNoDismissalsHasNoPowerPlays() {
        assertEquals(emptyList(), Fixtures.matchInSetup().powerPlaysRunningAt(kickoff + 20.minutes))
    }
}
