package cz.hspinovace.psmf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.language_self_name
import cz.hspinovace.psmf.resources.scaffold_notice
import cz.hspinovace.psmf.resources.scaffold_title
import org.jetbrains.compose.resources.stringResource

/**
 * Scaffold placeholder.
 *
 * Deliberately none of the six demo screens: this exists so that Gate 1
 * can show the app launching and rendering localised text on a device.
 * It is replaced by the fixture list in a later session.
 *
 * [language_self_name] carries each language written in its own script, so
 * a screenshot taken with the device set to Ukrainian is what proves the
 * font actually has Cyrillic glyphs.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.scaffold_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.scaffold_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.language_self_name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
