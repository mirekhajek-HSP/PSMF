package cz.hspinovace.psmf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Yellow is a surface, not a text colour.**
 *
 * `#FBBA00` on white is about 1.8:1. The rule is easy to state and easy to
 * lose: Material uses `primary` as a content colour as well as a fill, so
 * putting the brand there would make every `TextButton` in the app — "Zpět"
 * and "Vrátit" included — unreadable, on the screens where the referee is
 * in the cold and in a hurry.
 *
 * These tests pin the arrangement that avoids it, in both themes, by
 * measuring rather than by inspection.
 */
@OptIn(ExperimentalTestApi::class)
class PsmfBrandTest {
    @Test
    fun inkOnYellowIsReadableAndWhiteOnYellowIsNot() {
        // The second half is the point: it is why the rule says never white.
        assertTrue(contrast(PsmfBrand.Ink, PsmfBrand.Yellow) >= BODY_TEXT_MINIMUM)
        assertTrue(contrast(PsmfBrand.Surface, PsmfBrand.Yellow) < BODY_TEXT_MINIMUM)
    }

    @Test
    fun theBrandIsInTheContainerSlotsAndNeverInPrimary() {
        bothThemes { dark ->
            val scheme = MaterialTheme.colorScheme
            assertEquals(PsmfBrand.Yellow, scheme.primaryContainer, dark.label())
            assertEquals(PsmfBrand.Yellow, scheme.secondaryContainer, dark.label())
            assertEquals(PsmfBrand.Ink, scheme.onPrimaryContainer, dark.label())
            assertEquals(PsmfBrand.Ink, scheme.onSecondaryContainer, dark.label())

            // `primary` is what a TextButton draws its label with.
            assertTrue(
                contrast(scheme.primary, scheme.surface) >= BODY_TEXT_MINIMUM,
                "a text button would be unreadable on the surface, ${dark.label()}",
            )
        }
    }

    @Test
    fun everyContentColourIsReadableOnTheSurfaceItSitsOn() {
        bothThemes { dark ->
            val scheme = MaterialTheme.colorScheme
            val pairs =
                listOf(
                    "onSurface" to (scheme.onSurface to scheme.surface),
                    "onBackground" to (scheme.onBackground to scheme.background),
                    "onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surface),
                    "onPrimary" to (scheme.onPrimary to scheme.primary),
                    "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "onSecondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                    "onErrorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                    // Warning text. The app warns and never clears, so a
                    // warning nobody can read is worse than none.
                    "error" to (scheme.error to scheme.surface),
                )
            pairs.forEach { (name, colours) ->
                val (content, background) = colours
                assertTrue(
                    contrast(content, background) >= BODY_TEXT_MINIMUM,
                    "$name is ${contrast(content, background)}:1 ${dark.label()}",
                )
            }
        }
    }

    @Test
    fun theDarkThemeKeepsTheBrandAndSwapsTheNeutrals() {
        var light: Color? = null
        var dark: Color? = null
        bothThemes { isDark ->
            if (isDark) dark = MaterialTheme.colorScheme.surface else light = MaterialTheme.colorScheme.surface
        }
        assertEquals(PsmfBrand.Surface, light)
        assertTrue(contrast(dark!!, PsmfBrand.Surface) > BODY_TEXT_MINIMUM, "the dark surface is not dark")
    }

    /** Runs [block] once inside the light theme and once inside the dark. */
    private fun bothThemes(block: @Composable (dark: Boolean) -> Unit) =
        listOf(false, true).forEach { dark ->
            runComposeUiTest {
                setContent { PsmfTheme(darkTheme = dark) { block(dark) } }
            }
        }

    private fun Boolean.label(): String = if (this) "(dark)" else "(light)"

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val first = a.relativeLuminance()
        val second = b.relativeLuminance()
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    /** WCAG 2.1, and worth having spelled out rather than eyeballed. */
    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private companion object {
        /** WCAG AA for body text. */
        const val BODY_TEXT_MINIMUM = 4.5
    }
}
