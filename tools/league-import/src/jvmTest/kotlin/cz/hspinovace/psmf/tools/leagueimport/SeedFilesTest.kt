package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The README's one rule, as the importer keeps it: **a re-run keeps every
 * id whose ref it has seen**, and the next run, after more rounds, must not
 * orphan a match recorded against this one.
 */
class SeedFilesTest {
    private val asOf = LocalDate(2026, 10, 7)
    private var minted = 0

    private fun mint(): String = "uuid-${++minted}"

    private fun player(
        team: String,
        surname: String,
        first: String,
        yellows: Int = 0,
    ) = SquadPlayer(team, surname, first, yellows)

    private fun team(
        slug: String,
        vararg players: SquadPlayer,
    ) = AssembledTeam(slug, slug.replaceFirstChar { it.uppercase() }, listOf("bílá"), players.toList())

    private fun fixture(
        home: String,
        away: String,
        round: Int = 1,
    ) = ScrapedFixture(round, LocalDate(2026, 9, 6), LocalTime(10, 0), "ZAK", home, away, played = false)

    private fun group(
        slug: String,
        vararg teams: AssembledTeam,
    ) = AssembledGroup(slug, teams.toList(), listOf(fixture(teams[0].slug, teams[1].slug)), detailedMatches = 0)

    private val pitches = listOf(Pitch("ZAK", "Zákostelní", "Novoškolská 696/2, Praha 9"))

    private fun run(
        existing: ExistingSeed,
        vararg groups: AssembledGroup,
        report: ImportReport = ImportReport(),
    ) = SeedFiles(existing, report, asOf, ::mint).build(groups.toList(), pitches)

    private fun SeedOutput.asExisting() = ExistingSeed(index, groups, venues)

    private val krabice = team("krabice", player("krabice", "Bělohlávek", "Jan", yellows = 1))
    private val hustec = team("hustec", player("hustec", "Kumšta", "Jakub"))

    @Test
    fun aFirstRunMintsAnIdForEveryRefAndUsesReadableRefs() {
        val output = run(ExistingSeed.EMPTY, group("6-k", krabice, hustec))

        val sixK = output.groups.single()
        assertEquals("6k", sixK.id)
        assertEquals("6. liga K", sixK.name)
        assertEquals("6K", sixK.reportCode)
        assertEquals(listOf("hustec", "krabice"), sixK.teams.map { it.ref })
        assertEquals("6k-krabice-vs-hustec", sixK.fixtures.single().ref)
        val belohlavek =
            sixK.teams
                .single { it.ref == "krabice" }
                .players
                .single()
        assertEquals("belohlavek-jan", belohlavek.ref)
        assertEquals(null, belohlavek.rpNumber)
        assertEquals(null, belohlavek.dateOfBirth)
        assertEquals(1, belohlavek.discipline?.yellowsThisSeason)
        assertEquals(asOf, belohlavek.discipline?.asOf)
        assertEquals(listOf("6k.json"), output.index.groups.map { it.file })
    }

    @Test
    fun aSecondRunAfterMoreRoundsKeepsEveryIdAndMintsOnlyForWhatIsNew() {
        val first = run(ExistingSeed.EMPTY, group("6-k", krabice, hustec))
        val mintedBefore = minted

        // Round two: a new player has appeared for Krabice, a yellow more.
        val later =
            group(
                "6-k",
                team(
                    "krabice",
                    player("krabice", "Bělohlávek", "Jan", yellows = 2),
                    player("krabice", "Říha", "Daniel"),
                ),
                hustec,
            )
        val second = run(first.asExisting(), later)

        fun ids(output: SeedOutput) =
            output.groups
                .flatMap { g ->
                    g.teams.flatMap { t ->
                        listOf(t.ref to t.id) + t.kits.map { "kit:" + it.label to it.id } +
                            t.players.map { it.ref to it.id }
                    } +
                        g.fixtures.map { it.ref to it.id }
                }.toMap()
        val before = ids(first)
        val after = ids(second)
        before.forEach { (ref, id) -> assertEquals(id, after[ref], "$ref changed id") }
        assertEquals(mintedBefore + 1, minted, "only the new player should have been given an id")
        assertTrue("riha-daniel" in after)
    }

    @Test
    fun twoPlayersWithOneNameGetTwoRefsAndARerunNeverSwapsThem() {
        val report = ImportReport()
        val first =
            run(
                ExistingSeed.EMPTY,
                group(
                    "6-a",
                    team("alfa", player("alfa", "Novák", "Jan")),
                    team("beta", player("beta", "Novák", "Jan")),
                ),
                report = report,
            )
        val refs =
            first.groups
                .single()
                .teams
                .associate { it.ref to it.players.single().ref }
        assertEquals(mapOf("alfa" to "novak-jan", "beta" to "novak-jan-beta"), refs)
        assertEquals(1, report.refCollisions.size)

        // Next run lists the teams the other way round: identity holds.
        val again =
            run(
                first.asExisting(),
                group(
                    "6-a",
                    team("beta", player("beta", "Novák", "Jan")),
                    team("alfa", player("alfa", "Novák", "Jan")),
                ),
            )
        assertEquals(
            refs,
            again.groups
                .single()
                .teams
                .associate { it.ref to it.players.single().ref },
        )
    }

    @Test
    fun aPlayerWhoMovesTeamKeepsTheirIdBecauseRefsAreNotTeamScoped() {
        val first = run(ExistingSeed.EMPTY, group("6-k", krabice, hustec))
        val id =
            first.groups
                .single()
                .teams
                .single { it.ref == "krabice" }
                .players
                .single()
                .id

        val moved =
            run(
                first.asExisting(),
                group("6-k", team("krabice"), team("hustec", player("hustec", "Bělohlávek", "Jan"))),
            )

        val now =
            moved.groups
                .single()
                .teams
                .single { it.ref == "hustec" }
                .players
                .single { it.ref == "belohlavek-jan" }
        assertEquals(id, now.id)
        assertTrue(
            moved.groups
                .single()
                .teams
                .single { it.ref == "krabice" }
                .players
                .isEmpty(),
        )
    }

    @Test
    fun whatLeavesTheSiteIsCarriedOverAndReportedNeverDropped() {
        val first = run(ExistingSeed.EMPTY, group("6-k", krabice, hustec))
        val report = ImportReport()

        val second = run(first.asExisting(), group("6-k", team("krabice"), hustec), report = report)

        val kept =
            second.groups
                .single()
                .teams
                .single { it.ref == "krabice" }
                .players
        assertEquals(listOf("belohlavek-jan"), kept.map { it.ref })
        assertTrue(report.departed.any { "belohlavek-jan" in it })
    }

    @Test
    fun theOutputLoadsThroughTheAppsOwnCatalogue() {
        val output = run(ExistingSeed.EMPTY, group("6-k", krabice, hustec))

        val loaded = SeedJson.loadAsTheAppWould(SeedJson.render(output, asOf)).single()

        assertEquals(2, loaded.teams.size)
        // The rule Gate 2 moved: no identification at all, and still a player.
        assertTrue(loaded.players.all { it.identificationFor(registrationCardPresent = true) == null })
        assertEquals("Novoškolská 696/2, Praha 9", loaded.venues.single().address)
    }
}
