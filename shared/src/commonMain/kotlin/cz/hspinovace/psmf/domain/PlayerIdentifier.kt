package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

/**
 * What the single `Číslo RP` column on the ZoU actually holds.
 *
 * The form has **one** column, and the rule printed on it says a player
 * without their registration card has their **date of birth** written there
 * instead (analysis section 2.5). The worked example contains exactly that:
 * `33 | 990121 | Hlok Petr`, a six-digit value among five-digit RP numbers.
 *
 * So the model has one field plus a discriminator, not two or three
 * nullable columns. Two nullable fields would allow both to be set at once,
 * which the paper form cannot represent.
 */
@Serializable
enum class PlayerIdentifierType {
    /** `Číslo RP` — registration card number, five digits in the example. */
    RP,

    /** Written in the RP column when the player has no card with them. */
    DATE_OF_BIRTH,

    /**
     * `Rodné číslo`. Exists **only** because A28 is unresolved: the
     * Soutěžní řád refers to birth numbers while the form itself has RP
     * with date of birth as the fallback. A birth number is a national
     * identifier and a materially heavier data-protection obligation, so
     * this must not be used until A28 is answered from a completed real ZoU.
     */
    BIRTH_NUMBER,
}

/**
 * One identifier value together with what kind of value it is.
 */
@Serializable
data class PlayerIdentifier(
    val value: String,
    val type: PlayerIdentifierType,
) {
    init {
        require(value.isNotBlank()) {
            "A player identifier cannot be blank; use null for 'not recorded'."
        }
    }

    /** What gets written in the `Číslo RP` column. */
    val asWrittenOnReport: String get() = value
}
