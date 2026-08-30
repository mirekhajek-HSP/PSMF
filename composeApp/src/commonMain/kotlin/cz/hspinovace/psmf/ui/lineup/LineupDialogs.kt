package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import cz.hspinovace.psmf.resources.lineup_captain_deputy
import cz.hspinovace.psmf.resources.lineup_captain_pick
import cz.hspinovace.psmf.resources.lineup_date_of_birth
import cz.hspinovace.psmf.resources.lineup_date_of_birth_hint
import cz.hspinovace.psmf.resources.lineup_error_new_player
import cz.hspinovace.psmf.resources.lineup_first_name
import cz.hspinovace.psmf.resources.lineup_surname
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import org.jetbrains.compose.resources.stringResource

/**
 * A player who is present but not in the squad list.
 *
 * **Three fields: surname, first name, date of birth. There is no RP
 * field, and there is nowhere for one to go** — the request type has no
 * such property, the use case has no such parameter, and
 * `Player.addedAtThePitch` has none either. That is the rule made
 * structural rather than merely obeyed.
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
                DialogField(
                    label = stringResource(Res.string.lineup_date_of_birth),
                    value = request.dateOfBirth,
                    onValueChange = { onEvent(LineupEvent.NewPlayerEdited(request.copy(dateOfBirth = it))) },
                    supporting = stringResource(Res.string.lineup_date_of_birth_hint),
                    numeric = true,
                )

                // Echoed back so a mistyped date is visible before it becomes
                // six digits in the Číslo RP column.
                request.parsedDateOfBirth?.let {
                    Text(it.asFullDate(), style = MaterialTheme.typography.bodyLarge)
                }

                if (rejected) {
                    Text(
                        text = stringResource(Res.string.lineup_error_new_player),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    capitalise: Boolean = false,
    numeric: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = if (capitalise) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    )
}

/**
 * `Podpis kapitána` — a tap, not a signature (DEMO_SCOPE, A7).
 *
 * The tap replaces the *signature*, not the name: on paper the captain
 * writes both. So the referee picks who is confirming from the players who
 * are actually there, which is one extra tap and gives PSMF a real name.
 * Captaincy can be delegated — the worked example has a deputy signing as
 * `Lepiš (zást.)` — so the deputy flag is on the same screen.
 */
@Composable
fun CaptainConfirmationDialog(
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    var asDeputy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onEvent(LineupEvent.CaptainConfirmationDismissed) },
        title = { Text(stringResource(Res.string.lineup_captain_pick)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                    horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(checked = asDeputy, onCheckedChange = { asDeputy = it })
                    Text(
                        text = stringResource(Res.string.lineup_captain_deputy),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                // Only players who are present: someone who did not turn up
                // cannot have confirmed anything at the pitch.
                team.present.forEach { member ->
                    Text(
                        text = member.player.name.asWrittenOnReport,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = PsmfDimens.minTouchTarget)
                                .clickable {
                                    onEvent(
                                        LineupEvent.CaptainConfirmed(
                                            side = team.side,
                                            playerId = member.player.id,
                                            name = member.player.name.asWrittenOnReport,
                                            asDeputy = asDeputy,
                                        ),
                                    )
                                }.padding(vertical = PsmfDimens.itemSpacing),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(LineupEvent.CaptainConfirmationDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
