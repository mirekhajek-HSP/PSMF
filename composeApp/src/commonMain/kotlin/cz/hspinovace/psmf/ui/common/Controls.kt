package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
 */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (mirrored) {
                Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.Start)
            } else {
                Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.End)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

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
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PsmfDimens.primaryActionHeight),
        shape = RoundedCornerShape(PsmfDimens.cornerRadius),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

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
