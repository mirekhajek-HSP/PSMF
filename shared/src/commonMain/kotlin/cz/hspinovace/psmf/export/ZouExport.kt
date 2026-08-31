package cz.hspinovace.psmf.export

/** One rendering of the report, ready to be written to a file. */
data class ZouDocument(
    val fileName: String,
    val mimeType: String,
    val content: String,
) {
    val format: ZouFormat get() = ZouFormat.entries.first { it.extension == fileName.substringAfterLast('.') }
}

/**
 * What actually goes on disk, once and in one place.
 *
 * Both places the report is written out -- attached to the mail draft and
 * saved to the device the referee picks -- go through this and neither may
 * re-derive the bytes independently. `encodeToByteArray()` is UTF-8 on
 * every Kotlin target, which is the encoding [ZouCsv.BYTE_ORDER_MARK]
 * assumes: the mark is one character, `﻿`, and it is the UTF-8
 * encoding of that character -- three bytes, `EF BB BF` -- that tells
 * Excel the file is UTF-8 rather than the system code page.
 */
fun ZouDocument.bytes(): ByteArray = content.encodeToByteArray()

enum class ZouFormat(
    val extension: String,
    val mimeType: String,
) {
    /** What goes in the body of the email, and what a person reads. */
    TEXT("txt", "text/plain"),

    /** What replaces retyping. See [ZouCsv] for why the details matter. */
    CSV("csv", "text/csv"),

    /** What a system reads. */
    JSON("json", "application/json"),
}

/**
 * The report in all three formats at once.
 *
 * All three, always, rather than a format the referee has to choose: they
 * cost nothing to produce, PSMF has not yet said which one they want
 * (analysis, ask A8), and the demo is more persuasive holding up the
 * spreadsheet than asking somebody to pick.
 *
 * PDF and `.xlsx` are deliberately absent: no good shared-Kotlin library
 * exists, so building them in the app means writing each one twice
 * (DEMO_SCOPE screen 7).
 */
class ExportZou {
    operator fun invoke(report: ZouReport): List<ZouDocument> {
        val stem = report.fileStem()
        return listOf(
            document(stem, ZouFormat.TEXT, ZouText.format(report)),
            document(stem, ZouFormat.CSV, ZouCsv.format(report)),
            document(stem, ZouFormat.JSON, ZouJson.format(report)),
        )
    }

    private fun document(
        stem: String,
        format: ZouFormat,
        content: String,
    ) = ZouDocument("$stem.${format.extension}", format.mimeType, content)
}

/** The email PSMF already accepts reports at (analysis section 2.4). */
const val PSMF_REPORT_ADDRESS: String = "psmf@psmf.cz"

/**
 * `zapis_6K_2026-08-31_Kominici_United-Smichov`.
 *
 * ASCII only, and diacritics stripped rather than dropped: a file called
 * `Kominci` is confusing and one called `Kominíci` is at the mercy of
 * whatever filesystem, mail client and spreadsheet it passes through on
 * the way to somebody in an office.
 */
fun ZouReport.fileStem(): String =
    listOf(
        "zapis",
        header.league,
        header.date.toString(),
        header.homeTeam,
        header.awayTeam,
    ).joinToString("_") { it.asFileNamePart() }

private fun String.asFileNamePart(): String =
    map { it.deaccented() }
        .joinToString("")
        .map { if (it.isLetterOrDigit() || it == '-') it else '-' }
        .joinToString("")
        .trim('-')
        .ifEmpty { "x" }

/**
 * Czech and Slovak diacritics, folded to their base letters.
 *
 * Hand-written because `java.text.Normalizer` is JVM-only and this has to
 * compile for iOS. The alphabet is closed and short, which is the whole
 * reason that is acceptable here and would not be for general text.
 */
private fun Char.deaccented(): Char {
    val index = ACCENTED.indexOf(this)
    return if (index >= 0) PLAIN[index] else this
}

private const val ACCENTED = "áäčďéěëíĺľňóôöřŕšťúůüýžÁÄČĎÉĚËÍĹĽŇÓÔÖŘŔŠŤÚŮÜÝŽ"
private const val PLAIN = "aacdeeeillnooorrstuuuyzAACDEEEILLNOOORRSTUUUYZ"
