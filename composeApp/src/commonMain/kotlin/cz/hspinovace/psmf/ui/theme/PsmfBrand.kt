package cz.hspinovace.psmf.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PSMF's colours, read off psmf.cz rather than invented.
 *
 * | Role | Where it is on their site |
 * |---|---|
 * | [Yellow] | the logo block, section headings, primary buttons |
 * | [Ink] | body text, the dark bar |
 * | [Black] | the top nav strip |
 * | [Surface] | content cards |
 * | [Page] | behind the cards |
 * | [Alert] | their single accent — it takes the red card |
 *
 * **[Yellow] is a surface, not a text colour.** `#FBBA00` on white is about
 * 1.8:1, which fails for anything anyone has to read. It fills; [Ink] goes
 * on top of it, never white.
 *
 * That is why it reaches the colour scheme through the *container* slots and
 * never through `primary`. Material uses `primary` as a content colour as
 * well as a fill — every `TextButton` in the app takes it, "Zpět" and
 * "Vrátit" included — so a yellow `primary` would quietly make those
 * unreadable, on the one screen where the referee is in a hurry.
 *
 * Dark theme keeps the same yellow, which holds up on a dark ground, and
 * swaps the neutrals rather than the brand.
 */
object PsmfBrand {
    val Yellow = Color(0xFFFBBA00)
    val Ink = Color(0xFF2B2B2B)
    val Black = Color(0xFF000000)
    val Surface = Color(0xFFFFFFFF)
    val Page = Color(0xFFF2F2F2)
    val Alert = Color(0xFFD60010)
}
