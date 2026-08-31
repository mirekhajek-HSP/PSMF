package cz.hspinovace.psmf.ui.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.PSMF_REPORT_ADDRESS
import cz.hspinovace.psmf.export.ZouFormat
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.export_blocked
import cz.hspinovace.psmf.resources.export_czech_note
import cz.hspinovace.psmf.resources.export_failed
import cz.hspinovace.psmf.resources.export_loading
import cz.hspinovace.psmf.resources.export_preview
import cz.hspinovace.psmf.resources.export_problem_assessment
import cz.hspinovace.psmf.resources.export_problem_card_reason
import cz.hspinovace.psmf.resources.export_problem_cards
import cz.hspinovace.psmf.resources.export_problem_commentary
import cz.hspinovace.psmf.resources.export_problem_confirmation_away
import cz.hspinovace.psmf.resources.export_problem_confirmation_home
import cz.hspinovace.psmf.resources.export_problem_confirmation_referee
import cz.hspinovace.psmf.resources.export_problem_lineup
import cz.hspinovace.psmf.resources.export_problem_officials
import cz.hspinovace.psmf.resources.export_problem_result
import cz.hspinovace.psmf.resources.export_problem_score_mismatch
import cz.hspinovace.psmf.resources.export_ready
import cz.hspinovace.psmf.resources.export_send
import cz.hspinovace.psmf.resources.export_sent
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 7.
 *
 * **The output file is what the demo is selling** — not a better referee
 * experience but the end of a week of retyping (analysis section 1) — so
 * all three renderings are on screen, in full, before anything is sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onEvent: (ExportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.loading) {
            Text(
                text = stringResource(Res.string.export_loading),
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
            Readiness(state)

            Section(
                title = stringResource(Res.string.export_preview),
                // The report is Czech whatever language the app is in. This
                // sentence is the only place a referee reading the app in
                // Ukrainian is told that, so it is worth the room.
                note = stringResource(Res.string.export_czech_note),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                    ZouFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.selected == format,
                            onClick = { onEvent(ExportEvent.FormatSelected(format)) },
                            label = { Text(format.extension.uppercase()) },
                            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                        )
                    }
                }
                state.preview?.let { document ->
                    Text(document.fileName, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = document.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        // CSV rows are wider than a phone; scrolling beats
                        // wrapping, which would misrepresent the file.
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }

            if (state.sent) {
                Text(
                    text = stringResource(Res.string.export_sent, PSMF_REPORT_ADDRESS),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.sendFailed) {
                Text(
                    text = stringResource(Res.string.export_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.ready) {
                ActionRow {
                    PrimaryAction(
                        text = stringResource(Res.string.export_send),
                        onClick = { onEvent(ExportEvent.SendPressed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Readiness(state: ExportUiState) {
    if (state.ready) {
        Text(
            text = stringResource(Res.string.export_ready),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    Section(title = stringResource(Res.string.export_blocked)) {
        state.problems.forEach {
            Text(
                text = it.describe(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ReportProblem.describe(): String =
    when (this) {
        ReportProblem.CardsNotAccountedFor -> {
            stringResource(Res.string.export_problem_cards)
        }

        is ReportProblem.CardWithoutReason -> {
            stringResource(Res.string.export_problem_card_reason, minute.written)
        }

        ReportProblem.MissingCommentary -> {
            stringResource(Res.string.export_problem_commentary)
        }

        is ReportProblem.AssessmentIncomplete -> {
            stringResource(Res.string.export_problem_assessment, side.mark())
        }

        ReportProblem.MissingResult -> {
            stringResource(Res.string.export_problem_result)
        }

        is ReportProblem.ScoreDisagreesWithGoals -> {
            stringResource(
                Res.string.export_problem_score_mismatch,
                recorded.asWrittenOnReport,
                fromGoals.asWrittenOnReport,
            )
        }

        ReportProblem.MissingOfficials -> {
            stringResource(Res.string.export_problem_officials)
        }

        is ReportProblem.MissingLineup -> {
            stringResource(Res.string.export_problem_lineup, side.mark())
        }

        is ReportProblem.MissingConfirmation -> {
            when (party) {
                ConfirmingParty.HOME_CAPTAIN -> stringResource(Res.string.export_problem_confirmation_home)
                ConfirmingParty.AWAY_CAPTAIN -> stringResource(Res.string.export_problem_confirmation_away)
                ConfirmingParty.REFEREE -> stringResource(Res.string.export_problem_confirmation_referee)
            }
        }
    }

/** `D` and `H`, as the form marks the two sides. */
private fun TeamSide.mark(): String = if (this == TeamSide.HOME) "D" else "H"
