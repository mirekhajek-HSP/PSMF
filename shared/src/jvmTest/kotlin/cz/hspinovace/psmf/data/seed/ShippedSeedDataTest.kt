package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.domain.PlayerOrigin
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads the **actual seed files that ship in the app**, not fakes.
 *
 * `SeedLeagueCatalogTest` proves the loader is general; this proves the
 * data it will really be given is well formed. Without it, a typo in
 * `6k.json` is discovered on a phone at a pitch rather than in a build.
 *
 * JVM-only on purpose: it touches the filesystem, which `commonTest`
 * cannot do, and it is reading files from a sibling Gradle module.
 */
class ShippedSeedDataTest {
    private val seedDirectory = File("../composeApp/src/commonMain/composeResources/files/leagues")

    private val reader =
        SeedFileReader { fileName ->
            File(seedDirectory, fileName).takeIf { it.isFile }?.readText()
        }

    private fun catalog() = SeedLeagueCatalog(reader)

    /** The same files, with one edit applied — for proving a check bites. */
    private fun catalogWith(
        fileName: String,
        edit: (String) -> String,
    ) = SeedLeagueCatalog(
        SeedFileReader { requested ->
            File(seedDirectory, requested).takeIf { it.isFile }?.readText()?.let {
                if (requested == fileName) edit(it) else it
            }
        },
    )

    @Test
    fun theSeedDirectoryIsWhereTheAppExpectsIt() {
        assertTrue(
            seedDirectory.isDirectory,
            "Seed files must live in composeApp/src/commonMain/composeResources/" +
                "${SeedLeagueCatalog.DIRECTORY}, looked in ${seedDirectory.absolutePath}",
        )
        assertTrue(File(seedDirectory, SeedLeagueCatalog.INDEX_FILE).isFile)
        assertTrue(File(seedDirectory, SeedLeagueCatalog.VENUES_FILE).isFile)
        assertTrue(
            File(seedDirectory, "README.md").isFile,
            "The seed README carries the UUID rule; it ships beside the data on purpose",
        )
    }

    @Test
    fun everyGroupNamedInTheIndexLoadsCleanly() =
        runTest {
            val groups = catalog().loadAll()

            assertTrue(groups.isNotEmpty(), "The index lists no groups at all")
            groups.forEach { league ->
                assertTrue(league.teams.isNotEmpty(), "${league.group.name} has no teams")
                assertTrue(league.fixtures.isNotEmpty(), "${league.group.name} has no fixtures")
            }
        }

    @Test
    fun theDemoGroupLooksLikeARealHanspaulskaGroup() =
        runTest {
            val league = catalog().load("6k")

            // Analysis section 2.3: a group is 12 teams and 11 rounds, which is
            // 66 matches per half-season.
            assertEquals(12, league.teams.size, "A Hanspaulská group has 12 teams")
            assertEquals(66, league.fixtures.size, "11 rounds x 6 matches")
            assertEquals(
                (1..11).toList(),
                league.fixtures
                    .map { it.round }
                    .distinct()
                    .sorted(),
            )
            assertEquals(30, league.group.halfLengthMinutes, "2 x 30 across HL")
            assertEquals(2, league.group.periods)
        }

    // --- identity ----------------------------------------------------------

    @Test
    fun everyIdIsAUuidAndEveryIdIsUnique() =
        runTest {
            val league = catalog().load("6k")

            val ids =
                league.teams.map { it.id.value } +
                    league.teams.flatMap { team -> team.kits.map { it.id.value } } +
                    league.players.map { it.id.value } +
                    league.fixtures.map { it.id.value }

            ids.forEach { id ->
                assertTrue(UUID_SHAPE.matches(id), "'$id' is not a UUID; ids must be opaque, not name-derived")
            }
            assertEquals(ids.size, ids.distinct().size, "Two entities share an id")
        }

    @Test
    fun theseUuidsAreTheOnesAlreadyShippedAndMustNotBeRegenerated() =
        runTest {
            // THE RULE, anchored against the actual file. A regenerated UUID
            // orphans every persisted match that referenced it, and
            // regenerating from scratch is what a scraper does by default.
            //
            // If this fails, the data was regenerated rather than edited. That
            // is not something to fix by updating the expectations here --
            // read the seed README first.
            val league = catalog().load("6k")

            val kominici = assertNotNull(league.teams.firstOrNull { it.ref == "kominici" })
            assertEquals("d58671d2-21b0-4d25-8728-8b280323f020", kominici.id.value)

            val ruzicka = assertNotNull(league.players.firstOrNull { it.ref == "ruzicka-radek" })
            assertEquals("d5f9e2e1-8a67-444f-8863-b3d1e8912e70", ruzicka.id.value)

            val opener = assertNotNull(league.fixtures.firstOrNull { it.ref == "6k-r01-1" })
            assertEquals("59f0fdc4-d6c3-43e4-aa7e-ee2c929ca86a", opener.id.value)
        }

    @Test
    fun playerRefsAreNotTeamScopedSoATransferKeepsTheirIdentity() =
        runTest {
            // `ruzicka-radek`, never `kominici-01`. The analysis permits one
            // transfer per season; a team-scoped ref would change on transfer,
            // mint a new UUID and orphan every match already recorded.
            val league = catalog().load("6k")
            val teamRefs = league.teams.map { it.ref }

            league.players.forEach { player ->
                assertTrue(
                    teamRefs.none { player.ref.startsWith(it) },
                    "Player ref '${player.ref}' is team-scoped",
                )
            }
            assertEquals(league.players.size, league.players.distinctBy { it.ref }.size)
        }

    // --- squads, kits, identification --------------------------------------

    @Test
    fun everyTeamHasASquadAndEveryPlayerADefaultNumber() =
        runTest {
            val league = catalog().load("6k")

            league.teams.forEach { team ->
                val squad = league.playersOf(team.id)
                // Analysis section 6: a registered squad is ~10-15 players.
                assertTrue(squad.size in 8..20, "${team.name} has ${squad.size} players")

                val numbers = squad.mapNotNull { it.defaultJerseyNumber }
                assertEquals(numbers.size, numbers.distinct().size, "${team.name} has duplicate default numbers")
            }
        }

    @Test
    fun everyTeamOwnsAtLeastOneKitAndEveryLabelIsUsable() =
        runTest {
            // Barva dresů is written verbatim on the report and cannot be
            // derived from the colour list, so a blank label makes the report
            // ungeneratable.
            val league = catalog().load("6k")

            league.teams.forEach { team ->
                assertTrue(team.kits.isNotEmpty(), "${team.name} owns no kits")
                team.kits.forEach { kit ->
                    assertTrue(kit.label.isNotBlank(), "${team.name} has a kit with a blank label")
                }
                // A team owns two so the sides can avoid clashing.
                assertEquals(2, team.kits.size, "${team.name} does not own two kit sets")
            }
        }

    @Test
    fun aBlankKitLabelWouldFailTheBuild() =
        runTest {
            // Proving the check bites, rather than trusting that it would.
            val problem =
                assertFailsWith<SeedException> {
                    catalogWith("6k.json") { it.replaceFirst("\"label\": \"modrá\"", "\"label\": \"\"") }
                        .load("6k")
                }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("blank label"))
        }

    @Test
    fun everyPlayerCanBeIdentifiedAndNoneCarriesAnRpNumberYet() =
        runTest {
            // RP numbers are the one roster dependency that cannot be met from
            // public data. When PSMF supplies them (A2), this test is the
            // reminder to check the data-protection position first.
            val league = catalog().load("6k")

            assertTrue(
                league.players.all { it.rpNumber == null },
                "Seed data now carries RP numbers; check the data-protection position before changing this",
            )
            league.players.forEach { player ->
                assertNotNull(
                    player.dateOfBirth,
                    "${player.ref} has no date of birth, and no RP number either",
                )
                assertEquals(PlayerOrigin.LEAGUE_RECORD, player.origin)
                // Every row can therefore fill the Číslo RP column.
                assertNotNull(player.identificationFor(registrationCardPresent = true))
                assertNotNull(player.identificationFor(registrationCardPresent = false))
            }
        }

    @Test
    fun everyDisciplinaryRecordCarriesItsAsOfDate() =
        runTest {
            // A count without a date cannot be reasoned about.
            val league = catalog().load("6k")

            league.players.forEach { player ->
                val record = assertNotNull(player.discipline, "${player.ref} has no disciplinary record")
                assertTrue(record.yellowsThisSeason >= 0)
                assertTrue(
                    record.asOf.year == 2026,
                    "${player.ref} has an asOf of ${record.asOf}, outside the season",
                )
            }
        }

    // --- fixtures and venues -----------------------------------------------

    @Test
    fun eachPairOfTeamsMeetsExactlyOnce() =
        runTest {
            val fixtures = catalog().load("6k").fixtures

            val pairings = fixtures.map { setOf(it.homeTeamId, it.awayTeamId) }
            assertEquals(pairings.size, pairings.distinct().size, "A pairing is repeated")
            assertEquals(66, pairings.distinct().size)
        }

    @Test
    fun kickoffsAreEveningFixturesOnTheStaggeredSchedule() =
        runTest {
            val fixtures = catalog().load("6k").fixtures

            // Analysis section 2.2: evening fixtures, 19:00 to 20:45 in
            // 15-minute steps.
            fixtures.forEach { fixture ->
                val minutes = fixture.time.hour * 60 + fixture.time.minute
                assertTrue(
                    minutes in (19 * 60)..(20 * 60 + 45),
                    "${fixture.ref} kicks off at ${fixture.time}, outside 19:00-20:45",
                )
                assertEquals(0, fixture.time.minute % 15, "${fixture.ref} is not on a 15-minute step")
            }
        }

    @Test
    fun everyVenueAFixtureNamesExistsInVenuesJson() =
        runTest {
            val league = catalog().load("6k")
            val known = league.venues.map { it.code }.toSet()

            assertTrue(known.isNotEmpty(), "venues.json lists no pitches")
            league.fixtures.forEach { fixture ->
                assertTrue(
                    fixture.venue in known,
                    "${fixture.ref} is at ${fixture.venue.value}, which is not in venues.json",
                )
            }
        }

    @Test
    fun aFixtureAtAnUnknownVenueWouldFailTheBuild() =
        runTest {
            // The same class of check as the unknown-team one, proven to bite.
            val problem =
                assertFailsWith<SeedException> {
                    catalogWith("venues.json") { it.replace("\"ZAKOS\"", "\"MOVED\"") }.load("6k")
                }.problem
            assertTrue(problem is SeedProblem.InconsistentData)
            assertTrue(problem.detail.contains("ZAKOS"))
            assertTrue(problem.detail.contains(SeedLeagueCatalog.VENUES_FILE))
        }

    private companion object {
        val UUID_SHAPE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
