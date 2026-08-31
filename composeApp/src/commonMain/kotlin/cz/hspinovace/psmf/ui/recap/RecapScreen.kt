package cz.hspinovace.psmf.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.export.ZouText
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.recap_confirm
import cz.hspinovace.psmf.resources.recap_confirm_away_captain
import cz.hspinovace.psmf.resources.recap_confirm_error
import cz.hspinovace.psmf.resources.recap_confirm_home_captain
import cz.hspinovace.psmf.resources.recap_confirm_name
import cz.hspinovace.psmf.resources.recap_confirm_referee
import cz.hspinovace.psmf.resources.recap_confirmed
import cz.hspinovace.psmf.resources.recap_continue
import cz.hspinovace.psmf.resources.recap_deputy
import cz.hspinovace.psmf.resources.recap_document
import cz.hspinovace.psmf.resources.recap_document_note
import cz.hspinovace.psmf.resources.recap_full_time
import cz.hspinovace.psmf.resources.recap_half_time
import cz.hspinovace.psmf.resources.recap_loading
import cz.hspinovace.psmf.resources.recap_no_cards
import cz.hspinovace.psmf.resources.recap_no_cards_confirmed
import cz.hspinovace.psmf.resources.recap_no_cards_note
import cz.hspinovace.psmf.resources.recap_no_cards_title
import cz.hspinovace.psmf.resources.recap_result
import cz.hspinovace.psmf.resources.recap_result_note
import cz.hspinovace.psmf.resources.recap_signatures
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ResultDraft
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 6.
 *
 * **The document, not a summary.** On paper a captain signs one sheet they
 * can see in full; here they confirm a screen, and whatever is not on it
 * is not being checked (analysis section 5.5). So the middle of this
 * screen is the report itself, rendered by the same formatter the export
 * uses — the captains confirm exactly the bytes that leave the phone.
 */
@Composable
fun RecapScreen(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val report = state.report
        if (state.loading || report == null) {
            Text(
                text = stringResource(Res.string.recap_loading),
                modifier = Modifier.fillMaxWidth().padding(PsmfDimens.screenPadding),
            )
            return@Surface
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PsmfDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.sectionSpacing),
        ) {
            ResultSection(state, onEvent)

            Section(
                title = stringResource(Res.string.recap_document),
                note = stringResource(Res.string.recap_document_note),
            ) {
                // Monospaced, because it is the report and not a summary of
                // one: the columns are the form's columns.
                Text(
                    text = ZouText.format(report),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            CardsAffirmation(state, onEvent)
            SignatureSection(state, onEvent)

            ActionRow {
                PrimaryAction(
                    text = stringResource(Res.string.recap_continue),
                    onClick = { onEvent(RecapEvent.ContinuePressed) },
                )
            }
        }
    }

    state.confirming?.let { ConfirmationDialog(state, it, onEvent) }
}

@Composable
private fun ResultSection(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
) {
    val draft = state.result
    Section(
        title = stringResource(Res.string.recap_result),
        note = stringResource(Res.string.recap_result_note),
    ) {
        ScoreRow(
            label = stringResource(Res.string.recap_half_time),
            home = draft.halfTimeHome,
            away = draft.halfTimeAway,
            onHome = { onEvent(RecapEvent.ResultEdited(draft.copy(halfTimeHome = it))) },
            onAway = { onEvent(RecapEvent.ResultEdited(draft.copy(halfTimeAway = it))) },
        )
        ScoreRow(
            label = stringResource(Res.string.recap_full_time),
            home = draft.fullTimeHome,
            away = draft.fullTimeAway,
            onHome = { onEvent(RecapEvent.ResultEdited(draft.copy(fullTimeHome = it))) },
            onAway = { onEvent(RecapEvent.ResultEdited(draft.copy(fullTimeAway = it))) },
        )
    }
}

@Composable
private fun ScoreRow(
    label: String,
    home: String,
    away: String,
    onHome: (String) -> Unit,
    onAway: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreField(home, onHome)
            Text(":", style = MaterialTheme.typography.titleLarge)
            ScoreField(away, onAway)
        }
    }
}

@Composable
private fun ScoreField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(SCORE_FIELD_WIDTH).heightIn(min = PsmfDimens.minTouchTarget),
    )
}

/**
 * `políčka proškrtne` — the referee striking the boxes through.
 *
 * Only offered while nothing has been recorded. An empty block and an
 * affirmed "no cards" look the same on a screen and mean different things
 * to PSMF, which is the whole reason the distinction is modelled.
 */
@Composable
private fun CardsAffirmation(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
) {
    if (state.noCardsAffirmed) {
        Section(title = stringResource(Res.string.recap_no_cards_title)) {
            Text(
                text = stringResource(Res.string.recap_no_cards_confirmed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    if (!state.cardsUnaccountedFor) return

    Section(
        title = stringResource(Res.string.recap_no_cards_title),
        note = stringResource(Res.string.recap_no_cards_note),
    ) {
        OutlinedButton(
            onClick = { onEvent(RecapEvent.NoCardsAffirmed) },
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        ) {
            Text(stringResource(Res.string.recap_no_cards))
        }
    }
}

@Composable
private fun SignatureSection(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
) {
    Section(title = stringResource(Res.string.recap_signatures)) {
        ConfirmingParty.entries.forEach { party ->
            if (party in state.confirmed) {
                Text(
                    text = "${party.label()} — ${stringResource(Res.string.recap_confirmed)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(
                    onClick = { onEvent(RecapEvent.ConfirmationOpened(party)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                ) {
                    Text(party.label())
                }
            }
        }
    }
}

@Composable
private fun ConfirmingParty.label(): String =
    when (this) {
        ConfirmingParty.HOME_CAPTAIN -> stringResource(Res.string.recap_confirm_home_captain)
        ConfirmingParty.AWAY_CAPTAIN -> stringResource(Res.string.recap_confirm_away_captain)
        ConfirmingParty.REFEREE -> stringResource(Res.string.recap_confirm_referee)
    }

@Composable
private fun ConfirmationDialog(
    state: RecapUiState,
    party: ConfirmingParty,
    onEvent: (RecapEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(RecapEvent.ConfirmationDismissed) },
        title = { Text(party.label()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
                OutlinedTextField(
                    value = state.confirmingName,
                    onValueChange = { onEvent(RecapEvent.ConfirmingNameChanged(it)) },
                    label = { Text(stringResource(Res.string.recap_confirm_name)) },
                    singleLine = true,
                    isError = state.confirmingRejected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                )
                if (party != ConfirmingParty.REFEREE) {
                    // Captaincy may be delegated: the worked example has a
                    // deputy signing as `Lepiš (zást.)`.
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = state.confirmingAsDeputy,
                            onCheckedChange = { onEvent(RecapEvent.ConfirmingDeputyChanged(it)) },
                        )
                        Text(stringResource(Res.string.recap_deputy), modifier = Modifier.weight(1f))
                    }
                }
                if (state.confirmingRejected) {
                    Text(
                        text = stringResource(Res.string.recap_confirm_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(RecapEvent.ConfirmationSubmitted) }) {
                Text(stringResource(Res.string.recap_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(RecapEvent.ConfirmationDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

private val SCORE_FIELD_WIDTH = 88.dp
