package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * League reference data: the things the referee selects from and never
 * invents. A referee creating a team or a player is a data-integrity
 * failure, not a feature (analysis section 7).
 */

@Serializable
data class Season(
    val id: SeasonId,
    /** e.g. "2026 Hanspaulská liga podzim". */
    val name: String,
)

/**
 * The unit of competition, e.g. `6. liga K`.
 *
 * Not merely a label: yellow-card accumulation is per group per season
 * (analysis section 2.6), and the group is what appears in the `Liga`
 * field of the report header.
 */
@Serializable
data class Group(
    val id: GroupId,
    val seasonId: SeasonId,
    /** Display name, e.g. "6. liga K". */
    val name: String,
    /** As written in the `Liga` header field, e.g. "6K". */
    val reportCode: String,
    /**
     * Half length in minutes for this group.
     *
     * 2 x 30 is universal across Hanspaulská liga as far as anyone knows,
     * but it is read from the group definition rather than hardcoded, so a
     * competition with a different length costs a data change and not a
     * code change. **The league sets this. A referee changing it is a
     * defect**, so nothing in the UI may edit it.
     */
    val halfLengthMinutes: Int = Minute.HALF_LENGTH,
) {
    init {
        require(halfLengthMinutes > 0) { "Half length must be positive" }
    }
}

@Serializable
data class Team(
    val id: TeamId,
    val groupId: GroupId,
    val name: String,
    /** `Barva dresů`, e.g. "modrá", "černo-bílá". Public data (section 2.9). */
    val kitColour: String,
)

/**
 * A registered player.
 *
 * Name and identifier are **read-only to the referee**. The jersey number
 * is deliberately absent: it belongs to the [Appearance]. What lives here
 * is only the *default* to pre-fill, since numbers change between matches.
 */
@Serializable
data class Player(
    val id: PlayerId,
    val teamId: TeamId,
    val name: PlayerName,
    /**
     * Null until PSMF supplies RP numbers, which is the one roster
     * dependency that cannot be met from public data (analysis section 2.9).
     */
    val identifier: PlayerIdentifier?,
    /** Last known number, offered as a default and corrected by exception. */
    val defaultJerseyNumber: JerseyNumber?,
)

/**
 * A scheduled match. Public and scrapeable per analysis section 2.9,
 * though for the demo it comes from seed data.
 */
@Serializable
data class Fixture(
    val id: FixtureId,
    val groupId: GroupId,
    val round: Int,
    val date: LocalDate,
    val time: LocalTime,
    val venue: VenueCode,
    val homeTeamId: TeamId,
    val awayTeamId: TeamId,
) {
    init {
        require(round > 0) { "Round number must be positive, was $round" }
        require(homeTeamId != awayTeamId) { "A team cannot play itself" }
    }

    fun teamId(side: TeamSide): TeamId = when (side) {
        TeamSide.HOME -> homeTeamId
        TeamSide.AWAY -> awayTeamId
    }
}
