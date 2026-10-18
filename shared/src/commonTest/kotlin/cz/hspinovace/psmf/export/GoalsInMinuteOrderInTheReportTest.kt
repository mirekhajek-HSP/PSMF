package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import cz.hspinovace.psmf.usecase.FakeMatchRepository
import cz.hspinovace.psmf.usecase.LogGoal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A goal written with a minute of its own sits where it happened in the
 * report, in every format, with the `Stav` a reader of the list would work
 * out (prompt 13, part 3). The recording order is what breaks a tie.
 */
class GoalsInMinuteOrderInTheReportTest {
    private val league = FakeLeagueRepository(listOf(CompleteReport.leagueGroup))
    private val withNoGoals: Match = CompleteReport.match.copy(goals = emptyList())

    private suspend fun reportAfter(vararg goals: Pair<TeamSide, Minute>): ZouReport {
        val matches = FakeMatchRepository(listOf(withNoGoals))
        var match = withNoGoals
        goals.forEach { (side, minute) -> match = LogGoal(matches)(match, side, null, minute) }
        return assertNotNull(BuildZouReport(league, NoAddedPlayers())(match))
    }

    @Test
    fun aGoalRememberedLateListsAtItsMinuteAndStavFollowsIt() =
        runTest {
            // Home at 20´, then the referee remembers away at 12´ and home at 20´ again.
            val report =
                reportAfter(
                    TeamSide.HOME to Minute.Played(20),
                    TeamSide.AWAY to Minute.Played(12),
                    TeamSide.HOME to Minute.Played(20),
                )

            assertEquals(listOf("12´", "20´", "20´"), report.goals.map { it.minute })
            assertEquals(listOf("0:1", "1:1", "2:1"), report.goals.map { it.scoreAfter })
            assertEquals(listOf(ZouSide.AWAY, ZouSide.HOME, ZouSide.HOME), report.goals.map { it.side })
        }

    @Test
    fun theTextCsvAndJsonAllListThemInThatOrder() =
        runTest {
            val report = reportAfter(TeamSide.HOME to Minute.Played(20), TeamSide.AWAY to Minute.Played(12))

            val text = ZouText.format(report)
            val csv = ZouCsv.format(report)
            val json = ZouJson.format(report)

            fun orderOf(
                whole: String,
                vararg needles: String,
            ) = needles.map { whole.indexOf(it) }

            assertTrue(orderOf(text, "12´", "20´").let { it[0] in 0 until it[1] }, "text: $text")
            assertTrue(orderOf(csv, "12´", "20´").let { it[0] in 0 until it[1] }, "csv: $csv")
            assertTrue(orderOf(json, "\"12´\"", "\"20´\"").let { it[0] in 0 until it[1] }, "json: $json")
            // The running score at each is the one that follows from the order.
            assertTrue("0:1" in text && "1:1" in text)
        }

    @Test
    fun aGoalAtHalfTimeSitsBetweenTheFirstHalfAndTheSecond() =
        runTest {
            val report =
                reportAfter(
                    TeamSide.HOME to Minute.Played(31),
                    TeamSide.AWAY to Minute.HalfTime,
                    TeamSide.HOME to Minute.Played(29),
                )

            assertEquals(listOf("29´", "30´+", "31´"), report.goals.map { it.minute })
            assertEquals(listOf("1:0", "1:1", "2:1"), report.goals.map { it.scoreAfter })
        }
}
