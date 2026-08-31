package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_filter_any
import cz.hspinovace.psmf.resources.fixtures_filter_clear
import cz.hspinovace.psmf.resources.fixtures_filter_followed
import cz.hspinovace.psmf.resources.fixtures_filter_league
import cz.hspinovace.psmf.resources.fixtures_filter_others
import cz.hspinovace.psmf.resources.fixtures_filter_team
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import org.jetbrains.compose.resources.stringResource

/**
 * Narrow the fixture list by league and by team.
 *
 * # Why the list needs this at all
 *
 * One bundled group of twelve teams fits on a screen. The real competition
 * is nine divisions — on the order of nine hundred teams — and a referee
 * officiates a handful of fixtures out of several thousand. A flat list
 * does not survive that, and the demo has to show the shape of the answer
 * even where the data cannot show the need.
 *
 * # Followed teams first
 *
 * The team menu puts followed teams above everyone else. This is where the
 * Týmy tab's follow button pays for itself: without it, picking a team at
 * league scale is a nine-hundred-item scroll.
 *
 * # The honest limitation
 *
 * A `DropdownMenu` is the right control for twelve teams and the wrong one
 * for nine hundred. At that size this needs a searchable picker — the same
 * search field the Týmy tab already has. Left as a menu because the shape
 * of the decision is what the demo is for, and a picker built against
 * twelve rows would be a guess.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixtureFilterRow(
    listing: FixtureListing,
    onFilterChanged: (FixtureFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listing.options
    val filter = listing.filter

    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.labelGap),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
    ) {
        FilterMenu(
            label = stringResource(Res.string.fixtures_filter_league),
            selection = filter.groupId?.let { options.league(it) }?.name,
        ) { dismiss ->
            AnyItem {
                onFilterChanged(filter.copy(groupId = null))
                dismiss()
            }
            options.leagues.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onFilterChanged(filter.copy(groupId = group.id))
                        dismiss()
                    },
                )
            }
        }

        FilterMenu(
            label = stringResource(Res.string.fixtures_filter_team),
            selection = filter.teamId?.let { options.team(it) }?.name,
        ) { dismiss ->
            AnyItem {
                onFilterChanged(filter.copy(teamId = null))
                dismiss()
            }
            if (options.followedTeams.isNotEmpty()) {
                MenuHeading(stringResource(Res.string.fixtures_filter_followed))
            }
            options.followedTeams.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team.name) },
                    onClick = {
                        onFilterChanged(filter.copy(teamId = team.id))
                        dismiss()
                    },
                )
            }
            if (options.followedTeams.isNotEmpty() && options.otherTeams.isNotEmpty()) {
                HorizontalDivider()
                MenuHeading(stringResource(Res.string.fixtures_filter_others))
            }
            options.otherTeams.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team.name) },
                    onClick = {
                        onFilterChanged(filter.copy(teamId = team.id))
                        dismiss()
                    },
                )
            }
        }

        if (!filter.isEmpty) {
            TextButton(
                onClick = { onFilterChanged(FixtureFilter()) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.fixtures_filter_clear))
            }
        }
    }
}

/**
 * A button that opens a menu, showing what it is currently set to.
 *
 * The label stays visible beside the selection — `Liga: 6. liga K` rather
 * than `6. liga K` — so a filtered list says *why* it is short.
 */
@Composable
private fun FilterMenu(
    label: String,
    selection: String?,
    items: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
        ) {
            Text("$label: ${selection ?: stringResource(Res.string.fixtures_filter_any)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

/** "Vše" — the entry that clears one dimension of the filter. */
@Composable
private fun AnyItem(onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(Res.string.fixtures_filter_any)) }, onClick = onClick)
}

/**
 * A section label inside a menu.
 *
 * Plain text rather than a disabled `DropdownMenuItem`: a greyed row that
 * does nothing when tapped is indistinguishable from a broken one.
 */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.padding(
                horizontal = PsmfDimens.itemSpacing,
                vertical = PsmfDimens.labelGap,
            ),
    )
}
