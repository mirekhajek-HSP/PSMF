package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_cancel
import cz.hspinovace.psmf.resources.console_takeback_body
import cz.hspinovace.psmf.resources.console_takeback_confirm
import cz.hspinovace.psmf.resources.console_takeback_title
import cz.hspinovace.psmf.resources.console_whistle_end_period
import cz.hspinovace.psmf.resources.console_whistle_final
import cz.hspinovace.psmf.resources.console_whistle_kickoff
import cz.hspinovace.psmf.resources.console_whistle_start_period
import cz.hspinovace.psmf.resources.signatures_withdraw_body
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Asked before a whistle is taken back, because that changes the clock.
 *
 * It names the whistle -- *Konec 1. poločasu*, *Výkop*, ... -- because Undo
 * takes back the last thing recorded and the referee may not have expected it
 * to be one. Saying yes removes that one instant from those the clock is
 * worked out from; nothing is paused, stopped, resumed or adjusted, and the
 * goals and cards already written stay.
 */
@Composable
fun TakeBackDialog(
    step: UndoStep.TakesBackWhistle,
    signed: Boolean,
    onEvent: (ConsoleEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ConsoleEvent.TakeBackDismissed) },
        title = { Text(stringResource(Res.string.console_takeback_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
                Text(stringResource(Res.string.console_takeback_body, step.named()))
                // A whistle is among the things the captains attest, so on a
                // signed report this one answer also takes the signatures back.
                if (signed) Text(stringResource(Res.string.signatures_withdraw_body), fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.TakeBackConfirmed) }) {
                Text(stringResource(Res.string.console_takeback_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConsoleEvent.TakeBackDismissed) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun UndoStep.TakesBackWhistle.named(): String = whistleName(whistle, period)

/**
 * A whistle in words, as Undo's question names it and the log lists it: one place
 * for the wording, so the question and the line it is about cannot differ.
 */
@Composable
internal fun whistleName(
    whistle: Whistle,
    period: Int,
): String =
    when (whistle) {
        Whistle.KICKOFF -> stringResource(Res.string.console_whistle_kickoff)
        Whistle.END_OF_PERIOD -> stringResource(Res.string.console_whistle_end_period, period.toString())
        Whistle.START_OF_PERIOD -> stringResource(Res.string.console_whistle_start_period, period.toString())
        Whistle.FINAL -> stringResource(Res.string.console_whistle_final)
    }
