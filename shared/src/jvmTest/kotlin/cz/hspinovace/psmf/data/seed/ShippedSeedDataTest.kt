package cz.hspinovace.psmf.data.seed

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun theSeedDirectoryIsWhereTheAppExpectsIt() {
        assertTrue(
            seedDirectory.isDirectory,
            "Seed files must live in composeApp/src/commonMain/composeResources/" +
                "${SeedLeagueCatalog.DIRECTORY}, looked in ${seedDirectory.absolutePath}",
        )
        assertTrue(File(seedDirectory, SeedLeagueCatalog.INDEX_FILE).isFile)
    }

    @Test
    fun everyGroupNamedInTheIndexLoadsCleanly() =
        runTest {
            val catalog = SeedLeagueCatalog(reader)
            val groups = catalog.loadAll()

            assertTrue(groups.isNotEmpty(), "The index lists no groups at all")
            groups.forEach { league ->
                assertTrue(league.teams.isNotEmpty(), "${league.group.name} has no teams")
                assertTrue(league.fixtures.isNotEmpty(), "${league.group.name} has no fixtures")
            }
        }

    @Test
    fun theDemoGroupLooksLikeARealHanspaulskaGroup() =
        runTest {
            val league = SeedLeagueCatalog(reader).load("6k")

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
        }

    @Test
    fun everyTeamHasASquadAndEveryPlayerADefaultNumber() =
        runTest {
            val league = SeedLeagueCatalog(reader).load("6k")

            league.teams.forEach { team ->
                val squad = league.playersOf(team.id)
                // Analysis section 6: a registered squad is ~10-15 players.
                assertTrue(squad.size in 8..20, "${team.name} has ${squad.size} players")
                assertTrue(team.kitColour.isNotBlank(), "${team.name} has no kit colour")

                val numbers = squad.mapNotNull { it.defaultJerseyNumber }
                assertEquals(numbers.size, numbers.distinct().size, "${team.name} has duplicate default numbers")
            }
        }

    @Test
    fun eachPairOfTeamsMeetsExactlyOnce() =
        runTest {
            val fixtures = SeedLeagueCatalog(reader).load("6k").fixtures

            val pairings = fixtures.map { setOf(it.homeTeamId, it.awayTeamId) }
            assertEquals(pairings.size, pairings.distinct().size, "A pairing is repeated")
            assertEquals(66, pairings.distinct().size)
        }

    @Test
    fun kickoffsAreEveningFixturesOnTheStaggeredSchedule() =
        runTest {
            val fixtures = SeedLeagueCatalog(reader).load("6k").fixtures

            // Analysis section 2.2: evening fixtures, 19:00 to 20:45 in
            // 15-minute steps.
            fixtures.forEach { fixture ->
                val minutes = fixture.time.hour * 60 + fixture.time.minute
                assertTrue(
                    minutes in (19 * 60)..(20 * 60 + 45),
                    "${fixture.id} kicks off at ${fixture.time}, outside 19:00-20:45",
                )
                assertEquals(0, fixture.time.minute % 15, "${fixture.id} is not on a 15-minute step")
            }
        }

    @Test
    fun noRpNumbersArePresentBecauseNoneHaveBeenSuppliedYet() =
        runTest {
            // The one roster dependency that cannot be met from public data.
            // When PSMF supplies them (A2), this test is the reminder to change.
            val league = SeedLeagueCatalog(reader).load("6k")
            assertTrue(
                league.players.all { it.identifier == null },
                "Seed data now carries identifiers; update this test and check the data-protection position first",
            )
        }
}
