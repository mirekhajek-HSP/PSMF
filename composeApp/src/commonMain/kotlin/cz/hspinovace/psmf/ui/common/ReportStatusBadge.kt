package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.fixtures_status_confirmed
import cz.hspinovace.psmf.resources.fixtures_status_finished
import cz.hspinovace.psmf.resources.fixtures_status_in_progress
import cz.hspinovace.psmf.resources.fixtures_status_setup
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * How far a report has got: *Rozepsáno*, *Probíhá*, *Dohráno*, *Potvrzeno*.
 *
 * One badge for both places that show it, a fixture's row in Zápasy and a
 * report's row in the Zápis tab, so the two cannot come to say a status in
 * different words or look.
 *
 * In Zápasy it has to exist, though the fixture list is not a dashboard: it
 * is how a referee finds a match the app was killed in the middle of.
 */
@Composable
fun ReportStatusBadge(
    status: MatchStatus,
    modifier: Modifier = Modifier,
) {
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
            modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(PsmfDimens.labelGap),
                ).padding(horizontal = PsmfDimens.itemSpacing, vertical = PsmfDimens.labelGap),
    )
}
