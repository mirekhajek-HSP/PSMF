package cz.hspinovace.psmf.ui.teams

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
import cz.hspinovace.psmf.resources.teams_placeholder
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The Týmy tab, empty.
 *
 * Placeholder on purpose: this phase builds the frame, and search, follow
 * and the roster arrive in the next one. It exists so that the tab is real
 * from the moment the bar is — a fourth tab that appears later would
 * change the layout of the other three under the referee.
 */
@Composable
fun TeamsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(PsmfDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.teams_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
