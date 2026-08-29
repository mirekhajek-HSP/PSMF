package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

/**
 * The per-team half of the assessment block, page 2.
 *
 * Legend from the form: `NH` best player, `Čd` waiting time, `Č` shirt
 * numbering, `B` uniform kit colour (analysis section 2.5).
 *
 * `Č` and `B` feed directly into fines, so they are ratings with money
 * attached rather than opinions.
 */
@Serializable
data class TeamAssessment(
    /** `NH` — best player, given **by jersey number**, not by name. */
    val bestPlayer: JerseyNumber? = null,
    /**
     * `Čd` — waiting time in minutes, recorded when a team was not ready to
     * play at the official kickoff time. Zero is the normal case and is
     * different from "not assessed".
     */
    val waitingTimeMinutes: Int = 0,
    /** `Č` — are the team's shirts properly numbered? */
    val shirtsProperlyNumbered: Boolean? = null,
    /** `B` — is the team in uniform kit colour? */
    val uniformKitColour: Boolean? = null,
) {
    init {
        require(waitingTimeMinutes >= 0) {
            "Waiting time cannot be negative, was $waitingTimeMinutes"
        }
    }

    /** True once both fine-bearing ratings have been given. */
    val isComplete: Boolean
        get() = shirtsProperlyNumbered != null && uniformKitColour != null
}

/**
 * `Hodnocení a POVINNÝ komentář rozhodčího k utkání`.
 *
 * The commentary is **mandatory** on the form. It is held as plain text
 * here, and may be blank while the referee is still working: it was settled
 * (A6, DEMO_SCOPE) that it need not be complete before the captains
 * confirm, and stays editable until export. Whether it is present is
 * therefore an *export* condition, checked by [ReportReadiness], not an
 * invariant of this type.
 */
@Serializable
data class Assessment(
    val home: TeamAssessment = TeamAssessment(),
    val away: TeamAssessment = TeamAssessment(),
    val commentary: String = "",
) {
    fun forSide(side: TeamSide): TeamAssessment = when (side) {
        TeamSide.HOME -> home
        TeamSide.AWAY -> away
    }

    val hasCommentary: Boolean get() = commentary.isNotBlank()
}

/**
 * `poločas`, `Konečný výsledek`, `Vítěz utkání`.
 *
 * The winner is derived rather than stored: the form records it explicitly,
 * but a stored winner that disagrees with the stored score is a
 * contradiction the report should not be able to express.
 */
@Serializable
data class MatchResult(
    val halfTime: Score,
    val fullTime: Score,
) {
    init {
        require(fullTime.home >= halfTime.home && fullTime.away >= halfTime.away) {
            "Full-time score $fullTime is lower than the half-time score $halfTime; " +
                "goals cannot be un-scored."
        }
    }

    /** `Vítěz utkání`. */
    val winner: TeamSide? get() = when {
        fullTime.home > fullTime.away -> TeamSide.HOME
        fullTime.away > fullTime.home -> TeamSide.AWAY
        else -> null // a draw; the league awards 1 point each
    }

    val isDraw: Boolean get() = winner == null
}
