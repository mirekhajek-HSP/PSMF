package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_back_to_filter
import cz.hspinovace.psmf.resources.fixtures_day_today
import cz.hspinovace.psmf.resources.fixtures_empty
import cz.hspinovace.psmf.resources.fixtures_error_title
import cz.hspinovace.psmf.resources.fixtures_filtered_empty
import cz.hspinovace.psmf.resources.fixtures_loading
import cz.hspinovace.psmf.resources.fixtures_retry
import cz.hspinovace.psmf.resources.fixtures_round
import cz.hspinovace.psmf.resources.fixtures_status_confirmed
import cz.hspinovace.psmf.resources.fixtures_status_finished
import cz.hspinovace.psmf.resources.fixtures_status_in_progress
import cz.hspinovace.psmf.resources.fixtures_status_setup
import cz.hspinovace.psmf.resources.fixtures_summary_team
import cz.hspinovace.psmf.resources.weekday_friday
import cz.hspinovace.psmf.resources.weekday_monday
import cz.hspinovace.psmf.resources.weekday_saturday
import cz.hspinovace.psmf.resources.weekday_sunday
import cz.hspinovace.psmf.resources.weekday_thursday
import cz.hspinovace.psmf.resources.weekday_tuesday
import cz.hspinovace.psmf.resources.weekday_wednesday
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.WaitVeil
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.FixtureRow
import cz.hspinovace.psmf.usecase.SectionHeading
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 1, as a plain state-driven composable: immutable state in,
 * callbacks out, no ViewModel and no Koin. Everything about how it looks
 * can therefore be tested and previewed without a dependency graph.
 *
 * **Nothing here replaces the screen while a list is being built.** The
 * filter and the last list stay put, and a wait long enough to notice
 * ([FixturesUiState.waiting]) veils them under a spinner. Before the first
 * list exists there is nothing to veil, so the first load shows the same
 * spinner over an empty frame.
 */
@Composable
fun FixturesScreen(
    state: FixturesUiState,
    onFixtureSelected: (FixtureId) -> Unit,
    onRetry: () -> Unit,
    onFilterChanged: (FixtureFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        WaitVeil(waiting = state.waiting, description = stringResource(Res.string.fixtures_loading)) {
            val listing = state.listing
            when {
                state.failure != null -> {
                    SeedFailure(state.failure, onRetry)
                }

                listing == null -> {
                    // The first load, not yet landed: an empty frame, which
                    // the veil covers if the wait is long enough to notice.
                }

                else -> {
                    FixtureList(state.filter, listing, onFixtureSelected, onFilterChanged)
                }
            }
        }
    }
}

/**
 * The filter and the list, scrolling as one.
 *
 * # The filter scrolls away (DECISIONS 2026-10-08)
 *
 * It used to sit above the list and stay put, on the argument that a
 * filter scrolled out of sight cannot be cleared. With real league data
 * that left the list no height at all. It is the list's first item now,
 * and once it is out of view two things answer the old argument:
 *
 * - **a button back to it**, bottom right, clear of the tab bar; the list
 *   ends with room under its last row so the button never covers it;
 * - **a slim line at the top saying what the list is narrowed to**, so a
 *   referee deep in the list still knows it is "6. liga K · ZAK" and not
 *   everything.
 *
 * It does not come back on a short scroll up. The filter is four or five
 * fields tall, a third of a phone's list; sliding that over the rows the
 * referee is scrolling back to read would fight the list, and the line and
 * the button already say and do what it would.
 */
@Composable
private fun FixtureList(
    filter: FixtureFilter,
    listing: FixtureListing,
    onFixtureSelected: (FixtureId) -> Unit,
    onFilterChanged: (FixtureFilter) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Item 0 is the filter. Any later first item means it has gone entirely.
    val filterOutOfView by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BUTTON_CLEARANCE),
        ) {
            item(key = FILTER_KEY, contentType = FILTER_KEY) {
                FixtureFilterRow(filter, listing.options, onFilterChanged)
            }
            when {
                listing.filteredToNothing -> {
                    item(key = "filtered-empty") { Message(stringResource(Res.string.fixtures_filtered_empty)) }
                }

                listing.isEmpty -> {
                    item(key = "empty") { Message(stringResource(Res.string.fixtures_empty)) }
                }

                else -> {
                    sections(listing, onFixtureSelected)
                }
            }
        }

        AnimatedVisibility(
            visible = filterOutOfView,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            FilterSummary(filter, listing)
        }

        AnimatedVisibility(
            visible = filterOutOfView,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(PsmfDimens.screenPadding),
        ) {
            FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.fixtures_back_to_filter),
                )
            }
        }
    }
}

private fun LazyListScope.sections(
    listing: FixtureListing,
    onFixtureSelected: (FixtureId) -> Unit,
) {
    listing.sections.forEach { section ->
        val key =
            when (val heading = section.heading) {
                is SectionHeading.Round -> "round-${heading.round}"
                is SectionHeading.Day -> "day-${heading.date}"
            }
        item(key = key, contentType = "heading") {
            ListHeading(section.heading.text(listing.today))
        }
        val rows = section.rows
        items(rows.size, key = { rows[it].fixture.id.value }, contentType = { "fixture" }) { index ->
            val row = rows[index]
            FixtureListItem(row = row, showGroup = listing.byDay, onClick = { onFixtureSelected(row.fixture.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** `6. kolo` for one group; `Dnes · čtvrtek 8. 10.` or `Pátek 9. 10.` for several. */
@Composable
private fun SectionHeading.text(today: LocalDate?): String =
    when (this) {
        is SectionHeading.Round -> {
            stringResource(Res.string.fixtures_round, round)
        }

        is SectionHeading.Day -> {
            val day = "${date.dayOfWeek.label()} ${date.asDayAndMonth()}"
            if (date == today) {
                stringResource(Res.string.fixtures_day_today, day)
            } else {
                day.replaceFirstChar { it.uppercase() }
            }
        }
    }

@Composable
private fun DayOfWeek.label(): String =
    stringResource(
        when (this) {
            DayOfWeek.MONDAY -> Res.string.weekday_monday
            DayOfWeek.TUESDAY -> Res.string.weekday_tuesday
            DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday
            DayOfWeek.THURSDAY -> Res.string.weekday_thursday
            DayOfWeek.FRIDAY -> Res.string.weekday_friday
            DayOfWeek.SATURDAY -> Res.string.weekday_saturday
            DayOfWeek.SUNDAY -> Res.string.weekday_sunday
        },
    )

/**
 * What the list is narrowed to, in one line, for when the filter itself is
 * out of view: the dates always, then whatever else is chosen.
 *
 * Information, not a control -- the button is the way back -- so it can
 * stay one line of text tall.
 */
@Composable
private fun FilterSummary(
    filter: FixtureFilter,
    listing: FixtureListing,
) {
    val level = listing.options.leagueLevels.firstOrNull { it.level == filter.leagueLevel }
    val parts =
        listOfNotNull(
            filter.dates.label(),
            level?.let { it.groups.firstOrNull { group -> group.id == filter.groupId }?.name ?: it.name },
            filter.venue?.value,
            filter.teamQuery.trim().takeIf { it.isNotEmpty() }?.let {
                stringResource(
                    Res.string.fixtures_summary_team,
                    it,
                )
            },
        )
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = SUMMARY_ELEVATION,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PsmfDimens.screenPadding, vertical = SUMMARY_PADDING),
        )
    }
}

@Composable
private fun ListHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.itemSpacing)
                .semantics { heading() },
    )
}

/**
 * One fixture.
 *
 * Laid out as time · teams · pitch because that is the order a referee
 * scans for their own match. The whole row is the target, not a chevron:
 * [PsmfDimens.minTouchTarget] is a floor, and the row grows past it as
 * soon as the system font scale does.
 *
 * [showGroup] when the list holds more than one group: it is then headed
 * by day, and nothing else on screen says which group a row is in.
 */
@Composable
private fun FixtureListItem(
    row: FixtureRow,
    showGroup: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = PsmfDimens.minTouchTarget)
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.itemSpacing),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = row.fixture.time.asClockTime(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.fixture.date.asDayAndMonth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
        ) {
            // Home above away rather than "A - B" on one line: Ukrainian
            // and Czech team names both run long, and a single line would
            // either truncate a name or force a smaller type size.
            Text(row.homeTeam.name, style = MaterialTheme.typography.bodyLarge)
            Text(row.awayTeam.name, style = MaterialTheme.typography.bodyLarge)
            row.reportStatus?.let { ReportBadge(it) }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (showGroup) {
                Text(
                    text = row.group.reportCode,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = row.fixture.venue.value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * That a report already exists for this fixture.
 *
 * The fixture list is not a dashboard, so this is a property of the row
 * rather than a panel above the list — but it has to exist, because it is
 * the only route back into a match the app was killed in the middle of.
 */
@Composable
private fun ReportBadge(status: MatchStatus) {
    val label =
        when (status) {
            MatchStatus.SETUP -> stringResource(Res.string.fixtures_status_setup)
            MatchStatus.IN_PROGRESS -> stringResource(Res.string.fixtures_status_in_progress)
            MatchStatus.FINISHED -> stringResource(Res.string.fixtures_status_finished)
            MatchStatus.CONFIRMED -> stringResource(Res.string.fixtures_status_confirmed)
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(PsmfDimens.labelGap),
                ).padding(horizontal = PsmfDimens.itemSpacing, vertical = PsmfDimens.labelGap),
    )
}

/** A sentence in place of the rows, under the filter, which stays reachable above it. */
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.sectionSpacing),
    )
}

@Composable
private fun SeedFailure(
    detail: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.fixtures_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        // The loader's own message, verbatim. It names the file and the row,
        // and seed data is hand-edited -- whoever broke it is holding the
        // phone, so hiding this behind a log would help nobody.
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        ActionRow {
            PrimaryAction(text = stringResource(Res.string.fixtures_retry), onClick = onRetry)
        }
    }
}

private const val FILTER_KEY = "filter"

/**
 * Room under the last row for the button back to the filter: its 56dp and
 * the margin either side. Scrolled to the end, the last row sits above it.
 */
private val BUTTON_CLEARANCE = 56.dp + PsmfDimens.screenPadding * 2

private val SUMMARY_PADDING = 8.dp
private val SUMMARY_ELEVATION = 2.dp
