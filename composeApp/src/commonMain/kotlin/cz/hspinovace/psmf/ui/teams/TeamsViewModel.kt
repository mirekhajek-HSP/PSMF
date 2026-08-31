package cz.hspinovace.psmf.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.seed.SeedException
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.usecase.BrowseTeams
import cz.hspinovace.psmf.usecase.TeamDirectory
import cz.hspinovace.psmf.usecase.ToggleFollowedTeam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val directory: TeamDirectory? = null,
    /**
     * The seed loader's own message.
     *
     * Shown on screen rather than only logged: the data is hand-edited, so
     * the person who broke it is the person holding the phone.
     */
    val failure: String? = null,
)

sealed interface TeamsEvent {
    data class QueryChanged(
        val query: String,
    ) : TeamsEvent

    data class FollowToggled(
        val teamId: TeamId,
        val followed: Boolean,
    ) : TeamsEvent
}

/**
 * The Týmy tab: search, follow, and browse by league.
 *
 * **No debounce on the query.** Searching filters seed data the league
 * repository already holds in memory — twelve teams today, and one string
 * comparison per team at league scale. A debounce would add a delay to hide
 * work that is not happening. If this ever reads from a network the delay
 * goes in then, and not before.
 *
 * The query lives here rather than in the screen so that leaving the tab
 * and coming back does not silently discard it — which is the same reason
 * the tab keeps its own back stack.
 */
class TeamsViewModel(
    private val browseTeams: BrowseTeams,
    private val toggleFollowed: ToggleFollowedTeam,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun onEvent(event: TeamsEvent) {
        when (event) {
            is TeamsEvent.QueryChanged -> {
                _state.update { it.copy(query = event.query) }
                reload()
            }

            is TeamsEvent.FollowToggled -> {
                viewModelScope.launch {
                    toggleFollowed(event.teamId, event.followed)
                    reload()
                }
            }
        }
    }

    fun reload() {
        val query = _state.value.query
        viewModelScope.launch {
            val loaded =
                try {
                    Result.success(browseTeams(query))
                } catch (e: SeedException) {
                    // Only the loader's own failure is caught. Anything
                    // else is a defect and should not be dressed up as bad
                    // data.
                    Result.failure(e)
                }

            _state.update { current ->
                // A result for a query the referee has already typed past
                // would put the wrong list on screen. Cannot happen while
                // the data is in memory; it is one line, and it is the kind
                // of thing that stops being true quietly.
                if (current.query != query) {
                    current
                } else {
                    current.copy(
                        loading = false,
                        directory = loaded.getOrNull(),
                        failure = loaded.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }
}
