package cz.hspinovace.psmf.usecase

import kotlinx.datetime.LocalDate

/**
 * Reads a date of birth the way a referee would type one at a pitch.
 *
 * A date *picker* is the wrong control here: reaching 1992 means scrolling
 * back thirty years one gesture at a time, in the cold, one-handed. Typing
 * eight digits is faster and the parsed date is echoed back for checking.
 *
 * Accepts what people actually write:
 * - `18.5.1992`, `18. 5. 1992`, `18/5/1992`
 * - `18051992` — eight digits, no separators, the fastest to enter
 * - `1992-05-18` — ISO, for anyone pasting from elsewhere
 *
 * Returns null for anything else, including dates that do not exist:
 * 31 February is a typo, not a birthday.
 */
fun parseDateOfBirth(raw: String): LocalDate? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    return when {
        ISO.matches(trimmed) -> {
            runCatching { LocalDate.parse(trimmed) }.getOrNull()
        }

        EIGHT_DIGITS.matches(trimmed) -> {
            dateOrNull(
                day = trimmed.substring(0, DAY_DIGITS).toInt(),
                month = trimmed.substring(DAY_DIGITS, DAY_DIGITS + MONTH_DIGITS).toInt(),
                year = trimmed.substring(DAY_DIGITS + MONTH_DIGITS).toInt(),
            )
        }

        else -> {
            fromSeparatedParts(trimmed)
        }
    }
}

private fun fromSeparatedParts(trimmed: String): LocalDate? {
    val parts = trimmed.split('.', '/').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size != PARTS_IN_A_DATE) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    return dateOrNull(day = numbers[0], month = numbers[1], year = numbers[2])
}

/**
 * A year outside this range is a typo — a mistyped two-digit year, or a
 * day and a year swapped. Rejecting it is kinder than writing 0018 on a
 * report that goes to PSMF.
 */
private fun dateOrNull(
    day: Int,
    month: Int,
    year: Int,
): LocalDate? {
    if (year !in PLAUSIBLE_YEARS) return null
    // LocalDate throws on 31 February and friends, which is the point.
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

private val ISO = Regex("""\d{4}-\d{2}-\d{2}""")
private val EIGHT_DIGITS = Regex("""\d{8}""")
private const val DAY_DIGITS = 2
private const val MONTH_DIGITS = 2
private const val PARTS_IN_A_DATE = 3
private val PLAUSIBLE_YEARS = 1900..2100
