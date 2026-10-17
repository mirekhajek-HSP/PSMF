package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.lineup_add_confirm
import cz.hspinovace.psmf.resources.lineup_add_player_note
import cz.hspinovace.psmf.resources.lineup_add_player_title
import cz.hspinovace.psmf.resources.lineup_date_of_birth
import cz.hspinovace.psmf.resources.lineup_date_of_birth_hint
import cz.hspinovace.psmf.resources.lineup_error_new_player
import cz.hspinovace.psmf.resources.lineup_first_name
import cz.hspinovace.psmf.resources.lineup_no_card
import cz.hspinovace.psmf.resources.lineup_rp_column
import cz.hspinovace.psmf.resources.lineup_surname
import cz.hspinovace.psmf.ui.common.KeyboardDismissal
import cz.hspinovace.psmf.ui.common.PsmfTextField
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import org.jetbrains.compose.resources.stringResource

/**
 * A player who is present but not in the squad list.
 *
 * **Surname, first name, and then what the form's `Číslo RP` column takes:**
 * the number copied from the player's card, or -- with the switch on, for a
 * player with no card -- the date of birth, which is the form's own printed
 * rule (DECISIONS 2026-10-17). No rodné číslo, and no field that could become
 * an RP number as PSMF issues them: the request has no such property, the use
 * case no such parameter, and `Player.addedAtThePitch` none either. What is
 * typed here is what the appearance writes that day.
 */
@Composable
fun AddPlayerDialog(
    request: NewPlayerRequest,
    rejected: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(LineupEvent.AddPlayerDismissed) },
        title = { Text(stringResource(Res.string.lineup_add_player_title)) },
        text = {
            // A dialog is a window of its own: the screen's keyboard rules do
            // not reach into it.
            KeyboardDismissal {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
                ) {
                    Text(
                        text = stringResource(Res.string.lineup_add_player_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Surname first: it is how the ZoU column is written, and how
                    // the captain will read the name out.
                    DialogField(
                        label = stringResource(Res.string.lineup_surname),
                        value = request.surname,
                        onValueChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(surname = it))) },
                        capitalise = true,
                    )
                    DialogField(
                        label = stringResource(Res.string.lineup_first_name),
                        value = request.firstName,
                        onValueChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(firstName = it))) },
                        capitalise = true,
                    )

                    NoCardSwitch(
                        noCard = request.noCard,
                        onChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(noCard = it))) },
                    )

                    if (request.noCard) {
                        DialogField(
                            label = stringResource(Res.string.lineup_date_of_birth),
                            value = request.dateOfBirth,
                            onValueChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(dateOfBirth = it))) },
                            supporting = stringResource(Res.string.lineup_date_of_birth_hint),
                            numeric = true,
                            imeAction = ImeAction.Done,
                        )
                        // Echoed back so a mistyped date is visible before it
                        // becomes six digits in the Číslo RP column.
                        request.parsedDateOfBirth?.let {
                            Text(it.asFullDate(), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        DialogField(
                            label = stringResource(Res.string.lineup_rp_column),
                            value = request.cardNumber,
                            onValueChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(cardNumber = it))) },
                            numeric = true,
                            imeAction = ImeAction.Done,
                        )
                    }

                    if (rejected) {
                        Text(
                            text = stringResource(Res.string.lineup_error_new_player),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(LineupEvent.NewPlayerSubmitted) }) {
                Text(stringResource(Res.string.lineup_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(LineupEvent.AddPlayerDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NoCardSwitch(
    noCard: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = noCard, onCheckedChange = onChange)
        Text(
            text = stringResource(Res.string.lineup_no_card),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    capitalise: Boolean = false,
    numeric: Boolean = false,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    PsmfTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        capitalization = if (capitalise) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        imeAction = imeAction,
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    )
}
