package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

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
 */
@Serializable
data class Lineup(
    val side: TeamSide,
    val teamId: TeamId,
    val appearances: List<Appearance>,
    /**
     * `Barva dresů` — **which of the team's two kit sets was worn**.
     *
     * A reference rather than a colour string, because the team owns the
     * kits and the label written on the report is theirs. Selected on
     * screen 3, defaulting to [Team.primaryKit]; recorded per match because
     * the two sides must not clash and the referee separately rates whether
     * the team turned out in uniform kit at all (`B`).
     *
     * Resolve it with [Team.kit]. This type cannot check the reference
     * itself — a lineup does not know its team — so that check lives in the
     * seed loader and in [Team.owns].
     */
    val kitId: KitId,
) {
    init {
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

    /** True when [kitId] is one this team actually owns. */
    fun kitBelongsTo(team: Team): Boolean = team.id == teamId && team.owns(kitId)

    /** Maximum on the field per side, including the goalkeeper: 5+1. */
    companion object {
        const val PLAYERS_ON_FIELD = 6
    }
}
