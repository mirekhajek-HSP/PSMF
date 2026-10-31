package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.card_colour_note
import cz.hspinovace.psmf.resources.card_colour_red
import cz.hspinovace.psmf.resources.card_colour_yellow
import cz.hspinovace.psmf.resources.card_correct_title
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
import cz.hspinovace.psmf.resources.card_player
import cz.hspinovace.psmf.resources.card_reason
import cz.hspinovace.psmf.resources.card_reason_note
import cz.hspinovace.psmf.resources.card_remove
import cz.hspinovace.psmf.resources.card_remove_body
import cz.hspinovace.psmf.resources.card_remove_body_red
import cz.hspinovace.psmf.resources.card_remove_body_second_yellow
import cz.hspinovace.psmf.resources.card_remove_keep
import cz.hspinovace.psmf.resources.card_remove_refused
import cz.hspinovace.psmf.resources.card_remove_title
import cz.hspinovace.psmf.resources.card_save
import cz.hspinovace.psmf.resources.card_save_sends_off
import cz.hspinovace.psmf.resources.card_second_yellow_hint
import cz.hspinovace.psmf.resources.card_title
import cz.hspinovace.psmf.resources.goal_remove_confirm
import cz.hspinovace.psmf.resources.lineup_number_short
import cz.hspinovace.psmf.resources.signatures_withdraw_body
import cz.hspinovace.psmf.ui.common.Choice
import cz.hspinovace.psmf.ui.common.KeyboardDismissal
import cz.hspinovace.psmf.ui.common.PsmfTextField
import cz.hspinovace.psmf.ui.common.Select
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
    val correcting = draft.corrects != null
    val scroll = rememberScrollState()
    // The refusal is written under Odebrat kartu, the last thing in the sheet, which on a
    // small phone is below its fold: bring it into view, or the tap seems to do nothing.
    LaunchedEffect(state.cardRemovalRefused) {
        if (state.cardRemovalRefused) {
            withFrameNanos { }
            scroll.animateScrollTo(scroll.maxValue)
        }
    }
    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.CardDismissed) },
        title = { Text(stringResource(if (correcting) Res.string.card_correct_title else Res.string.card_title)) },
        text = {
            // A dialog is a window of its own: the screen's keyboard rules do
            // not reach into it.
            KeyboardDismissal {
                Column(
                    modifier = Modifier.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
                ) {
                    CardSubjectRow(draft, state, onEvent)

                    if (correcting) {
                        CardKind(draft)
                    } else {
                        ColourChips(draft, onEvent)
                    }

                    if (draft.isRed && !correcting) {
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
                        // The last field of the sheet.
                        imeAction = ImeAction.Done,
                    )
                    Text(
                        text = stringResource(Res.string.card_reason_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (correcting) RemoveCardRow(state, onEvent)
                }
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

/**
 * Who the card is for. A new card is for the row it was opened from, so nothing is asked
 * unless it was opened for someone not in the lineup; a card in the log offers its player.
 */
@Composable
private fun CardSubjectRow(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    if (draft.corrects != null && draft.appearance != null) {
        PlayerChoice(draft, state, onEvent)
    } else if (draft.appearance == null) {
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
}

/**
 * Who a card in the log is for (0.4.0): any player of the team with no card in this
 * match, or, for a second yellow and a first yellow one stands on, only its own player,
 * shown and not offered.
 */
@Composable
private fun PlayerChoice(
    draft: CardDraft,
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val label = stringResource(Res.string.card_player)
    val shirt = Res.string.lineup_number_short
    val rows = state.cardPlayerChoices
    if (rows.size > 1) {
        val choices =
            rows.map { row ->
                Choice<AppearanceId?>(
                    value = row.appearanceId,
                    text = row.name.asWrittenOnReport,
                    detail = row.jerseyNumber?.value?.let { stringResource(shirt, it) },
                )
            }
        Select(
            label = label,
            choices = choices,
            selected = draft.appearance,
            onSelect = { onEvent(ConsoleEvent.CardEdited(draft.copy(appearance = it))) },
        )
    } else {
        rows.singleOrNull()?.let { row ->
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(row.name.asWrittenOnReport, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The colour of a card in the log, and a red's kind, said and not offered: a card of the
 * wrong colour is removed and recorded again, which the line under it says (0.4.0).
 */
@Composable
private fun CardKind(draft: CardDraft) {
    val colour = stringResource(if (draft.isRed) Res.string.card_colour_red else Res.string.card_colour_yellow)
    val kind =
        when (draft.dismissal) {
            Dismissal.SECOND_YELLOW -> stringResource(Res.string.card_dismissal_second)
            Dismissal.STRAIGHT -> stringResource(Res.string.card_dismissal_straight)
            null -> null
        }
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(
            text = listOfNotNull(colour, kind.takeIf { draft.isRed }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(Res.string.card_colour_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Removing the card, under a sheet that corrects one. A first yellow its `2. ŽK` stands
 * on cannot go yet: the tap says so, in words, instead of asking.
 */
@Composable
private fun RemoveCardRow(
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        TextButton(
            onClick = { onEvent(ConsoleEvent.CardRemovePressed) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(Res.string.card_remove))
        }
        if (state.cardRemovalRefused) Problem(stringResource(Res.string.card_remove_refused))
    }
}

/**
 * Asked before a card is removed from the log (0.4.0), as a goal's removal is
 * ([RemoveGoalDialog]): it names the card as the log line has it, says that a second
 * yellow goes whole and that a red takes its sending-off with it, and, on a signed
 * report, that the signatures go. A No goes back to the sheet.
 */
@Composable
fun RemoveCardDialog(
    card: CardEvent,
    line: String,
    signed: Boolean,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val red = card as? RedCard
    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.CardRemoveDismissed) },
        title = { Text(stringResource(Res.string.card_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
                Text(stringResource(Res.string.card_remove_body, line))
                if (red?.dismissal == Dismissal.SECOND_YELLOW) {
                    Text(stringResource(Res.string.card_remove_body_second_yellow))
                }
                if (red?.subject is CardSubject.Player) Text(stringResource(Res.string.card_remove_body_red))
                if (signed) Text(stringResource(Res.string.signatures_withdraw_body), fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.CardRemoveConfirmed) }) {
                Text(stringResource(Res.string.goal_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.CardRemoveDismissed) }) {
                Text(stringResource(Res.string.card_remove_keep))
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
internal fun Chip(
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
internal fun Problem(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    numeric: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        PsmfTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        )
        if (error != null) Problem(error)
    }
}
