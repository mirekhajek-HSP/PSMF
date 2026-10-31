package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.isPastTheFinalWhistle
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.card_error_minute
import cz.hspinovace.psmf.resources.card_minute
import cz.hspinovace.psmf.resources.card_minute_half
import cz.hspinovace.psmf.resources.card_minute_played
import cz.hspinovace.psmf.resources.goal_after_whistle_note
import cz.hspinovace.psmf.resources.goal_correct_save
import cz.hspinovace.psmf.resources.goal_correct_title
import cz.hspinovace.psmf.resources.goal_error_later
import cz.hspinovace.psmf.resources.goal_error_not_started
import cz.hspinovace.psmf.resources.goal_minute_note
import cz.hspinovace.psmf.resources.goal_other_team_note
import cz.hspinovace.psmf.resources.goal_remove
import cz.hspinovace.psmf.resources.goal_remove_body
import cz.hspinovace.psmf.resources.goal_remove_confirm
import cz.hspinovace.psmf.resources.goal_remove_keep
import cz.hspinovace.psmf.resources.goal_remove_title
import cz.hspinovace.psmf.resources.goal_save
import cz.hspinovace.psmf.resources.goal_scorer
import cz.hspinovace.psmf.resources.goal_scorer_none
import cz.hspinovace.psmf.resources.goal_sheet_title
import cz.hspinovace.psmf.resources.lineup_number_short
import cz.hspinovace.psmf.resources.signatures_withdraw_body
import cz.hspinovace.psmf.ui.common.Choice
import cz.hspinovace.psmf.ui.common.KeyboardDismissal
import cz.hspinovace.psmf.ui.common.Select
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.GoalDraft
import cz.hspinovace.psmf.usecase.GoalProblem
import cz.hspinovace.psmf.usecase.MinuteMark
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * A goal written with a minute of its own: one the referee forgot, during the
 * match or after the final whistle (prompt 13, part 3).
 *
 * The scorer is chosen from the team on screen, or left out -- the form has
 * goals with no scorer -- and the minute is entered the way the card sheet
 * takes it: a number, or `30´+`. There is no `60´+`, because no goal has one.
 *
 * **A minute the clock has not reached is refused, and the sheet says which
 * minute is the latest**: a goal moved to another minute without being asked
 * would be a goal in the wrong place on a report the referee signs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSheet(
    draft: GoalDraft,
    state: ConsoleUiState,
    now: Instant,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val entry = state.entry ?: return
    val team = entry.side(draft.side)
    val correcting = draft.corrects != null
    val noScorer = stringResource(Res.string.goal_scorer_none)
    val shirt = Res.string.lineup_number_short
    // A new goal is scored by someone still on the pitch; a goal already in the log
    // may have been scored by a player sent off since.
    val scorers =
        listOf(Choice<AppearanceId?>(value = null, text = noScorer)) +
            team.rows.filter { correcting || !it.dismissed }.map { row ->
                Choice<AppearanceId?>(
                    value = row.appearanceId,
                    text = row.name.asWrittenOnReport,
                    detail = row.jerseyNumber?.value?.let { stringResource(shirt, it) },
                )
            }
    val latest = entry.latestGoalMinuteAt(now).written

    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.GoalDismissed) },
        title = {
            Column {
                Text(stringResource(if (correcting) Res.string.goal_correct_title else Res.string.goal_sheet_title))
                Text(
                    text = team.teamName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            // A dialog is a window of its own: the screen's keyboard rules do
            // not reach into it.
            KeyboardDismissal {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
                ) {
                    Text(
                        text =
                            if (entry.status.isPastTheFinalWhistle) {
                                stringResource(Res.string.goal_after_whistle_note)
                            } else {
                                stringResource(Res.string.goal_minute_note, latest)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Select(
                        label = stringResource(Res.string.goal_scorer),
                        choices = scorers,
                        selected = draft.scorer,
                        onSelect = { onEvent(ConsoleEvent.GoalEdited(draft.copy(scorer = it))) },
                    )
                    GoalMinuteRow(draft, state, latest, onEvent)
                    if (correcting) RemoveGoalRow(onEvent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.GoalSubmitted) }) {
                Text(stringResource(if (correcting) Res.string.goal_correct_save else Res.string.goal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.GoalDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/** The minute: a number, or `30´+`. No `60´+` is offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalMinuteRow(
    draft: GoalDraft,
    state: ConsoleUiState,
    latest: String,
    onEvent: (ConsoleEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(stringResource(Res.string.card_minute), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            Chip(stringResource(Res.string.card_minute_played), draft.minute.mark == MinuteMark.PLAYED) {
                onEvent(ConsoleEvent.GoalEdited(draft.copy(minute = draft.minute.copy(mark = MinuteMark.PLAYED))))
            }
            Chip(stringResource(Res.string.card_minute_half), draft.minute.mark == MinuteMark.HALF_TIME) {
                onEvent(ConsoleEvent.GoalEdited(draft.copy(minute = draft.minute.copy(mark = MinuteMark.HALF_TIME))))
            }
        }
        if (draft.minute.mark == MinuteMark.PLAYED) {
            Field(
                label = stringResource(Res.string.card_minute_played),
                value = draft.minute.played,
                onValueChange = {
                    onEvent(ConsoleEvent.GoalEdited(draft.copy(minute = draft.minute.copy(played = it))))
                },
                error = state.goalError(latest),
                numeric = true,
                // The only text field of the sheet, and the last.
                imeAction = ImeAction.Done,
            )
        } else {
            state.goalError(latest)?.let { Problem(it) }
        }
    }
}

/**
 * Removing the goal, under a sheet that corrects one. Its team is not changed here:
 * a goal given to the wrong team is removed and recorded for the other, which the
 * line above the button says.
 */
@Composable
private fun RemoveGoalRow(onEvent: (ConsoleEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(
            text = stringResource(Res.string.goal_other_team_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { onEvent(ConsoleEvent.GoalRemovePressed) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(Res.string.goal_remove))
        }
    }
}

/**
 * Asked before a goal is removed from the log (0.4.0). It names the goal as the log
 * line has it, says what follows, and, on a signed report, that the signatures go:
 * one question, as Undo's for a whistle is ([TakeBackDialog]). A No goes back to the
 * sheet. A goal removed by mistake is recorded again, at its minute.
 */
@Composable
fun RemoveGoalDialog(
    line: String,
    signed: Boolean,
    onEvent: (ConsoleEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.GoalRemoveDismissed) },
        title = { Text(stringResource(Res.string.goal_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
                Text(stringResource(Res.string.goal_remove_body, line))
                if (signed) Text(stringResource(Res.string.signatures_withdraw_body), fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.GoalRemoveConfirmed) }) {
                Text(stringResource(Res.string.goal_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.GoalRemoveDismissed) }) {
                Text(stringResource(Res.string.goal_remove_keep))
            }
        },
    )
}

@Composable
private fun ConsoleUiState.goalError(latest: String): String? =
    when {
        goalProblem(GoalProblem.NOT_STARTED) -> stringResource(Res.string.goal_error_not_started)
        goalProblem(GoalProblem.NO_MINUTE) -> stringResource(Res.string.card_error_minute)
        goalProblem(GoalProblem.LATER_THAN_THE_CLOCK) -> stringResource(Res.string.goal_error_later, latest)
        else -> null
    }
