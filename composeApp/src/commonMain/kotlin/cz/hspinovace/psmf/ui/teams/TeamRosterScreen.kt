package cz.hspinovace.psmf.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.lineup_jersey
import cz.hspinovace.psmf.resources.teams_absence_note
import cz.hspinovace.psmf.resources.teams_cards_season
import cz.hspinovace.psmf.resources.teams_follow
import cz.hspinovace.psmf.resources.teams_jersey_range
import cz.hspinovace.psmf.resources.teams_jersey_restore
import cz.hspinovace.psmf.resources.teams_kits
import cz.hspinovace.psmf.resources.teams_kits_note
import cz.hspinovace.psmf.resources.teams_read_only_note
import cz.hspinovace.psmf.resources.teams_roster
import cz.hspinovace.psmf.resources.teams_rp_missing
import cz.hspinovace.psmf.resources.teams_rp_number
import cz.hspinovace.psmf.resources.teams_unfollow
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.RosterRow
import cz.hspinovace.psmf.usecase.TeamRoster
import org.jetbrains.compose.resources.stringResource

/**
 * One team's roster.
 *
 * # What is editable
 *
 * **The jersey number, and nothing else.** A default number is a standing
 * attribute of a player, which is what a team screen is for. Names, RP
 * numbers and card history are the league's records, and a referee editing
 * a registered player is a data-integrity failure — the same rule the
 * lineup screen follows.
 *
 * # What is deliberately absent
 *
 * **Absence.** It is a fact about one match, not about a player. Set here it
 * would either persist forever or need a fixture attached, at which point
 * this is the lineup screen with extra steps. The screen says so, because
 * it is the thing a referee would most reasonably look for here.
 *
 * # Card history
 *
 * Shown, and advisory only. The app must never state that a player is
 * eligible: the counts are stale by construction, and the absence of a
 * warning must not read as clearance.
 */
@Composable
fun TeamRosterScreen(
    state: TeamRosterUiState,
    onEvent: (TeamRosterEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roster = state.roster
    if (roster == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loading) CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        // The team's name is here rather than in the top bar. The bar's
        // title comes from the destination, and a destination cannot know a
        // name that has to be loaded -- so it says "Týmy" and this says
        // which team, which is also the reading order.
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                Text(
                    text = roster.team.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = roster.group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "kits") { KitsSection(roster, onEvent) }
        item(key = "roster-heading") {
            Text(
                text = stringResource(Res.string.teams_roster),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item(key = "read-only") { Note(stringResource(Res.string.teams_read_only_note)) }

        items(roster.rows.size, key = {
            roster.rows[it]
                .player.id.value
        }) { index ->
            PlayerRow(row = roster.rows[index], state = state, onEvent = onEvent)
        }

        item(key = "absence") { Note(stringResource(Res.string.teams_absence_note)) }
        if (roster.rows.none { it.rpNumber != null }) {
            item(key = "rp") { Note(stringResource(Res.string.teams_rp_missing)) }
        }
    }
}

@Composable
private fun KitsSection(
    roster: TeamRoster,
    onEvent: (TeamRosterEvent) -> Unit,
) {
    Section(
        title = stringResource(Res.string.teams_kits),
        note = stringResource(Res.string.teams_kits_note),
    ) {
        // Both sets, in order, as plain text. Not chips: a chip that does
        // nothing when tapped is worse than a label, and these are labels
        // -- a team owns two sets and picks one per match so the sides are
        // not in similar colours. Which one was worn is a fact about a
        // match and is not on this screen.
        //
        // The labels are verbatim from PSMF and never derived from the
        // colours: "bílo-modrá" is not obtainable from ["bílá", "modrá"].
        Text(
            text = roster.kits.joinToString(KIT_SEPARATOR) { it.label },
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = { onEvent(TeamRosterEvent.FollowToggled(!roster.followed)) },
            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
        ) {
            Text(
                stringResource(
                    if (roster.followed) Res.string.teams_unfollow else Res.string.teams_follow,
                ),
            )
        }
    }
}

@Composable
private fun PlayerRow(
    row: RosterRow,
    state: TeamRosterUiState,
    onEvent: (TeamRosterEvent) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = PsmfDimens.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
            ) {
                Text(
                    // Surname first, the order the ZoU column is written in.
                    text = row.player.name.asWrittenOnReport,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val rp = row.rpNumber
                if (rp != null) {
                    Text(
                        text = stringResource(Res.string.teams_rp_number, rp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val yellows = row.player.discipline?.yellowsThisSeason
                if (yellows != null && yellows > 0) {
                    Text(
                        text = stringResource(Res.string.teams_cards_season, yellows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.corrected) {
                    TextButton(
                        onClick = { onEvent(TeamRosterEvent.CorrectionCleared(row.player.id)) },
                        modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                    ) {
                        Text(stringResource(Res.string.teams_jersey_restore))
                    }
                }
            }
            JerseyField(row, state, onEvent)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * The one editable field on the screen.
 *
 * An unparseable value — including an empty field, which is what the
 * referee sees halfway through replacing 27 with 7 — is **kept and marked
 * in error rather than silently dropped**. Dropping it would leave the old
 * number on screen with no sign that the keystroke did nothing.
 */
@Composable
private fun JerseyField(
    row: RosterRow,
    state: TeamRosterUiState,
    onEvent: (TeamRosterEvent) -> Unit,
) {
    val invalid = state.isInError(row.player.id)
    OutlinedTextField(
        value = state.fieldText(row.player.id, row.jerseyNumber),
        onValueChange = { onEvent(TeamRosterEvent.JerseyNumberChanged(row.player.id, it)) },
        label = { Text(stringResource(Res.string.lineup_jersey)) },
        singleLine = true,
        isError = invalid,
        supportingText =
            if (invalid) {
                { Text(stringResource(Res.string.teams_jersey_range)) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(PsmfDimens.labelGap),
        modifier = Modifier.width(JERSEY_FIELD_WIDTH).heightIn(min = PsmfDimens.minTouchTarget),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = PsmfDimens.labelGap),
    )
}

private val JERSEY_FIELD_WIDTH = 112.dp

/** Wide enough to read at arm's length, narrow enough not to look tappable. */
private const val KIT_SEPARATOR = "  ·  "
