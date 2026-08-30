package cz.hspinovace.psmf.ui.fixtures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.seed.SeedException
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.ResumePoint
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import cz.hspinovace.psmf.usecase.resumePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A report to open, and where in it to land. */
data class OpenMatch(
    val matchId: MatchId,
    val resumePoint: ResumePoint,
)

sealed interface FixturesUiState {
    data object Loading : FixturesUiState

    data class Ready(
        val listing: FixtureListing,
    ) : FixturesUiState

    /**
     * Seed data would not load.
     *
     * [detail] is the loader's own message — it names the file and the row
     * — and is shown on screen rather than only logged. The data is hand
     * edited, so the person who broke it is the person holding the phone.
     */
    data class Failed(
        val detail: String?,
    ) : FixturesUiState
}

/**
 * Screen 1. A flat list of matches to pick from, and nothing else: no
 * history, no standings, no ongoing-match hero (DEMO_SCOPE screen 1).
 *
 * This is also the screen that first exercises the Compose-resource seed
 * path on a device. Everything below it is tested on the JVM; whether the
 * files are actually *packaged* is only answered by running it.
 */
class FixturesViewModel(
    private val listFixtures: ListFixtures,
    private val startOrResumeMatch: StartOrResumeMatch,
) : ViewModel() {
    private val _state = MutableStateFlow<FixturesUiState>(FixturesUiState.Loading)
    val state: StateFlow<FixturesUiState> = _state.asStateFlow()

    /**
     * Set when a report is ready to be opened, with **how far it has got**
     * — a match in progress resumes at the console, not at the header.
     *
     * The route navigates and calls [matchOpened]; the ViewModel does not
     * know what a screen is.
     */
    private val _openMatch = MutableStateFlow<OpenMatch?>(null)
    val openMatch: StateFlow<OpenMatch?> = _openMatch.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FixturesUiState.Loading
        viewModelScope.launch {
            _state.value =
                try {
                    FixturesUiState.Ready(listFixtures())
                } catch (e: SeedException) {
                    // Only the loader's own failure is caught. Anything else
                    // is a defect and should not be dressed up as bad data.
                    FixturesUiState.Failed(e.message)
                }
        }
    }

    /**
     * Start a report, or pick up the one already under way.
     *
     * One tap does both. A referee whose phone died mid-match taps the
     * same row they tapped the first time.
     */
    fun onFixtureSelected(fixtureId: FixtureId) {
        viewModelScope.launch {
            startOrResumeMatch(fixtureId)?.let {
                _openMatch.value = OpenMatch(it.id, it.status.resumePoint())
            }
        }
    }

    fun matchOpened() {
        _openMatch.value = null
    }
}
