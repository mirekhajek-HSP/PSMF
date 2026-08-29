package cz.hspinovace.psmf.domain

import kotlinx.serialization.Serializable

/**
 * When something happened, as the *Zápis o utkání* records it.
 *
 * **This is not an `Int`, and that is the whole point.** The form has two
 * values that no integer can hold (analysis section 2.5):
 *
 * - `30´+` — issued at half-time
 * - `60´+` — issued after the final whistle but before the captains sign
 *
 * Both are ordinary occurrences: the worked example contains
 * `30´+ Lepiš A. - nesp. chování`. Storing minutes as an integer forces
 * either a lie (calling half-time "minute 30") or a second nullable flag
 * that every call site has to remember to check.
 */
@Serializable
sealed interface Minute : Comparable<Minute> {
    /** As written on the form, e.g. `5´`, `30´+`, `60´+`. */
    val written: String

    /**
     * Ordering position. Doubled so that the two half-open markers can sit
     * between whole minutes: `30´+` falls after minute 30 and before 31.
     */
    val sortKey: Int

    override fun compareTo(other: Minute): Int = sortKey.compareTo(other.sortKey)

    /** A minute of play. The clock runs continuously and the referee may add time. */
    @Serializable
    data class Played(
        val value: Int,
    ) : Minute {
        init {
            require(value >= 0) { "Minute of play cannot be negative, was $value" }
        }

        override val written: String get() = "$value$MARK"
        override val sortKey: Int get() = value * 2
    }

    /** `30´+` — the half-time interval. */
    @Serializable
    data object HalfTime : Minute {
        override val written: String get() = "$HALF_LENGTH$MARK+"

        // +1 so it sorts after minute 30 and before minute 31.
        override val sortKey: Int get() = HALF_LENGTH * 2 + 1
    }

    /**
     * `60´+` — after the final whistle, before the captains sign. Cards may
     * still be issued here: the form requires them entered
     * *"vždy však před konečným podepsáním ZoU kapitány týmů"*.
     */
    @Serializable
    data object AfterFinalWhistle : Minute {
        override val written: String get() = "$FULL_LENGTH$MARK+"

        // Always last, even after added time beyond the nominal 60 minutes.
        override val sortKey: Int get() = Int.MAX_VALUE
    }

    companion object {
        /**
         * The acute accent the form uses, U+00B4. Deliberately not an
         * apostrophe: the export must reproduce the form's own character.
         */
        const val MARK: String = "´"

        /** 2 x 30 minutes, per the rules of 5+1 (analysis section 2.6). */
        const val HALF_LENGTH: Int = 30
        const val FULL_LENGTH: Int = 60

        /**
         * Reads a minute back from the form's notation. Returns null rather
         * than throwing, so that parsing imported or typed data can report
         * a problem instead of crashing.
         */
        fun parse(raw: String): Minute? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val body = trimmed.removeSuffix("+")
            val isOpenEnded = trimmed.endsWith("+")
            val digits = body.removeSuffix(MARK).removeSuffix("'").trim()
            val value = digits.toIntOrNull() ?: return null

            return when {
                !isOpenEnded -> if (value >= 0) Played(value) else null

                value == HALF_LENGTH -> HalfTime

                value == FULL_LENGTH -> AfterFinalWhistle

                // A "+" on any other minute is not something the form defines.
                else -> null
            }
        }
    }
}
