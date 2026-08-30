package cz.hspinovace.psmf.ui.header

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.usecase.HeaderProblem
import cz.hspinovace.psmf.usecase.MatchHeaderEntry
import cz.hspinovace.psmf.usecase.SaveMatchHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The read-only half of the header: everything the fixture already knows. */
data class FixtureHeadline(
    val venueCode: String,
    val date: LocalDate,
    val time: LocalTime,
    val leagueCode: String,
    val groupName: String,
    val homeTeamName: String,
    val awayTeamName: String,
)

data class MatchHeaderUiState(
    val loading: Boolean = true,
    val fixture: FixtureHeadline? = null,
    val entry: MatchHeaderEntry = MatchHeaderEntry(),
    /**
     * The teams of this group, offered as taps.
     *
     * Typing a team name outdoors in the cold is the thing this screen
     * exists to avoid, and the delegating team is almost always in the
     * same group. It is still only an offer — see [otherTeamSelected].
     */
    val teamOptions: List<String> = emptyList(),
    /**
     * True when the referee has said the delegating team is not in the
     * list. Substitute referees write **their own** team, which may be in
     * another group entirely (analysis section 2.5).
     */
    val otherTeamSelected: Boolean = false,
    /**
     * Only shown once the referee has tried to move on.
     *
     * A form that scolds you about a field you have not reached yet is
     * worse than useless in the dark; one that refuses to explain a
     * disabled button is worse still. So: never disabled, and it explains
     * itself when pressed.
     */
    val problems: List<HeaderProblem> = emptyList(),
    /**
     * The referee pressed Continue and nothing was missing.
     *
     * The route turns this into navigation to screen 3 and then clears it;
     * the ViewModel does not know what a screen is.
     */
    val complete: Boolean = false,
) {
    fun problem(kind: HeaderProblem): Boolean = kind in problems
}

sealed interface MatchHeaderEvent {
    data class RefereeNameChanged(
        val value: String,
    ) : MatchHeaderEvent

    data class RefereeLicensedChanged(
        val value: Boolean,
    ) : MatchHeaderEvent

    data class AssistantNameChanged(
        val value: String,
    ) : MatchHeaderEvent

    data class AssistantLicensedChanged(
        val value: Boolean,
    ) : MatchHeaderEvent

    data class DelegatingTeamPicked(
        val value: String,
    ) : MatchHeaderEvent

    data object DelegatingTeamOtherPicked : MatchHeaderEvent

    data class DelegatingTeamTyped(
        val value: String,
    ) : MatchHeaderEvent

    data object ContinuePressed : MatchHeaderEvent
}

/**
 * Screen 2. ZoU page 1: pitch, date, time and group come from the fixture;
 * the referee supplies the officials and the delegating team.
 *
 * Writes through on every change that produces a complete assignment, so
 * the report on disk keeps up with the screen. See [SaveMatchHeader] for
 * what is and is not persisted while the form is half-filled.
 */
class MatchHeaderViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val league: LeagueRepository,
    private val saveMatchHeader: SaveMatchHeader,
) : ViewModel() {
    private val _state = MutableStateFlow(MatchHeaderUiState())
    val state: StateFlow<MatchHeaderUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = matches.load(matchId)
        match = loaded
        val fixture = loaded?.let { league.fixture(it.fixtureId) }
        val entry = MatchHeaderEntry.from(loaded?.officials)
        // Excludes the two playing teams; see LoadedFixture.delegatingTeamOptions.
        val options = fixture?.delegatingTeamOptions().orEmpty()

        _state.value =
            MatchHeaderUiState(
                loading = false,
                fixture =
                    fixture?.let {
                        FixtureHeadline(
                            venueCode = it.fixture.venue.value,
                            date = it.fixture.date,
                            time = it.fixture.time,
                            leagueCode = it.leagueGroup.group.reportCode,
                            groupName = it.leagueGroup.group.name,
                            homeTeamName = it.homeTeam.name,
                            awayTeamName = it.awayTeam.name,
                        )
                    },
                entry = entry,
                teamOptions = options,
                // A team already recorded but absent from the list means the
                // referee typed it last time, so keep the text field open.
                otherTeamSelected = entry.delegatingTeam.isNotBlank() && entry.delegatingTeam !in options,
            )
    }

    fun onEvent(event: MatchHeaderEvent) {
        when (event) {
            is MatchHeaderEvent.RefereeNameChanged -> {
                edit { it.copy(refereeName = event.value) }
            }

            is MatchHeaderEvent.RefereeLicensedChanged -> {
                edit { it.copy(refereeLicensedHire = event.value) }
            }

            is MatchHeaderEvent.AssistantNameChanged -> {
                edit { it.copy(assistantName = event.value) }
            }

            is MatchHeaderEvent.AssistantLicensedChanged -> {
                edit { it.copy(assistantLicensedHire = event.value) }
            }

            is MatchHeaderEvent.DelegatingTeamPicked -> {
                _state.update { it.copy(otherTeamSelected = false) }
                edit { it.copy(delegatingTeam = event.value) }
            }

            MatchHeaderEvent.DelegatingTeamOtherPicked -> {
                _state.update { it.copy(otherTeamSelected = true) }
                edit { it.copy(delegatingTeam = "") }
            }

            is MatchHeaderEvent.DelegatingTeamTyped -> {
                edit { it.copy(delegatingTeam = event.value) }
            }

            MatchHeaderEvent.ContinuePressed -> {
                val problems = _state.value.entry.problems()
                _state.update { it.copy(problems = problems, complete = problems.isEmpty()) }
            }
        }
    }

    /** Called by the route once it has acted on [MatchHeaderUiState.complete]. */
    fun continued() {
        _state.update { it.copy(complete = false) }
    }

    /**
     * Applies an edit, clears any problem the edit resolves, and writes
     * through.
     *
     * Problems are recomputed rather than merely cleared, so a field that
     * was flagged and then fixed stops being flagged as the referee types
     * — but a field they have not touched does not light up on them.
     */
    private fun edit(change: (MatchHeaderEntry) -> MatchHeaderEntry) {
        val updated = change(_state.value.entry)
        _state.update { current ->
            current.copy(
                entry = updated,
                problems = if (current.problems.isEmpty()) emptyList() else updated.problems(),
                complete = false,
            )
        }
        viewModelScope.launch {
            match?.let { match = saveMatchHeader(it, updated) }
        }
    }
}
