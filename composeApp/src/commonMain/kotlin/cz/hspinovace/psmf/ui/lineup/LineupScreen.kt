package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.ExperimentalFoundationApi
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
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.ZouWords
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.lineup_add_player
import cz.hspinovace.psmf.resources.lineup_captain_attestation
import cz.hspinovace.psmf.resources.lineup_captain_confirm
import cz.hspinovace.psmf.resources.lineup_captain_confirm_needs_captain
import cz.hspinovace.psmf.resources.lineup_captain_confirmed
import cz.hspinovace.psmf.resources.lineup_captain_label
import cz.hspinovace.psmf.resources.lineup_captain_section
import cz.hspinovace.psmf.resources.lineup_continue
import cz.hspinovace.psmf.resources.lineup_error_duplicate_number
import cz.hspinovace.psmf.resources.lineup_error_no_captain
import cz.hspinovace.psmf.resources.lineup_error_no_goalkeeper
import cz.hspinovace.psmf.resources.lineup_error_no_identification
import cz.hspinovace.psmf.resources.lineup_error_nobody
import cz.hspinovace.psmf.resources.lineup_goalkeeper_label
import cz.hspinovace.psmf.resources.lineup_kit
import cz.hspinovace.psmf.resources.lineup_loading
import cz.hspinovace.psmf.resources.lineup_mark_present_hint
import cz.hspinovace.psmf.resources.lineup_number_short
import cz.hspinovace.psmf.resources.lineup_present_count
import cz.hspinovace.psmf.resources.lineup_role_unset
import cz.hspinovace.psmf.resources.lineup_roles_note
import cz.hspinovace.psmf.resources.lineup_roles_section
import cz.hspinovace.psmf.resources.lineup_saved
import cz.hspinovace.psmf.resources.lineup_suspension_note
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.Choice
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.common.Select
import cz.hspinovace.psmf.ui.common.SignedBanner
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 3, as a plain state-driven composable.
 *
 * **The referee marks who came** (DECISIONS 2026-10-17): the squad from
 * psmf.cz is everyone who has played this season, and nobody is on the report
 * until they are tapped. Then one captain and one goalkeeper among them --
 * possibly the same person -- and the captain's own tap under the lineup.
 * Everything else on this screen is an exception: a jersey number that
 * changed, a player without their card, someone who is not on the list at
 * all.
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
            // Saves as you edit, so the report's signatures are taken back by the
            // first change; the notice is here before it.
            if (state.signed) {
                SignedBanner(Modifier.padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.labelGap))
            }
            SideTabs(state, onEvent)
            SquadColumn(state, team, onEvent, modifier = Modifier.weight(1f))
        }
    }

    state.newPlayer?.let { AddPlayerDialog(request = it, rejected = state.newPlayerRejected, onEvent = onEvent) }
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

@OptIn(ExperimentalFoundationApi::class)
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
        // Pinned: with twenty-five names to work down the count has to stay
        // where it can be read, or the referee scrolls back up to ask it.
        stickyHeader(key = "hint") { PresenceHint(team) }

        items(team.members.size, key = {
            team.members[it]
                .player.id.value
        }) { index ->
            val member = team.members[index]
            SquadRow(
                member = member,
                duplicateNumber = member.present && member.jerseyNumber in team.duplicateJerseyNumbers,
                onEvent = onEvent,
                isCaptain = member.present && member.player.id == team.captain,
                isGoalkeeper = member.present && member.player.id == team.goalkeeper,
                missingIdentification = state.problem(LineupProblem.NoIdentification(team.side, member.player.id)),
            )
        }

        item(key = "advisory") { SuspensionAdvisoryNote() }
        item(key = "add") { AddPlayerButton(onEvent) }
        item(key = "roles") { RolesSection(team, onEvent) }
        item(key = "confirm") { CaptainSection(team, onEvent) }
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

/**
 * How many are marked, and what tapping does. On a ground of its own, so
 * the rows that scroll under it do not show through.
 */
@Composable
private fun PresenceHint(team: TeamLineupEntry) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = PsmfDimens.labelGap),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
        ) {
            Text(
                text = stringResource(Res.string.lineup_present_count, team.present.size, team.members.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.lineup_mark_present_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/**
 * The captain and the goalkeeper: one of each among the players marked, and
 * the same person may be both. Needed before the block is written.
 *
 * Pick-lists of those present rather than a mark on every row, so that
 * naming them adds nothing to the rows the referee is working down.
 */
@Composable
private fun RolesSection(
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    Section(
        title = stringResource(Res.string.lineup_roles_section),
        note = stringResource(Res.string.lineup_roles_note),
    ) {
        val unset = Choice<PlayerId?>(value = null, text = stringResource(Res.string.lineup_role_unset))
        val present =
            team.present.map { member ->
                Choice<PlayerId?>(
                    value = member.player.id,
                    text = member.player.name.asWrittenOnReport,
                    detail = member.jerseyNumber?.let { stringResource(Res.string.lineup_number_short, it.value) },
                )
            }
        Select(
            label = stringResource(Res.string.lineup_captain_label),
            choices = listOf(unset) + present,
            selected = team.markedCaptain?.player?.id,
            onSelect = { onEvent(LineupEvent.CaptainChosen(it)) },
        )
        Select(
            label = stringResource(Res.string.lineup_goalkeeper_label),
            choices = listOf(unset) + present,
            selected = team.markedGoalkeeper?.player?.id,
            onSelect = { onEvent(LineupEvent.GoalkeeperChosen(it)) },
        )
    }
}

/**
 * `Podpis kapitána před zahájením utkání`: one tap by the marked captain,
 * *Potvrzuje &lt;name&gt;*. Nobody is picked from a list and there is no
 * deputy to switch on -- the captain is whoever is marked captain for this
 * match, which is how a stand-in shows up on paper too.
 *
 * It is the captain's confirmation of the lineup and **nothing else**: the
 * signature under the finished report is asked for again on the recap.
 */
@Composable
private fun CaptainSection(
    team: TeamLineupEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    Section(
        title = stringResource(Res.string.lineup_captain_section),
        // The form's own words, and they are the CAPTAIN's claim rather
        // than the app's. The app says nothing about who may play.
        note = stringResource(Res.string.lineup_captain_attestation),
    ) {
        val confirmation = team.confirmation
        val captain = team.markedCaptain
        when {
            confirmation != null -> {
                // A confirmation carried over from before 0.3.0 keeps its
                // deputy mark, in the form's own words.
                val by =
                    if (confirmation.asDeputy) {
                        "${confirmation.by.value} ${ZouWords.DEPUTY}"
                    } else {
                        confirmation.by.value
                    }
                Text(
                    text = stringResource(Res.string.lineup_captain_confirmed, by),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            captain != null -> {
                OutlinedButton(
                    onClick = { onEvent(LineupEvent.CaptainConfirms) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                ) {
                    Text(stringResource(Res.string.lineup_captain_confirm, captain.player.name.asWrittenOnReport))
                }
            }

            else -> {
                Text(
                    text = stringResource(Res.string.lineup_captain_confirm_needs_captain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                text = problem.describe(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LineupProblem.describe(state: LineupUiState): String =
    when (this) {
        // Named by team, because both teams' problems share this list.
        is LineupProblem.NobodyPresent -> {
            stringResource(Res.string.lineup_error_nobody, state.teamName(side))
        }

        is LineupProblem.NoCaptain -> {
            stringResource(Res.string.lineup_error_no_captain, state.teamName(side))
        }

        is LineupProblem.NoGoalkeeper -> {
            stringResource(Res.string.lineup_error_no_goalkeeper, state.teamName(side))
        }

        is LineupProblem.DuplicateJerseyNumber -> {
            stringResource(Res.string.lineup_error_duplicate_number, number.value)
        }

        // Named, and with the fix: the field is in that player's row.
        is LineupProblem.NoIdentification -> {
            stringResource(Res.string.lineup_error_no_identification, state.nameOf(side, playerId))
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
