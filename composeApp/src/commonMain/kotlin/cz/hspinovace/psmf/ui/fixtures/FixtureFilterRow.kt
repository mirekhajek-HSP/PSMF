@file:OptIn(ExperimentalMaterial3Api::class)

package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_dates_all
import cz.hspinovace.psmf.resources.fixtures_dates_played
import cz.hspinovace.psmf.resources.fixtures_dates_today
import cz.hspinovace.psmf.resources.fixtures_dates_upcoming
import cz.hspinovace.psmf.resources.fixtures_filter_all_leagues
import cz.hspinovace.psmf.resources.fixtures_filter_all_pitches
import cz.hspinovace.psmf.resources.fixtures_filter_clear
import cz.hspinovace.psmf.resources.fixtures_filter_dates
import cz.hspinovace.psmf.resources.fixtures_filter_group
import cz.hspinovace.psmf.resources.fixtures_filter_league
import cz.hspinovace.psmf.resources.fixtures_filter_team
import cz.hspinovace.psmf.resources.fixtures_filter_whole_league
import cz.hspinovace.psmf.resources.header_pitch
import cz.hspinovace.psmf.ui.common.Choice
import cz.hspinovace.psmf.ui.common.PsmfTextField
import cz.hspinovace.psmf.ui.common.Select
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.DateWindow
import cz.hspinovace.psmf.usecase.FilterOptions
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.LeagueLevelOption
import org.jetbrains.compose.resources.stringResource

/**
 * Narrow the fixture list: league, then group, then pitch, then dates,
 * then team.
 *
 * # Select boxes, not chips (DECISIONS 2026-10-08)
 *
 * This was a wall of chips, argued for at one group and seven pitches: a
 * chip costs one tap where a menu costs two, shows what is chosen without
 * opening, and is a bigger target. Real league 6 is twelve groups and
 * forty-three pitches, and on a phone the chips filled the screen: no
 * match was visible, and the filter could not be scrolled to find one.
 * At that size a menu's second tap is cheap and a screenful of chips is
 * not. What was worth keeping from the argument is kept: **a closed select
 * still says what is chosen**, and every option in an open one is a
 * full-width row at least [PsmfDimens.minTouchTarget] tall.
 *
 * - **League** is always there: every level the data holds, after
 *   "Všechny ligy".
 * - **Group** appears under it once a league is chosen: the whole league
 *   first, then its groups. Choosing a different league clears it.
 * - **Pitch** offers only pitches with a match in the chosen league and
 *   group, by code and name, and clears when a new league or group no
 *   longer plays there ([FilterOptions.normalise]).
 * - **Dates** defaults to upcoming. "Zrušit filtr" returns it there rather
 *   than to every date.
 * - **Team** is a text field, not a select: a hundred and forty-four
 *   teams is a list to search, not to scroll. Matched the way the Týmy
 *   tab's own search is, diacritics folded.
 *
 * Nothing here hardcodes a count, a letter or a pitch: all of it comes from
 * [FilterOptions], so a new group file changes what is offered and nothing
 * else.
 *
 * It is the first item of the list and scrolls away with it. The list has
 * a button back to it once it is out of view, and a line saying what it
 * is set to (`FixturesScreen`).
 */
@Composable
fun FixtureFilterRow(
    filter: FixtureFilter,
    options: FilterOptions,
    onFilterChanged: (FixtureFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every change goes through the cascade, so what is reported is always
    // a filter the options allow -- the ViewModel applies the same rule.
    val change: (FixtureFilter) -> Unit = { onFilterChanged(options.normalise(it)) }
    val level = options.leagueLevels.firstOrNull { it.level == filter.leagueLevel }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
    ) {
        Select(
            label = stringResource(Res.string.fixtures_filter_league),
            choices =
                listOf(Choice<Int?>(null, stringResource(Res.string.fixtures_filter_all_leagues))) +
                    options.leagueLevels.map { Choice(it.level, it.name) },
            selected = filter.leagueLevel,
            // A group from "6. liga" means nothing under another league.
            onSelect = { change(filter.copy(leagueLevel = it, groupId = null)) },
        )

        if (level != null) {
            GroupSelect(level, filter, change)
        }

        Select(
            label = stringResource(Res.string.header_pitch),
            choices =
                listOf(Choice<VenueCode?>(null, stringResource(Res.string.fixtures_filter_all_pitches))) +
                    options.venuesInScope(filter).map { Choice(it.code, it.code.value, it.name) },
            selected = filter.venue,
            onSelect = { change(filter.copy(venue = it)) },
        )

        Select(
            label = stringResource(Res.string.fixtures_filter_dates),
            choices = DateWindow.entries.map { Choice(it, it.label()) },
            selected = filter.dates,
            onSelect = { change(filter.copy(dates = it)) },
        )

        PsmfTextField(
            value = filter.teamQuery,
            onValueChange = { change(filter.copy(teamQuery = it)) },
            label = { Text(stringResource(Res.string.fixtures_filter_team)) },
            // The list narrows as the referee types, so Search has nothing
            // left to do but put the keyboard away.
            imeAction = ImeAction.Search,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PsmfDimens.minTouchTarget),
        )

        if (!filter.isDefault) {
            TextButton(
                // Everything back to how the tab opens, dates included: to
                // upcoming, not to every date.
                onClick = { onFilterChanged(FixtureFilter()) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.fixtures_filter_clear))
            }
        }
    }
}

/** Under the league once one is chosen: the whole league, then its groups. */
@Composable
private fun GroupSelect(
    level: LeagueLevelOption,
    filter: FixtureFilter,
    change: (FixtureFilter) -> Unit,
) {
    Select(
        label = stringResource(Res.string.fixtures_filter_group),
        choices =
            listOf(Choice<GroupId?>(null, stringResource(Res.string.fixtures_filter_whole_league, level.name))) +
                level.groups.map { Choice(it.id, it.name) },
        selected = filter.groupId,
        // The league stays chosen; only the group changes. A league alone
        // is a valid filter, not a step back to every league.
        onSelect = { change(filter.copy(groupId = it)) },
    )
}

/** The dates option's name, in the select and in the line that stands in for the filter. */
@Composable
internal fun DateWindow.label(): String =
    when (this) {
        DateWindow.UPCOMING -> stringResource(Res.string.fixtures_dates_upcoming)
        DateWindow.TODAY -> stringResource(Res.string.fixtures_dates_today)
        DateWindow.PLAYED -> stringResource(Res.string.fixtures_dates_played)
        DateWindow.ALL -> stringResource(Res.string.fixtures_dates_all)
    }
