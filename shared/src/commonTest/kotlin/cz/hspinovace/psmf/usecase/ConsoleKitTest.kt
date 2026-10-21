package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The console knows which kit each side wears in this match (0.3.1, Gate 5), so
 * that the screen can draw a shirt in its colours.
 *
 * **The kit the lineup names, not the team's first.** A team owns two and picks
 * one per match (`Fixtures.homeTeam` owns a blue one and a white-and-blue one).
 * And **nothing is shown that cannot be vouched for**: a kit the team no longer
 * has, or one whose label is no longer the one the lineup snapshotted, may not
 * be what was worn, and a wrong colour is worse than none.
 */
class ConsoleKitTest {
    private var minted = 0

    /** Both blocks finished, the home side in [home] and the away side in [away]. */
    private suspend fun matchWearing(
        home: Kit,
        away: Kit,
    ): Match {
        val build =
            BuildLineupEntry(
                TestLeague.repository(),
                FakeAddedPlayerRepository(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) { "id-${++minted}" }
        val plain = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        val entry = assertNotNull(build(plain)).everyoneCame()
        return plain.copy(
            homeLineup = assertNotNull(entry.home.copy(kitId = home.id).toLineup()),
            awayLineup = assertNotNull(entry.away.copy(kitId = away.id).toLineup()),
        )
    }

    private suspend fun console(match: Match) =
        assertNotNull(BuildConsoleEntry(TestLeague.repository(), FakeAddedPlayerRepository())(match))

    @Test
    fun aSideShowsTheKitItWearsAndNotItsFirst() =
        runTest {
            val worn = console(matchWearing(Fixtures.homeAlternateKit, Fixtures.awayPrimaryKit))
            assertEquals(Fixtures.homeAlternateKit, worn.home.kit)
            assertEquals(Fixtures.awayPrimaryKit, worn.away.kit)

            val first = console(matchWearing(Fixtures.homePrimaryKit, Fixtures.awayPrimaryKit))
            assertEquals(Fixtures.homePrimaryKit, first.home.kit)
        }

    @Test
    fun theKitShownIsTheOneTheReportNames() =
        runTest {
            val match = matchWearing(Fixtures.homeAlternateKit, Fixtures.awayPrimaryKit)
            val entry = console(match)

            // The label on the report is the lineup's snapshot; the shirt is of the same kit.
            assertEquals(match.homeLineup?.kitLabel, entry.home.kit?.label)
            assertEquals(match.awayLineup?.kitLabel, entry.away.kit?.label)
        }

    @Test
    fun aSideWithNoLineupYetHasNoKit() =
        runTest {
            val entry = console(Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId))

            assertNull(entry.home.kit)
            assertNull(entry.away.kit)
        }

    @Test
    fun aKitTheTeamDoesNotOwnIsNotShown() =
        runTest {
            val match = matchWearing(Fixtures.homeAlternateKit, Fixtures.awayPrimaryKit)
            val gone = checkNotNull(match.homeLineup).copy(kitId = KitId("a-kit-that-was-dropped"))

            assertNull(console(match.copy(homeLineup = gone)).home.kit)
        }

    @Test
    fun aKitWhoseLabelHasChangedSinceTheLineupWasWrittenIsNotShown() =
        runTest {
            // The lineup snapshotted "bílo-modrá"; the kit it points at now says something else, and
            // its colours may have changed with it. The report still says what was written that day.
            val match = matchWearing(Fixtures.homeAlternateKit, Fixtures.awayPrimaryKit)
            val renamed = checkNotNull(match.homeLineup).copy(kitLabel = "světle modrá")

            val entry = console(match.copy(homeLineup = renamed))

            assertNull(entry.home.kit)
            assertEquals(Fixtures.awayPrimaryKit, entry.away.kit)
        }
}
