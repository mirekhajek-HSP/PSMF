package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerOrigin
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.SuspensionWarning
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.suspensionWarning

/**
 * One row of a team's block on page 1, while it is still being edited.
 *
 * Distinct from [Appearance] the way [MatchHeaderEntry] is distinct from
 * `RefereeAssignment`: an appearance is a finished row on a report and
 * cannot hold nonsense, whereas this is a form that is allowed to be
 * momentarily wrong — two players sharing a number while the referee is
 * halfway through typing the second one.
 *
 * **Absence, not presence.** The squad is already known, so the referee's
 * job inverts from writing ten names to marking the three who did not turn
 * up (analysis section 5.1). [absent] defaults to false for that reason,
 * and it is why this type exists at all: an [Appearance] for an absent
 * player is not a thing.
 */
data class SquadMemberEntry(
    val player: Player,
    /**
     * Minted once when the row is first built and kept.
     *
     * Goals and cards are attributed to an appearance, so this id must not
     * change when the referee toggles someone absent and back, or a card
     * logged in the first half would lose its subject.
     */
    val appearanceId: AppearanceId,
    val absent: Boolean = false,
    val jerseyNumber: JerseyNumber? = player.defaultJerseyNumber,
    /**
     * Whether the player has their registration card with them.
     *
     * True by default because it usually is — the captain typically
     * carries the whole set (DEMO_SCOPE, A15). The referee changes it by
     * exception, which is why it is not prominent on the screen.
     */
    val registrationCardPresent: Boolean = true,
) {
    /** What will be written in the `Číslo RP` column for this row. */
    val identification: ReportedIdentification? get() = player.identificationFor(registrationCardPresent)

    /**
     * True when saying "no card" would actually change anything.
     *
     * A player with no RP number on file writes their date of birth
     * whatever happens, so offering the toggle would be offering a control
     * that does nothing. In the demo data that is every player.
     */
    val cardMakesADifference: Boolean
        get() = player.rpNumber != null && (player.dateOfBirth != null || player.birthNumber != null)

    /**
     * Advisory only, and stale by construction.
     *
     * Null is **not** clearance. See [SuspensionWarning] and the note on
     * [TeamLineupEntry.problems].
     */
    val suspensionWarning: SuspensionWarning? get() = player.discipline?.suspensionWarning()

    val addedAtThePitch: Boolean get() = player.origin == PlayerOrigin.ADDED_AT_PITCH

    fun toAppearance(): Appearance? =
        identification?.let {
            Appearance(
                id = appearanceId,
                playerId = player.id,
                jerseyNumber = jerseyNumber,
                reportedIdentification = it,
            )
        }
}

/** A team's block on page 1, while it is still being edited. */
data class TeamLineupEntry(
    val side: TeamSide,
    val team: Team,
    val members: List<SquadMemberEntry>,
    /** Defaults to [Team.primaryKit]; the label is snapshotted on save. */
    val kitId: KitId,
) {
    val present: List<SquadMemberEntry> get() = members.filterNot { it.absent }

    val absentCount: Int get() = members.count { it.absent }

    val kit: Kit? get() = team.kit(kitId)

    /** Numbers worn by more than one player who is actually playing. */
    val duplicateJerseyNumbers: Set<JerseyNumber>
        get() =
            present
                .mapNotNull { it.jerseyNumber }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

    /**
     * What stops this block being written down yet.
     *
     * **Nothing here is about eligibility.** A suspension warning is
     * advisory and never appears in this list: the app must never claim a
     * player may not play, and the absence of a problem must never read as
     * clearance. Fielding an ineligible player is a technical forfeit, so
     * an app that said "clear" would have caused it.
     */
    fun problems(): List<LineupProblem> =
        buildList {
            if (present.isEmpty()) add(LineupProblem.NobodyPresent(side))
            duplicateJerseyNumbers.sortedBy { it.value }.forEach {
                add(LineupProblem.DuplicateJerseyNumber(side, it))
            }
            present.filter { it.identification == null }.forEach {
                add(LineupProblem.NoIdentification(side, it.player.id))
            }
        }

    /** The finished block, or null while [problems] is not empty. */
    fun toLineup(): Lineup? {
        if (problems().isNotEmpty()) return null
        val chosen = kit ?: return null
        val appearances = present.map { it.toAppearance() ?: return null }
        // Snapshots the label as well as the reference: a report says what
        // was written on the day, and kits get renamed.
        return Lineup.wearing(side = side, teamId = team.id, appearances = appearances, kit = chosen)
    }

    fun withMember(
        playerId: PlayerId,
        change: (SquadMemberEntry) -> SquadMemberEntry,
    ): TeamLineupEntry = copy(members = members.map { if (it.player.id == playerId) change(it) else it })
}

/** A reason a team's block cannot be written down yet. */
sealed interface LineupProblem {
    val side: TeamSide

    /** A team that turned nobody up did not play; that is a forfeit, not a lineup. */
    data class NobodyPresent(
        override val side: TeamSide,
    ) : LineupProblem

    /**
     * Goals and cards are attributed by number, so two players sharing one
     * makes the rest of the report ambiguous.
     */
    data class DuplicateJerseyNumber(
        override val side: TeamSide,
        val number: JerseyNumber,
    ) : LineupProblem

    /**
     * Nothing can go in the `Číslo RP` column: a player with an RP number
     * on file, no card with them, and no date of birth known.
     */
    data class NoIdentification(
        override val side: TeamSide,
        val playerId: PlayerId,
    ) : LineupProblem
}
