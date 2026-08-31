package cz.hspinovace.psmf.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.usecase.LoadTeamRoster
import cz.hspinovace.psmf.usecase.SetDefaultJerseyNumber
import cz.hspinovace.psmf.usecase.TeamRoster
import cz.hspinovace.psmf.usecase.ToggleFollowedTeam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamRosterUiState(
    val loading: Boolean = true,
    val roster: TeamRoster? = null,
    /**
     * What is in a number field when it does **not** hold a saved number.
     *
     * The referee halfway through deleting `27` to type `7` has an empty
     * field, and an empty field is not a jersey number. Rather than
     * silently dropping the keystroke — which leaves the old number on
     * screen and the referee unsure whether it took — the text stays here
     * and the field marks itself in error until it parses.
     *
     * The consequence that matters: **nothing unparseable is ever saved**,
     * and nothing is ever saved without the field showing it.
     */
    val unsaved: Map<PlayerId, String> = emptyMap(),
) {
    /** What to draw in a player's number field. */
    fun fieldText(
        playerId: PlayerId,
        saved: JerseyNumber?,
    ): String = unsaved[playerId] ?: saved?.value?.toString() ?: ""

    fun isInError(playerId: PlayerId): Boolean = playerId in unsaved
}

sealed interface TeamRosterEvent {
    /** Raw text, because that is what a keystroke is. */
    data class JerseyNumberChanged(
        val playerId: PlayerId,
        val text: String,
    ) : TeamRosterEvent

    /** Drop the correction and go back to what the league says. */
    data class CorrectionCleared(
        val playerId: PlayerId,
    ) : TeamRosterEvent

    data class FollowToggled(
        val followed: Boolean,
    ) : TeamRosterEvent
}

/**
 * One team's roster.
 *
 * **The only editable thing here is a default jersey number.** Names, RP
 * numbers and card history are league records; a referee editing a
 * registered player is a data-integrity failure. And absence is not here at
 * all — it is a fact about one match, and it belongs on the lineup screen
 * where the referee is standing next to the captain.
 *
 * Saved on every valid keystroke rather than behind a save button. There is
 * nothing to lose by writing early — the number is a pre-fill, not a report
 * — and a referee who taps away from a half-finished field should not have
 * to wonder.
 */
class TeamRosterViewModel(
    private val teamId: TeamId,
    private val loadRoster: LoadTeamRoster,
    private val setDefaultJerseyNumber: SetDefaultJerseyNumber,
    private val toggleFollowed: ToggleFollowedTeam,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamRosterUiState())
    val state: StateFlow<TeamRosterUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun onEvent(event: TeamRosterEvent) {
        when (event) {
            is TeamRosterEvent.JerseyNumberChanged -> {
                onJerseyNumber(event.playerId, event.text)
            }

            is TeamRosterEvent.CorrectionCleared -> {
                _state.update { it.copy(unsaved = it.unsaved - event.playerId) }
                viewModelScope.launch {
                    setDefaultJerseyNumber(event.playerId, null)
                    reload()
                }
            }

            is TeamRosterEvent.FollowToggled -> {
                viewModelScope.launch {
                    toggleFollowed(teamId, event.followed)
                    reload()
                }
            }
        }
    }

    private fun onJerseyNumber(
        playerId: PlayerId,
        text: String,
    ) {
        // Digits only, and no more than two of them. The keyboard is
        // numeric, but a paste is not, and `JerseyNumber` throws rather
        // than truncating.
        val cleaned = text.filter { it.isDigit() }.take(JERSEY_DIGITS)
        val number = JerseyNumber.orNull(cleaned.toIntOrNull())

        if (number == null) {
            _state.update { it.copy(unsaved = it.unsaved + (playerId to cleaned)) }
            return
        }

        _state.update { it.copy(unsaved = it.unsaved - playerId) }
        viewModelScope.launch {
            setDefaultJerseyNumber(playerId, number)
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val roster = loadRoster(teamId)
            _state.update { it.copy(loading = false, roster = roster) }
        }
    }

    private companion object {
        /** `JerseyNumber.RANGE` is 0..99. */
        const val JERSEY_DIGITS = 2
    }
}
