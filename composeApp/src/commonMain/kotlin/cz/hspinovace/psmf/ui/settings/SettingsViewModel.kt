package cz.hspinovace.psmf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.league.LeagueRepository
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
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * The league's own rules, read from the group definition rather than
     * hardcoded — a competition with a different half length is a data
     * change, not a code change.
     */
    val rules: List<Pair<String, String>> = emptyList(),
)

sealed interface SettingsEvent {
    data class ThemeSelected(
        val theme: ThemeChoice,
    ) : SettingsEvent
}

/** Screen 8. Two settings, and the league's rules as information. */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val league: LeagueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val group = league.groups().firstOrNull()?.group
            _state.value =
                SettingsUiState(
                    theme = settings.load().theme,
                    rules =
                        listOfNotNull(
                            group?.let { HALF_LENGTH to "${it.halfLengthMinutes} min" },
                            group?.let { PERIODS to it.periods.toString() },
                            PLAYERS to Lineup.PLAYERS_ON_FIELD.toString(),
                            POWER_PLAY to "${PowerPlay.LENGTH.inWholeMinutes} min",
                        ),
                )
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeSelected -> {
                _state.update { it.copy(theme = event.theme) }
                viewModelScope.launch { settings.setTheme(event.theme) }
            }
        }
    }

    private companion object {
        // Czech labels: these name rules from PSMF's own documents, and the
        // referee reads them beside the report they will produce.
        const val HALF_LENGTH = "Délka poločasu"
        const val PERIODS = "Počet poločasů"
        const val PLAYERS = "Hráčů na hřišti (5+1)"
        const val POWER_PLAY = "Oslabení po vyloučení"
    }
}
