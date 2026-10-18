package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

private fun Match.minutesWritten() = goals.map { it.minute.written }

private fun Match.stav() = goals.map { it.scoreAfter.asWrittenOnReport }

/**
 * A goal written with a minute of its own (prompt 13, part 3).
 *
 * The goal list, `Stav` and the half-time score follow **the minute written**,
 * and goals at one minute keep the order they were recorded in. Undo is
 * another matter and stays what it was: it takes back the last thing
 * *recorded*, whatever minute it carries.
 *
 * These are the tests the brief asks to be written first; they run on the
 * use cases as they were, which already accept any minute.
 */
class GoalWithItsOwnMinuteTest {
    @Test
    fun aGoalWrittenAtTwelveAfterOneAtTwentyListsFirstAndStavFollowsTheMinute() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            val started = StartMatch(matches)(match(), KICKOFF)

            val afterTwenty = log(started, TeamSide.HOME, null, Minute.Played(20))
            val afterTwelve = log(afterTwenty, TeamSide.AWAY, null, Minute.Played(12))

            // The forgotten goal sits where it happened, and the running
            // score is the one a reader of the list would work out.
            assertEquals(listOf("12´", "20´"), afterTwelve.minutesWritten())
            assertEquals(listOf("0:1", "1:1"), afterTwelve.stav())
            assertEquals("1:1", afterTwelve.scoreFromGoals().asWrittenOnReport)
        }

    @Test
    fun goalsAtOneMinuteKeepTheOrderTheyWereRecordedIn() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            val started = StartMatch(matches)(match(), KICKOFF)

            val first = log(started, TeamSide.HOME, null, Minute.Played(20))
            val second = log(first, TeamSide.AWAY, null, Minute.Played(20))
            val third = log(second, TeamSide.HOME, null, Minute.Played(20))

            assertEquals(listOf(TeamSide.HOME, TeamSide.AWAY, TeamSide.HOME), third.goals.map { it.side })
            assertEquals(listOf("1:0", "1:1", "2:1"), third.stav())
        }

    @Test
    fun aGoalWrittenInTheSecondHalfWithAFirstHalfMinuteCountsForTheHalfTimeScore() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            var m = StartMatch(matches)(match(), KICKOFF)
            m = log(m, TeamSide.HOME, null, Minute.Played(10))
            m = EndPeriod(matches)(m, KICKOFF + 30.minutes)
            m = StartNextPeriod(matches)(m, KICKOFF + 45.minutes)

            // Forgotten in the first half, remembered in the second.
            m = log(m, TeamSide.AWAY, null, Minute.Played(24))
            m = log(m, TeamSide.AWAY, null, Minute.Played(41))

            assertEquals("1:1", m.scoreAtEndOfFirstPeriod()?.asWrittenOnReport)
            assertEquals("1:2", m.scoreFromGoals().asWrittenOnReport)
            // ...and the suggested result the recap starts from says so too.
            val suggested = ResultDraft.suggestedFrom(m)
            assertEquals("1", suggested.halfTimeHome)
            assertEquals("1", suggested.halfTimeAway)
            assertEquals(listOf("10´", "24´", "41´"), m.minutesWritten())
            assertEquals(listOf("1:0", "1:1", "1:2"), m.stav())
        }

    @Test
    fun undoTakesBackTheLastGoalRecordedNotTheLatestMinute() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            var m = StartMatch(matches)(match(), KICKOFF)
            m = log(m, TeamSide.HOME, null, Minute.Played(20))
            m = log(m, TeamSide.AWAY, null, Minute.Played(12))

            val undone = UndoLastEvent(matches)(m)

            // The goal written second goes, though it is the earlier minute.
            assertEquals(listOf("20´"), undone.minutesWritten())
            assertEquals(listOf("1:0"), undone.stav())
        }

    @Test
    fun aMinuteLaterThanTheClockIsRefusedNotMovedToAnother() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            val started = StartMatch(matches)(match(), KICKOFF)
            val now = KICKOFF + 20.minutes + 40.seconds

            val tooLate = log(started, TeamSide.HOME, null, Minute.Played(21), now)
            val ahead = log(started, TeamSide.HOME, null, Minute.HalfTime, now)
            val onTheClock = log(started, TeamSide.HOME, null, Minute.Played(20), now)
            val earlier = log(started, TeamSide.AWAY, null, Minute.Played(3), now)

            // 21´ has not been played and 30´+ is not yet; 20´ is the clock's
            // own minute and 3´ is the past.
            assertTrue(tooLate.goals.isEmpty(), "21´ was written at 20:40")
            assertTrue(ahead.goals.isEmpty(), "30´+ was written in the first half")
            assertEquals(listOf("20´"), onTheClock.minutesWritten())
            assertEquals(listOf("3´"), earlier.minutesWritten())
        }

    @Test
    fun aFirstHalfMinuteCanBeWrittenInTheIntervalAndTheSecondHalfNotYet() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            var m = StartMatch(matches)(match(), KICKOFF)
            m = EndPeriod(matches)(m, KICKOFF + 31.minutes)
            val now = KICKOFF + 38.minutes

            assertEquals(listOf("29´"), log(m, TeamSide.HOME, null, Minute.Played(29), now).minutesWritten())
            assertEquals(listOf("30´+"), log(m, TeamSide.HOME, null, Minute.HalfTime, now).minutesWritten())
            assertTrue(log(m, TeamSide.HOME, null, Minute.Played(30), now).goals.isEmpty(), "30´ in the interval")

            val resumed = StartNextPeriod(matches)(m, KICKOFF + 45.minutes)
            val later = KICKOFF + 50.minutes
            assertEquals(listOf("34´"), log(resumed, TeamSide.HOME, null, Minute.Played(34), later).minutesWritten())
            assertTrue(log(resumed, TeamSide.HOME, null, Minute.Played(36), later).goals.isEmpty(), "36´ at 35:00")
        }

    @Test
    fun afterTheFinalWhistleNoMinuteIsLaterThanThePlayWas() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val log = LogGoal(matches)
            var m = StartMatch(matches)(match(), KICKOFF)
            m = EndPeriod(matches)(m, KICKOFF + 30.minutes)
            m = StartNextPeriod(matches)(m, KICKOFF + 45.minutes)
            // Sixty minutes of play and two over: the whistle at 62:00.
            m = FinishMatch(matches)(m, KICKOFF + 77.minutes)
            // Written an hour afterwards.
            val now = KICKOFF + 150.minutes

            assertEquals(listOf("47´"), log(m, TeamSide.HOME, null, Minute.Played(47), now).minutesWritten())
            assertEquals(listOf("62´"), log(m, TeamSide.HOME, null, Minute.Played(62), now).minutesWritten())
            assertTrue(log(m, TeamSide.HOME, null, Minute.Played(63), now).goals.isEmpty(), "63´ after a 62´ whistle")
            assertTrue(log(m, TeamSide.HOME, null, Minute.AfterFinalWhistle, now).goals.isEmpty(), "60´+")
        }

    @Test
    fun aGoalWrittenAfterTheFinalWhistleIsUndoneBeforeTheWhistleIs() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            var m = StartMatch(matches)(match(), KICKOFF)
            m = EndPeriod(matches)(m, KICKOFF + 30.minutes)
            m = StartNextPeriod(matches)(m, KICKOFF + 45.minutes)
            m = FinishMatch(matches)(m, KICKOFF + 75.minutes)
            m = LogGoal(matches)(m, TeamSide.HOME, null, Minute.Played(40), KICKOFF + 90.minutes)

            val once = UndoLastEvent(matches)(m)
            assertTrue(once.goals.isEmpty())
            assertEquals(MatchStatus.FINISHED, once.status)

            assertEquals(MatchStatus.IN_PROGRESS, UndoLastEvent(matches)(once).status)
        }

    @Test
    fun aDraftSaysWhatStandsInTheWayOfWritingIt() {
        val latest = Minute.Played(20)
        val draft = GoalDraft(TeamSide.HOME)

        assertEquals(listOf(GoalProblem.NO_MINUTE), draft.problems(latest, started = true))
        assertEquals(listOf(GoalProblem.NOT_STARTED), draft.problems(latest, started = false))

        val typed = draft.copy(minute = MinuteDraft("12"))
        assertEquals(Minute.Played(12), typed.toMinute())
        assertTrue(typed.problems(latest, started = true).isEmpty())

        val tooLate = draft.copy(minute = MinuteDraft("21"))
        assertEquals(listOf(GoalProblem.LATER_THAN_THE_CLOCK), tooLate.problems(latest, started = true))

        // 30´+ is a minute a goal can have; 60´+ is not.
        val halfTime = draft.copy(minute = MinuteDraft(mark = MinuteMark.HALF_TIME))
        assertEquals(Minute.HalfTime, halfTime.toMinute())
        assertTrue(halfTime.problems(Minute.HalfTime, started = true).isEmpty())
        val afterTheWhistle = draft.copy(minute = MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE))
        assertEquals(null, afterTheWhistle.toMinute())
        assertEquals(listOf(GoalProblem.NO_MINUTE), afterTheWhistle.problems(Minute.Played(99), started = true))
    }

    @Test
    fun noGoalIsWrittenAtSixtyPlus() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val started = StartMatch(matches)(match(), KICKOFF)
            val finished = FinishMatch(matches)(started)

            val refused = LogGoal(matches)(finished, TeamSide.HOME, null, Minute.AfterFinalWhistle)

            // Nothing a goal can be is "after the whistle": the form has no
            // such minute for one, and the score would be unplaceable.
            assertTrue(refused.goals.isEmpty(), "a goal was written at 60´+: ${refused.minutesWritten()}")
        }
}
