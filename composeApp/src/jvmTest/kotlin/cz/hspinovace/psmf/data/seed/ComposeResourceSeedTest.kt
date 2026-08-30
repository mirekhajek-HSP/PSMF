package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.data.league.SeedLeagueRepository
import cz.hspinovace.psmf.domain.FixtureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The first test that reads the shipped seed files through the real
 * Compose-resource reader.**
 *
 * Everything else about seed loading is tested against an in-memory map,
 * which proves the parser and proves nothing about whether the files can
 * actually be fetched at runtime. [ComposeResourceSeedFileReader] is the
 * one link in that chain with no test, precisely because it needs a
 * Compose resource loader to exist.
 *
 * **What this still does not prove:** that the files are packaged into an
 * *Android* APK. This runs on the JVM target, where resources come off the
 * classpath. The Android resource pipeline is a different mechanism and
 * only a device answers for it.
 */
class ComposeResourceSeedTest {
    private val catalog = SeedLeagueCatalog(ComposeResourceSeedFileReader())

    @Test
    fun theShippedIndexIsReadableThroughComposeResources() =
        runTest {
            val groups = catalog.listGroups()

            assertTrue(groups.isNotEmpty(), "index.json was not readable through Compose resources")
        }

    @Test
    fun theShippedGroupLoadsEndToEnd() =
        runTest {
            val groups = catalog.loadAll()

            val sixK = groups.single()
            assertEquals("6. liga K", sixK.group.name)
            assertEquals(12, sixK.teams.size)
            assertTrue(sixK.players.isNotEmpty())
            assertTrue(sixK.fixtures.isNotEmpty())
        }

    @Test
    fun venuesComeFromTheirOwnFileBecauseCodesAreLeagueWide() =
        runTest {
            val venues = catalog.loadVenues()

            assertTrue(venues.isNotEmpty())
            assertNotNull(venues.firstOrNull { it.code.value == "ZAKOS" })
        }

    @Test
    fun aFileThatIsNotThereComesBackAsNullRatherThanCrashing() =
        runTest {
            // A named-but-absent file is a data error the catalog reports as
            // a SeedProblem. The reader's only job is to fetch bytes.
            assertNull(ComposeResourceSeedFileReader().read("no-such-group.json"))
        }

    @Test
    fun theRepositoryResolvesAFixtureToItsTeamsAndPitch() =
        runTest {
            val repository = SeedLeagueRepository(catalog)
            val anyFixture =
                repository
                    .groups()
                    .first()
                    .fixtures
                    .first()

            val loaded = assertNotNull(repository.fixture(anyFixture.id))

            assertEquals(anyFixture.homeTeamId, loaded.homeTeam.id)
            assertEquals(anyFixture.awayTeamId, loaded.awayTeam.id)
            assertNotNull(loaded.venue, "Every fixture's pitch code must be in venues.json")
        }

    @Test
    fun anUnknownFixtureResolvesToNothing() =
        runTest {
            assertNull(SeedLeagueRepository(catalog).fixture(FixtureId("not-a-fixture")))
        }

    @Test
    fun theCatalogueIsParsedOnceAndKept() =
        runTest {
            // Tens of kilobytes of JSON per screen would show up as a slow
            // first frame. Identity rather than equality: the point is that
            // it is the same object, not merely equal to it.
            val repository = SeedLeagueRepository(catalog)

            assertTrue(repository.groups() === repository.groups())
        }
}
