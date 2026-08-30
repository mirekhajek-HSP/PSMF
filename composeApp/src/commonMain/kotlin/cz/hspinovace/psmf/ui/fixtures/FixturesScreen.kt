package cz.hspinovace.psmf.ui.fixtures

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_empty
import cz.hspinovace.psmf.resources.fixtures_error_title
import cz.hspinovace.psmf.resources.fixtures_loading
import cz.hspinovace.psmf.resources.fixtures_retry
import cz.hspinovace.psmf.resources.fixtures_round
import cz.hspinovace.psmf.resources.fixtures_status_confirmed
import cz.hspinovace.psmf.resources.fixtures_status_finished
import cz.hspinovace.psmf.resources.fixtures_status_in_progress
import cz.hspinovace.psmf.resources.fixtures_status_setup
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.FixtureRow
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 1, as a plain state-driven composable: immutable state in,
 * callbacks out, no ViewModel and no Koin. Everything about how it looks
 * can therefore be tested and previewed without a dependency graph.
 */
@Composable
fun FixturesScreen(
    state: FixturesUiState,
    onFixtureSelected: (FixtureId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            FixturesUiState.Loading -> {
                CentredMessage(stringResource(Res.string.fixtures_loading), busy = true)
            }

            is FixturesUiState.Failed -> {
                SeedFailure(state, onRetry)
            }

            is FixturesUiState.Ready -> {
                if (state.listing.isEmpty) {
                    CentredMessage(stringResource(Res.string.fixtures_empty))
                } else {
                    FixtureList(state, onFixtureSelected)
                }
            }
        }
    }
}

@Composable
private fun FixtureList(
    state: FixturesUiState.Ready,
    onFixtureSelected: (FixtureId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PsmfDimens.sectionSpacing),
    ) {
        state.listing.groups.forEach { group ->
            // Only when the shipped data holds more than one group. Adding a
            // group is a data change, so this heading has to appear on its
            // own -- nobody edits a screen to add a league.
            if (state.listing.hasSeveralGroups) {
                item(key = "group-${group.group.id.value}") {
                    ListHeading("${group.group.name} · ${group.season.name}", emphasised = true)
                }
            }
            group.rounds.forEach { round ->
                item(key = "round-${group.group.id.value}-${round.round}") {
                    ListHeading(stringResource(Res.string.fixtures_round, round.round))
                }
                fixtureItems(round.fixtures, onFixtureSelected)
            }
        }
    }
}

private fun LazyListScope.fixtureItems(
    fixtures: List<FixtureRow>,
    onFixtureSelected: (FixtureId) -> Unit,
) {
    items(fixtures.size, key = { fixtures[it].fixture.id.value }) { index ->
        val row = fixtures[index]
        FixtureListItem(row = row, onClick = { onFixtureSelected(row.fixture.id) })
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun ListHeading(
    text: String,
    emphasised: Boolean = false,
) {
    Text(
        text = text,
        style = if (emphasised) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
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
 */
@Composable
private fun FixtureListItem(
    row: FixtureRow,
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

        Text(
            text = row.fixture.venue.value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun CentredMessage(
    text: String,
    busy: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize().padding(PsmfDimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        ) {
            if (busy) CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SeedFailure(
    state: FixturesUiState.Failed,
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
        state.detail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        ActionRow {
            PrimaryAction(text = stringResource(Res.string.fixtures_retry), onClick = onRetry)
        }
    }
}
