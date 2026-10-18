package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.domain.elapsedAt
import cz.hspinovace.psmf.domain.inPeriodInterval
import cz.hspinovace.psmf.domain.nextUndoStep
import cz.hspinovace.psmf.domain.playersShortAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

/**
 * Taking a whistle back (prompt 13, part 3).
 *
 * **In recording order, along with everything else**: a whistle is one more
 * thing recorded, and Undo takes back the last thing recorded. Nothing here
 * pauses, stops, resumes or adjusts anything; a boundary taken back is
 * simply no longer among the instants the clock is worked out from, so the
 * clock is what those that remain say.
 */
class TakingBackAWhistleTest {
    private val matches = FakeMatchRepository(listOf(match()))
    private val start = StartMatch(matches)
    private val endPeriod = EndPeriod(matches)
    private val startNext = StartNextPeriod(matches)
    private val finish = FinishMatch(matches)
    private val undo = UndoLastEvent(matches)

    @Test
    fun aMisTappedKickoffCanBeTakenBackAndPressedAgain() =
        runTest {
            val started = start(match(), KICKOFF)

            val takenBack = undo(started)

            assertNull(takenBack.kickoffAt, "the kickoff is still there")
            assertEquals(MatchStatus.SETUP, takenBack.status)
            assertEquals(takenBack, matches.load(MatchId("m1")))

            // Pressed again, a few minutes later, and that is the kickoff.
            val again = start(takenBack, KICKOFF + 4.minutes)
            assertEquals(KICKOFF + 4.minutes, again.kickoffAt)
            assertEquals(MatchStatus.IN_PROGRESS, again.status)
        }

    @Test
    fun takingBackTheEndOfTheFirstHalfLeavesTheClockContinuous() =
        runTest {
            val ended = endPeriod(start(match(), KICKOFF), KICKOFF + 31.minutes)
            assertTrue(ended.inPeriodInterval)
            // In the interval the clock holds at what had been played.
            assertEquals(31.minutes, ended.elapsedAt(KICKOFF + 33.minutes))

            val takenBack = undo(ended)

            assertFalse(takenBack.inPeriodInterval)
            assertTrue(takenBack.periodBreaks.isEmpty())
            // With nothing but the kickoff left, play simply went on: two
            // minutes since the (mistaken) whistle at 31, and no gap.
            assertEquals(33.minutes, takenBack.elapsedAt(KICKOFF + 33.minutes))
            assertEquals(31.minutes, takenBack.elapsedAt(KICKOFF + 31.minutes))
            assertEquals(Minute.HalfTime, takenBack.clock.minuteAt(KICKOFF + 33.minutes, takenBack.status, 30))
        }

    @Test
    fun takingBackTheStartOfTheNextHalfPutsTheMatchBackInTheInterval() =
        runTest {
            val ended = endPeriod(start(match(), KICKOFF), KICKOFF + 30.minutes)
            val resumed = startNext(ended, KICKOFF + 45.minutes)
            assertFalse(resumed.inPeriodInterval)

            val takenBack = undo(resumed)

            assertTrue(takenBack.inPeriodInterval)
            assertEquals(1, takenBack.periodBreaks.size)
            assertNull(takenBack.periodBreaks.single().nextStartedAt)
            // The break is back, and the clock holds again.
            assertEquals(30.minutes, takenBack.elapsedAt(KICKOFF + 50.minutes))

            // And the second half can be started again, at its real time.
            val again = startNext(takenBack, KICKOFF + 47.minutes)
            assertEquals(KICKOFF + 47.minutes, again.periodBreaks.single().nextStartedAt)
        }

    @Test
    fun takingBackTheFinalWhistleSetsTheMatchGoingAgain() =
        runTest {
            var m = endPeriod(start(match(), KICKOFF), KICKOFF + 30.minutes)
            m = startNext(m, KICKOFF + 45.minutes)
            val finished = finish(m)
            assertEquals(MatchStatus.FINISHED, finished.status)

            val takenBack = undo(finished)

            assertEquals(MatchStatus.IN_PROGRESS, takenBack.status)
            assertEquals(KICKOFF, takenBack.kickoffAt)
            // Play went on: a minute after 75:00 on the wall clock is the
            // second half's 31st, written 61´.
            assertEquals(
                Minute.Played(61),
                takenBack.clock.minuteAt(KICKOFF + 76.minutes, takenBack.status, 30),
            )
            // Pressed again, and it is over.
            assertEquals(MatchStatus.FINISHED, finish(takenBack).status)
        }

    @Test
    fun aRedCardsTenMinutesOfPlayAreWorkedOutAgainFromWhatIsLeft() =
        runTest {
            val red =
                CardDraft(
                    side = TeamSide.AWAY,
                    appearance = Fixtures.bacaAppearance.id,
                    colour = CardColour.RED,
                    dismissal = Dismissal.STRAIGHT,
                    reason = "oplácení",
                    minute = MinuteDraft("25"),
                )
            var m = start(match(), KICKOFF)
            m = assertNotNull(LogCard(matches)(m, red, KICKOFF + 25.minutes))
            m = endPeriod(m, KICKOFF + 30.minutes)
            m = startNext(m, KICKOFF + 45.minutes)
            val now = KICKOFF + 47.minutes
            val powerPlay = m.powerPlays.single()

            // Five minutes played before the break, two since it: three to go.
            assertEquals(3.minutes, powerPlay.remainingAt(m.clock, now))

            // The second half taken back: the match is on the break again,
            // the clock holds at 30:00, and five minutes of the ten are left.
            val inTheInterval = undo(m)
            assertEquals(5.minutes, inTheInterval.powerPlays.single().remainingAt(inTheInterval.clock, now))
            assertEquals(1, inTheInterval.playersShortAt(TeamSide.AWAY, now))

            // The end of the half taken back too: no break ever happened, so
            // 22 minutes of play have gone since the card and nobody is short.
            val running = undo(inTheInterval)
            assertEquals(0.minutes, running.powerPlays.single().remainingAt(running.clock, now))
            assertEquals(0, running.playersShortAt(TeamSide.AWAY, now))
            // The card itself is still written.
            assertEquals(1, running.cardEvents.size)
        }

    @Test
    fun undoOffersTheLastThingRecordedAndSaysWhetherItIsAWhistle() =
        runTest {
            val steps = mutableListOf<UndoStep?>()
            var m = match()
            steps += m.nextUndoStep()
            m = start(m, KICKOFF)
            steps += m.nextUndoStep()
            m = LogGoal(matches)(m, TeamSide.HOME, null, Minute.Played(10))
            steps += m.nextUndoStep()
            m = endPeriod(m, KICKOFF + 30.minutes)
            steps += m.nextUndoStep()
            m = startNext(m, KICKOFF + 45.minutes)
            steps += m.nextUndoStep()
            m = finish(m, KICKOFF + 75.minutes)
            steps += m.nextUndoStep()

            assertEquals(
                listOf(
                    null,
                    UndoStep.TakesBackWhistle(Whistle.KICKOFF, 1),
                    UndoStep.Event,
                    UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, 1),
                    UndoStep.TakesBackWhistle(Whistle.START_OF_PERIOD, 2),
                    UndoStep.TakesBackWhistle(Whistle.FINAL, 2),
                ),
                steps,
            )
        }

    @Test
    fun whistlesPressedBeforeThisReleaseComeBackAfterEverythingRecordedSince() =
        runTest {
            // As 0.2.1 wrote it: the goal has its place in the order (it has
            // since 0.2.0), the whistles do not.
            val legacy =
                match().copy(
                    status = MatchStatus.IN_PROGRESS,
                    kickoffAt = KICKOFF,
                    periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes)),
                    goals = listOf(GoalEvent(Minute.Played(10), TeamSide.HOME, null, Score(1, 0), sequence = 1)),
                )
            // Pressed now, with the first place in the order that a whistle has.
            val resumed = startNext(legacy, KICKOFF + 45.minutes)

            val steps = mutableListOf<String>()
            var m = resumed
            while (true) {
                val before = m
                m = undo(m)
                if (m == before) break
                steps +=
                    when {
                        m.goals.size < before.goals.size -> "goal"
                        m.periodBreaks.size < before.periodBreaks.size -> "end of half"
                        m.kickoffAt == null -> "kickoff"
                        else -> "start of half"
                    }
            }

            // The new start of the second half first, then the goal, and only
            // then the two whistles that 0.2.1 pressed: the end of the half
            // (last pressed) and the kickoff.
            assertEquals(listOf("start of half", "goal", "end of half", "kickoff"), steps)
        }

    @Test
    fun aMatchFinishedBeforeThisReleaseCanHaveItsFinalWhistleTakenBack() =
        runTest {
            val legacy =
                match().copy(
                    status = MatchStatus.FINISHED,
                    kickoffAt = KICKOFF,
                    periodBreaks =
                        listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes, nextStartedAt = KICKOFF + 45.minutes)),
                )

            assertEquals(UndoStep.TakesBackWhistle(Whistle.FINAL, 2), legacy.nextUndoStep())
            val takenBack = undo(legacy)
            assertEquals(MatchStatus.IN_PROGRESS, takenBack.status)
            assertNull(takenBack.finishedAt)
        }

    @Test
    fun aGoalTappedBeforeTheWhistleIsTakenBackAfterTheWhistleIs() =
        runTest {
            // A mis-tap before the kickoff lands at 0´, and the kickoff is
            // pressed over it. Last recorded, first taken back.
            val tapped = LogGoal(matches)(match(), TeamSide.HOME, null, Minute.Played(0))
            val kickedOff = start(tapped, KICKOFF)

            val once = undo(kickedOff)
            assertNull(once.kickoffAt, "the goal went before the kickoff did")
            assertEquals(1, once.goals.size)

            assertTrue(undo(once).goals.isEmpty())
        }

    @Test
    fun aSignedReportsWhistlesAreOfferedAndTakingOneBackTakesTheSignaturesWithIt() =
        runTest {
            // The whistles are among the things the captains attest
            // (prompt 13, part 4), so they are offered like any other, and the
            // signatures go with the one that is taken back.
            val signed =
                finish(start(match(), KICKOFF), KICKOFF + 60.minutes)
                    .copy(
                        status = MatchStatus.CONFIRMED,
                        confirmations =
                            cz.hspinovace.psmf.domain.ConfirmingParty.entries.map {
                                cz.hspinovace.psmf.domain
                                    .Confirmation(it, KICKOFF, PersonName.of("Vlk Jiří"))
                            },
                    )

            assertEquals(UndoStep.TakesBackWhistle(Whistle.FINAL, 1), signed.nextUndoStep())

            val takenBack = undo(signed)

            assertEquals(MatchStatus.IN_PROGRESS, takenBack.status)
            assertEquals(emptyList(), takenBack.confirmations)
        }

    @Test
    fun pressingTheFinalWhistleTwiceIsOneWhistle() =
        runTest {
            val finished = finish(start(match(), KICKOFF), KICKOFF + 60.minutes)

            val again = finish(finished, KICKOFF + 65.minutes)

            assertEquals(finished, again)
            assertEquals(KICKOFF + 60.minutes, again.finishedAt)
        }

    @Test
    fun undoWalksBackThroughGoalsAndWhistlesInTheOrderTheyWereRecorded() =
        runTest {
            val log = LogGoal(matches)
            var m = start(match(), KICKOFF)
            m = log(m, TeamSide.HOME, null, Minute.Played(10))
            m = endPeriod(m, KICKOFF + 30.minutes)
            m = log(m, TeamSide.AWAY, null, Minute.HalfTime)
            m = startNext(m, KICKOFF + 45.minutes)

            val steps = mutableListOf<String>()
            while (true) {
                val before = m
                m = undo(m)
                if (m == before) break
                steps += if (m.goals.size < before.goals.size) "goal" else "whistle"
            }

            // Start of the second half, the half-time goal, end of the first
            // half, the first goal, the kickoff.
            assertEquals(listOf("whistle", "goal", "whistle", "goal", "whistle"), steps)
            assertNull(m.kickoffAt)
            assertTrue(m.goals.isEmpty())
        }
}
