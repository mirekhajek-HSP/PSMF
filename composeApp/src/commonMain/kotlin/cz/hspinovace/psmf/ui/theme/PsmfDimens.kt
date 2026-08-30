package cz.hspinovace.psmf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/**
 * Sizes chosen for the person actually holding the phone: a referee
 * outdoors, in the cold, possibly in the rain, one-handed.
 *
 * **Nothing here is a text size.** Type comes from [MaterialTheme]
 * typography in `sp`, so the system font scale applies — the referee
 * population skews older and many will have it turned up. Every container
 * below is therefore a *minimum*, never a fixed height: a row that is
 * exactly 56dp tall clips its own label the moment someone raises their
 * font size.
 */
object PsmfDimens {
    /**
     * Minimum height and width of anything tappable.
     *
     * Material's own floor is 48dp, which assumes a warm dry index finger.
     * 56 is the smallest that stayed comfortable with gloves on.
     */
    val minTouchTarget = 56.dp

    /** Taps that carry a match with them — start, continue, confirm. */
    val primaryActionHeight = 64.dp

    val screenPadding = 16.dp
    val labelGap = 4.dp
    val itemSpacing = 12.dp
    val sectionSpacing = 24.dp
    val cornerRadius = 12.dp
}
