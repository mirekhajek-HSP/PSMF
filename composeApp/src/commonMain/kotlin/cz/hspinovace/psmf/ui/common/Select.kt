@file:OptIn(ExperimentalMaterial3Api::class)

package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.ui.theme.PsmfDimens

/**
 * A select box: closed, a field that says what is chosen; open, a menu as
 * wide as the field, one full-width row per option, the chosen one ticked.
 *
 * Material's exposed dropdown, so the platform's own menu behaviour --
 * scrolling a long list, dismissing on a tap outside, reading as a list to
 * a screen reader -- comes with it.
 *
 * **Not a place to type, and so not a [PsmfTextField].** The field is
 * read-only and only ever opens the menu; it raises no keyboard. It began in
 * the fixture filter and moved here when the lineup needed one for its
 * captain and goalkeeper.
 */
@Composable
fun <T> Select(
    label: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == selected }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = current?.closedText ?: selected.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = PsmfDimens.minTouchTarget),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Spelled out: the theme sets no surfaceContainer, so Material's
            // default would draw the menu in its own lavender.
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            choices.forEach { choice ->
                val isSelected = choice == current
                DropdownMenuItem(
                    text = { ChoiceText(choice) },
                    onClick = {
                        expanded = false
                        onSelect(choice.value)
                    },
                    leadingIcon = { Tick(visible = isSelected) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier =
                        Modifier
                            .heightIn(min = PsmfDimens.minTouchTarget)
                            .then(
                                if (isSelected) {
                                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                } else {
                                    Modifier
                                },
                            ).semantics { this.selected = isSelected },
                )
            }
        }
    }
}

@Composable
private fun ChoiceText(choice: Choice<*>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = choice.text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (choice.detail != null) FontWeight.SemiBold else null,
        )
        choice.detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The chosen option's tick, or the same width of nothing so the rows line up. */
@Composable
private fun Tick(visible: Boolean) {
    if (visible) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(TICK_SIZE))
    } else {
        Spacer(Modifier.size(TICK_SIZE))
    }
}

private val TICK_SIZE = 24.dp
