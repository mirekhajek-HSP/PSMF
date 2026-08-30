package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.lineup_add_player
import cz.hspinovace.psmf.resources.lineup_captain_attestation
import cz.hspinovace.psmf.resources.lineup_captain_confirm
import cz.hspinovace.psmf.resources.lineup_captain_confirmed
import cz.hspinovace.psmf.resources.lineup_captain_section
import cz.hspinovace.psmf.resources.lineup_continue
import cz.hspinovace.psmf.resources.lineup_error_duplicate_number
import cz.hspinovace.psmf.resources.lineup_error_no_identification
import cz.hspinovace.psmf.resources.lineup_error_nobody
import cz.hspinovace.psmf.resources.lineup_kit
import cz.hspinovace.psmf.resources.lineup_loading
import cz.hspinovace.psmf.resources.lineup_mark_absent_hint
import cz.hspinovace.psmf.resources.lineup_present_count
import cz.hspinovace.psmf.resources.lineup_saved
import cz.hspinovace.psmf.resources.lineup_suspension_note
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 3, as a plain state-driven composable.
 *
 * **The squad is already known, so the job is marking who did not turn
 * up** — three to five taps rather than writing ten names (analysis
 * section 5.1). Everything else on this screen is an exception: a jersey
 * number that changed, a player without their card, someone who is not on
 * the list at all.
 */
@Composable
fun LineupScreen(
    state: LineupUiState,
    onEvent: (LineupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val team = state.selected
        if (state.loading || team == null) {
            Text(
                text = stringResource(Res.string.lineup_loading),
                modifier = Modifier.fillMaxWidth().padding(PsmfDimens.screenPadding),
                textAlign = TextAlign.Center,
            )
            return@Surface
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SideTabs(state, onEvent)
            SquadColumn(state, team, onEvent, modifier = Modifier.weight(1f))
        }
    }

    state.newPlayer?.let { AddPlayerDialog(request = it, rejected = state.newPlayerRejected, onEvent = onEvent) }
    state.confirmingSide?.let { side ->
        state.entry?.side(side)?.let { CaptainConfirmationDialog(team = it, onEvent = onEvent) }
    }
}

@Composable
private fun SideTabs(
    state: LineupUiState,
    onEvent: (LineupEvent) -> Unit,
) {
    val entry = state.entry ?: return
    TabRow(selectedTabIndex = if (state.selectedSide == TeamSide.HOME) 0 else 1) {
        listOf(entry.home, entry.away).forEach { team ->
            Tab(
                selected = state.selectedSide == team.side,
                onClick = { onEvent(LineupEvent.SideSelected(team.side)) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                text = {
                    // The team's own name, not "home" and "away": the referee
                    // is looking at a captain, not at a side of a form.
                    Text(team.team.name, maxLines = 2, textAlign = TextAlign.Center)
                },
            )
        }
    }
}

@Composable
private fun SquadColumn(
    state: LineupUiState,
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        item(key = "kit") { KitSection(team, onEvent) }
        item(key = "hint") { PresenceHint(team) }

        items(team.members.size, key = {
            team.members[it]
                .player.id.value
        }) { index ->
            SquadRow(
                member = team.members[index],
                duplicateNumber = team.members[index].jerseyNumber in team.duplicateJerseyNumbers,
                onEvent = onEvent,
            )
        }

        item(key = "advisory") { SuspensionAdvisoryNote() }
        item(key = "add") { AddPlayerButton(onEvent) }
        item(key = "confirm") { CaptainSection(state, team, onEvent) }
        item(key = "problems") { Problems(state) }
        item(key = "continue") { ContinueSection(state, onEvent) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitSection(
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    Section(title = stringResource(Res.string.lineup_kit)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            team.team.kits.forEach { kit ->
                FilterChip(
                    selected = team.kitId == kit.id,
                    onClick = { onEvent(LineupEvent.KitSelected(kit.id)) },
                    // The label verbatim from PSMF. It is what gets written
                    // on the report, and it is never derived from the colours.
                    label = { Text(kit.label) },
                    modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun PresenceHint(team: TeamLineupEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(
            text = stringResource(Res.string.lineup_present_count, team.present.size, team.members.size),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.lineup_mark_absent_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The sentence that keeps the card counts honest.
 *
 * **The app must never claim a player is eligible.** It may warn that one
 * might not be; the absence of a warning must not read as clearance.
 * Fielding an ineligible player is a technical forfeit, so an app that
 * displayed "clear" would have caused it.
 */
@Composable
private fun SuspensionAdvisoryNote() {
    Text(
        text = stringResource(Res.string.lineup_suspension_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = PsmfDimens.labelGap),
    )
}

@Composable
private fun AddPlayerButton(onEvent: (LineupEvent) -> Unit) {
    OutlinedButton(
        onClick = { onEvent(LineupEvent.AddPlayerOpened) },
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    ) {
        Text(stringResource(Res.string.lineup_add_player))
    }
}

@Composable
private fun CaptainSection(
    state: LineupUiState,
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    Section(
        title = stringResource(Res.string.lineup_captain_section),
        // The form's own words, and they are the CAPTAIN's claim rather
        // than the app's. The app says nothing about who may play.
        note = stringResource(Res.string.lineup_captain_attestation),
    ) {
        if (state.confirmed(team.side)) {
            Text(
                text = stringResource(Res.string.lineup_captain_confirmed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(
                onClick = { onEvent(LineupEvent.CaptainConfirmationOpened(team.side)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.lineup_captain_confirm))
            }
        }
    }
}

@Composable
private fun Problems(state: LineupUiState) {
    if (!state.showProblems || state.problems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        state.problems.forEach { problem ->
            Text(
                text = problem.describe(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LineupProblem.describe(): String =
    when (this) {
        is LineupProblem.NobodyPresent -> {
            stringResource(Res.string.lineup_error_nobody)
        }

        is LineupProblem.DuplicateJerseyNumber -> {
            stringResource(Res.string.lineup_error_duplicate_number, number.value)
        }

        is LineupProblem.NoIdentification -> {
            stringResource(Res.string.lineup_error_no_identification)
        }
    }

@Composable
private fun ContinueSection(
    state: LineupUiState,
    onEvent: (LineupEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing)) {
        if (state.showProblems && state.problems.isEmpty()) {
            Text(
                text = stringResource(Res.string.lineup_saved),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ActionRow {
            PrimaryAction(
                text = stringResource(Res.string.lineup_continue),
                onClick = { onEvent(LineupEvent.ContinuePressed) },
            )
        }
    }
}
