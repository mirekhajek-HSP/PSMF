package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a squad row opens and closes (DECISIONS 2026-10-21, *From the testers
 * of 0.3.0*).
 *
 * 0.3.0 gave every player who had not come the height of one who had, about
 * 160 dp of blank, so that marking never moved the rows below. The testers
 * found four screens of scrolling for a squad of twenty. What was meant was
 * only that **the name being tapped stays where it is**; the rows below
 * moving down is what every list does.
 */
@OptIn(ExperimentalTestApi::class)
class SquadRowTest : LineupScreenTestBase() {
    // ------------------------------------------------------------------
    // Opening and closing
    // ------------------------------------------------------------------

    @Test
    fun markingARowOpensItDownwardsAndTheTappedNameStaysWhereItIs() = markingOpensTheRow(fontScale = 1f)

    @Test
    fun markingARowOpensItDownwardsAndTheTappedNameStaysWhereItIsAtALargeFontToo() =
        markingOpensTheRow(fontScale = 1.5f)

    private fun markingOpensTheRow(fontScale: Float) =
        runComposeUiTest {
            var current by mutableStateOf(state(listOf(member(novak), member(poupe), member(kriz))))
            withLanguage("cs") {
                showLineup(fontScale) { current }
                val nameBefore = topOf("Novák Jan")
                val belowBefore = topOf("Poupě Petr")

                current = state(listOf(member(novak, present = true), member(poupe), member(kriz)))
                waitForIdle()

                assertEquals(nameBefore, topOf("Novák Jan"), "the name that was tapped moved")
                val opened = topOf("Poupě Petr") - belowBefore
                assertTrue(
                    opened >= PsmfDimens.minTouchTarget,
                    "the rows below moved down by $opened: the row did not open under the name",
                )

                // And taking the mark back closes it, to where it was.
                current = state(listOf(member(novak), member(poupe), member(kriz)))
                waitForIdle()
                assertEquals(belowBefore, topOf("Poupě Petr"), "taking the mark back did not close the row")
                assertEquals(nameBefore, topOf("Novák Jan"), "closing the row moved the name")
            }
        }

    @Test
    fun aRowOpensAtOnceSoNothingIsStillMovingUnderTheNextTap() =
        runComposeUiTest {
            // A short animation of the opening was tried and dropped: a tap on
            // the next name, made while the row above was still opening, was
            // lost -- the name had moved under the finger, where a tap on the
            // row that was opening (it does not move) registered. So two
            // frames after the tap (the
            // second settles a one-off re-measure of the new fields, 2 dp),
            // the rows are where they will stay.
            var current by mutableStateOf(state(listOf(member(novak), member(poupe), member(kriz))))
            withLanguage("cs") {
                showLineup(fontScale = 1f) { current }
                val closed = topOf("Poupě Petr")

                mainClock.autoAdvance = false
                current = state(listOf(member(novak, present = true), member(poupe), member(kriz)))
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                val twoFramesLater = topOf("Poupě Petr")

                mainClock.autoAdvance = true
                waitForIdle()
                val settled = topOf("Poupě Petr")

                assertTrue(settled > closed, "the row did not open: $closed, $settled")
                assertEquals(settled, twoFramesLater, "two frames after the tap the next name was still on its way")
            }
        }

    // ------------------------------------------------------------------
    // How tall a row that has not been marked is
    // ------------------------------------------------------------------

    @Test
    fun anUnmarkedRowIsAsTallAsATouchTargetAndNoTaller() = unmarkedRowsAreOneLine(fontScale = 1f)

    @Test
    fun anUnmarkedRowIsAsTallAsATouchTargetAndNoTallerAtALargeFontToo() = unmarkedRowsAreOneLine(fontScale = 1.5f)

    /**
     * The distance from one unmarked name to the next is the row and its
     * hairline, and nothing else: no gap that a thumb falls into, and no
     * room kept for controls the row does not show.
     */
    private fun unmarkedRowsAreOneLine(fontScale: Float) =
        runComposeUiTest {
            withLanguage("cs") {
                showLineup(fontScale) { state(listOf(member(novak), member(poupe), member(kriz))) }

                val pitch = topOf("Poupě Petr") - topOf("Novák Jan")

                assertTrue(pitch >= PsmfDimens.minTouchTarget, "an unmarked row is only $pitch: not a touch target")
                assertTrue(
                    pitch <= PsmfDimens.minTouchTarget + HAIRLINE_AND_ROUNDING,
                    "an unmarked row takes $pitch; one line of its name needs " +
                        "${PsmfDimens.minTouchTarget} and a hairline",
                )
            }
        }

    // ------------------------------------------------------------------
    // What was typed
    // ------------------------------------------------------------------

    @Test
    fun whatWasTypedInARowIsThereWhenTheRowOpensAgain() =
        runComposeUiTest {
            // The row's fields are not drawn while the player is unmarked, so
            // they are made again when the mark is given. What they show is
            // the screen's, which the ViewModel kept: half a card number, a
            // shirt number the referee changed, a date of birth.
            val withCard = member(poupe, card = "59").copy(jerseyNumber = JerseyNumber(14))
            val withDate = member(novak, cardPresent = false).withDateOfBirthTyped("18.5.1992")
            var current by mutableStateOf(state(listOf(withCard, withDate)))
            withLanguage("cs") {
                showLineup(fontScale = 1f) { current }
                onAllNodes(hasSetTextAction()).assertCountEquals(0)

                current = state(listOf(withCard.copy(present = true), withDate.copy(present = true)))
                waitForIdle()

                onNode(hasSetTextAction() and hasText("59")).assertExists()
                onNode(hasSetTextAction() and hasText("14")).assertExists()
                onNode(hasSetTextAction() and hasText("18.5.1992")).assertExists()
            }
        }

    // ------------------------------------------------------------------

    private fun ComposeUiTest.showLineup(
        fontScale: Float,
        shown: () -> LineupUiState,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = fontScale)) {
                // Tall enough to lay every row out: the host's window shows
                // a few at this density.
                Box(modifier = Modifier.requiredSize(width = 360.dp, height = 2400.dp)) {
                    PsmfTheme { LineupScreen(state = shown(), onEvent = {}) }
                }
            }
        }
    }

    private fun ComposeUiTest.topOf(name: String): Dp = onNodeWithText(name).getBoundsInRoot().top

    private companion object {
        /** The divider under a row is 1 dp; the rest is a pixel or two of rounding at 2.625 px per dp. */
        val HAIRLINE_AND_ROUNDING = 2.dp
    }
}
