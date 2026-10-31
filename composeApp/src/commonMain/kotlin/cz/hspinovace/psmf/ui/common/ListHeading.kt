package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import cz.hspinovace.psmf.ui.theme.PsmfDimens

/**
 * The band over a run of rows in a list: a round or a day in Zápasy, what
 * is being worked on and what is signed in the Zápis tab.
 */
@Composable
fun ListHeading(text: String) {
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
