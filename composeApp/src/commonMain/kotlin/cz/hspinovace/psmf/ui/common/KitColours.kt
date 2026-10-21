package cz.hspinovace.psmf.ui.common

import androidx.compose.ui.graphics.Color
import cz.hspinovace.psmf.domain.Kit

/**
 * What colour a kit's colour name is, for the shirt drawn on the console and on the team tabs
 * (0.3.1, prompt 15, Gate 5).
 *
 * **Here and nowhere else.** `Kit.colours` are Czech names, "for the app only". The report's
 * *Barva dresů* is the label PSMF wrote, snapshotted on the lineup, and is never derived from
 * the colours or from this table; nothing in `shared/` or the export can read it, because it is
 * in this module and they are not.
 *
 * **Never a guess.** A name that is not in the table has no colour, and the shirt draws nothing
 * for it: a wrong colour is worse than none, because the referee would trust it. The twenty
 * names are those league 6 uses; a new league's new name draws nothing until it is added here.
 * An entry is the colour of the name, as a kit would be dyed: `světle` is lighter and `tmavě`
 * darker than the plain name, and `rudá` is a deeper red than `červená`.
 */
object KitColours {
    private val table: Map<String, Color> =
        mapOf(
            "černá" to Color(0xFF1E1E1E),
            "bílá" to Color(0xFFFFFFFF),
            "šedá" to Color(0xFF9E9E9E),
            "modrá" to Color(0xFF1E5BC6),
            "světle modrá" to Color(0xFF64B5F6),
            "tmavě modrá" to Color(0xFF0D2A6B),
            "červená" to Color(0xFFE53935),
            "rudá" to Color(0xFFB71C1C),
            "vínová" to Color(0xFF6D1930),
            "žlutá" to Color(0xFFFFEB00),
            "zlatá" to Color(0xFFC9A227),
            "oranžová" to Color(0xFFF57C00),
            "zelená" to Color(0xFF2E7D32),
            "světle zelená" to Color(0xFF66BB6A),
            "limetková" to Color(0xFFB5D334),
            "pistáciová" to Color(0xFF93C572),
            "mentolová" to Color(0xFF8EE3C2),
            "fialová" to Color(0xFF7B1FA2),
            "růžová" to Color(0xFFF06292),
            "hnědá" to Color(0xFF795548),
        )

    /**
     * The colour the app draws for [name], or null for a name it does not know. Case and the
     * spaces around the name do not matter.
     */
    fun of(name: String): Color? = table[name.trim().lowercase()]
}

/**
 * What a shirt is drawn from: one entry for each colour [kit] lists, in the order it lists them,
 * and null where the table has no colour for the name (that band is left empty). Null altogether
 * when it has none for any of them, or there is no kit: then there is no shirt.
 */
internal fun shirtBands(kit: Kit?): List<Color?>? {
    val bands = kit?.colours.orEmpty().map { KitColours.of(it) }
    return bands.takeIf { known -> known.any { it != null } }
}
