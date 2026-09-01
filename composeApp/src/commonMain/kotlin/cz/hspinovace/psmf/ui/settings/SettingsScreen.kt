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
import cz.hspinovace.psmf.data.settings.AppLanguage
import cz.hspinovace.psmf.data.settings.ThemeChoice
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.settings_export_folder
import cz.hspinovace.psmf.resources.settings_export_folder_change
import cz.hspinovace.psmf.resources.settings_export_folder_choose
import cz.hspinovace.psmf.resources.settings_export_folder_note
import cz.hspinovace.psmf.resources.settings_language
import cz.hspinovace.psmf.resources.settings_language_note
import cz.hspinovace.psmf.resources.settings_rule_half_length
import cz.hspinovace.psmf.resources.settings_rule_periods
import cz.hspinovace.psmf.resources.settings_rule_players
import cz.hspinovace.psmf.resources.settings_rule_power_play
import cz.hspinovace.psmf.resources.settings_rules
import cz.hspinovace.psmf.resources.settings_rules_note
import cz.hspinovace.psmf.resources.settings_theme
import cz.hspinovace.psmf.resources.settings_theme_dark
import cz.hspinovace.psmf.resources.settings_theme_light
import cz.hspinovace.psmf.resources.settings_theme_system
import cz.hspinovace.psmf.ui.common.PrimaryAction
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
    /**
     * The language actually in force, which is not the same as
     * [SettingsUiState.language]: that is null until something is picked,
     * and the device decides in the meantime. The chip has to show what the
     * referee is reading, not what is stored.
     */
    language: AppLanguage,
    onEvent: (SettingsEvent) -> Unit,
    /**
     * Opens the platform folder picker and reports back through
     * [SettingsEvent.ExportFolderChosen] on success. A callback rather
     * than routed through [onEvent] because opening it needs the hosting
     * Activity -- see `ReportSaver` -- which this screen has no business
     * knowing about, the same reasoning `ExportRoute` follows for saving
     * itself.
     */
    onChangeExportFolder: () -> Unit,
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
                // What this note has to say, and the only thing it has to
                // say: the report does not follow the picker.
                note = stringResource(Res.string.settings_language_note),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                    AppLanguage.entries.forEach { choice ->
                        FilterChip(
                            selected = language == choice,
                            onClick = { onEvent(SettingsEvent.LanguageSelected(choice)) },
                            // Each language's own name for itself. A
                            // Ukrainian captain has to find Ukrainian in a
                            // list they cannot otherwise read.
                            label = { Text(choice.autonym) },
                            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                        )
                    }
                }
            }

            Section(
                title = stringResource(Res.string.settings_rules),
                note = stringResource(Res.string.settings_rules_note),
            ) {
                state.rules.forEach { (kind, value) -> ReadOnlyField(kind.label(), value) }
            }

            Section(
                title = stringResource(Res.string.settings_export_folder),
                note = stringResource(Res.string.settings_export_folder_note),
            ) {
                PrimaryAction(
                    text =
                        stringResource(
                            if (state.exportFolderChosen) {
                                Res.string.settings_export_folder_change
                            } else {
                                Res.string.settings_export_folder_choose
                            },
                        ),
                    onClick = onChangeExportFolder,
                )
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

@Composable
private fun RuleKind.label(): String =
    when (this) {
        RuleKind.HALF_LENGTH -> stringResource(Res.string.settings_rule_half_length)
        RuleKind.PERIODS -> stringResource(Res.string.settings_rule_periods)
        RuleKind.PLAYERS -> stringResource(Res.string.settings_rule_players)
        RuleKind.POWER_PLAY -> stringResource(Res.string.settings_rule_power_play)
    }
