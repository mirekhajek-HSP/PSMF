package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One player in one lineup: a single row of a team's block on page 1.
 *
 * Carries the two values that belong to *this match* rather than to the
 * player: the jersey number (analysis section 3.6) and
 * [reportedIdentification], which is what actually went in the `Číslo RP`
 * column that day.
 */
@Serializable
data class Appearance(
    val id: AppearanceId,
    val playerId: PlayerId,
    val jerseyNumber: JerseyNumber?,
    /**
     * **Not nullable, and stored rather than derived.**
     *
     * A row on the ZoU always has something in the `Číslo RP` column, and
     * what it is depends on the day: the RP number if the card was
     * present, the date of birth if it was not. Deriving it at export time
     * would mean a player who later gains an RP number retroactively
     * changes an old report — the same versioning principle as analysis
     * section 5.3.
     *
     * Default it with [Player.identificationFor].
     */
    val reportedIdentification: ReportedIdentification,
)

/**
 * Who actually turned up for one team in one match, and the captain's
 * confirmation that they are all eligible.
 *
 * Distinct from the registered squad: typically about eight of ten to
 * fifteen (analysis section 6). The screen builds this by **marking who is
 * absent**, since most of the squad turns up — three to five taps rather
 * than writing ten names (section 5.1).
 *
 * # `Barva dresů` is stored twice, on purpose
 *
 * [kitLabel] is what the report says. [kitId] is what the UI shows as
 * selected. They are not redundant: one is a **snapshot** and the other is
 * a **reference**, and they disagree the moment PSMF renames a kit.
 * Build both together with [wearing] so they cannot drift apart.
 */
@Serializable
data class Lineup(
    val side: TeamSide,
    val teamId: TeamId,
    val appearances: List<Appearance>,
    /**
     * Which of the team's two kit sets was selected. **For the UI only.**
     *
     * Resolve it with [Team.kit] to show which chip is active, or to hint
     * at a clash with the other side. Never read it to build the report —
     * that is [kitLabel]'s job, and the two can legitimately differ.
     */
    val kitId: KitId,
    /**
     * `Barva dresů` — **the label as it stood on the day, copied here.**
     *
     * A snapshot, not a lookup, for exactly the reason
     * [Appearance.reportedIdentification] is: a report states what was
     * written at the time. If PSMF renames Kominíci's second kit from
     * "bílo-modrá" to "světle modrá" next month, a match played today must
     * still read "bílo-modrá" — otherwise editing reference data silently
     * rewrites history, which is the failure mode analysis section 5.3
     * exists to prevent.
     *
     * The reference in [kitId] survives alongside it because the UI still
     * needs to know *which* kit is selected. The report never asks.
     */
    val kitLabel: String,
    /**
     * The one captain of this team in this match, among [appearances], and
     * the one goalkeeper, who may be the same person.
     *
     * **Null on a lineup written before 0.3.0, and never filled in for one**
     * (DECISIONS 2026-10-17): older reports have no captain or goalkeeper
     * and must not be given one by an upgrade. A lineup the app writes now
     * has both, because a block without them is not saved.
     */
    val captain: AppearanceId? = null,
    val goalkeeper: AppearanceId? = null,
    /**
     * The captain's confirmation under the lineup, *before kickoff*
     * (*podpis kapitána před zahájením utkání*). **Not the captain's
     * signature under the finished report** -- that is a [Confirmation] on
     * the match, and the two are separate records because the form has them
     * sign twice. A late one is allowed and carries its real time.
     */
    val confirmation: LineupConfirmation? = null,
) {
    init {
        require(kitLabel.isNotBlank()) {
            "Barva dresů cannot be blank: the ZoU cannot be generated without it."
        }
        require(captain == null || appearance(captain) != null) {
            "The captain is not in this lineup."
        }
        require(goalkeeper == null || appearance(goalkeeper) != null) {
            "The goalkeeper is not in this lineup."
        }
        val duplicateNumbers =
            appearances
                .mapNotNull { it.jerseyNumber }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicateNumbers.isEmpty()) {
            "Two players in one lineup share jersey number(s) $duplicateNumbers; " +
                "goals and cards are attributed by number, so this would be ambiguous."
        }
        require(appearances.distinctBy { it.playerId }.size == appearances.size) {
            "The same player appears twice in one lineup."
        }
    }

    fun appearance(id: AppearanceId): Appearance? = appearances.firstOrNull { it.id == id }

    fun byJerseyNumber(number: JerseyNumber): Appearance? = appearances.firstOrNull { it.jerseyNumber == number }

    /**
     * True when [kitId] is one this team actually owns.
     *
     * A check on the *reference*, and therefore on the UI rather than on
     * the report: [kitLabel] stays valid whatever happens to the team's
     * kit list afterwards.
     */
    fun kitBelongsTo(team: Team): Boolean = team.id == teamId && team.owns(kitId)

    /**
     * True when the stored label no longer matches the referenced kit —
     * i.e. the kit has been renamed since this match.
     *
     * Not an error. The report is right and the reference is stale, which
     * is the whole point of keeping both. Exposed so the UI can say so
     * rather than quietly showing a name the report does not use.
     */
    fun kitLabelHasDriftedFrom(team: Team): Boolean {
        val current = team.kit(kitId) ?: return false
        return current.label != kitLabel
    }

    companion object {
        /** Maximum on the field per side, including the goalkeeper: 5+1. */
        const val PLAYERS_ON_FIELD = 6

        /**
         * The only way a lineup should be built at a screen: pass the [Kit]
         * that was selected and let the snapshot be taken here.
         *
         * Calling the constructor directly is still possible and is what
         * the repository does when rehydrating a stored match — at that
         * point the label is being *read back*, not taken.
         */
        fun wearing(
            side: TeamSide,
            teamId: TeamId,
            appearances: List<Appearance>,
            kit: Kit,
            captain: AppearanceId? = null,
            goalkeeper: AppearanceId? = null,
            confirmation: LineupConfirmation? = null,
        ): Lineup =
            Lineup(
                side = side,
                teamId = teamId,
                appearances = appearances,
                kitId = kit.id,
                kitLabel = kit.label,
                captain = captain,
                goalkeeper = goalkeeper,
                confirmation = confirmation,
            )
    }
}

/**
 * The captain's one tap under a team's lineup: who confirmed, and when.
 *
 * The name is the marked captain's, as it stands on the report -- nobody is
 * picked from a list at this step. [asDeputy] is only ever true for one read
 * back from a report written before 0.3.0, which let a deputy sign and keeps
 * its `(zást.)` (DECISIONS 2026-10-17); nothing offers it now.
 */
@Serializable
data class LineupConfirmation(
    val at: Instant,
    val by: PersonName,
    val asDeputy: Boolean = false,
)
