package cz.hspinovace.psmf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.settings.AppLanguage
import cz.hspinovace.psmf.data.settings.SettingsRepository
import cz.hspinovace.psmf.data.settings.ThemeChoice
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.PowerPlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    /**
     * False until the stored settings have been read.
     *
     * The shell waits for it rather than drawing with the defaults and
     * correcting itself: a first frame in the wrong language, then the
     * right one, reads as a glitch — and the language is the setting most
     * likely to differ from the default, because the whole point of it is
     * that the referee chose something.
     */
    val loaded: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Null means nothing has been picked, so the device decides. */
    val language: AppLanguage? = null,
    /**
     * The league's own rules, read from the group definition rather than
     * hardcoded — a competition with a different half length is a data
     * change, not a code change.
     *
     * The label is [RuleKind] rather than a string: this is a plain
     * `ViewModel`, not a `@Composable`, so it cannot resolve a
     * `stringResource` itself -- the screen does that, the same way it
     * already turns [cz.hspinovace.psmf.data.settings.ThemeChoice] into a
     * label. The value alongside it is not translated at all: "30 min",
     * "2", "6", "10 min" are numbers, not prose.
     */
    val rules: List<Pair<RuleKind, String>> = emptyList(),
    /**
     * Whether a folder has been picked for saved reports to go into.
     *
     * The folder itself is picked and stored by `AndroidReportSaver`, on
     * the far side of `ReportSaver.changeFolder()` -- this ViewModel does
     * not own that choice, only whether Settings should offer "Choose a
     * folder" or "Change the folder". [ExportRoute][cz.hspinovace.psmf.App]
     * and the settings route report back through
     * [SettingsEvent.ExportFolderChosen] once the picker returns.
     */
    val exportFolderChosen: Boolean = false,
)

/** Which league rule a row of the panel names. See [SettingsUiState.rules]. */
enum class RuleKind {
    HALF_LENGTH,
    PERIODS,
    PLAYERS,
    POWER_PLAY,
}

sealed interface SettingsEvent {
    data class ThemeSelected(
        val theme: ThemeChoice,
    ) : SettingsEvent

    data class LanguageSelected(
        val language: AppLanguage,
    ) : SettingsEvent

    /** A folder was picked (or already existed) the last time saving ran. */
    data object ExportFolderChosen : SettingsEvent
}

/** Screen 8. Three settings, and the league's rules as information. */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val league: LeagueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val group = league.groups().firstOrNull()?.group
            val stored = settings.load()
            _state.value =
                SettingsUiState(
                    loaded = true,
                    theme = stored.theme,
                    language = stored.language,
                    rules =
                        listOfNotNull(
                            group?.let { RuleKind.HALF_LENGTH to "${it.halfLengthMinutes} min" },
                            group?.let { RuleKind.PERIODS to it.periods.toString() },
                            RuleKind.PLAYERS to Lineup.PLAYERS_ON_FIELD.toString(),
                            RuleKind.POWER_PLAY to "${PowerPlay.LENGTH.inWholeMinutes} min",
                        ),
                    exportFolderChosen = stored.exportFolderUri != null,
                )
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeSelected -> {
                _state.update { it.copy(theme = event.theme) }
                viewModelScope.launch { settings.setTheme(event.theme) }
            }

            is SettingsEvent.LanguageSelected -> {
                // The screen changes language on the next frame, before the
                // write completes. Correct order: the referee has just
                // pressed the button and a local write cannot fail in a way
                // worth blocking a redraw for.
                _state.update { it.copy(language = event.language) }
                viewModelScope.launch { settings.setLanguage(event.language) }
            }

            SettingsEvent.ExportFolderChosen -> {
                // Already written by AndroidReportSaver by the time this
                // arrives -- this only catches the state up so the button
                // reads "Change" rather than "Choose" from here on.
                _state.update { it.copy(exportFolderChosen = true) }
            }
        }
    }
}
