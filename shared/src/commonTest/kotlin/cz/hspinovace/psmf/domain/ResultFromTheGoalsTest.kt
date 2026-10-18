package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val BREAK = Instant.parse("2026-08-31T19:32:00Z")

private fun match() = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)

/**
 * `poločas` and `Konečný výsledek` **from the goals** (prompt 13, part 4).
 *
 * The recap shows them and nothing edits them. A correction is made where the
 * goals are made -- the console -- and the result follows. These are the
 * rules the old typed-in result had, now of the derivation.
 */
class ResultFromTheGoalsTest {
    private fun goal(
        minute: Minute,
        side: TeamSide,
        after: Score,
    ) = GoalEvent(minute, side, null, after)

    @Test
    fun theFinalScoreIsWhatTheGoalsMake() {
        val played =
            match().copy(
                goals =
                    listOf(
                        goal(Minute.Played(5), TeamSide.AWAY, Score(0, 1)),
                        goal(Minute.Played(20), TeamSide.HOME, Score(1, 1)),
                        goal(Minute.Played(45), TeamSide.HOME, Score(2, 1)),
                    ),
            )

        val result = played.resultFromGoals()

        assertEquals(Score(2, 1), result.fullTime)
        assertEquals(TeamSide.HOME, result.winner)
    }

    @Test
    fun theHalfTimeScoreIsTheGoalsUpToAndIncludingTheBreakAndNeverBlank() {
        val withBreak =
            match().copy(
                periodBreaks = listOf(PeriodBreak(endedAt = BREAK)),
                goals =
                    listOf(
                        goal(Minute.Played(5), TeamSide.AWAY, Score(0, 1)),
                        goal(Minute.HalfTime, TeamSide.HOME, Score(1, 1)),
                        goal(Minute.Played(45), TeamSide.HOME, Score(2, 1)),
                    ),
            )

        val result = withBreak.resultFromGoals()

        // The first two happened up to and including the break; the third is
        // in the second half and does not count toward it.
        assertEquals(Score(1, 1), result.halfTime)
        assertEquals(Score(2, 1), result.fullTime)
    }

    @Test
    fun withNoBreakYetTheHalfTimeScoreFallsBackToTheScoreSoFar() {
        // An abandoned match, or one that has not reached the break: there is
        // no moment to read a half-time score from, and the closest true
        // statement is the score so far.
        val played = match().copy(goals = listOf(goal(Minute.Played(5), TeamSide.AWAY, Score(0, 1))))

        val result = played.resultFromGoals()

        assertEquals(Score(0, 1), result.halfTime)
        assertEquals(Score(0, 1), result.fullTime)
    }

    @Test
    fun aGoallessMatchHasAResultToo() {
        // It is never missing, so there is no "missing result" to refuse.
        val result = match().resultFromGoals()

        assertEquals(Score(0, 0), result.halfTime)
        assertEquals(Score(0, 0), result.fullTime)
        assertEquals(null, result.winner)
    }

    @Test
    fun aGoalWrittenLateWithAFirstHalfMinuteCountsForTheHalfTimeScore() {
        // Written in the second half, at 24´: where it happened.
        val m =
            match().copy(
                periodBreaks = listOf(PeriodBreak(endedAt = BREAK, nextStartedAt = BREAK)),
                goals =
                    listOf(
                        goal(Minute.Played(10), TeamSide.HOME, Score(1, 0)),
                        goal(Minute.Played(24), TeamSide.AWAY, Score(1, 1)),
                        goal(Minute.Played(41), TeamSide.AWAY, Score(1, 2)),
                    ),
            )

        assertEquals(Score(1, 1), m.resultFromGoals().halfTime)
        assertEquals(Score(1, 2), m.resultFromGoals().fullTime)
    }

    // ------------------------------------------------------------------
    // What the report says
    // ------------------------------------------------------------------

    private val typedUnderTheOldRules = MatchResult(Score(0, 0), Score(5, 5))
    private val oneGoal = listOf(goal(Minute.Played(5), TeamSide.HOME, Score(1, 0)))

    @Test
    fun aReportThatIsNotConfirmedSaysWhatItsGoalsSayWhateverWasTypedBefore() {
        val old = match().copy(status = MatchStatus.FINISHED, goals = oneGoal, result = typedUnderTheOldRules)

        assertEquals(Score(1, 0), old.resultForReport().fullTime)
    }

    @Test
    fun aConfirmedReportKeepsWhatItSaidWhenItWasSigned() {
        // Signed under the old rules with a typed result: its export says what
        // it said, even though its goals say something else.
        val signed = match().copy(status = MatchStatus.CONFIRMED, goals = oneGoal, result = typedUnderTheOldRules)

        assertEquals(typedUnderTheOldRules, signed.resultForReport())
    }

    @Test
    fun aConfirmedReportWrittenUnderTheNewRulesHasNoTypedResultAndSaysItsGoals() {
        val signed = match().copy(status = MatchStatus.CONFIRMED, goals = oneGoal)

        assertEquals(Score(1, 0), signed.resultForReport().fullTime)
    }
}
