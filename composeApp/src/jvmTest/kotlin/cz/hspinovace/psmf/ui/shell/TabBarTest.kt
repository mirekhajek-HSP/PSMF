package cz.hspinovace.psmf.ui.shell

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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import cz.hspinovace.psmf.ui.breaksInsideAWord
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.textLayout
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bar at the foot of the screen, at the widths phones have (0.3.1, Gate 4; the
 * testers' point 4: in Ukrainian *Налаштування* did not fit its tab).
 *
 * **At a true width.** The width is given outside the density, so 360 is 360 dp
 * at whatever font scale is asked for; and what is asked of a label is where its
 * lines broke, because with `maxLines` set a word cut in two is still "two lines".
 */
@OptIn(ExperimentalTestApi::class)
class TabBarTest {
    @Test
    fun everyUkrainianLabelIsOnOneLineWithNoWordBrokenAtThePhoneWidthsAndBothFontScales() =
        runComposeUiTest {
            withLanguage("uk") {
                val phone = Phone(this)
                val problems = mutableListOf<String>()
                for (width in listOf(360.dp, 411.dp)) {
                    for (fontScale in listOf(1f, 1.3f)) {
                        phone.at(width, fontScale)
                        TABS_UK.forEach { label ->
                            val layout = onNodeWithText(label).textLayout()
                            if (layout.breaksInsideAWord() || layout.lineCount != 1) {
                                problems +=
                                    "$label at ${width.value.toInt()} dp x$fontScale: ${layout.lineCount} lines, " +
                                    "word broken ${layout.breaksInsideAWord()}"
                            }
                        }
                    }
                }
                assertTrue(problems.isEmpty(), problems.joinToString("\n"))
            }
        }

    @Test
    fun theCzechBarIsFourEqualQuartersAsItWas() =
        runComposeUiTest {
            withLanguage("cs") {
                val phone = Phone(this)
                for (fontScale in listOf(1f, 1.3f)) {
                    phone.at(360.dp, fontScale)
                    val widths = TABS_CS.map { onNodeWithText(it).getBoundsInRoot().width }
                    widths.forEach { assertNear(widths.first(), it, "Czech tabs at x$fontScale: $widths") }
                }
            }
        }

    @Test
    fun aTabThatNeedsMoreThanAQuarterKeepsWhatItNeedsAndTheRestShareWhatIsLeft() =
        runComposeUiTest {
            // The Report tab with its badge, in Ukrainian at 130 %: *Протокол* and the dot
            // are 102 dp, a quarter is 90.
            withLanguage("uk") {
                Phone(this).at(360.dp, 1.3f)
                val widths = TABS_UK.map { onNodeWithText(it).getBoundsInRoot().width }
                val report = widths[1]
                val others = widths.filterIndexed { index, _ -> index != 1 }

                assertTrue(report > others.max() + 1.dp, "the Report tab has no more room than the others: $widths")
                others.forEach { assertNear(others.first(), it, "the other three differ: $widths") }
                // And together they are the bar, with no gap and nothing over.
                assertNear(360.dp, widths.fold(0.dp) { sum, w -> sum + w }, "the tabs do not fill the bar: $widths")
            }
        }

    // ------------------------------------------------------------------
    // The sharing itself
    // ------------------------------------------------------------------

    @Test
    fun whenNoLabelNeedsMoreThanItsShareTheTabsAreEqual() {
        assertEquals(listOf(90, 90, 90, 90), shareOutTheWidth(360, listOf(50, 60, 70, 80)))
    }

    @Test
    fun aLabelThatNeedsMoreKeepsWhatItNeedsAndTheOthersShareTheRest() {
        assertEquals(listOf(84, 84, 84, 108), shareOutTheWidth(360, listOf(50, 60, 70, 108)))
    }

    @Test
    fun aTabThatFallsShortOnlyAfterAnotherGrewKeepsWhatItNeedsToo() {
        // 90 each to start with: two need 100 and keep it; that leaves 80 each for the other two,
        // and the first of those needs 88; it keeps that, and the last has what is left.
        assertEquals(listOf(88, 100, 100, 72), shareOutTheWidth(360, listOf(88, 100, 100, 40)))
    }

    @Test
    fun theWholeWidthIsUsedWhateverTheRounding() {
        listOf(358, 359, 360, 361, 411).forEach { width ->
            assertEquals(width, shareOutTheWidth(width, listOf(50, 61, 70, 108)).sum(), "at $width")
            assertEquals(width, shareOutTheWidth(width, listOf(50, 60, 70, 80)).sum(), "at $width")
        }
    }

    @Test
    fun whenTheLabelsTogetherNeedMoreThanThereIsTheyAreScaledDownTogether() {
        // In proportion to what each needs: 100 : 100 : 100 : 200 of 300.
        assertEquals(listOf(60, 60, 60, 120), shareOutTheWidth(300, listOf(100, 100, 100, 200)))
    }

    // ------------------------------------------------------------------

    /** The bar on a phone of the width and font scale last asked for; one `setContent`, then state. */
    private class Phone(
        private val test: ComposeUiTest,
    ) {
        private var shown by mutableStateOf(360.dp to 1f)

        init {
            test.setContent {
                val (width, fontScale) = shown
                CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = fontScale)) {
                    Box(modifier = Modifier.requiredSize(width = width, height = 400.dp)) {
                        PsmfTheme {
                            // In progress, so that the Report tab carries its badge: the widest it gets.
                            PsmfNavigationBar(selected = Tab.REPORT, reportInProgress = true, onSelect = {})
                        }
                    }
                }
            }
        }

        fun at(
            width: Dp,
            fontScale: Float,
        ) {
            shown = width to fontScale
            test.waitForIdle()
        }
    }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
        message: String,
    ) = assertTrue(abs((expected - actual).value) <= 1f, message)

    private companion object {
        val TABS_UK = listOf("Матчі", "Протокол", "Команди", "Опції")
        val TABS_CS = listOf("Zápasy", "Zápis", "Týmy", "Nastavení")
    }
}
