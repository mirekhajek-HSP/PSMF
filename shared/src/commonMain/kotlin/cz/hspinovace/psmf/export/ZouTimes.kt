package cz.hspinovace.psmf.export

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When a confirmation was given, as the Czech report writes it:
 * `31. 8. 2026 20:05`, in **Prague time whatever the phone's zone is**.
 *
 * The league is Czech and so is the report, in a fixed language
 * (see [ZouLabels]); a time that read differently depending on where the
 * referee's phone thought it was would make the same report say two things.
 * The JSON keeps the instant itself.
 */
object ZouTimes {
    private val prague = TimeZone.of("Europe/Prague")

    fun written(at: Instant): String {
        val local = at.toLocalDateTime(prague)
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        return "${local.day}. ${(local.month.ordinal + 1)}. ${local.year} $hour:$minute"
    }
}
