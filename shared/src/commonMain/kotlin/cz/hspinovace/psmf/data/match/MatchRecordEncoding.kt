package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Minute

/**
 * Kind-plus-value encodings shared by [SqlDelightMatchRepository]'s write
 * and read halves -- split out on its own once the file holding both
 * crossed detekt's function-count threshold. Same package, so nothing
 * on the other side needs an import: only the visibility changed, from
 * file-private to module-internal.
 */

internal const val COLOUR_RED = "RED"
internal const val COLOUR_YELLOW = "YELLOW"
internal const val CARDS_NONE_ISSUED = "NONE_ISSUED"
internal const val CARDS_ISSUED = "ISSUED"
internal const val SUBJECT_PLAYER = "PLAYER"
internal const val SUBJECT_NAMED_PERSON = "NAMED_PERSON"

internal fun Boolean.toLong(): Long = if (this) 1L else 0L

internal fun CardsSection.stateName(): String =
    when (this) {
        CardsSection.NoneIssued -> CARDS_NONE_ISSUED
        is CardsSection.Issued -> CARDS_ISSUED
    }

internal fun CardSubject.kindName(): String =
    when (this) {
        is CardSubject.Player -> SUBJECT_PLAYER
        is CardSubject.NamedPerson -> SUBJECT_NAMED_PERSON
    }

/** A minute is stored as a kind plus an optional number, never as one integer. */
internal fun Minute.kindName(): String =
    when (this) {
        is Minute.Played -> MINUTE_PLAYED
        Minute.HalfTime -> MINUTE_HALF_TIME
        Minute.AfterFinalWhistle -> MINUTE_AFTER_FINAL_WHISTLE
    }

internal fun Minute.numericValue(): Long? = (this as? Minute.Played)?.value?.toLong()

internal fun minuteOf(
    kind: String,
    value: Long?,
): Minute =
    when (kind) {
        MINUTE_HALF_TIME -> Minute.HalfTime
        MINUTE_AFTER_FINAL_WHISTLE -> Minute.AfterFinalWhistle
        else -> Minute.Played(value?.toInt() ?: 0)
    }

internal const val MINUTE_PLAYED = "PLAYED"
internal const val MINUTE_HALF_TIME = "HALF_TIME"
internal const val MINUTE_AFTER_FINAL_WHISTLE = "AFTER_FINAL_WHISTLE"
