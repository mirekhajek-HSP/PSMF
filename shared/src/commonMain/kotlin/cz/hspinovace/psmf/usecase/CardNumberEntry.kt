package cz.hspinovace.psmf.usecase

/**
 * Reads the number a referee copies from a player's registration card into
 * the `Číslo RP` column (DECISIONS 2026-10-17).
 *
 * **Digits only, and four to seven of them.** The bounds are a judgement
 * about typing, not a fact about PSMF's numbering, which nobody has told us:
 *
 * - *Five* is what every example on the form has (`59001`, `56252`), so
 *   that is the middle of the range.
 * - *At least four*, because shorter is likelier a jersey number typed into
 *   the wrong box than a card number -- and the field next to it is the
 *   jersey number. A missing digit is also what a hurried thumb does.
 * - *At most seven*, so a number a digit or two longer than the examples is
 *   never refused at the pitch by an app that guessed wrong; nothing is
 *   gained by turning away a real number, and no length check can tell a
 *   wrong digit from a right one anyway.
 *
 * Spaces are dropped, so `59 001` as the card prints it is read as it is
 * meant. Anything else -- a letter, a dot, a dash -- is not a number copied
 * from a card. A string of digits is returned as it was typed, leading
 * zeros included: it is what was written, not a quantity.
 *
 * **Nothing here makes an `RpNumber`.** The result is a string that goes on
 * an appearance as what was written that day; there is no path from it to
 * the player or to league data.
 */
fun parseCardNumber(raw: String): String? {
    val digits = raw.filterNot { it.isWhitespace() }
    if (digits.length !in CARD_NUMBER_LENGTHS) return null
    return digits.takeIf { text -> text.all { it in '0'..'9' } }
}

/** How many digits a number copied from a card may have. See [parseCardNumber]. */
val CARD_NUMBER_LENGTHS: IntRange = 4..7
