package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A person's name as it may appear in PSMF's records.
 *
 * **Latin script only.** PSMF's records are Latin, so names are Latin
 * throughout, and the app UI being available in Ukrainian does not change
 * that: Cyrillic is permitted in *interface text*, never in name data. A
 * Cyrillic surname on a generated ZoU would be a name PSMF cannot match
 * against its own card cabinet.
 *
 * Czech diacritics are of course fine — they are Latin.
 */
@Serializable
@JvmInline
value class PersonName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        private const val MAX_LENGTH = 100

        /** Punctuation that legitimately appears in names. */
        private val ALLOWED_PUNCTUATION = setOf(' ', '-', '\'', '’', '.')

        /**
         * True for a letter in Basic Latin, Latin-1 Supplement, or Latin
         * Extended-A/B. Covers every Czech and Slovak diacritic; excludes
         * Cyrillic, Greek and everything else.
         */
        private fun isLatinLetter(c: Char): Boolean =
            when (c) {
                in 'A'..'Z', in 'a'..'z' -> true

                '×', '÷' -> false

                // multiplication and division signs
                in 'À'..'ɏ' -> true

                else -> false
            }

        /** Returns null if [raw] is not a usable Latin name. */
        fun orNull(raw: String): PersonName? {
            val trimmed = raw.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            if (trimmed.none { isLatinLetter(it) }) return null
            val everyCharacterAcceptable = trimmed.all { isLatinLetter(it) || it in ALLOWED_PUNCTUATION }
            return if (everyCharacterAcceptable) PersonName(trimmed) else null
        }

        /** As [orNull], but throws. Use only where the value is already trusted. */
        fun of(raw: String): PersonName = orNull(raw) ?: error("Not a valid Latin name: '$raw'")
    }
}

/**
 * A player's name.
 *
 * The fields are named the way people are named. The ZoU displays
 * `Příjmení a jméno` — surname first — but that is a **display order**, not
 * a naming scheme, and [asWrittenOnReport] is where it belongs.
 */
@Serializable
data class PlayerName(
    val surname: PersonName,
    val firstName: PersonName,
) {
    /** Surname first, the order used on the ZoU. */
    val asWrittenOnReport: String get() = "$surname $firstName"
}
