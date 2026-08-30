package cz.hspinovace.psmf.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

/**
 * Dates and times as the ZoU writes them, which is the Czech convention:
 * `29.2.24`, `19:00`.
 *
 * Not localised, and that is deliberate rather than an omission. The
 * referee is reading a Czech league schedule whatever language the app is
 * in, and a date reformatted to `2/29/24` for an English UI would not
 * match the paper they are holding or the fixture list on psmf.cz. Only
 * *words* are translated here; numbers keep their shape.
 */
private const val MINUTES_PAD = 2

/** `31. 8.` — day and month, as a fixture list reads. */
fun LocalDate.asDayAndMonth(): String = "$day. ${month.number}."

/** `31. 8. 2026` — the full date, for the header block. */
fun LocalDate.asFullDate(): String = "$day. ${month.number}. $year"

/** `19:00`. */
fun LocalTime.asClockTime(): String = "$hour:${minute.toString().padStart(MINUTES_PAD, '0')}"
