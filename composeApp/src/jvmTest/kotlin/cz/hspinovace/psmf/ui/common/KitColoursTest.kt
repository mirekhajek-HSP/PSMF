package cz.hspinovace.psmf.ui.common

import androidx.compose.ui.graphics.Color
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How far apart two hues are on the colour wheel, 0 to 180 degrees, going the short way round. */
private fun Float.degreesFrom(other: Float): Float = abs(((this - other + 540f) % 360f) - 180f)

/**
 * The table that says what colour a kit's colour name is (0.3.1, Gate 5).
 *
 * **Never a guess.** A name the table does not have draws nothing, because the referee
 * would trust a wrong colour; so most of what is pinned here is what the table does *not*
 * do. The twenty names are those league 6 uses (the shipped seed files, read once to write
 * this list; a test never reads them).
 */
class KitColoursTest {
    private val twenty =
        listOf(
            "černá",
            "bílá",
            "modrá",
            "červená",
            "žlutá",
            "zelená",
            "oranžová",
            "fialová",
            "růžová",
            "šedá",
            "světle zelená",
            "zlatá",
            "tmavě modrá",
            "rudá",
            "světle modrá",
            "hnědá",
            "mentolová",
            "pistáciová",
            "vínová",
            "limetková",
        )

    private fun kit(vararg colours: String) =
        Kit(KitId("k"), colours.joinToString("-").ifBlank { "neznámá" }, colours.toList())

    @Test
    fun everyColourNameTheLeagueUsesHasAColour() {
        assertEquals(20, twenty.size, "the list of names is not the twenty")
        twenty.forEach { assertNotNull(KitColours.of(it), "no colour for '$it'") }
    }

    @Test
    fun eachNameIsDrawnInTheColourItNames() {
        // Not the hex values (a copy of the table would only prove it is a copy): the family each
        // name belongs to, as hue, saturation and brightness. A swapped or mistyped entry leaves it.
        assertEquals(twenty.toSet(), families.keys, "a name has no family to be checked against")
        families.forEach { (name, family) ->
            val colour = assertNotNull(KitColours.of(name), "no colour for '$name'")
            val hsb = colour.hsb()
            assertTrue(
                family.holds(hsb),
                "'$name' is drawn as hue ${hsb.hue.toInt()}, saturation ${hsb.saturation.hundredths()}, " +
                    "brightness ${hsb.brightness.hundredths()}; it should be $family",
            )
        }
    }

    @Test
    fun noTwoOfTheTwentyAreTheSameColourOrNearlyIt() {
        // Two sides are in different kits so that they can be told apart: names that look alike
        // would defeat the shirt. 40 of 255 apart is a difference you can see on a small shirt.
        val colours = twenty.map { it to assertNotNull(KitColours.of(it)) }
        val tooClose =
            colours.flatMapIndexed { index, (name, colour) ->
                colours.drop(index + 1).filter { (_, other) -> colour.distanceTo(other) < 40 }.map { name to it.first }
            }
        assertEquals(emptyList(), tooClose, "these look alike")
    }

    @Test
    fun aNameThatIsNotInTheTableHasNoColour() {
        listOf("tyrkysová", "", "  ", "bordó", "bílo-modrá", "modro", "blue").forEach {
            assertNull(KitColours.of(it), "'$it' was given a colour")
        }
    }

    @Test
    fun theCaseAndTheSpacesAroundANameDoNotChangeWhichNameItIs() {
        assertNotNull(KitColours.of("bílá"))
        assertEquals(KitColours.of("bílá"), KitColours.of(" Bílá "))
        assertNotNull(KitColours.of("světle zelená"))
        assertEquals(KitColours.of("světle zelená"), KitColours.of("SVĚTLE ZELENÁ"))
    }

    // ------------------------------------------------------------------
    // What a kit is drawn from
    // ------------------------------------------------------------------

    @Test
    fun aKitIsDrawnWithOneBandForEachColourItListsInTheOrderItListsThem() {
        assertEquals(listOf(KitColours.of("modrá")), shirtBands(kit("modrá")))
        assertEquals(listOf(KitColours.of("bílá"), KitColours.of("modrá")), shirtBands(kit("bílá", "modrá")))
        assertEquals(
            listOf(KitColours.of("bílá"), KitColours.of("modrá"), KitColours.of("žlutá")),
            shirtBands(kit("bílá", "modrá", "žlutá")),
        )
    }

    @Test
    fun aColourTheTableDoesNotKnowIsLeftEmptyInItsPlaceAndTheOthersAreStillDrawn() {
        assertEquals(listOf(KitColours.of("bílá"), null), shirtBands(kit("bílá", "tyrkysová")))
        assertEquals(listOf(null, KitColours.of("černá")), shirtBands(kit("tyrkysová", "černá")))
    }

    @Test
    fun aKitWithNoKnownColourHasNoShirtAtAll() {
        assertNull(shirtBands(kit("tyrkysová")))
        assertNull(shirtBands(kit("tyrkysová", "bordó")))
        assertNull(shirtBands(kit()))
        assertNull(shirtBands(null))
    }

    // ------------------------------------------------------------------

    private data class Family(
        val hue: Float?,
        val within: Float,
        val saturation: ClosedFloatingPointRange<Float>,
        val brightness: ClosedFloatingPointRange<Float>,
    ) {
        fun holds(hsb: Hsb): Boolean =
            (hue == null || hsb.hue.degreesFrom(hue) <= within) &&
                hsb.saturation in saturation &&
                hsb.brightness in brightness
    }

    private data class Hsb(
        val hue: Float,
        val saturation: Float,
        val brightness: Float,
    )

    private fun Color.hsb(): Hsb {
        val parts = java.awt.Color.RGBtoHSB((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), null)
        return Hsb(parts[0] * 360f, parts[1], parts[2])
    }

    private fun Float.hundredths() = (this * 100).toInt() / 100.0

    private fun Color.distanceTo(other: Color): Double {
        val r = (red - other.red) * 255.0
        val g = (green - other.green) * 255.0
        val b = (blue - other.blue) * 255.0
        return sqrt(r * r + g * g + b * b)
    }

    private fun chromatic(
        hue: Float,
        within: Float,
        saturation: ClosedFloatingPointRange<Float>,
        brightness: ClosedFloatingPointRange<Float>,
    ) = Family(hue, within, saturation, brightness)

    private fun grey(brightness: ClosedFloatingPointRange<Float>) = Family(null, 0f, 0f..0.05f, brightness)

    private val families =
        mapOf(
            "černá" to grey(0f..0.2f),
            "bílá" to grey(0.97f..1f),
            "šedá" to grey(0.5f..0.75f),
            "modrá" to chromatic(220f, 15f, 0.6f..1f, 0.6f..0.9f),
            "světle modrá" to chromatic(210f, 15f, 0.3f..0.7f, 0.9f..1f),
            "tmavě modrá" to chromatic(220f, 15f, 0.6f..1f, 0.2f..0.5f),
            "červená" to chromatic(0f, 10f, 0.6f..1f, 0.8f..1f),
            "rudá" to chromatic(0f, 10f, 0.6f..1f, 0.6f..0.8f),
            "vínová" to chromatic(345f, 15f, 0.6f..1f, 0.3f..0.55f),
            "žlutá" to chromatic(55f, 8f, 0.9f..1f, 0.9f..1f),
            "zlatá" to chromatic(45f, 8f, 0.6f..0.9f, 0.7f..0.9f),
            "oranžová" to chromatic(30f, 10f, 0.9f..1f, 0.9f..1f),
            "zelená" to chromatic(125f, 20f, 0.5f..0.8f, 0.35f..0.6f),
            "světle zelená" to chromatic(125f, 20f, 0.3f..0.55f, 0.65f..0.85f),
            "limetková" to chromatic(72f, 10f, 0.6f..0.9f, 0.75f..0.9f),
            "pistáciová" to chromatic(95f, 12f, 0.3f..0.55f, 0.7f..0.85f),
            "mentolová" to chromatic(157f, 12f, 0.25f..0.5f, 0.8f..1f),
            "fialová" to chromatic(282f, 12f, 0.6f..0.9f, 0.55f..0.8f),
            "růžová" to chromatic(340f, 12f, 0.4f..0.75f, 0.85f..1f),
            "hnědá" to chromatic(15f, 15f, 0.3f..0.6f, 0.35f..0.6f),
        )
}
