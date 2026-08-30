package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// Identification of a player -- TWO GENUINELY DIFFERENT THINGS, which an
// earlier version of this model wrongly collapsed into one polymorphic field.
//
//   - An RpNumber is ISSUED BY PSMF when a player registers. It arrives from
//     their database, it is immutable, and THE USER MUST NEVER BE ABLE TO
//     TYPE OR EDIT ONE. A referee inventing an RP number would be writing
//     into PSMF's own key space.
//   - A date of birth or a BirthNumber is ENTERED BY A PERSON when there is
//     no RP number to use.
//
// They are not two cases of one thing. One is a foreign key into somebody
// else's system; the other is a fallback a human writes at a pitch.

/**
 * `Číslo RP` — the registration card number, five digits in the worked
 * example (analysis section 2.5).
 *
 * **League-issued and never user-editable.** There is deliberately no
 * constructor path from user input: a player the referee adds at the pitch
 * is built by [Player.addedAtThePitch], which does not take one.
 */
@Serializable
@JvmInline
value class RpNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "An RP number cannot be blank; use null for 'not issued'." }
    }

    override fun toString(): String = value
}

/**
 * `Rodné číslo`.
 *
 * Exists **only** because A28 is unresolved: the Soutěžní řád refers to
 * birth numbers while the form itself has RP with date of birth as the
 * fallback. A birth number is a national identifier and a materially
 * heavier data-protection obligation, so this must not be used until A28 is
 * answered from a completed real ZoU.
 */
@Serializable
@JvmInline
value class BirthNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "A birth number cannot be blank; use null for 'not recorded'." }
    }

    override fun toString(): String = value
}

/** Which of the three kinds of value ended up in the `Číslo RP` column. */
@Serializable
enum class IdentificationSource {
    /** The league-issued registration number. */
    RP,

    /**
     * Written in the RP column when the player has no card with them:
     * *"U hráčů, kteří nemají k dispozici svůj registrační průkaz (RP),
     * uvedou místo čísla RP jejich datum narození."*
     */
    DATE_OF_BIRTH,

    /** See [BirthNumber]. Blocked on A28. */
    BIRTH_NUMBER,
}

/**
 * **What was actually written in the `Číslo RP` column, on the day.**
 *
 * This is a per-match fact and lives on the [Appearance], not on the
 * player. It is *stored*, never derived at export time — for the same
 * reason the report is versioned rather than locked (analysis section 5.3):
 * if a player later gains an RP number, an old report must not
 * retroactively change what it says. A report records what was written.
 */
@Serializable
data class ReportedIdentification(
    val value: String,
    val source: IdentificationSource,
) {
    init {
        require(value.isNotBlank()) {
            "The Číslo RP column cannot be blank; a lineup row has to identify its player."
        }
    }

    /** What gets written in the `Číslo RP` column. */
    val asWrittenOnReport: String get() = value

    companion object {
        fun of(rpNumber: RpNumber): ReportedIdentification =
            ReportedIdentification(rpNumber.value, IdentificationSource.RP)

        fun of(birthNumber: BirthNumber): ReportedIdentification =
            ReportedIdentification(birthNumber.value, IdentificationSource.BIRTH_NUMBER)

        /**
         * A date of birth as the form writes it: **YYMMDD**.
         *
         * The worked example row is `33 | 990121 | Hlok Petr` — six digits
         * among five-digit RP numbers, which is 21 January 1999 in the same
         * order a `rodné číslo` starts with.
         */
        fun of(dateOfBirth: LocalDate): ReportedIdentification =
            ReportedIdentification(dateOfBirth.asWrittenInTheRpColumn(), IdentificationSource.DATE_OF_BIRTH)
    }
}

/** YYMMDD, the form's own order. See [ReportedIdentification.Companion.of]. */
fun LocalDate.asWrittenInTheRpColumn(): String =
    buildString {
        append(paddedToTwoDigits(year % YEARS_IN_A_CENTURY))
        append(paddedToTwoDigits(month.number))
        append(paddedToTwoDigits(day))
    }

private const val YEARS_IN_A_CENTURY = 100
private const val TWO_DIGITS = 2

private fun paddedToTwoDigits(value: Int): String = value.toString().padStart(TWO_DIGITS, '0')
