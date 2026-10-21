package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.ui.theme.PsmfBrand
import cz.hspinovace.psmf.ui.theme.PsmfDimens

/**
 * The row of primary actions at the foot of a screen.
 *
 * **Its own composable, with [mirrored] as a parameter, on purpose.**
 * Left-handed mode is deferred rather than cancelled (DEMO_SCOPE
 * settings), and the difference between adding it later and retrofitting
 * it later is whether every screen already routes its buttons through
 * here. It costs nothing now; it is surgery once six screens have laid out
 * their own buttons.
 *
 * **It wraps rather than squeezes.** A row of three does not fit on a narrow
 * phone, and on one line the last button was left a strip a letter wide --
 * once the end-of-match button, on the screen where it matters. The buttons
 * keep their own width and the one that does not fit goes to the line below.
 *
 * **For one button, or for buttons that need no particular order.** Buttons
 * that sit together and should look like a set -- the console's whistles --
 * are [EvenActionRow]: wrapping gives a ragged edge, and the console showed
 * that was wrong for them (0.3.1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (mirrored) {
                Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.Start)
            } else {
                Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.End)
            },
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * Buttons that sit together as a set: one row as wide as the content, every
 * button the same width and the same height, nothing wrapped onto a second
 * line and no button left a strip (0.3.1, the testers' point 5).
 *
 * One button has the whole row. Two have half each, less the gap. **A label
 * that does not fit on one line wraps inside its button**, at a space, and the
 * buttons beside it grow to the same height: the row is regular whatever the
 * words are, and it is the same in every language and at every font scale
 * without anything having to decide which fits. Each button is handed
 * [content]'s `each` modifier and must use it.
 */
@Composable
fun EvenActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(each: Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        content(Modifier.weight(1f).fillMaxHeight().heightIn(min = PsmfDimens.minTouchTarget))
    }
}

/**
 * The padding inside a button of an [EvenActionRow]: less than Material's 24 dp,
 * because the width is a half of a phone's and every dp it gives the label is a
 * dp less to wrap in.
 */
val EvenActionContentPadding = PaddingValues(horizontal = PsmfDimens.itemSpacing, vertical = PsmfDimens.labelGap * 2)

/**
 * The button that carries a screen forward.
 *
 * Taller than Material's default and never disabled: a greyed-out button
 * with no explanation is the worst thing to meet in the dark. Pressing it
 * with something missing reveals what is missing.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PsmfDimens.primaryActionHeight),
        shape = RoundedCornerShape(PsmfDimens.cornerRadius),
        colors = brandFilledColors(),
        contentPadding = contentPadding,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/**
 * A filled button in PSMF's yellow, with ink on top of it.
 *
 * Not `primary`: Material uses that as a content colour as well as a fill,
 * so a yellow `primary` would take every `TextButton` in the app with it.
 * The brand lives in the container slots -- see [PsmfBrand] -- and this is
 * how a button asks for it.
 */
@Composable
fun brandFilledColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

/** A titled block of the form, matching a block of the paper page. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PsmfDimens.cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(PsmfDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** A read-only `label: value` line, as the printed header block reads. */
@Composable
fun ReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
