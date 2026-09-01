@file:OptIn(ExperimentalLayoutApi::class)

package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_filter_any
import cz.hspinovace.psmf.resources.fixtures_filter_clear
import cz.hspinovace.psmf.resources.fixtures_filter_group
import cz.hspinovace.psmf.resources.fixtures_filter_league
import cz.hspinovace.psmf.resources.fixtures_filter_team
import cz.hspinovace.psmf.resources.header_pitch
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.LeagueLevelOption
import org.jetbrains.compose.resources.stringResource

/**
 * Narrow the fixture list by league and group, by pitch, and by team name.
 *
 * # Sized to the real competition, not the bundled twelve teams
 *
 * The real competition (analysis 2.2) is eight league levels split into
 * up to sixty groups, roughly thirty-five pitches, and on the order of
 * nine hundred teams. Three different shapes need three different
 * controls:
 *
 * - **League and group** cascade. Row one is every level the loaded data
 *   holds -- one today, up to eight at full scale -- and row two is that
 *   level's group letters, appearing only once a level is picked. Neither
 *   row hardcodes a count or a letter range: both are built from
 *   [FilterOptions.leagueLevels][cz.hspinovace.psmf.usecase.FilterOptions],
 *   so a sixty-first group file changes what is drawn and nothing else.
 *   Picking a level alone is a complete filter -- the whole league, no
 *   group required -- and picking a different level always drops whatever
 *   letter was chosen under the old one.
 * - **Pitch** is a flat, wrapping row of chips. There is no cascade to a
 *   pitch, and thirty-five of them still wrap onto a couple of lines
 *   rather than needing a menu.
 * - **Team** is a text field, not a picker: nine hundred rows is not a
 *   list a chip row or a dropdown survives. Matched the way the Týmy tab's
 *   own search is, diacritics folded. The followed-team shortcut this
 *   screen used to offer now lives entirely in the Týmy tab.
 *
 * Chips throughout, not dropdowns: a menu costs an extra tap, hides which
 * option is currently active, and gives a smaller target than a chip sized
 * for a cold thumb.
 */
@Composable
fun FixtureFilterRow(
    listing: FixtureListing,
    onFilterChanged: (FixtureFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listing.options
    val filter = listing.filter

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.labelGap),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        LeagueLevelRow(options.leagueLevels, filter, onFilterChanged)

        val selectedLevel = options.leagueLevels.firstOrNull { it.level == filter.leagueLevel }
        if (selectedLevel != null) {
            GroupLetterRow(selectedLevel, filter, onFilterChanged)
        }

        if (options.venues.isNotEmpty()) {
            VenueRow(options.venues, filter, onFilterChanged)
        }

        OutlinedTextField(
            value = filter.teamQuery,
            onValueChange = { onFilterChanged(filter.copy(teamQuery = it)) },
            label = { Text(stringResource(Res.string.fixtures_filter_team)) },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PsmfDimens.minTouchTarget),
        )

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

/** Row one: every league level the loaded data holds. Always shown. */
@Composable
private fun LeagueLevelRow(
    levels: List<LeagueLevelOption>,
    filter: FixtureFilter,
    onFilterChanged: (FixtureFilter) -> Unit,
) {
    ChipRow(stringResource(Res.string.fixtures_filter_league)) {
        Chip(
            label = stringResource(Res.string.fixtures_filter_any),
            selected = filter.leagueLevel == null,
            onClick = { onFilterChanged(filter.copy(leagueLevel = null, groupId = null)) },
        )
        levels.forEach { option ->
            Chip(
                label = option.level.toString(),
                selected = filter.leagueLevel == option.level,
                // A group id from "6. liga" makes no sense once the level
                // reads "7" -- changing the league always clears the group.
                onClick = { onFilterChanged(filter.copy(leagueLevel = option.level, groupId = null)) },
            )
        }
    }
}

/** Row two: the chosen level's group letters. Only once a level is picked. */
@Composable
private fun GroupLetterRow(
    level: LeagueLevelOption,
    filter: FixtureFilter,
    onFilterChanged: (FixtureFilter) -> Unit,
) {
    ChipRow(stringResource(Res.string.fixtures_filter_group)) {
        Chip(
            label = stringResource(Res.string.fixtures_filter_any),
            selected = filter.groupId == null,
            // The level stays chosen -- only the letter clears. A league
            // alone is a valid filter, not a step back to "every league".
            onClick = { onFilterChanged(filter.copy(groupId = null)) },
        )
        level.groups.forEach { group ->
            Chip(
                label = group.groupLetter,
                selected = filter.groupId == group.id,
                onClick = { onFilterChanged(filter.copy(leagueLevel = level.level, groupId = group.id)) },
            )
        }
    }
}

/** The pitch row: flat and wrapping, sized for roughly thirty-five codes. */
@Composable
private fun VenueRow(
    venues: List<Venue>,
    filter: FixtureFilter,
    onFilterChanged: (FixtureFilter) -> Unit,
) {
    ChipRow(stringResource(Res.string.header_pitch)) {
        Chip(
            label = stringResource(Res.string.fixtures_filter_any),
            selected = filter.venue == null,
            onClick = { onFilterChanged(filter.copy(venue = null)) },
        )
        venues.forEach { venue ->
            Chip(
                label = venue.code.value,
                selected = filter.venue == venue.code,
                onClick = { onFilterChanged(filter.copy(venue = venue.code)) },
            )
        }
    }
}

/** A labelled, wrapping row of chips -- the shape all three filter rows share. */
@Composable
private fun ChipRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) { content() }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
    )
}
