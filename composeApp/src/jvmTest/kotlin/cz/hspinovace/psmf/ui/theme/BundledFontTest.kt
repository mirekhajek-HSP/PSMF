package cz.hspinovace.psmf.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The trap this exists for.**
 *
 * PSMF's own site uses Anton for headings and Barlow for body. Both are
 * Google Fonts under the OFL, so bundling them would cost nothing — and
 * **neither covers Cyrillic**: both are `latin`, `latin-ext`,
 * `vietnamese`. `latin-ext` handles every Czech diacritic, so a Czech
 * screen looks perfect and a Ukrainian one silently falls back to a system
 * face mid-sentence. That reads as a rendering bug because it is one.
 *
 * So the app uses Oswald and Noto Sans, and this test is the reason it can
 * be said rather than assumed: it reads the actual bundled files and asks
 * their `cmap` tables whether they can draw the six letters that separate
 * Ukrainian from Russian, and the Czech diacritics beside them. Swap a font
 * for one without Cyrillic and this fails; look at a screenshot instead and
 * it looks fine until a Ukrainian captain holds the phone.
 */
class BundledFontTest {
    private val fonts: File =
        File(
            System.getProperty(FONT_DIRECTORY_PROPERTY)
                ?: error("$FONT_DIRECTORY_PROPERTY is unset — see the Test wiring in composeApp/build.gradle.kts"),
        )

    @Test
    fun everyBundledFontCanDrawUkrainian() {
        bundled().forEach { font ->
            val covered = font.codePoints()
            UKRAINIAN.forEach { (code, letter) ->
                assertContains(covered, code, "${font.name} cannot draw $letter")
            }
        }
    }

    @Test
    fun everyBundledFontCanDrawCzech() {
        bundled().forEach { font ->
            val covered = font.codePoints()
            CZECH.forEach { (code, letter) ->
                assertContains(covered, code, "${font.name} cannot draw $letter")
            }
        }
    }

    @Test
    fun bothFamiliesAreThereInTheWeightsTheTypeScaleAsksFor() {
        // The scale uses Bold for display and headings, and Normal, Medium
        // and Bold for body. A weight that is not bundled is synthesised,
        // and faux bold on a condensed face looks like a mistake.
        assertEquals(
            listOf(
                "noto_sans_bold.ttf",
                "noto_sans_medium.ttf",
                "noto_sans_regular.ttf",
                "oswald_bold.ttf",
                "oswald_regular.ttf",
            ),
            bundled().map { it.name }.sorted(),
        )
    }

    @Test
    fun theFontsAreBundledRatherThanFetched() {
        // The app has no network by design and a referee on a pitch may
        // have no signal, so a font that is downloaded is a screen that is
        // sometimes blank. Files on disk are the whole mechanism.
        bundled().forEach { assertTrue(it.length() > 0, "${it.name} is empty") }
    }

    private fun bundled(): List<File> {
        val files = fonts.listFiles { file -> file.extension == "ttf" }?.toList().orEmpty()
        assertTrue(files.isNotEmpty(), "no fonts found in $fonts")
        return files
    }

    // -----------------------------------------------------------------
    // Enough of a TrueType reader to answer one question
    //
    // Hand-rolled because the alternative is a font-tooling dependency in
    // the test classpath for a single lookup. Only the two `cmap` formats a
    // modern TTF uses: 4, which covers the basic plane, and 12, which
    // covers everything.
    // -----------------------------------------------------------------

    private fun File.codePoints(): Set<Int> {
        val bytes = readBytes()
        val cmap = bytes.tableOffset("cmap") ?: error("$name has no cmap table")
        val subtables = bytes.uint16(cmap + 2)

        val covered = mutableSetOf<Int>()
        repeat(subtables) { index ->
            val record = cmap + 4 + index * 8
            val subtable = cmap + bytes.uint32(record + 4)
            when (bytes.uint16(subtable)) {
                FORMAT_SEGMENTS -> covered += bytes.segmentCoverage(subtable)
                FORMAT_GROUPS -> covered += bytes.groupCoverage(subtable)
                else -> Unit
            }
        }
        return covered
    }

    private fun ByteArray.tableOffset(tag: String): Int? {
        val tables = uint16(4)
        repeat(tables) { index ->
            val record = TABLE_DIRECTORY + index * 16
            val name = (0 until 4).map { this[record + it].toInt().toChar() }.joinToString("")
            if (name == tag) return uint32(record + 8)
        }
        return null
    }

    /** Format 4: parallel arrays of segment start and end. */
    private fun ByteArray.segmentCoverage(subtable: Int): Set<Int> {
        val segments = uint16(subtable + 6) / 2
        val ends = subtable + 14
        val starts = ends + segments * 2 + 2
        val covered = mutableSetOf<Int>()
        repeat(segments) { index ->
            val start = uint16(starts + index * 2)
            val end = uint16(ends + index * 2)
            if (start != LAST_SEGMENT) covered += start..end
        }
        return covered
    }

    /** Format 12: groups of (first, last, glyph). */
    private fun ByteArray.groupCoverage(subtable: Int): Set<Int> {
        val groups = uint32(subtable + 12)
        val covered = mutableSetOf<Int>()
        repeat(groups) { index ->
            val group = subtable + 16 + index * 12
            covered += uint32(group)..uint32(group + 4)
        }
        return covered
    }

    private fun ByteArray.uint16(at: Int): Int = (this[at].toInt() and 0xFF shl 8) or (this[at + 1].toInt() and 0xFF)

    private fun ByteArray.uint32(at: Int): Int = (uint16(at) shl 16) or uint16(at + 2)

    private companion object {
        const val FONT_DIRECTORY_PROPERTY = "psmf.fontDirectory"
        const val TABLE_DIRECTORY = 12
        const val FORMAT_SEGMENTS = 4
        const val FORMAT_GROUPS = 12
        const val LAST_SEGMENT = 0xFFFF

        /** The letters that separate Ukrainian from Russian. */
        val UKRAINIAN =
            mapOf(
                0x0404 to "Є",
                0x0406 to "І",
                0x0407 to "Ї",
                0x0456 to "і",
                0x0457 to "ї",
                0x0491 to "ґ",
            )

        /** What `latin-ext` is supposed to cover, and Anton and Barlow do. */
        val CZECH =
            mapOf(
                0x010D to "č",
                0x011B to "ě",
                0x0159 to "ř",
                0x0161 to "š",
                0x016F to "ů",
                0x017E to "ž",
            )
    }
}
