package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shirt drawn in a kit's colours (0.3.1, Gate 5), read from its pixels.
 *
 * What is pinned: two colours show as two and three as three, in the order the label names
 * them; a colour the table does not know is a gap and never a stand-in; a kit with no known
 * colour draws no shirt and takes no room; it is described by the kit's label; and it is
 * seen against the ground it sits on, light or dark, whatever colour it is (a white shirt
 * on a light screen and a black one on a dark screen need an outline to be there at all).
 */
@OptIn(ExperimentalTestApi::class)
class KitShirtTest {
    private fun kit(
        label: String,
        vararg colours: String,
    ) = Kit(KitId(label), label, colours.toList())

    private val white = KitColours.of("bílá")
    private val blue = KitColours.of("modrá")
    private val yellow = KitColours.of("žlutá")

    /** The shirt alone on a scoreboard's ground, large enough (4 px to the dp) to read bands from. */
    private fun ComposeUiTest.show(
        kit: Kit?,
        dark: Boolean = false,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LARGE, 1f)) {
                PsmfTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(Modifier.padding(8.dp)) { KitShirt(kit, Modifier.testTag(SHIRT)) }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.shirt() = ShirtImage(onNodeWithTag(SHIRT).captureToImage().toPixelMap())

    private fun assertBands(
        expected: List<Color?>,
        actual: List<Color>,
        ground: Color,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (colour, drawn) ->
            val want = colour ?: ground
            assertTrue(drawn.isNear(want), "band ${index + 1} of ${expected.size} is $drawn, it should be $want")
        }
    }

    @Test
    fun oneColourFillsTheShirt() =
        runComposeUiTest {
            show(kit("modrá", "modrá"))
            val shirt = shirt()

            assertBands(listOf(blue), shirt.bands(1), shirt.ground)
        }

    @Test
    fun twoColoursShowAsTwoInTheOrderTheLabelNamesThem() =
        runComposeUiTest {
            show(kit("bílo-modrá", "bílá", "modrá"))
            val shirt = shirt()

            assertBands(listOf(white, blue), shirt.bands(2), shirt.ground)
        }

    @Test
    fun threeColoursShowAsThree() =
        runComposeUiTest {
            show(kit("bílo-modro-žlutá", "bílá", "modrá", "žlutá"))
            val shirt = shirt()

            assertBands(listOf(white, blue, yellow), shirt.bands(3), shirt.ground)
        }

    @Test
    fun aColourTheTableDoesNotKnowIsAGapAndNotAStandIn() =
        runComposeUiTest {
            // bílo-tyrkysová: the white is drawn; the other half draws nothing -- not grey, not white.
            show(kit("bílo-tyrkysová", "bílá", "tyrkysová"))
            val shirt = shirt()

            assertBands(listOf(white, null), shirt.bands(2), shirt.ground)
        }

    @Test
    fun aKitWithNoKnownColourDrawsNoShirtAndTakesNoRoom() =
        runComposeUiTest {
            setContent {
                PsmfTheme {
                    Row {
                        KitShirt(kit("tyrkysová", "tyrkysová"), Modifier.testTag(SHIRT))
                        KitShirt(null, Modifier.testTag(SHIRT))
                        Text("after", Modifier.testTag("after"))
                    }
                }
            }

            onAllNodesWithTag(SHIRT).assertCountEquals(0)
            assertEquals(0.dp, onNodeWithTag("after").getBoundsInRoot().left, "the missing shirt left a gap")
        }

    @Test
    fun theShirtIsDescribedByTheKitsLabel() =
        runComposeUiTest {
            show(kit("bílo-modrá", "bílá", "modrá"))

            onNodeWithTag(SHIRT).assertContentDescriptionEquals("bílo-modrá")
        }

    // ------------------------------------------------------------------
    // Seen, whatever the colour and whatever it is on
    // ------------------------------------------------------------------

    @Test
    fun everyColourIsSeenOnTheScoreboardInTheLightTheme() = everyColourIsSeen(dark = false, onScoreboard = true)

    @Test
    fun everyColourIsSeenOnATabInTheLightTheme() = everyColourIsSeen(dark = false, onScoreboard = false)

    @Test
    fun everyColourIsSeenOnTheScoreboardInTheDarkTheme() = everyColourIsSeen(dark = true, onScoreboard = true)

    @Test
    fun everyColourIsSeenOnATabInTheDarkTheme() = everyColourIsSeen(dark = true, onScoreboard = false)

    private fun everyColourIsSeen(
        dark: Boolean,
        onScoreboard: Boolean,
    ) = runComposeUiTest {
        val names = TWENTY
        setContent {
            // At the standard size, which is the one that has to read; a column of twenty fits the window.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                PsmfTheme(darkTheme = dark) {
                    // A scoreboard sits on surfaceVariant and the tab bar on surface.
                    val colours = MaterialTheme.colorScheme
                    Surface(color = if (onScoreboard) colours.surfaceVariant else colours.surface) {
                        Column {
                            names.forEach { name -> KitShirt(kit(name, name), Modifier.testTag(name)) }
                        }
                    }
                }
            }
        }

        val unseen =
            names.filter { name ->
                ShirtImage(onNodeWithTag(name).captureToImage().toPixelMap()).strongestContrast() < MINIMUM_CONTRAST
            }
        val where = (if (dark) "dark" else "light") + (if (onScoreboard) ", scoreboard" else ", tab bar")
        assertEquals(emptyList(), unseen, "these shirts cannot be told from the ground ($where)")
    }

    @Test
    fun theShirtsOfTheTwentyAreAllThereToBeChecked() {
        // Guards the loop above: a name the table lacks draws nothing, and "nothing" has no contrast
        // to fail on, so a gap in the table would otherwise read as a pass.
        TWENTY.forEach { assertFalse(shirtBands(kit(it, it)) == null, "no shirt for '$it'") }
    }

    private companion object {
        const val SHIRT = "shirt"
        const val LARGE = 4f

        /** WCAG's floor for the parts of a control that are not text. */
        const val MINIMUM_CONTRAST = 3.0

        val TWENTY =
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
    }
}
