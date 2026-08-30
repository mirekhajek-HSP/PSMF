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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardSheet(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val alreadyBooked =
        draft.appearance?.let { state.entry?.row(it)?.yellowsInThisMatch ?: 0 } ?: 0

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
                    DismissalChips(draft, onEvent)
                    if (state.cardProblem(CardProblem.NO_DISMISSAL_KIND)) {
                        Problem(stringResource(Res.string.card_error_dismissal))
                    }
                } else if (alreadyBooked > 0) {
                    // Not a block: the referee decides. But they should not
                    // discover afterwards that this was a dismissal.
                    Text(
                        text = stringResource(Res.string.card_second_yellow_hint),
                        style = MaterialTheme.typography.bodyMedium,
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
            TextButton(onClick = { onEvent(ConsoleEvent.CardSubmitted) }) {
                Text(stringResource(Res.string.card_save))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DismissalChips(
    draft: CardDraft,
    onEvent: (ConsoleEvent) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Chip(stringResource(Res.string.card_dismissal_straight), draft.dismissal == Dismissal.STRAIGHT) {
            onEvent(ConsoleEvent.CardEdited(draft.copy(dismissal = Dismissal.STRAIGHT)))
        }
        // `2. ŽK` is the literal string the form uses, so it is also what
        // goes in the reason unless the referee writes something else.
        val secondYellow = stringResource(Res.string.card_dismissal_second)
        Chip(secondYellow, draft.dismissal == Dismissal.SECOND_YELLOW) {
            onEvent(
                ConsoleEvent.CardEdited(
                    draft.copy(
                        dismissal = Dismissal.SECOND_YELLOW,
                        reason = draft.reason.ifBlank { secondYellow },
                    ),
                ),
            )
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
