package cz.hspinovace.psmf.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.no_report_action
import cz.hspinovace.psmf.resources.no_report_body
import cz.hspinovace.psmf.resources.no_report_title
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The report tab with no report on the device.
 *
 * The tab is always present — a match in progress has to be reachable from
 * anywhere — so it needs something to show when there is nothing in it.
 * This says what the tab is for and offers the one route in, rather than
 * being a blank screen a referee has to guess at.
 *
 * Until 0.4.0 it was the tab's root until a report was opened, and said
 * so after every restart over reports the database still held. It is the
 * empty state of [cz.hspinovace.psmf.ui.reports.ReportsScreen] now.
 */
@Composable
fun NoReportScreen(
    onPickFixture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.sectionSpacing, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.no_report_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.no_report_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryAction(
            text = stringResource(Res.string.no_report_action),
            onClick = onPickFixture,
        )
    }
}
