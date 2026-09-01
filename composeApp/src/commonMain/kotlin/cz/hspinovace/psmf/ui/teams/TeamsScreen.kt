package cz.hspinovace.psmf.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_error_title
import cz.hspinovace.psmf.resources.teams_follow
import cz.hspinovace.psmf.resources.teams_followed
import cz.hspinovace.psmf.resources.teams_followed_empty
import cz.hspinovace.psmf.resources.teams_no_matches
import cz.hspinovace.psmf.resources.teams_search
import cz.hspinovace.psmf.resources.teams_squad_size
import cz.hspinovace.psmf.resources.teams_unfollow
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.TeamCard
import cz.hspinovace.psmf.usecase.TeamDirectory
import org.jetbrains.compose.resources.stringResource

/**
 * The Týmy tab.
 *
 * **A reference screen, not an admin screen.** Following a team says "I
 * officiate these" — it is a shortcut through league data that is already
 * on the device, which is why the verb is follow and not download. There is
 * no captain-facing surface anywhere in this app.
 */
@Composable
fun TeamsScreen(
    state: TeamsUiState,
    onEvent: (TeamsEvent) -> Unit,
    onOpenTeam: (TeamId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Back to the top whenever the query changes.
    //
    // Not cosmetic, and **not covered by any test in this repository.**
    //
    // `LazyColumn` keeps its place by the *key* of the first visible item.
    // Clearing a search puts the followed section back above the league
    // heading the list was anchored to, so the list scrolls to keep that
    // heading where it was -- and the followed teams end up off-screen
    // above. The referee clears a search and appears to have lost them.
    // Found on the emulator; fixed and re-verified there.
    //
    // Three attempts at a JVM test that fails without this line all passed
    // with it deleted, including a phone-sized container and the list's own
    // scroll-to-index. The test host does not preserve a lazy list's scroll
    // position across the recomposition, so there is nothing to catch. If
    // this line is ever removed, only a device will notice.
    LaunchedEffect(state.query) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(TeamsEvent.QueryChanged(it)) },
            label = { Text(stringResource(Res.string.teams_search)) },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PsmfDimens.screenPadding)
                    .heightIn(min = PsmfDimens.minTouchTarget),
        )

        when {
            state.failure != null -> {
                Failure(state.failure)
            }

            state.loading || state.directory == null -> {
                Loading()
            }

            else -> {
                Directory(state.directory, listState, onEvent, onOpenTeam)
            }
        }
    }
}

@Composable
private fun Directory(
    directory: TeamDirectory,
    listState: LazyListState,
    onEvent: (TeamsEvent) -> Unit,
    onOpenTeam: (TeamId) -> Unit,
) {
    // Which league sections are open, by group id. Missing means closed.
    //
    // Browsing by league is secondary to followed teams and to search --
    // at full scale this is sixty sections of up to fifteen teams each,
    // and a tab that opened with all of them expanded would bury the
    // followed section the tab exists for under a wall of rows. A search
    // ignores this map entirely: a query is already a narrowing, and
    // hiding its own results behind a second tap would be perverse.
    val expandedLeagues = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PsmfDimens.sectionSpacing),
    ) {
        if (directory.isEmpty) {
            item(key = "none") { Empty(directory) }
            return@LazyColumn
        }

        // The followed section is skipped while searching rather than
        // shown empty: a search is a question about every team, and an
        // empty box captioned "followed" is not an answer to it.
        if (!directory.searching) {
            item(key = "followed-heading") { SectionHeading(stringResource(Res.string.teams_followed)) }
            if (directory.followed.isEmpty()) {
                item(key = "followed-empty") { FollowNothingYet() }
            }
            teamRows(directory.followed, onEvent, onOpenTeam, keyPrefix = "followed")
        }

        directory.leagues.forEach { league ->
            val key = league.group.id.value
            val open = directory.searching || expandedLeagues[key] == true
            item(key = "league-$key") {
                CollapsibleSectionHeading(
                    text = league.group.name,
                    expanded = open,
                    onToggle = { expandedLeagues[key] = !(expandedLeagues[key] ?: false) },
                )
            }
            if (open) {
                teamRows(league.teams, onEvent, onOpenTeam, keyPrefix = key)
            }
        }
    }
}

/**
 * The rows of one section.
 *
 * Keyed by section *and* team: a followed team appears twice on the
 * unsearched screen — once at the top and once under its league — and two
 * items with the same key is a crash rather than a glitch.
 */
private fun LazyListScope.teamRows(
    teams: List<TeamCard>,
    onEvent: (TeamsEvent) -> Unit,
    onOpenTeam: (TeamId) -> Unit,
    keyPrefix: String,
) = items(teams.size, key = { "$keyPrefix-${teams[it].team.id.value}" }) { index ->
    TeamRow(
        card = teams[index],
        onOpen = { onOpenTeam(teams[index].team.id) },
        onToggleFollow = {
            onEvent(TeamsEvent.FollowToggled(teams[index].team.id, !teams[index].followed))
        },
    )
}

@Composable
private fun TeamRow(
    card: TeamCard,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PsmfDimens.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen)
                        .padding(
                            start = PsmfDimens.screenPadding,
                            top = PsmfDimens.itemSpacing,
                            bottom = PsmfDimens.itemSpacing,
                            end = PsmfDimens.labelGap,
                        ),
                verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
            ) {
                Text(text = card.team.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(Res.string.teams_squad_size, card.squadSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A star, not a word: "which teams have I starred" is a
            // glance, not a read, and the Týmy tab is opened far more
            // often than the label is. The old localised string stays as
            // contentDescription, so screen readers are unaffected.
            val followDescription =
                stringResource(if (card.followed) Res.string.teams_unfollow else Res.string.teams_follow)
            IconButton(
                onClick = onToggleFollow,
                modifier =
                    Modifier
                        .heightIn(min = PsmfDimens.minTouchTarget)
                        .padding(end = PsmfDimens.labelGap)
                        .semantics { contentDescription = followDescription },
            ) {
                Icon(
                    imageVector = if (card.followed) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint =
                        if (card.followed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = PsmfDimens.screenPadding,
                    end = PsmfDimens.screenPadding,
                    top = PsmfDimens.sectionSpacing,
                    bottom = PsmfDimens.labelGap,
                ).semantics { heading() },
    )
}

/**
 * A league heading that opens and closes its section.
 *
 * The chevron is decoration, not the control -- the whole row is the
 * target, sized the same as everything else a cold thumb has to hit, and
 * its accessible name is the league name Compose already merges in from
 * the [Text] below rather than a second, separately translated label.
 */
@Composable
private fun CollapsibleSectionHeading(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PsmfDimens.minTouchTarget)
                .clickable(onClick = onToggle)
                .padding(
                    start = PsmfDimens.screenPadding,
                    end = PsmfDimens.screenPadding,
                    top = PsmfDimens.sectionSpacing,
                    bottom = PsmfDimens.labelGap,
                ).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FollowNothingYet() {
    Text(
        text = stringResource(Res.string.teams_followed_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PsmfDimens.screenPadding),
    )
}

@Composable
private fun Empty(directory: TeamDirectory) {
    Text(
        text = stringResource(Res.string.teams_no_matches, directory.query),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(PsmfDimens.screenPadding),
    )
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Failure(detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        Text(
            text = stringResource(Res.string.fixtures_error_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
