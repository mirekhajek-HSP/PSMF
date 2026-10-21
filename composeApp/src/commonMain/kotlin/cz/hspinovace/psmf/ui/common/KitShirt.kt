package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.ui.theme.PsmfDimens

private val ShirtWidth = 32.dp
private val ShirtHeight = 29.dp
private val OutlineWidth = 1.5.dp

// The shirt, on a grid 24 wide and 22 tall: the collar, the shoulders, a short sleeve to each
// side, and a body from x 5 to x 19. A sleeve belongs to the band beside it.
private const val GRID_WIDTH = 24f
private const val GRID_HEIGHT = 22f
private const val BODY_LEFT = 5f
private const val BODY_RIGHT = 19f
private const val NECK_LEFT = 8f
private const val NECK_MIDDLE = 12f
private const val NECK_RIGHT = 16f
private const val COLLAR_DIP = 5f
private const val SHOULDER_X = 21f
private const val SHOULDER_Y = 2.5f
private const val SLEEVE_END_Y = 9f
private const val ARMPIT_Y = 11f

/**
 * A small shirt in the colours of [kit], drawn here and not borrowed (as the card glyph is:
 * `DECISIONS 2026-09-01`), for the console and the team tabs (0.3.1, Gate 5).
 *
 * **One band for each colour the kit lists**, the first on the left, cutting the body into equal
 * slices (the sleeves go with the band beside them), so that two colours show as two and three
 * as three. A colour the app has no name for ([KitColours]) is a band left empty, never a
 * stand-in; a kit with no known colour at all draws **nothing and takes no room**, so a screen
 * looks as it did before for a team whose colours are not known.
 *
 * **An outline, always**, in the theme's outline colour: a white shirt on a light screen and a
 * black one on a dark screen would otherwise not be there.
 *
 * It is described by the kit's label (what PSMF calls the kit), for a screen reader. Nothing
 * here is the report's `Barva dresů`, and nothing in the report reads it.
 */
@Composable
fun KitShirt(
    kit: Kit?,
    modifier: Modifier = Modifier,
) {
    val bands = shirtBands(kit) ?: return
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier =
            modifier
                .size(ShirtWidth, ShirtHeight)
                .semantics {
                    contentDescription = checkNotNull(kit).label
                    role = Role.Image
                },
    ) {
        val stroke = OutlineWidth.toPx()
        val shirt = shirtPath(size, inset = stroke / 2)
        val bodyLeft = size.width * BODY_LEFT / GRID_WIDTH
        val bodyWidth = size.width * (BODY_RIGHT - BODY_LEFT) / GRID_WIDTH

        // Where band `index` starts: the first from the edge, the others at equal steps across the body.
        fun startOf(index: Int): Float =
            when (index) {
                0 -> 0f
                bands.size -> size.width
                else -> bodyLeft + bodyWidth * index / bands.size
            }

        clipPath(shirt) {
            bands.forEachIndexed { index, colour ->
                if (colour != null) {
                    drawRect(
                        color = colour,
                        topLeft = Offset(startOf(index), 0f),
                        size = Size(startOf(index + 1) - startOf(index), size.height),
                    )
                }
            }
            for (index in 1 until bands.size) {
                val x = startOf(index)
                drawLine(outline, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke / 2)
            }
        }
        drawPath(shirt, color = outline, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

/** The silhouette, scaled from its grid to [size] and kept [inset] inside it, so that its outline is not cut. */
private fun shirtPath(
    size: Size,
    inset: Float,
): Path {
    val across = (size.width - 2 * inset) / GRID_WIDTH
    val down = (size.height - 2 * inset) / GRID_HEIGHT

    fun Path.line(
        x: Float,
        y: Float,
    ) = lineTo(inset + x * across, inset + y * down)

    return Path().apply {
        moveTo(inset + NECK_LEFT * across, inset)
        quadraticTo(inset + NECK_MIDDLE * across, inset + COLLAR_DIP * down, inset + NECK_RIGHT * across, inset)
        line(SHOULDER_X, SHOULDER_Y)
        line(GRID_WIDTH, SLEEVE_END_Y)
        line(BODY_RIGHT, ARMPIT_Y)
        line(BODY_RIGHT, GRID_HEIGHT)
        line(BODY_LEFT, GRID_HEIGHT)
        line(BODY_LEFT, ARMPIT_Y)
        line(0f, SLEEVE_END_Y)
        line(GRID_WIDTH - SHOULDER_X, SHOULDER_Y)
        close()
    }
}

/**
 * A team's name for a tab, with the shirt of the kit it wears in this match before it (0.3.1,
 * Gate 5). With no shirt to show it is the name alone, as it was.
 */
@Composable
fun TeamTabLabel(
    name: String,
    kit: Kit?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap * 2),
    ) {
        KitShirt(kit)
        Text(name, maxLines = 2, textAlign = TextAlign.Center)
    }
}
