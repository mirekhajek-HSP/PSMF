package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RULE: **a team owns two kit sets, and the match records which was worn.**
 *
 * A team does not have "a kit colour". It owns two and picks one per match
 * so that the sides are not in similar colours — which is exactly why
 * `Barva dresů` sits on the *lineup* block of the ZoU and is filled in at
 * the match. The form records what was actually worn that day.
 */
class KitTest {
    @Test
    fun aTeamOwnsItsKitsAndTheFirstIsThePrimary() {
        // Order is meaningful. The primary is what a lineup defaults to.
        assertEquals(Fixtures.homePrimaryKit, Fixtures.homeTeam.primaryKit)
        assertEquals(2, Fixtures.homeTeam.kits.size)
    }

    @Test
    fun aTeamWithNoKitsCannotBeBuilt() {
        assertFailsWith<IllegalArgumentException> {
            Team(TeamId("t"), "t", Fixtures.groupId, "Nudists", emptyList())
        }
    }

    @Test
    fun aBlankKitLabelIsRejectedBecauseTheReportNeedsIt() {
        // Barva dresů is a required field; without it the ZoU cannot be
        // generated at all.
        assertFailsWith<IllegalArgumentException> { Kit(KitId("k"), "", listOf("modrá")) }
        assertFailsWith<IllegalArgumentException> { Kit(KitId("k"), "   ", listOf("modrá")) }
    }

    @Test
    fun theLabelIsAuthoritativeAndIsNotDerivedFromTheColours() {
        // THE REASON BOTH FIELDS EXIST. "bílo-modrá" is not mechanically
        // obtainable from ["bílá", "modrá"] -- the first element takes a
        // different grammatical suffix in Czech -- and the ZoU takes
        // exactly what PSMF writes.
        val alternate = Fixtures.homeAlternateKit
        assertEquals("bílo-modrá", alternate.label)
        assertEquals(listOf("bílá", "modrá"), alternate.colours)
        assertFalse(alternate.colours.joinToString("-") == alternate.label)
    }

    @Test
    fun coloursMayBeEmptyBecauseOnlyTheLabelIsRequired() {
        // The app loses its clash hint; the report is unaffected.
        val sparse = Kit(KitId("k"), "modrá")
        assertEquals(emptyList(), sparse.colours)
        assertEquals("modrá", sparse.label)
    }

    @Test
    fun aLineupKitReferenceResolvesToOneOfThatTeamsKits() {
        val lineup = Fixtures.lineup(TeamSide.HOME, Fixtures.houzevAppearance)

        assertTrue(lineup.kitBelongsTo(Fixtures.homeTeam))
        assertEquals(Fixtures.homePrimaryKit, Fixtures.homeTeam.kit(lineup.kitId))
    }

    @Test
    fun aLineupCanRecordTheAlternateKitBecauseTheSidesMustNotClash() {
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kitId = Fixtures.homeAlternateKit.id,
            )

        assertTrue(lineup.kitBelongsTo(Fixtures.homeTeam))
        assertEquals("bílo-modrá", Fixtures.homeTeam.kit(lineup.kitId)?.label)
    }

    @Test
    fun aKitReferenceFromAnotherTeamDoesNotResolve() {
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kitId = Fixtures.awayPrimaryKit.id,
            )

        assertFalse(lineup.kitBelongsTo(Fixtures.homeTeam))
        assertNull(Fixtures.homeTeam.kit(Fixtures.awayPrimaryKit.id))
        assertFalse(Fixtures.homeTeam.owns(Fixtures.awayPrimaryKit.id))
    }

    @Test
    fun aTeamCannotOwnTwoKitsWithTheSameId() {
        assertFailsWith<IllegalArgumentException> {
            Team(
                TeamId("t"),
                "t",
                Fixtures.groupId,
                "Confused FC",
                listOf(Kit(KitId("k"), "modrá"), Kit(KitId("k"), "bílá")),
            )
        }
    }
}
