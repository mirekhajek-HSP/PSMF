package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A page saved from psmf.cz on 2026-10-07, from the test resources. Never the live site. */
internal fun savedPage(name: String): String =
    requireNotNull(PagesTest::class.java.getResource("/psmf/$name")) { "no saved page $name" }.readText()

internal const val SEASON = "/souteze/2026-hanspaulska-liga-podzim/"

/**
 * The parsers against pages saved from psmf.cz on 2026-10-07. When the
 * site changes shape, these fail before a run writes anything.
 */
class PagesTest {
    @Test
    fun theLeaguePageLinksTwelveGroups() {
        val groups = Pages.groupsOfLeague(savedPage("league-6.html"), SEASON, 6)

        assertEquals(('a'..'l').map { "6-$it" }, groups)
    }

    @Test
    fun aGroupPageLinksItsTwelveTeamsBySlug() {
        val teams = Pages.teamsOfGroup(savedPage("group-6-k.html"), SEASON, "6-k")

        assertEquals(12, teams.size)
        assertTrue(TeamLink("krabice", "Krabice") in teams)
        assertTrue(TeamLink("ameby-afc-a", "Améby AFC A") in teams)
        assertTrue(TeamLink("canda-buraku", "Čanda bůráků") in teams)
    }

    @Test
    fun theKitsPageGivesEachTeamItsCellVerbatim() {
        val kits = Pages.kits(savedPage("dresy-6-k.html")).toMap()

        assertEquals(12, kits.size)
        assertEquals("pistáciovo-černá", kits["Krabice"])
        assertEquals("černá, bílá", kits["Améby AFC A"])
        // One kit, not two: some teams list one.
        assertEquals("bílá", kits["HeyMed"])
    }

    @Test
    fun thePitchesPageGivesCodeNameAndAddress() {
        val pitches = Pages.pitches(savedPage("hriste.html")).associateBy { it.code }

        assertEquals(Pitch("STOD", "Sokol Stodůlky", "Kovářova 545, Praha 5"), pitches["STOD"])
        assertEquals("Zákostelní", pitches["ZAK"]?.name)
        assertEquals("Novoškolská 696/2, Praha 9", pitches["ZAK"]?.address)
        // Every code the 6-K fixtures use is there.
        listOf("ZAK", "P1", "P2", "TEMPO", "DEKAN", "ZABEH", "METE1").forEach { assertNotNull(pitches[it], it) }
    }

    @Test
    fun aTeamPageHoldsElevenFixturesPlayedAndToCome() {
        val page = Pages.team(savedPage("team-6-k-krabice.html"))

        assertEquals(11, page.fixtures.size)
        assertEquals(4, page.fixtures.count { it.played })
        assertEquals((1..11).toList(), page.fixtures.map { it.round }.sorted())
        val round4 = page.fixtures.single { it.round == 4 }
        assertEquals(
            ScrapedFixture(4, LocalDate(2026, 10, 4), LocalTime(10, 0), "ZAK", "heymed", "krabice", played = true),
            round4,
        )
        val round11 = page.fixtures.single { it.round == 11 }
        assertEquals(LocalDate(2026, 12, 6), round11.date)
        assertEquals("gop-united", round11.homeSlug)
    }

    @Test
    fun theSquadIsTheStatistikyTableSurnameFirst() {
        val squad = Pages.team(savedPage("team-6-k-krabice.html")).squad

        assertEquals(9, squad.size)
        assertEquals(SquadRow("Bělohlávek Jan", 3, 0), squad.first())
        assertTrue(SquadRow("Makovský Lubomír", 1, 1) in squad)
    }

    @Test
    fun aPlayedMatchCarriesBothLineupsGoalkeeperFirstAndTheCards() {
        val details = Pages.team(savedPage("team-6-k-krabice.html")).details.associateBy { it.gameId }

        assertEquals(setOf("311821", "311825", "311832"), details.keys)
        val hustec = assertNotNull(details["311832"])
        assertEquals(3, hustec.round)
        assertEquals("krabice", hustec.home.teamSlug)
        assertEquals("Michal Šeda", hustec.home.goalkeeper)
        // The best player is marked in the markup and is still just a name here.
        assertTrue("Daniel Říha" in hustec.home.outfield)
        assertEquals(9, hustec.home.lineup.size)
        assertEquals(listOf(ScrapedCard("is-yellow", 52, "Jakub Kumšta")), hustec.away.cards)

        val homogen = assertNotNull(details["311825"])
        assertEquals(listOf(ScrapedCard("is-red", 38, "Yordan Ganchev")), homogen.home.cards)
        assertEquals(listOf(ScrapedCard("is-yellow", 38, "Jakub Kašpárek")), homogen.away.cards)
    }

    @Test
    fun datesComeInTwoSpellingsAndBothRead() {
        assertEquals(LocalDate(2026, 10, 11), Pages.dateOf("Ne 11.10.26"))
        assertEquals(LocalDate(2026, 9, 20), Pages.dateOf("20. 9. 26"))
        assertEquals(LocalTime(17, 30), Pages.timeOf("17:30"))
    }
}
