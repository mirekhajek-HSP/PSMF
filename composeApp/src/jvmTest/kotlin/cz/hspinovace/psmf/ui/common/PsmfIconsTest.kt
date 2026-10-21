package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The seven icons the app draws itself (0.3.1, Gate 6). They came from `materialIconsExtended`,
 * which is eleven thousand icon classes and 31 MB of an APK for these seven.
 *
 * What is pinned: each icon is the Material shape, read from its pixels against what the
 * library's own copy measured when it was still a dependency; Less and More are each other
 * upside down; the star a team is followed by is solid and the one it is not is the same star
 * as an outline; and the extended set is not on the classpath, so nothing can quietly use it
 * again.
 */
@OptIn(ExperimentalTestApi::class)
class PsmfIconsTest {
    /** How much ink an icon puts on a white 96 px square, and where the middle of that ink is. */
    private class Golden(
        val area: Double,
        val centreX: Double,
        val centreY: Double,
    )

    private val icons: Map<String, ImageVector> =
        linkedMapOf(
            "Check" to PsmfIcons.Check,
            "ExpandLess" to PsmfIcons.ExpandLess,
            "ExpandMore" to PsmfIcons.ExpandMore,
            "KeyboardArrowUp" to PsmfIcons.KeyboardArrowUp,
            "SportsSoccer" to PsmfIcons.SportsSoccer,
            "Star" to PsmfIcons.Star,
            "StarBorder" to PsmfIcons.StarBorder,
        )

    // Measured from `Icons.Filled.<name>` of material-icons-extended 1.7.3 in this same test
    // harness, black on white at 96 px (4 px to a grid unit), on 2026-10-21 -- the day the
    // dependency left. Ink is 1 - red, so an edge pixel counts for the part of it that is covered.
    private val library =
        mapOf(
            "Check" to Golden(734.02, 48.84, 51.73),
            "ExpandLess" to Golden(480.31, 48.00, 48.09),
            "ExpandMore" to Golden(480.36, 48.00, 47.91),
            "KeyboardArrowUp" to Golden(480.31, 48.00, 48.09),
            "SportsSoccer" to Golden(3192.99, 47.92, 47.98),
            "Star" to Golden(2360.84, 48.00, 49.95),
            "StarBorder" to Golden(1487.46, 48.00, 49.95),
        )

    private class Ink(
        private val pixels: PixelMap,
    ) {
        val size = pixels.width

        fun at(
            x: Int,
            y: Int,
        ): Double = 1.0 - pixels[x, y].red

        private fun sum(weight: (Int, Int) -> Double): Double {
            var total = 0.0
            for (y in 0 until size) {
                for (x in 0 until size) total += at(x, y) * weight(x, y)
            }
            return total
        }

        val area: Double get() = sum { _, _ -> 1.0 }
        val centreX: Double get() = sum { x, _ -> x + 0.5 } / area
        val centreY: Double get() = sum { _, y -> y + 0.5 } / area
    }

    private fun ComposeUiTest.showAll() {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Row {
                    icons.forEach { (name, icon) ->
                        Box(Modifier.size(SIDE.dp).background(Color.White).testTag(name)) {
                            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(SIDE.dp))
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.ink(name: String) = Ink(onNodeWithTag(name).captureToImage().toPixelMap())

    @Test
    fun eachIconIsOneBlackPathOnTheGridOfAMaterialIcon() {
        icons.forEach { (name, icon) ->
            assertEquals(24.dp, icon.defaultWidth, "$name width")
            assertEquals(24.dp, icon.defaultHeight, "$name height")
            assertEquals(24f, icon.viewportWidth, "$name viewport width")
            assertEquals(24f, icon.viewportHeight, "$name viewport height")
            assertEquals(1, icon.root.size, "$name should be a single path")
            assertTrue(icon.root[0] is VectorPath, "$name should be a path")
        }
    }

    @Test
    fun eachIconInksTheAreaAndTheMiddleThatTheLibrarysCopyDid() =
        runComposeUiTest {
            showAll()

            library.forEach { (name, want) ->
                val got = ink(name)
                assertTrue(
                    abs(got.area - want.area) <= want.area * AREA_TOLERANCE,
                    "$name inks ${got.area}; the library's copy inked ${want.area}",
                )
                assertTrue(
                    abs(got.centreX - want.centreX) <= CENTRE_TOLERANCE &&
                        abs(got.centreY - want.centreY) <= CENTRE_TOLERANCE,
                    "$name is centred on (${got.centreX}, ${got.centreY}); the library's copy on " +
                        "(${want.centreX}, ${want.centreY})",
                )
            }
        }

    @Test
    fun lessAndMoreAreEachOtherUpsideDown() =
        runComposeUiTest {
            showAll()
            val less = ink("ExpandLess")
            val more = ink("ExpandMore")

            var differing = 0.0
            for (y in 0 until less.size) {
                for (x in 0 until less.size) differing += abs(less.at(x, y) - more.at(x, less.size - 1 - y))
            }

            assertTrue(less.area > 0.0, "Less draws nothing")
            assertTrue(differing <= less.area * MIRROR_TOLERANCE, "Less and More differ by $differing of ${less.area}")
        }

    @Test
    fun theFollowedStarIsSolidAndTheOtherIsTheSameStarAsAnOutline() =
        runComposeUiTest {
            showAll()
            val star = ink("Star")
            val outline = ink("StarBorder")
            val middle = star.size / 2

            assertTrue(star.at(middle, middle) > SOLID, "the followed star is not solid in the middle")
            assertTrue(outline.at(middle, middle) < HOLLOW, "the star that is not followed is not hollow in the middle")

            var outside = 0.0
            for (y in 0 until star.size) {
                for (x in 0 until star.size) outside = maxOf(outside, outline.at(x, y) - star.at(x, y))
            }
            assertTrue(outside <= EDGE_NOISE, "the outline inks $outside beyond the solid star somewhere")
        }

    @Test
    fun theExtendedIconSetIsNotOnTheClasspath() {
        // SportsSoccer is in the extended set only. Anything that adds the dependency again
        // puts eleven thousand classes back in the APK, whether or not a file imports them.
        assertFailsWith<ClassNotFoundException>("materialIconsExtended is a dependency again") {
            Class.forName("androidx.compose.material.icons.filled.SportsSoccerKt", false, javaClass.classLoader)
        }
    }

    private companion object {
        const val SIDE = 96
        const val AREA_TOLERANCE = 0.01
        const val CENTRE_TOLERANCE = 0.1
        const val MIRROR_TOLERANCE = 0.02
        const val SOLID = 0.9
        const val HOLLOW = 0.1
        const val EDGE_NOISE = 0.25
    }
}
