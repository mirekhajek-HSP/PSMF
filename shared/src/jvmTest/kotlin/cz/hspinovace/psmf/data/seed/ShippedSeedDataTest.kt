package cz.hspinovace.psmf.data.seed

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads the **actual seed files that ship in the app**: league 6, all twelve
 * groups, generated from psmf.cz by `tools/league-import`.
 *
 * **Nothing here depends on what was scraped**, which changes every run --
 * no team names, no player counts, no particular fixture. Only what every
 * correct import must be true of: twelve groups that load, ids that are
 * opaque and unique, refs that are unique, kits with labels, pitches that
 * exist, no RP numbers. Content checks live in `PlaceholderSeedDataTest`,
 * against a frozen dataset, and in the importer's own tests, against saved
 * pages.
 *
 * JVM-only on purpose: it touches the filesystem, which `commonTest`
 * cannot do, and it is reading files from a sibling Gradle module.
 */
class ShippedSeedDataTest {
    private val seedDirectory = File("../composeApp/src/commonMain/composeResources/files/leagues")

    private fun catalog() =
        SeedLeagueCatalog(SeedFileReader { File(seedDirectory, it).takeIf { file -> file.isFile }?.readText() })

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
    fun allTwelveGroupsOfLeagueSixLoadThroughTheCatalogue() =
        runTest {
            val groups = catalog().loadAll()

            assertEquals(('a'..'l').map { "6$it" }, groups.map { it.group.id.value })
            assertEquals(('A'..'L').map { "6. liga $it" }, groups.map { it.group.name })
            assertEquals(('A'..'L').map { "6$it" }, groups.map { it.group.reportCode })
            groups.forEach { league ->
                assertTrue(league.teams.isNotEmpty(), "${league.group.name} has no teams")
                assertTrue(league.fixtures.isNotEmpty(), "${league.group.name} has no fixtures")
                assertEquals(30, league.group.halfLengthMinutes, "2 x 30 across HL")
                assertEquals(2, league.group.periods)
            }
        }

    @Test
    fun everyIdIsAUuidAndNoIdIsUsedTwiceAnywhereInTheLeague() =
        runTest {
            val groups = catalog().loadAll()

            val ids =
                groups.flatMap { league ->
                    league.teams.map { it.id.value } +
                        league.teams.flatMap { team -> team.kits.map { it.id.value } } +
                        league.players.map { it.id.value } +
                        league.fixtures.map { it.id.value }
                }
            ids.forEach { assertTrue(UUID_SHAPE.matches(it), "'$it' is not a UUID; ids must be opaque") }
            assertEquals(ids.size, ids.toSet().size, "Two entities share an id")
        }

    @Test
    fun playerRefsAreUniqueAcrossTheLeagueBecauseTheyAreNotTeamScoped() =
        runTest {
            val refs = catalog().loadAll().flatMap { league -> league.players.map { it.ref } }

            assertEquals(
                refs.size,
                refs.toSet().size,
                "Two players share a ref: ${refs.groupBy { it }.filter { it.value.size > 1 }.keys}",
            )
        }

    @Test
    fun everyTeamOwnsAKitWithALabel() =
        runTest {
            // One or two: some teams list one on /dresy/. Never none, and
            // never blank -- Barva dresů is written verbatim on the report.
            catalog().loadAll().flatMap { it.teams }.forEach { team ->
                assertTrue(team.kits.size in 1..2, "${team.name} owns ${team.kits.size} kits")
                team.kits.forEach { assertTrue(it.label.isNotBlank(), "${team.name} has a blank kit label") }
            }
        }

    @Test
    fun noPlayerCarriesAnRpNumber() =
        runTest {
            // RP numbers are the one roster dependency public data cannot
            // meet. When PSMF supplies them (A2), this is the reminder to
            // check the data-protection position first.
            catalog().loadAll().flatMap { it.players }.forEach { player ->
                assertEquals(null, player.rpNumber, "${player.ref} carries an RP number")
                player.discipline?.let { assertTrue(it.yellowsThisSeason >= 0) }
            }
        }

    @Test
    fun everyPitchAFixtureNamesIsInVenuesJsonWithItsName() =
        runTest {
            catalog().loadAll().forEach { league ->
                league.fixtures.forEach { fixture ->
                    val venue =
                        assertNotNull(league.venue(fixture.venue), "${fixture.ref}: ${fixture.venue.value} is unknown")
                    assertNotNull(venue.name, "${fixture.venue.value} has no name; /hriste/ lists every pitch")
                }
            }
        }

    private companion object {
        val UUID_SHAPE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
