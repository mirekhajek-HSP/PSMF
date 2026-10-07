package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.card_colour_red
import cz.hspinovace.psmf.resources.card_colour_yellow
import cz.hspinovace.psmf.resources.card_dismissal_second
import cz.hspinovace.psmf.resources.card_dismissal_straight
import cz.hspinovace.psmf.resources.card_error_dismissal
import cz.hspinovace.psmf.resources.card_error_minute
import cz.hspinovace.psmf.resources.card_error_reason
import cz.hspinovace.psmf.resources.card_error_subject
import cz.hspinovace.psmf.resources.card_minute
import cz.hspinovace.psmf.resources.card_minute_end
import cz.hspinovace.psmf.resources.card_minute_half
import cz.hspinovace.psmf.resources.card_minute_played
import cz.hspinovace.psmf.resources.card_person
import cz.hspinovace.psmf.resources.card_reason
import cz.hspinovace.psmf.resources.card_reason_note
import cz.hspinovace.psmf.resources.card_save
import cz.hspinovace.psmf.resources.card_save_sends_off
import cz.hspinovace.psmf.resources.card_second_yellow_hint
import cz.hspinovace.psmf.resources.card_title
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.CardColour
import cz.hspinovace.psmf.usecase.CardDraft
import cz.hspinovace.psmf.usecase.CardProblem
import cz.hspinovace.psmf.usecase.MinuteMark
import org.jetbrains.compose.resources.stringResource

/**
 * `Osobní tresty` — one row of the block.
 *
 * The form requires *time, number, name and reason* on every card, and a
 * red must say whether it was straight or a second yellow. Both are
 * enforced here rather than left to the referee to remember, because the
 * fine for an incomplete report lands on the delegating team.
 *
 * **A yellow for a booked player sends them off**, and the sheet says so in
 * two places before anything is saved: a statement of what saving records,
 * and the save button itself, which stops reading "Uložit" and reads
 * "Vyloučit (2. ŽK)". The button is where the thumb already is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardSheet(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.CardDismissed) },
        title = { Text(stringResource(Res.string.card_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
            ) {
                if (draft.appearance == null) {
                    // Someone with no jersey number: the worked example
                    // cards a deputy captain by name alone.
                    Field(
                        label = stringResource(Res.string.card_person),
                        value = draft.namedPerson,
                        onValueChange = { onEvent(ConsoleEvent.CardEdited(draft.copy(namedPerson = it))) },
                        error =
                            stringResource(Res.string.card_error_subject).takeIf {
                                state.cardProblem(CardProblem.NO_SUBJECT)
                            },
                    )
                }

                ColourChips(draft, onEvent)

                if (draft.isRed) {
                    DismissalChips(draft, state, onEvent)
                    if (state.cardProblem(CardProblem.NO_DISMISSAL_KIND)) {
                        Problem(stringResource(Res.string.card_error_dismissal))
                    }
                }
                if (state.cardSendsOff) {
                    // Not a block: the referee decides. But they must not
                    // discover afterwards that this was a dismissal.
                    Text(
                        text = stringResource(Res.string.card_second_yellow_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                MinuteRow(draft, state, onEvent)

                Field(
                    label = stringResource(Res.string.card_reason),
                    value = draft.reason,
                    onValueChange = { onEvent(ConsoleEvent.CardEdited(draft.copy(reason = it))) },
                    error =
                        stringResource(Res.string.card_error_reason).takeIf {
                            state.cardProblem(CardProblem.NO_REASON)
                        },
                )
                Text(
                    text = stringResource(Res.string.card_reason_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (state.cardSendsOff) {
                TextButton(
                    onClick = { onEvent(ConsoleEvent.CardSubmitted) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.card_save_sends_off), fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { onEvent(ConsoleEvent.CardSubmitted) }) {
                    Text(stringResource(Res.string.card_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.CardDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourChips(
    draft: CardDraft,
    onEvent: (ConsoleEvent) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Chip(stringResource(Res.string.card_colour_yellow), !draft.isRed) {
            onEvent(ConsoleEvent.CardEdited(draft.copy(colour = CardColour.YELLOW, dismissal = null)))
        }
        Chip(stringResource(Res.string.card_colour_red), draft.isRed) {
            onEvent(ConsoleEvent.CardEdited(draft.copy(colour = CardColour.RED)))
        }
    }
}

/**
 * Straight, or `2. ŽK` -- the second only for a player already booked in
 * this match (or someone not in the lineup, who gets no automatic second
 * yellow). For anyone else a red can only be straight, and says so.
 *
 * Choosing a kind never writes into the reason. The report writes `2. ŽK`
 * from the stored kind, so pre-filling it would only leave words behind
 * when the referee switched back to straight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DismissalChips(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Chip(stringResource(Res.string.card_dismissal_straight), state.cardDismissalKind == Dismissal.STRAIGHT) {
            onEvent(ConsoleEvent.CardEdited(draft.copy(dismissal = Dismissal.STRAIGHT)))
        }
        if (state.cardOffersSecondYellowKind) {
            Chip(stringResource(Res.string.card_dismissal_second), state.cardDismissalKind == Dismissal.SECOND_YELLOW) {
                onEvent(ConsoleEvent.CardEdited(draft.copy(dismissal = Dismissal.SECOND_YELLOW)))
            }
        }
    }
}

/**
 * The minute, including the two the form has that no integer holds:
 * `30´+` for half-time and `60´+` for after the final whistle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinuteRow(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(stringResource(Res.string.card_minute), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            Chip(stringResource(Res.string.card_minute_played), draft.minute.mark == MinuteMark.PLAYED) {
                onEvent(ConsoleEvent.CardEdited(draft.copy(minute = draft.minute.copy(mark = MinuteMark.PLAYED))))
            }
            Chip(stringResource(Res.string.card_minute_half), draft.minute.mark == MinuteMark.HALF_TIME) {
                onEvent(ConsoleEvent.CardEdited(draft.copy(minute = draft.minute.copy(mark = MinuteMark.HALF_TIME))))
            }
            Chip(stringResource(Res.string.card_minute_end), draft.minute.mark == MinuteMark.AFTER_FINAL_WHISTLE) {
                onEvent(
                    ConsoleEvent.CardEdited(
                        draft.copy(minute = draft.minute.copy(mark = MinuteMark.AFTER_FINAL_WHISTLE)),
                    ),
                )
            }
        }
        if (draft.minute.mark == MinuteMark.PLAYED) {
            Field(
                label = stringResource(Res.string.card_minute_played),
                value = draft.minute.played,
                onValueChange = {
                    onEvent(ConsoleEvent.CardEdited(draft.copy(minute = draft.minute.copy(played = it))))
                },
                error =
                    stringResource(Res.string.card_error_minute).takeIf {
                        state.cardProblem(CardProblem.NO_MINUTE)
                    },
                numeric = true,
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
    )
}

@Composable
private fun Problem(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    numeric: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            keyboardOptions =
                KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        )
        if (error != null) Problem(error)
    }
}
