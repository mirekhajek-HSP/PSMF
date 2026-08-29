package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

/**
 * One official named in the report header.
 */
@Serializable
data class Official(
    val name: PersonName,
    /**
     * The `R` mark. A licensed referee hired by the delegating team writes
     * **R** next to their name; the worked example has
     * "Jiří Vlk, Roman Liška ®" (analysis section 2.5).
     */
    val licensedHire: Boolean = false,
)

/**
 * `Rozhodčí, asistent` and `Týmy`.
 */
@Serializable
data class RefereeAssignment(
    val main: Official,
    val assistant: Official?,
    /**
     * **The team that delegated the referees — not either team playing.**
     *
     * Easy to miss and expensive to get wrong: the fine for a report that
     * is incomplete, incorrect or late is charged to *this* team, not to
     * the referee (analysis section 2.5, rule 10). If the delegated referee
     * fails to appear, substitute referees write their own team here.
     */
    val delegatingTeam: String,
) {
    init {
        require(delegatingTeam.isNotBlank()) {
            "The delegating team must be recorded: it is who gets fined for a bad report."
        }
    }
}

/**
 * Who has confirmed the report, and when.
 *
 * **There is exactly one recorder — the referee.** Captains confirm what
 * the referee wrote; they do not record a version of their own. A design in
 * which two parties record independently and must be reconciled invents a
 * problem the paper process does not have (analysis section 6). That is why
 * nothing in this type carries match content: a confirmation is an
 * acknowledgement and a timestamp, and nothing else.
 */
@Serializable
data class Confirmation(
    val party: ConfirmingParty,
    val at: kotlin.time.Instant,
    /**
     * Who physically confirmed. Captaincy may be delegated: the worked
     * example shows a deputy signing as `Lepiš (zást.)`.
     */
    val confirmedBy: PersonName,
    val asDeputy: Boolean = false,
)

@Serializable
enum class ConfirmingParty {
    HOME_CAPTAIN,
    AWAY_CAPTAIN,
    REFEREE,
}
