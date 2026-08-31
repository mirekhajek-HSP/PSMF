package cz.hspinovace.psmf.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.hspinovace.psmf.data.settings.ThemeChoice
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.language_self_name
import cz.hspinovace.psmf.resources.settings_language
import cz.hspinovace.psmf.resources.settings_language_note
import cz.hspinovace.psmf.resources.settings_rules
import cz.hspinovace.psmf.resources.settings_rules_note
import cz.hspinovace.psmf.resources.settings_theme
import cz.hspinovace.psmf.resources.settings_theme_dark
import cz.hspinovace.psmf.resources.settings_theme_light
import cz.hspinovace.psmf.resources.settings_theme_system
import cz.hspinovace.psmf.ui.common.ReadOnlyField
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 8.
 *
 * **The league's rules are information, not settings.** Half length,
 * period count, players per side and the power-play length are set by
 * PSMF; a referee changing one is a defect, so they are shown and cannot
 * be touched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PsmfDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.sectionSpacing),
        ) {
            Section(title = stringResource(Res.string.settings_theme)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                    ThemeChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = state.theme == choice,
                            onClick = { onEvent(SettingsEvent.ThemeSelected(choice)) },
                            label = { Text(choice.label()) },
                            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                        )
                    }
                }
            }

            Section(
                title = stringResource(Res.string.settings_language),
                // Read-only: the app follows the device language. Changing
                // it in-app needs a platform API that minSdk 28 does not
                // have on every version, and the report is Czech either way.
                note = stringResource(Res.string.settings_language_note),
            ) {
                Text(stringResource(Res.string.language_self_name), style = MaterialTheme.typography.bodyLarge)
            }

            Section(
                title = stringResource(Res.string.settings_rules),
                note = stringResource(Res.string.settings_rules_note),
            ) {
                state.rules.forEach { ReadOnlyField(it.first, it.second) }
            }
        }
    }
}

@Composable
private fun ThemeChoice.label(): String =
    when (this) {
        ThemeChoice.SYSTEM -> stringResource(Res.string.settings_theme_system)
        ThemeChoice.LIGHT -> stringResource(Res.string.settings_theme_light)
        ThemeChoice.DARK -> stringResource(Res.string.settings_theme_dark)
    }
