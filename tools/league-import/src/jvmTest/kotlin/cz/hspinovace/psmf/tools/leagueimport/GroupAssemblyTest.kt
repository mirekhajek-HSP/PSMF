package cz.hspinovace.psmf.tools.leagueimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One saved team page, assembled: squad, names split, yellows counted by the app's rule. */
class GroupAssemblyTest {
    private val links = Pages.teamsOfGroup(savedPage("group-6-k.html"), SEASON, "6-k")
    private val kits = Pages.kits(savedPage("dresy-6-k.html"))
    private val krabice = Pages.team(savedPage("team-6-k-krabice.html"))

    private fun assemble(report: ImportReport = ImportReport()) =
        GroupAssembly(report).assemble("6-k", links, kits, mapOf("krabice" to krabice))

    @Test
    fun theSquadIsStatistikyWithNamesSplitTheWayTheLineupsSayThem() {
        val team = assemble().teams.single { it.slug == "krabice" }

        assertEquals(9, team.players.size)
        assertTrue(SquadPlayer("krabice", "Bělohlávek", "Jan", 0) in team.players)
        assertEquals(listOf("pistáciovo-černá"), team.kitLabels)
    }

    @Test
    fun aYellowShownInAMatchDetailCountsTowardsTheSeason() {
        // Kašpárek, 38´ at Homogen 1.FC A, round 2.
        val players = assemble().teams.single { it.slug == "krabice" }.players

        assertEquals(1, players.single { it.surname == "Kašpárek" }.yellows)
        assertEquals(listOf("Kašpárek"), players.filter { it.yellows > 0 }.map { it.surname })
    }

    @Test
    fun aNameWithNoStatistikyRowIsReportedNotGuessed() {
        // Only Krabice's page is loaded here, so its opponents' names have
        // no squad to match against -- every one must be reported.
        val report = ImportReport()
        assemble(report)

        assertTrue(report.unmatchedNames.any { "Jakub Kumšta" in it })
        assertTrue(report.unmatchedNames.none { "krabice" in it.substringBefore(",") })
    }

    @Test
    fun cardsAreCountedByTheClassTheMarkupGivesThem() {
        val report = ImportReport()
        assemble(report)

        assertEquals(mapOf("is-red" to 1, "is-yellow" to 2), report.cardClasses.toMap())
    }
}
