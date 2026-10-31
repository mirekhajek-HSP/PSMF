package cz.hspinovace.psmf.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.reports_confirmed
import cz.hspinovace.psmf.resources.reports_working
import cz.hspinovace.psmf.ui.common.ListHeading
import cz.hspinovace.psmf.ui.common.ReportStatusBadge
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.shell.NoReportScreen
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ReportListing
import cz.hspinovace.psmf.usecase.ReportRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Zápis tab's first screen: the reports on this device (prompt 17,
 * Gate 1, the tester's point 2).
 *
 * What is still being worked on, then what is signed, each under its
 * heading, in the order [ReportListing] gives. A row is a fixture's row in
 * Zápasy without the pitch, with the group always named -- the list holds
 * reports from any group -- and the same status badge. Tapping it opens
 * the report where tapping the fixture would.
 *
 * With no report on the device it says what the tab said before there was
 * a list. Before the first answer it says nothing at all: *Žádný zápis*
 * for the frame before a list of reports arrives would be untrue.
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onOpenReport: (ReportRow) -> Unit,
    onPickFixture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listing = state.listing
    when {
        listing == null -> {
            Box(modifier = modifier.fillMaxSize())
        }

        listing.isEmpty -> {
            NoReportScreen(onPickFixture = onPickFixture, modifier = modifier)
        }

        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                section(Res.string.reports_working, WORKING_KEY, listing.working, onOpenReport)
                section(Res.string.reports_confirmed, CONFIRMED_KEY, listing.confirmed, onOpenReport)
            }
        }
    }
}

/** A heading and its rows; nothing at all for a group with no report in it. */
private fun LazyListScope.section(
    heading: StringResource,
    key: String,
    rows: List<ReportRow>,
    onOpenReport: (ReportRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = key, contentType = "heading") { ListHeading(stringResource(heading)) }
    items(rows, key = { it.matchId.value }, contentType = { "report" }) { row ->
        ReportListItem(row = row, onClick = { onOpenReport(row) })
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * One report: kickoff · teams and status · group, laid out as a fixture's
 * row in Zápasy is, so the referee reads both lists the same way. The
 * whole row is the target, at least [PsmfDimens.minTouchTarget] tall.
 */
@Composable
private fun ReportListItem(
    row: ReportRow,
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
            Text(row.homeTeam.name, style = MaterialTheme.typography.bodyLarge)
            Text(row.awayTeam.name, style = MaterialTheme.typography.bodyLarge)
            ReportStatusBadge(row.status)
        }

        Text(
            text = row.group.reportCode,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val WORKING_KEY = "working"
private const val CONFIRMED_KEY = "confirmed"
