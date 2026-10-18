package cz.hspinovace.psmf.ui.assessment

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.assessment_best_player
import cz.hspinovace.psmf.resources.assessment_commentary
import cz.hspinovace.psmf.resources.assessment_commentary_note
import cz.hspinovace.psmf.resources.assessment_continue
import cz.hspinovace.psmf.resources.assessment_fines_note
import cz.hspinovace.psmf.resources.assessment_no
import cz.hspinovace.psmf.resources.assessment_shirts
import cz.hspinovace.psmf.resources.assessment_team_note
import cz.hspinovace.psmf.resources.assessment_team_note_hint
import cz.hspinovace.psmf.resources.assessment_uniform_kit
import cz.hspinovace.psmf.resources.assessment_waiting_time
import cz.hspinovace.psmf.resources.assessment_yes
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.PsmfTextField
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.TeamAssessmentDraft
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 5. The assessment block, which has no counterpart in golblok and
 * is mandatory on the form.
 *
 * `Č` and `B` are **three-state on purpose**: unanswered, yes, or no.
 * Both feed straight into fines, so a rating the referee has not given
 * must not arrive at PSMF looking like a pass.
 */
@Composable
fun AssessmentScreen(
    state: AssessmentUiState,
    onEvent: (AssessmentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PsmfDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.sectionSpacing),
        ) {
            TeamSide.entries.forEach { side ->
                TeamBlock(
                    title = state.teamName(side),
                    draft = state.draft.side(side),
                    onChange = { onEvent(AssessmentEvent.TeamEdited(side, it)) },
                )
            }

            Section(
                title = stringResource(Res.string.assessment_commentary),
                note = stringResource(Res.string.assessment_commentary_note),
            ) {
                PsmfTextField(
                    value = state.draft.commentary,
                    onValueChange = { onEvent(AssessmentEvent.CommentaryEdited(it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = COMMENTARY_HEIGHT),
                    singleLine = false,
                    minLines = COMMENTARY_LINES,
                    // Several lines: the return key is a new line here and the
                    // way off it is a tap elsewhere or a scroll.
                    imeAction = ImeAction.Default,
                )
            }

            ActionRow {
                PrimaryAction(
                    text = stringResource(Res.string.assessment_continue),
                    onClick = { onEvent(AssessmentEvent.ContinuePressed) },
                )
            }
        }
    }
}

@Composable
private fun TeamBlock(
    title: String,
    draft: TeamAssessmentDraft,
    onChange: (TeamAssessmentDraft) -> Unit,
) {
    Section(title = title, note = stringResource(Res.string.assessment_fines_note)) {
        NumberField(
            label = stringResource(Res.string.assessment_best_player),
            value = draft.bestPlayer,
            onValueChange = { onChange(draft.copy(bestPlayer = it)) },
        )
        NumberField(
            label = stringResource(Res.string.assessment_waiting_time),
            value = draft.waitingTimeMinutes,
            onValueChange = { onChange(draft.copy(waitingTimeMinutes = it)) },
        )
        YesNo(
            label = stringResource(Res.string.assessment_shirts),
            value = draft.shirtsProperlyNumbered,
            onChange = { onChange(draft.copy(shirtsProperlyNumbered = it)) },
        )
        YesNo(
            label = stringResource(Res.string.assessment_uniform_kit),
            value = draft.uniformKitColour,
            onChange = { onChange(draft.copy(uniformKitColour = it)) },
        )
        // A complaint or a protest the captain asks the referee to note. The
        // referee types it, being the only recorder; it is optional, and never
        // a reason the report cannot be sent.
        PsmfTextField(
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it)) },
            label = { Text(stringResource(Res.string.assessment_team_note)) },
            supportingText = { Text(stringResource(Res.string.assessment_team_note_hint)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = NOTE_HEIGHT),
            singleLine = false,
            minLines = NOTE_LINES,
            // Several lines: the return key is a new line, as in the commentary.
            imeAction = ImeAction.Default,
        )
    }
}

/**
 * Yes, no, or nothing yet.
 *
 * Neither chip is selected until the referee picks one, and picking the
 * selected one again clears it. A default would be an answer nobody gave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YesNo(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            FilterChip(
                selected = value == true,
                onClick = { onChange(if (value == true) null else true) },
                label = { Text(stringResource(Res.string.assessment_yes)) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            )
            FilterChip(
                selected = value == false,
                onClick = { onChange(if (value == false) null else false) },
                label = { Text(stringResource(Res.string.assessment_no)) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    PsmfTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardType = KeyboardType.Number,
        // Down the page to the commentary, which is the last field.
        imeAction = ImeAction.Next,
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    )
}

private val COMMENTARY_HEIGHT = 160.dp
private val NOTE_HEIGHT = 96.dp
private const val NOTE_LINES = 3
private const val COMMENTARY_LINES = 5
