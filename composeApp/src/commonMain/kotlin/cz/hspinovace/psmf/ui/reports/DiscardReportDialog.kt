package cz.hspinovace.psmf.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.reports_discard_assessment
import cz.hspinovace.psmf.resources.reports_discard_both_lineups
import cz.hspinovace.psmf.resources.reports_discard_cards
import cz.hspinovace.psmf.resources.reports_discard_confirm
import cz.hspinovace.psmf.resources.reports_discard_final
import cz.hspinovace.psmf.resources.reports_discard_goals
import cz.hspinovace.psmf.resources.reports_discard_header
import cz.hspinovace.psmf.resources.reports_discard_holds
import cz.hspinovace.psmf.resources.reports_discard_holds_nothing
import cz.hspinovace.psmf.resources.reports_discard_keep
import cz.hspinovace.psmf.resources.reports_discard_match
import cz.hspinovace.psmf.resources.reports_discard_one_lineup
import cz.hspinovace.psmf.resources.reports_discard_signatures
import cz.hspinovace.psmf.resources.reports_discard_signed
import cz.hspinovace.psmf.resources.reports_discard_title
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ReportContents
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/**
 * Asked before a report is discarded (prompt 17, Gate 2). Nothing removes a
 * report without a yes to this.
 *
 * It names the match -- the teams, the date and the time -- because the
 * referee tapped *Zahodit* on one row of several; says what goes, in words
 * (*hlavička, obě sestavy, 3 góly, 1 karta*, or that nothing is filled in
 * yet); and that it cannot be undone. A signed report's question says more:
 * that it was signed and may already have been sent to PSMF, and that what
 * was saved to the device's folder or sent by mail stays where it is.
 *
 * **The yes is never the one a quick tap lands on.** Two ways:
 *  - Keep is in the slot where the app's other questions put their yes, and
 *    a tap out of habit goes: the end of the row when the two fit side by
 *    side, the top when Material stacks them, as it does at 360 dp. It is
 *    the filled button; the yes is a plain one in the error colour.
 *  - The yes is armed only [ARMING] after the question appears. Wherever the
 *    layout puts it, the second tap of a double tap on *Zahodit* -- which, in
 *    a scrolled list, can be exactly where the yes is drawn -- or a reflex
 *    tap, finds it disabled. Keep works at once.
 *
 * Tapping beside the question, or back, keeps the report too.
 */
@Composable
fun DiscardReportDialog(
    question: DiscardQuestion,
    onEvent: (ReportsEvent) -> Unit,
) {
    val row = question.row
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ARMING)
        armed = true
    }
    AlertDialog(
        onDismissRequest = { onEvent(ReportsEvent.DiscardDismissed) },
        title = { Text(stringResource(Res.string.reports_discard_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
                Text(
                    stringResource(
                        Res.string.reports_discard_match,
                        row.homeTeam.name,
                        row.awayTeam.name,
                        row.fixture.date.asDayAndMonth(),
                        row.fixture.time.asClockTime(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(question.contents.whatGoes())
                if (question.contents.signed) {
                    Text(stringResource(Res.string.reports_discard_signed), fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(Res.string.reports_discard_final))
            }
        },
        // The slots are Material's names, not what is in them: "confirm" is
        // the end of the row, or the top of a stack, where keep belongs here.
        confirmButton = {
            Button(onClick = { onEvent(ReportsEvent.DiscardDismissed) }) {
                Text(stringResource(Res.string.reports_discard_keep))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ReportsEvent.DiscardConfirmed) },
                enabled = armed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.reports_discard_confirm))
            }
        },
    )
}

/**
 * How long the yes stays disabled once the question is up: longer than the
 * second tap of a double tap (300 ms on Android) and a reflex, shorter than
 * reading the question.
 */
internal val ARMING = 1.seconds

/** *Zahodí se: hlavička, obě sestavy, 3 góly, 1 karta.* -- each part only if the report holds it. */
@Composable
private fun ReportContents.whatGoes(): String {
    val parts =
        buildList {
            if (header) add(stringResource(Res.string.reports_discard_header))
            when (lineups) {
                0 -> Unit
                1 -> add(stringResource(Res.string.reports_discard_one_lineup))
                else -> add(stringResource(Res.string.reports_discard_both_lineups))
            }
            if (goals > 0) add(pluralStringResource(Res.plurals.reports_discard_goals, goals, goals))
            if (cards > 0) add(pluralStringResource(Res.plurals.reports_discard_cards, cards, cards))
            if (assessment) add(stringResource(Res.string.reports_discard_assessment))
            if (signatures >
                0
            ) {
                add(pluralStringResource(Res.plurals.reports_discard_signatures, signatures, signatures))
            }
        }
    return if (parts.isEmpty()) {
        stringResource(Res.string.reports_discard_holds_nothing)
    } else {
        stringResource(Res.string.reports_discard_holds, parts.joinToString(", "))
    }
}
