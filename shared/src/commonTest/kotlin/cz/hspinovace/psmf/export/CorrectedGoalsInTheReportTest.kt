package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.usecase.CorrectGoal
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import cz.hspinovace.psmf.usecase.FakeMatchRepository
import cz.hspinovace.psmf.usecase.RemoveGoal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A goal corrected or removed in the console is what the report says, in all three
 * formats, with nothing recounted by hand (0.4.0, Gate 5). No record of the change is
 * kept: the report says what the goals now are.
 */
class CorrectedGoalsInTheReportTest {
    private val league = FakeLeagueRepository(listOf(CompleteReport.leagueGroup))

    /** The complete report's three goals, as the console has numbered them since 0.2.0. */
    private val numbered: Match =
        CompleteReport.match.copy(goals = CompleteReport.match.goals.numberedFromOne())

    private fun List<GoalEvent>.numberedFromOne() = mapIndexed { i, goal -> goal.copy(sequence = i + 1) }

    @Test
    fun theTextCsvAndJsonSayWhatTheGoalsNowAre() =
        runTest {
            val matches = FakeMatchRepository(listOf(numbered))
            // 45´ Poupě was 44´ Houžev; 5´ Bača was not a goal.
            var match = CorrectGoal(matches)(numbered, 3, Fixtures.houzevAppearance.id, Minute.Played(44))
            match = RemoveGoal(matches)(match, 1)

            val report = assertNotNull(BuildZouReport(league, NoAddedPlayers())(match))

            assertEquals(listOf("13´", "44´"), report.goals.map { it.minute })
            assertEquals(listOf("1:0", "2:0"), report.goals.map { it.scoreAfter })
            assertEquals(listOf(null, 12), report.goals.map { it.jerseyNumber })
            val text = ZouText.format(report)
            val csv = ZouCsv.format(report)
            val json = ZouJson.format(report)
            listOf("text" to text, "csv" to csv, "json" to json).forEach { (format, written) ->
                assertTrue("44´" in written, "$format does not have the corrected minute: $written")
                assertFalse("45´" in written, "$format still has the old minute: $written")
                assertTrue("2:0" in written, "$format does not have the new score: $written")
                assertFalse("2:1" in written, "$format still has the old score: $written")
            }
        }
}
