package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.resultFromGoals
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private val houzev = Fixtures.houzevAppearance.id
private val poupe = Fixtures.poupeAppearance.id
private val baca = Fixtures.bacaAppearance.id

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

private fun Match.minutesWritten() = goals.map { it.minute.written }

private fun Match.stav() = goals.map { it.scoreAfter.asWrittenOnReport }

private fun Match.scorers() = goals.map { it.scorer }

/** The recording-order number of the goal written at [minute]. */
private fun Match.goalAt(minute: String): Int = assertNotNull(goals.single { it.minute.written == minute }.sequence)

/**
 * A goal already in the log is corrected or removed (0.4.0, Gate 5; the testers' point 5).
 *
 * The scorer and the minute change in place; the goal keeps its place in the
 * recording order, so Undo takes back what it would have taken before. A removed
 * goal leaves the order. `Stav`, the half-time score and the result are the goals',
 * so they follow without anything recounted. A minute is held to the rules a
 * written minute already has: never later than the clock, never `60´+`, refused
 * and never clamped. A goal's team is not changed in place.
 */
class GoalCorrectionTest {
    /** Kicked off at [KICKOFF], then 5´ Houžev (home), 12´ Bača (away), 20´ Poupě (home), one tap each. */
    private suspend fun threeGoals(matches: FakeMatchRepository): Match {
        var m = StartMatch(matches)(match(), KICKOFF)
        m = LogGoal(matches)(m, TeamSide.HOME, houzev, Minute.Played(5))
        m = LogGoal(matches)(m, TeamSide.AWAY, baca, Minute.Played(12))
        m = LogGoal(matches)(m, TeamSide.HOME, poupe, Minute.Played(20))
        return m
    }

    private val during = KICKOFF + 25.minutes

    @Test
    fun theScorerOfTheFirstOfThreeIsCorrectedAndTheOtherTwoTheOrderAndStavAreAsTheyWere() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)

            val after = CorrectGoal(matches)(before, before.goalAt("5´"), poupe, Minute.Played(5), during)

            assertEquals(listOf(poupe, baca, poupe), after.scorers(), "the scorer was not corrected")
            assertEquals(before.goals.drop(1), after.goals.drop(1), "the other two goals changed")
            assertEquals(listOf("5´", "12´", "20´"), after.minutesWritten())
            assertEquals(listOf("1:0", "1:1", "2:1"), after.stav())
            assertEquals(before.goals.map { it.sequence }, after.goals.map { it.sequence }, "the order changed")
            assertEquals(after, matches.load(MatchId("m1")), "the correction was not saved")
        }

    @Test
    fun aGoalCanBeLeftWithNoScorer() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)

            val after = CorrectGoal(matches)(before, before.goalAt("12´"), null, Minute.Played(12), during)

            assertEquals(listOf(houzev, null, poupe), after.scorers())
        }

    @Test
    fun aGoalMovedAcrossTheHalfMovesTheHalfTimeScoreWithIt() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            var m = StartMatch(matches)(match(), KICKOFF)
            m = LogGoal(matches)(m, TeamSide.HOME, houzev, Minute.Played(10))
            m = EndPeriod(matches)(m, KICKOFF + 30.minutes)
            m = StartNextPeriod(matches)(m, KICKOFF + 45.minutes)
            m = LogGoal(matches)(m, TeamSide.AWAY, baca, Minute.Played(35))
            val now = KICKOFF + 55.minutes
            assertEquals("1:0", m.resultFromGoals().halfTime.asWrittenOnReport)

            // The away goal was in the first half after all.
            m = CorrectGoal(matches)(m, m.goalAt("35´"), baca, Minute.Played(25), now)
            assertEquals("1:1", m.resultFromGoals().halfTime.asWrittenOnReport)
            assertEquals(listOf("10´", "25´"), m.minutesWritten())
            assertEquals(listOf("1:0", "1:1"), m.stav())

            // And the home goal in the second: it moves after the away one, and Stav with it.
            m = CorrectGoal(matches)(m, m.goalAt("10´"), houzev, Minute.Played(40), now)
            assertEquals("0:1", m.resultFromGoals().halfTime.asWrittenOnReport)
            assertEquals("1:1", m.resultFromGoals().fullTime.asWrittenOnReport)
            assertEquals(listOf("25´", "40´"), m.minutesWritten())
            assertEquals(listOf("0:1", "1:1"), m.stav())
        }

    @Test
    fun aMinuteLaterThanTheClockIsRefusedNotMovedToAnother() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)
            val now = KICKOFF + 20.minutes + 40.seconds
            val saves = matches.saves
            val correct = CorrectGoal(matches)
            val twelve = before.goalAt("12´")

            // 21´ has not been played, 30´+ is not yet, and no goal is ever 60´+.
            assertEquals(before, correct(before, twelve, baca, Minute.Played(21), now), "21´ written at 20:40")
            assertEquals(before, correct(before, twelve, baca, Minute.HalfTime, now), "30´+ in the first half")
            assertEquals(before, correct(before, twelve, baca, Minute.AfterFinalWhistle, now), "60´+")
            assertEquals(before, correct(before, twelve, baca, Minute.AfterFinalWhistle), "60´+ with no clock")
            assertEquals(saves, matches.saves, "a refused correction was saved")

            // The clock's own minute is not later than the clock.
            assertEquals(
                listOf("5´", "20´", "20´"),
                correct(before, twelve, baca, Minute.Played(20), now).minutesWritten(),
            )
        }

    @Test
    fun aScorerFromTheOtherTeamOrFromNoLineupIsRefused() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)
            val five = before.goalAt("5´")

            // The team of a goal is not changed in place: removed, then recorded for the other.
            assertEquals(before, CorrectGoal(matches)(before, five, baca, Minute.Played(5), during))
            assertEquals(before, CorrectGoal(matches)(before, five, AppearanceId("nobody"), Minute.Played(5), during))
        }

    @Test
    fun theMiddleOfThreeGoalsIsRemovedAndStavAfterItDropsByOne() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)

            val after = RemoveGoal(matches)(before, before.goalAt("12´"))

            assertEquals(listOf("5´", "20´"), after.minutesWritten())
            assertEquals(listOf("1:0", "2:0"), after.stav())
            assertEquals("2:0", after.scoreFromGoals().asWrittenOnReport)
            assertEquals(after, matches.load(MatchId("m1")), "the removal was not saved")
        }

    @Test
    fun undoAfterACorrectionTakesBackWhatItWouldHaveTakenBefore() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)

            // The first goal recorded is moved to the latest minute; the last recorded is still 20´ Poupě.
            val corrected = CorrectGoal(matches)(before, before.goalAt("5´"), houzev, Minute.Played(22), during)
            val undone = UndoLastEvent(matches)(corrected)

            assertEquals(listOf("12´", "22´"), undone.minutesWritten(), "Undo took the corrected goal, not the last")
            assertEquals(listOf(baca, houzev), undone.scorers())
        }

    @Test
    fun aRemovedGoalLeavesTheOrderAndUndoGoesOnFromWhatWasBeforeIt() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = threeGoals(matches)

            val removed = RemoveGoal(matches)(before, before.goalAt("20´"))
            val undone = UndoLastEvent(matches)(removed)

            assertEquals(listOf("5´"), undone.minutesWritten())
        }

    @Test
    fun onlyAGoalIsRemovedEvenWhenACardOrAWhistleHasTheNumber() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val goals = threeGoals(matches)
            val yellow =
                CardDraft(TeamSide.AWAY, baca, reason = "podražení", minute = MinuteDraft("21"))
            val before = assertNotNull(LogCard(matches)(goals, yellow, during))
            val card = assertNotNull(before.cardEvents.single().sequence)
            val kickoff = assertNotNull(before.kickoffSequence)

            assertEquals(before, RemoveGoal(matches)(before, card))
            assertEquals(before, RemoveGoal(matches)(before, kickoff))
            assertEquals(before, CorrectGoal(matches)(before, card, null, Minute.Played(3), during))
        }

    @Test
    fun aGoalFromBeforeTheRecordingOrderIsNeverTouched() =
        runTest {
            // 0.1.x kept no order: no goal of this report has a number to be addressed by.
            val old = threeGoals(FakeMatchRepository(listOf(match())))
            val unnumbered = old.copy(goals = old.goals.map { it.copy(sequence = null) })
            val matches = FakeMatchRepository(listOf(unnumbered))

            (1..5).forEach { n ->
                assertEquals(unnumbered, RemoveGoal(matches)(unnumbered, n))
                assertEquals(unnumbered, CorrectGoal(matches)(unnumbered, n, null, Minute.Played(3), during))
            }
            assertEquals(0, matches.saves)
        }

    // ------------------------------------------------------------------
    // A signed report
    // ------------------------------------------------------------------

    private val signedBy =
        ConfirmingParty.entries.map {
            Confirmation(
                it,
                KICKOFF + 90.minutes,
                PersonName.of("Vlk Jiří"),
            )
        }

    private suspend fun signed(matches: FakeMatchRepository): Match {
        val finished = FinishMatch(matches)(threeGoals(matches), KICKOFF + 75.minutes)
        return finished.copy(confirmations = signedBy, status = MatchStatus.CONFIRMED).also { matches.save(it) }
    }

    @Test
    fun aCorrectionOnASignedReportTakesTheSignaturesBack() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = signed(matches)

            val after = CorrectGoal(matches)(before, before.goalAt("5´"), poupe, Minute.Played(5), KICKOFF + 2.hours())

            assertEquals(listOf(poupe, baca, poupe), after.scorers())
            assertEquals(emptyList(), after.confirmations, "the signatures outlived the correction")
            assertEquals(MatchStatus.FINISHED, after.status)
        }

    @Test
    fun aRemovalOnASignedReportTakesTheSignaturesBack() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = signed(matches)

            val after = RemoveGoal(matches)(before, before.goalAt("5´"))

            assertEquals(2, after.goals.size)
            assertEquals(emptyList(), after.confirmations, "the signatures outlived the removal")
            assertEquals(MatchStatus.FINISHED, after.status)
        }

    @Test
    fun aCorrectionThatChangesNothingOrIsRefusedLeavesTheSignaturesAndSavesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = signed(matches)
            val saves = matches.saves
            val five = before.goalAt("5´")
            val later = KICKOFF + 2.hours()

            assertEquals(before, CorrectGoal(matches)(before, five, houzev, Minute.Played(5), later))
            // No half-time whistle was pressed, so play never left the first half: 61´ was never played.
            assertEquals(before, CorrectGoal(matches)(before, five, houzev, Minute.Played(61), later))
            assertEquals(saves, matches.saves)
        }

    private fun Int.hours() = (this * 60).minutes
}
