package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
                kit = Fixtures.homeAlternateKit,
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
                kit = Fixtures.awayPrimaryKit,
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

/**
 * RULE: **`Barva dresů` is a snapshot, not a lookup.**
 *
 * A report states what was written on the day. The same principle as
 * [Appearance.reportedIdentification], and for the same reason: reference
 * data changes, and a change to reference data must not reach backwards
 * into a report that is already written (analysis section 5.3).
 *
 * The lineup keeps the reference too, but only so the UI knows which chip
 * is selected. Everything the report touches comes from the snapshot.
 */
class KitLabelSnapshotTest {
    /** PSMF renames the second kit some weeks after the match was played. */
    private fun teamAfterRenaming(newLabel: String): Team =
        Fixtures.homeTeam.copy(
            kits =
                listOf(
                    Fixtures.homePrimaryKit,
                    Fixtures.homeAlternateKit.copy(label = newLabel),
                ),
        )

    @Test
    fun renamingAKitDoesNotChangeAReportAlreadyWritten() {
        // THE POINT OF THE FIELD. A match is played in the alternate kit.
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kit = Fixtures.homeAlternateKit,
            )
        assertEquals("bílo-modrá", lineup.kitLabel)

        // Later, PSMF renames it. The lineup is untouched -- it is a value,
        // and nothing in the domain looks the label up again.
        val renamed = teamAfterRenaming("světle modrá")

        assertEquals("bílo-modrá", lineup.kitLabel)
        assertEquals("světle modrá", renamed.kit(lineup.kitId)?.label)
        assertNotEquals(renamed.kit(lineup.kitId)?.label, lineup.kitLabel)
    }

    @Test
    fun theReferenceSurvivesTheRenameSoTheUiStillKnowsWhichKitWasPicked() {
        // Both fields earn their place: the snapshot is for the report, the
        // reference is for the screen. Dropping either loses something.
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kit = Fixtures.homeAlternateKit,
            )
        val renamed = teamAfterRenaming("světle modrá")

        assertEquals(Fixtures.homeAlternateKit.id, lineup.kitId)
        assertTrue(lineup.kitBelongsTo(renamed))
    }

    @Test
    fun driftBetweenTheSnapshotAndTheReferenceIsVisibleRatherThanSilent() {
        // Not an error: the report is right and the reference is stale. But
        // a screen showing the current name beside a report carrying the old
        // one would be lying, so the disagreement is askable.
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kit = Fixtures.homeAlternateKit,
            )

        assertFalse(lineup.kitLabelHasDriftedFrom(Fixtures.homeTeam))
        assertTrue(lineup.kitLabelHasDriftedFrom(teamAfterRenaming("světle modrá")))
    }

    @Test
    fun aKitRemovedFromTheTeamEntirelyStillPrintsOnTheOldReport() {
        // A team drops a kit set. The match that used it is unaffected --
        // which is exactly what a lookup could not have given us.
        val lineup =
            Fixtures.lineup(
                TeamSide.HOME,
                Fixtures.houzevAppearance,
                kit = Fixtures.homeAlternateKit,
            )
        val slimmed = Fixtures.homeTeam.copy(kits = listOf(Fixtures.homePrimaryKit))

        assertEquals("bílo-modrá", lineup.kitLabel)
        assertNull(slimmed.kit(lineup.kitId))
        assertFalse(lineup.kitLabelHasDriftedFrom(slimmed))
    }

    @Test
    fun aBlankSnapshotIsRejectedForTheSameReasonABlankKitLabelIs() {
        assertFailsWith<IllegalArgumentException> {
            Lineup(
                side = TeamSide.HOME,
                teamId = Fixtures.homeTeamId,
                appearances = listOf(Fixtures.houzevAppearance),
                kitId = Fixtures.homePrimaryKit.id,
                kitLabel = "  ",
            )
        }
    }

    @Test
    fun wearingTakesBothValuesFromOneKitSoTheyCannotBeSetInconsistently() {
        // The factory exists because two separate parameters invite exactly
        // one bug: the id of one kit beside the label of another.
        val lineup =
            Lineup.wearing(
                side = TeamSide.AWAY,
                teamId = Fixtures.awayTeamId,
                appearances = listOf(Fixtures.bacaAppearance),
                kit = Fixtures.awayPrimaryKit,
            )

        assertEquals(Fixtures.awayPrimaryKit.id, lineup.kitId)
        assertEquals(Fixtures.awayPrimaryKit.label, lineup.kitLabel)
    }
}
