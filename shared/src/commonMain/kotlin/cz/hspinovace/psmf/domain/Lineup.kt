package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

/**
 * One player in one lineup: a single row of a team's block on page 1.
 *
 * Carries the two values that belong to *this match* rather than to the
 * player: the jersey number (analysis section 3.6) and the identifier
 * actually written in the `Číslo RP` column that day, which may be a date
 * of birth if the player turned up without their card.
 */
@Serializable
data class Appearance(
    val id: AppearanceId,
    val playerId: PlayerId,
    val jerseyNumber: JerseyNumber?,
    /**
     * What went in the `Číslo RP` column. Defaults from the player record
     * but is editable, because the exception is decided at the pitch.
     */
    val identifier: PlayerIdentifier?,
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
     * `Barva dresů`. Defaulted from the team record but recorded per match,
     * because the referee rates whether the team actually turned out in
     * uniform kit (`B`) and a team may not match its registered colour.
     */
    val kitColour: String,
) {
    init {
        val duplicateNumbers = appearances
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

    fun byJerseyNumber(number: JerseyNumber): Appearance? =
        appearances.firstOrNull { it.jerseyNumber == number }

    /** Maximum on the field per side, including the goalkeeper: 5+1. */
    companion object {
        const val PLAYERS_ON_FIELD = 6
    }
}
