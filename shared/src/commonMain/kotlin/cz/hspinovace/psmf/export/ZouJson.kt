package cz.hspinovace.psmf.export

import kotlinx.serialization.json.Json

/**
 * The report as structured data, for whatever PSMF's systems vendor
 * eventually wants to read.
 *
 * **Keys are stable ASCII identifiers; values are the report.** That is
 * not a localisation: a key is a field identifier and does not change with
 * anything, whereas every human-readable value here is exactly what goes
 * on the Czech form. The rule the export must not break is that the
 * *report* follows the form rather than the app's language, and it does.
 *
 * Pretty-printed on purpose. This is a demo artefact somebody will open in
 * a text editor to see whether the data is all there, and a single line of
 * minified JSON answers that question badly.
 */
object ZouJson {
    val FORMAT: Json =
        Json {
            prettyPrint = true
            // A report with no assistant should say so, not omit the field:
            // a reader cannot tell an absent key from an unsupported one.
            explicitNulls = true
            encodeDefaults = true
        }

    fun format(report: ZouReport): String = FORMAT.encodeToString(report)

    fun parse(raw: String): ZouReport = FORMAT.decodeFromString(raw)
}
