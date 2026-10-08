package cz.hspinovace.psmf.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * [content], with a spinner in the middle over it while [waiting].
 *
 * The owner's design for a wait worth showing (DECISIONS 2026-10-08): the
 * content stays where it is, **blurred where the platform can** -- iOS,
 * Android 12 and up -- **and dimmed where it cannot**, Android 9 to 11.
 * `Modifier.blur` needs a `RenderEffect`, which Android only has from 12;
 * below that it is silently ignored, which would leave a spinner over a
 * sharp, live-looking list. `RenderEffect.isSupported` is Compose's own
 * answer to which side of that line the device is on, in common code, so
 * no platform has to be told.
 *
 * *Whether* a wait is worth showing is the caller's decision, not this
 * one's: it draws whatever [waiting] says. The veil fades in and out
 * rather than switching, so a wait that only just crosses the caller's
 * threshold does not blink.
 *
 * [description] is what a screen reader says for the spinner.
 */
@Composable
fun WaitVeil(
    waiting: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val blurs = remember { BlurEffect(BLUR_RADIUS_PX, BLUR_RADIUS_PX).isSupported() }
    val amount by animateFloatAsState(
        targetValue = if (waiting) 1f else 0f,
        animationSpec = tween(FADE_MILLIS),
        label = "wait veil",
    )
    val veiled = amount > 0f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (veiled && blurs) Modifier.blur(BLUR_RADIUS * amount) else Modifier),
        ) {
            content()
        }
        if (veiled && !blurs) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = DIM_ALPHA * amount)),
            )
        }
        if (veiled) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .alpha(amount)
                        .semantics { contentDescription = description },
            )
        }
    }
}

private val BLUR_RADIUS = 8.dp

/** Only asked whether the device can blur at all; the radius is irrelevant to the answer. */
private const val BLUR_RADIUS_PX = 8f

/** Enough to say "not now" over a list; not so much that it reads as a dialog. */
private const val DIM_ALPHA = 0.32f

private const val FADE_MILLIS = 150
