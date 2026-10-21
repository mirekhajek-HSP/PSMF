package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.domain.nextUndoStep
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

/**
 * The console's log lists the whistles (0.3.1, Gate 4), because Undo walks back
 * through them: a mis-tapped end of the half is something the referee should be
 * able to see in the list before Undo asks about it.
 *
 * Lines are written here as the screen would show them, newest first:
 * `goal 10´`, `kickoff`, `end of half 1`, `start of half 2`, `final whistle`.
 */
class ConsoleLogWhistlesTest {
    private val matches = FakeMatchRepository(listOf(match()))
    private val start = StartMatch(matches)
    private val endPeriod = EndPeriod(matches)
    private val startNext = StartNextPeriod(matches)
    private val finish = FinishMatch(matches)
    private val goal = LogGoal(matches)
    private val undo = UndoLastEvent(matches)

    private suspend fun console(match: Match) =
        assertNotNull(BuildConsoleEntry(TestLeague.repository(), FakeAddedPlayerRepository())(match))

    private fun List<LogLine>.written(): List<String> =
        map { line ->
            when (line) {
                is LogLine.Recorded -> {
                    val kind = if (line.event is GoalEvent) "goal" else "card"
                    "$kind ${line.event.minute.written}"
                }

                is LogLine.Whistled -> {
                    when (line.whistle.whistle) {
                        Whistle.KICKOFF -> "kickoff ${line.minute.written}"
                        Whistle.END_OF_PERIOD -> "end of half ${line.whistle.period} ${line.minute.written}"
                        Whistle.START_OF_PERIOD -> "start of half ${line.whistle.period} ${line.minute.written}"
                        Whistle.FINAL -> "final whistle ${line.minute.written}"
                    }
                }
            }
        }

    @Test
    fun theLogListsTheWhistlesInTheirPlaceNewestFirst() =
        runTest {
            var m = start(match(), KICKOFF)
            m = goal(m, TeamSide.HOME, null, Minute.Played(10))
            m = goal(m, TeamSide.AWAY, null, Minute.HalfTime)
            m = endPeriod(m, KICKOFF + 31.minutes)
            m = startNext(m, KICKOFF + 45.minutes)
            m = goal(m, TeamSide.HOME, null, Minute.Played(35))
            m = finish(m, KICKOFF + 75.minutes)

            assertEquals(
                listOf(
                    "final whistle 60´+",
                    "goal 35´",
                    "start of half 2 30´",
                    "end of half 1 30´+",
                    "goal 30´+",
                    "goal 10´",
                    "kickoff 0´",
                ),
                console(m).logLines.written(),
            )
        }

    @Test
    fun aGoalWrittenInTheIntervalStandsBetweenTheEndOfTheHalfAndTheStartOfTheNext() =
        runTest {
            // The one-tap goal in the interval is written 30´+, so it belongs to the
            // first half's added time; it was recorded after the whistle that ended
            // the half, and the log keeps it there.
            var m = start(match(), KICKOFF)
            m = endPeriod(m, KICKOFF + 30.minutes)
            m = goal(m, TeamSide.HOME, null, Minute.HalfTime)
            m = startNext(m, KICKOFF + 45.minutes)

            assertEquals(
                listOf(
                    "start of half 2 30´",
                    "goal 30´+",
                    "end of half 1 30´+",
                    "kickoff 0´",
                ),
                console(m).logLines.written(),
            )
        }

    @Test
    fun aGoalTappedBeforeTheKickoffStandsUnderIt() =
        runTest {
            // Undo takes the kickoff back first, then the goal; the log reads the same.
            val tapped = goal(match(), TeamSide.HOME, null, Minute.Played(0))
            val kickedOff = start(tapped, KICKOFF)

            assertEquals(listOf("kickoff 0´", "goal 0´"), console(kickedOff).logLines.written())
        }

    @Test
    fun beforeTheKickoffThereIsNoWhistleToList() =
        runTest {
            assertEquals(emptyList(), console(match()).logLines)
        }

    @Test
    fun aWhistleTakenBackIsNoLongerInTheLog() =
        runTest {
            var m = endPeriod(start(match(), KICKOFF), KICKOFF + 30.minutes)
            m = startNext(m, KICKOFF + 45.minutes)

            assertEquals(
                listOf("start of half 2 30´", "end of half 1 30´+", "kickoff 0´"),
                console(m).logLines.written(),
            )

            m = undo(m)

            assertEquals(listOf("end of half 1 30´+", "kickoff 0´"), console(m).logLines.written())
        }

    @Test
    fun theLogListsExactlyTheWhistlesUndoWalksBackThrough() =
        runTest {
            var m = start(match(), KICKOFF)
            m = goal(m, TeamSide.HOME, null, Minute.Played(10))
            m = endPeriod(m, KICKOFF + 30.minutes)
            m = startNext(m, KICKOFF + 45.minutes)
            m = finish(m, KICKOFF + 75.minutes)
            val listed =
                console(m)
                    .logLines
                    .filterIsInstance<LogLine.Whistled>()
                    .map { it.whistle.whistle to it.whistle.period }
                    .toSet()

            val walked = mutableSetOf<Pair<Whistle, Int>>()
            var step = m.nextUndoStep()
            while (step != null) {
                if (step is UndoStep.TakesBackWhistle) walked += step.whistle to step.period
                val next = undo(m)
                if (next == m) break
                m = next
                step = m.nextUndoStep()
            }

            assertEquals(4, listed.size, "the kickoff, both ends of the interval and the final whistle")
            assertEquals(walked, listed, "the log and Undo disagree on which whistles there are")
        }

    @Test
    fun aMatchFromBefore030HasItsWhistlesInTheLogToo() =
        runTest {
            // Pressed under 0.2.1, which numbered nothing: the whistles have no place in
            // the recording order and the goals have none either.
            val legacy =
                match().copy(
                    status = MatchStatus.FINISHED,
                    kickoffAt = KICKOFF,
                    periodBreaks =
                        listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes, nextStartedAt = KICKOFF + 45.minutes)),
                    goals =
                        listOf(
                            GoalEvent(Minute.Played(12), TeamSide.HOME, null, Score(1, 0)),
                            GoalEvent(Minute.HalfTime, TeamSide.AWAY, null, Score(1, 1)),
                            GoalEvent(Minute.Played(41), TeamSide.HOME, null, Score(2, 1)),
                        ),
                )

            assertEquals(
                listOf(
                    "final whistle 60´+",
                    "goal 41´",
                    "start of half 2 30´",
                    "end of half 1 30´+",
                    "goal 30´+",
                    "goal 12´",
                    "kickoff 0´",
                ),
                console(legacy).logLines.written(),
            )
        }

    @Test
    fun theWhistlesCarryTheMinutesTheClockWritesAtThem() =
        runTest {
            var m = endPeriod(start(match(), KICKOFF), KICKOFF + 33.minutes)
            m = startNext(m, KICKOFF + 50.minutes)
            m = finish(m, KICKOFF + 90.minutes)

            val minutes =
                console(m)
                    .logLines
                    .filterIsInstance<LogLine.Whistled>()
                    .associate { it.whistle.whistle to it.minute }

            assertEquals(Minute.Played(0), minutes[Whistle.KICKOFF])
            assertEquals(Minute.HalfTime, minutes[Whistle.END_OF_PERIOD])
            assertEquals(Minute.Played(30), minutes[Whistle.START_OF_PERIOD])
            assertEquals(Minute.AfterFinalWhistle, minutes[Whistle.FINAL])
        }
}
