package cz.hspinovace.psmf.domain

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Identity
// ---------------------------------------------------------------------------

@Serializable @JvmInline value class SeasonId(val value: String)

@Serializable @JvmInline value class GroupId(val value: String)

@Serializable @JvmInline value class TeamId(val value: String)

@Serializable @JvmInline value class PlayerId(val value: String)

@Serializable @JvmInline value class FixtureId(val value: String)

@Serializable @JvmInline value class MatchId(val value: String)

@Serializable @JvmInline value class AppearanceId(val value: String)

/** Short pitch code, e.g. `ZAKOS`, `METE1` (analysis section 2.2). */
@Serializable @JvmInline value class VenueCode(val value: String)

// ---------------------------------------------------------------------------
// Small domain values
// ---------------------------------------------------------------------------

/**
 * `Dres č.` — shirt number.
 *
 * **Belongs to the appearance, not the player** (analysis section 3.6):
 * numbers change between matches. The form even carries a referee rating
 * `Č` for whether a team's shirts are properly numbered at all, which is
 * only a question worth asking because numbering is loose.
 */
@Serializable
@JvmInline
value class JerseyNumber(val value: Int) {
    init {
        require(value in RANGE) { "Jersey number $value is outside $RANGE" }
    }

    override fun toString(): String = value.toString()

    companion object {
        val RANGE = 0..99
        fun orNull(value: Int?): JerseyNumber? = value?.takeIf { it in RANGE }?.let(::JerseyNumber)
    }
}

/** Which side of the report something belongs to. `D` and `H` on the form. */
@Serializable
enum class TeamSide {
    /** `D` — domácí. */
    HOME,

    /** `H` — hosté. */
    AWAY,
    ;

    fun opposite(): TeamSide = if (this == HOME) AWAY else HOME
}

/** `Stav` — the running score, and the final and half-time scores. */
@Serializable
data class Score(val home: Int, val away: Int) {
    init {
        require(home >= 0 && away >= 0) { "A score cannot be negative: $home:$away" }
    }

    /** Written `2:1`, home first, as on the form. */
    val asWrittenOnReport: String get() = "$home:$away"

    fun scoredBy(side: TeamSide): Score = when (side) {
        TeamSide.HOME -> copy(home = home + 1)
        TeamSide.AWAY -> copy(away = away + 1)
    }

    companion object {
        val GOALLESS = Score(0, 0)
    }
}
