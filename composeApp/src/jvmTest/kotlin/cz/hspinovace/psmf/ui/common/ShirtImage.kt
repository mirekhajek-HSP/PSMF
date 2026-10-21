package cz.hspinovace.psmf.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What a drawn shirt looks like, read back from its pixels, so that a test can say
 * "the left half is white and the right half is blue" and mean what is on the screen.
 *
 * Built from a capture of the shirt's own box. **The corner of the box is outside the
 * silhouette**, so it is the ground the shirt sits on; anything else that differs from it is
 * the shirt. Nothing here knows the silhouette's coordinates: it finds the shirt by what is
 * drawn, so that a redesign of the shape does not change the tests, only a break does.
 */
internal class ShirtImage(
    private val pixels: PixelMap,
) {
    val ground: Color = pixels[0, 0]

    private fun inked(
        x: Int,
        y: Int,
    ) = !pixels[x, y].isNear(ground)

    private val inkedRows = (0 until pixels.height).filter { y -> (0 until pixels.width).any { x -> inked(x, y) } }

    /** True if anything at all is drawn. */
    val isDrawn: Boolean get() = inkedRows.isNotEmpty()

    /**
     * The colour in the middle of each of [count] equal slices of the body, read along a row
     * well below the sleeves: the left slice first.
     */
    fun bands(count: Int): List<Color> {
        // Four fifths of the way down: below the sleeves, above the hem.
        val y = inkedRows.first() + (inkedRows.last() - inkedRows.first()) * 4 / 5
        val xs = (0 until pixels.width).filter { inked(it, y) }
        val left = xs.first()
        val width = xs.last() - left + 1
        return List(count) { index -> pixels[left + width * (2 * index + 1) / (2 * count), y] }
    }

    /** The strongest contrast any pixel of the box has with [ground]: what it takes to see the shirt at all. */
    fun strongestContrast(): Double =
        (0 until pixels.height).maxOf { y -> (0 until pixels.width).maxOf { x -> contrast(pixels[x, y], ground) } }
}

internal fun Color.isNear(
    other: Color,
    tolerance: Float = 0.02f,
): Boolean =
    abs(red - other.red) <= tolerance && abs(green - other.green) <= tolerance && abs(blue - other.blue) <= tolerance

/** WCAG's contrast ratio between two colours, 1 (none) to 21. */
internal fun contrast(
    first: Color,
    second: Color,
): Double {
    val lighter = max(first.luminance(), second.luminance())
    val darker = min(first.luminance(), second.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}
