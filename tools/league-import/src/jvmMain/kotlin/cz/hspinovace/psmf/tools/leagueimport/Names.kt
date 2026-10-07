package cz.hspinovace.psmf.tools.leagueimport

import java.text.Normalizer

/**
 * Names as psmf.cz writes them, which is two ways round.
 *
 * *Statistiky* writes **surname first** (`Bělohlávek Jan`); lineups, goals
 * and cards write **first name first** (`Jan Bělohlávek`). Matching goes by
 * the set of words, so the order does not matter -- and nothing else is
 * forgiven: no diacritic folding, no edit distance. `Jamrik` and `Jamrík`
 * are two names, and the importer reports them rather than guessing.
 */
object Names {
    private val WHITESPACE = Regex("\\s+")

    fun words(name: String): List<String> =
        Normalizer
            .normalize(name.trim(), Normalizer.Form.NFC)
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }

    /** Order-free and case-free, and nothing more forgiving than that. */
    fun matchKey(name: String): String = words(name).map { it.lowercase() }.sorted().joinToString(" ")

    /**
     * `Bělohlávek Jan` into surname and first name.
     *
     * Where the same person's lineup spelling is known, the split is the one
     * that turns one order into the other -- which is what makes `Di Maria
     * Angel` / `Angel Di Maria` come out as surname `Di Maria`. Without one,
     * the first word is the surname. Null for a one-word name, which cannot
     * be a `PlayerName`.
     */
    fun split(
        surnameFirst: String,
        firstNameFirst: String?,
    ): Pair<String, String>? {
        val stats = words(surnameFirst)
        if (stats.size < 2) return null
        val lineup = firstNameFirst?.let(::words)?.map { it.lowercase() }
        if (lineup != null && lineup.size == stats.size) {
            for (cut in 1 until stats.size) {
                val rotated = stats.drop(cut) + stats.take(cut)
                if (rotated.map { it.lowercase() } == lineup) {
                    return stats.take(cut).joinToString(" ") to stats.drop(cut).joinToString(" ")
                }
            }
        }
        return stats.first() to stats.drop(1).joinToString(" ")
    }

    /** `Bělohlávek Jan` -> `belohlavek-jan`. ASCII, for refs a person can type. */
    fun slug(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}

/**
 * `/dresy/` labels, read the way the site writes them.
 *
 * A comma separates a team's **two kits**; some teams list one. Each kit's
 * label is kept verbatim -- it is what the report writes -- and its colours
 * are a best effort for the app's chips only.
 */
object KitLabels {
    fun split(cell: String): List<String> = cell.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * `červeno-černá` -> `červená`, `černá`. The first element of a Czech
     * compound takes `-o`; the adjective takes `-á`. `světle zelená` is one
     * colour and stays one.
     */
    fun colours(label: String): List<String> {
        val parts = label.split('-').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.mapIndexed { index, part ->
            if (index < parts.lastIndex && part.endsWith("o")) part.dropLast(1) + "á" else part
        }
    }
}
