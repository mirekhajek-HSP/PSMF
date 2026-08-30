package cz.hspinovace.psmf.ui.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.header_assistant
import cz.hspinovace.psmf.resources.header_continue
import cz.hspinovace.psmf.resources.header_date
import cz.hspinovace.psmf.resources.header_delegating_note
import cz.hspinovace.psmf.resources.header_delegating_other
import cz.hspinovace.psmf.resources.header_delegating_other_label
import cz.hspinovace.psmf.resources.header_delegating_section
import cz.hspinovace.psmf.resources.header_error_delegating_missing
import cz.hspinovace.psmf.resources.header_error_name_not_latin
import cz.hspinovace.psmf.resources.header_error_referee_missing
import cz.hspinovace.psmf.resources.header_fixture_section
import cz.hspinovace.psmf.resources.header_league
import cz.hspinovace.psmf.resources.header_licensed_hire_assistant
import cz.hspinovace.psmf.resources.header_licensed_hire_note
import cz.hspinovace.psmf.resources.header_licensed_hire_referee
import cz.hspinovace.psmf.resources.header_officials_section
import cz.hspinovace.psmf.resources.header_pitch
import cz.hspinovace.psmf.resources.header_referee
import cz.hspinovace.psmf.resources.header_saved
import cz.hspinovace.psmf.resources.header_time
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.ReadOnlyField
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.HeaderProblem
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 2, as a plain state-driven composable.
 *
 * The blocks follow page 1 of the paper form in the order it is printed,
 * because the referee's habit is the paper's order and rearranging it
 * would cost more than it could possibly buy.
 */
@Composable
fun MatchHeaderScreen(
    state: MatchHeaderUiState,
    onEvent: (MatchHeaderEvent) -> Unit,
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
            state.fixture?.let { FixtureSection(it) }
            OfficialsSection(state, onEvent)
            DelegatingTeamSection(state, onEvent)
            if (state.complete) {
                // The header is already on disk -- it was written through as
                // it was typed. The lineup screen is the next phase.
                Text(
                    text = stringResource(Res.string.header_saved),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ActionRow {
                PrimaryAction(
                    text = stringResource(Res.string.header_continue),
                    onClick = { onEvent(MatchHeaderEvent.ContinuePressed) },
                )
            }
        }
    }
}

@Composable
private fun FixtureSection(fixture: FixtureHeadline) {
    Section(title = stringResource(Res.string.header_fixture_section)) {
        Text(
            text = "${fixture.homeTeamName} – ${fixture.awayTeamName}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        // Four fields the referee never types: they come from the fixture,
        // and a referee inventing them is a data-integrity failure.
        ReadOnlyField(stringResource(Res.string.header_pitch), fixture.venueCode)
        ReadOnlyField(stringResource(Res.string.header_date), fixture.date.asFullDate())
        ReadOnlyField(stringResource(Res.string.header_time), fixture.time.asClockTime())
        ReadOnlyField(
            stringResource(Res.string.header_league),
            "${fixture.leagueCode} · ${fixture.groupName}",
        )
    }
}

@Composable
private fun OfficialsSection(
    state: MatchHeaderUiState,
    onEvent: (MatchHeaderEvent) -> Unit,
) {
    Section(title = stringResource(Res.string.header_officials_section)) {
        NameField(
            label = stringResource(Res.string.header_referee),
            value = state.entry.refereeName,
            onValueChange = { onEvent(MatchHeaderEvent.RefereeNameChanged(it)) },
            error =
                when {
                    state.problem(HeaderProblem.RefereeNameMissing) -> {
                        stringResource(Res.string.header_error_referee_missing)
                    }

                    state.problem(HeaderProblem.RefereeNameNotLatin) -> {
                        stringResource(Res.string.header_error_name_not_latin)
                    }

                    else -> {
                        null
                    }
                },
        )
        LicensedHireToggle(
            label = stringResource(Res.string.header_licensed_hire_referee),
            checked = state.entry.refereeLicensedHire,
            onCheckedChange = { onEvent(MatchHeaderEvent.RefereeLicensedChanged(it)) },
        )

        NameField(
            label = stringResource(Res.string.header_assistant),
            value = state.entry.assistantName,
            onValueChange = { onEvent(MatchHeaderEvent.AssistantNameChanged(it)) },
            error =
                stringResource(Res.string.header_error_name_not_latin)
                    .takeIf { state.problem(HeaderProblem.AssistantNameNotLatin) },
        )
        LicensedHireToggle(
            label = stringResource(Res.string.header_licensed_hire_assistant),
            checked = state.entry.assistantLicensedHire,
            onCheckedChange = { onEvent(MatchHeaderEvent.AssistantLicensedChanged(it)) },
        )
        Text(
            text = stringResource(Res.string.header_licensed_hire_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `Týmy` — who gets fined.
 *
 * Given its own block with its own explanation, because it is the field
 * most easily mistaken for "one of the teams playing", and the one whose
 * consequence lands on somebody else.
 */
@Composable
private fun DelegatingTeamSection(
    state: MatchHeaderUiState,
    onEvent: (MatchHeaderEvent) -> Unit,
) {
    Section(
        title = stringResource(Res.string.header_delegating_section),
        note = stringResource(Res.string.header_delegating_note),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            state.teamOptions.forEach { team ->
                FilterChip(
                    selected = !state.otherTeamSelected && state.entry.delegatingTeam == team,
                    onClick = { onEvent(MatchHeaderEvent.DelegatingTeamPicked(team)) },
                    label = { Text(team) },
                    modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                )
            }
            FilterChip(
                selected = state.otherTeamSelected,
                onClick = { onEvent(MatchHeaderEvent.DelegatingTeamOtherPicked) },
                label = { Text(stringResource(Res.string.header_delegating_other)) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            )
        }

        if (state.otherTeamSelected) {
            NameField(
                label = stringResource(Res.string.header_delegating_other_label),
                value = state.entry.delegatingTeam,
                onValueChange = { onEvent(MatchHeaderEvent.DelegatingTeamTyped(it)) },
                error = null,
            )
        }

        if (state.problem(HeaderProblem.DelegatingTeamMissing)) {
            Text(
                text = stringResource(Res.string.header_error_delegating_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NameField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The `R` mark: a licensed referee hired by the delegating team.
 *
 * [label] names the official rather than the rule. Two switches reading
 * "Placený rozhodčí (R)" one under the other say nothing about which of
 * the two names above them they belong to.
 */
@Composable
private fun LicensedHireToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
