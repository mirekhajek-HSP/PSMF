package cz.hspinovace.psmf.ui.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.usecase.AssessmentDraft
import cz.hspinovace.psmf.usecase.SaveAssessment
import cz.hspinovace.psmf.usecase.TeamAssessmentDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssessmentUiState(
    val loading: Boolean = true,
    val draft: AssessmentDraft = AssessmentDraft(),
    val homeTeam: String = "",
    val awayTeam: String = "",
    val readyToContinue: Boolean = false,
) {
    fun teamName(side: TeamSide): String = if (side == TeamSide.HOME) homeTeam else awayTeam
}

sealed interface AssessmentEvent {
    data class TeamEdited(
        val side: TeamSide,
        val draft: TeamAssessmentDraft,
    ) : AssessmentEvent

    data class CommentaryEdited(
        val text: String,
    ) : AssessmentEvent

    data object ContinuePressed : AssessmentEvent
}

/**
 * Screen 5. `NH`, `Čd`, `Č`, `B` and the mandatory commentary.
 *
 * Nothing here blocks: the commentary stays editable until export (A6),
 * and `Č` and `B` may be left unanswered — the readiness check on the
 * export screen is what refuses to send an incomplete report, because
 * that is where refusing is useful.
 */
class AssessmentViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val league: LeagueRepository,
    private val saveAssessment: SaveAssessment,
) : ViewModel() {
    private val _state = MutableStateFlow(AssessmentUiState())
    val state: StateFlow<AssessmentUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = matches.load(matchId) ?: return
        match = loaded
        val fixture = league.fixture(loaded.fixtureId)
        _state.value =
            AssessmentUiState(
                loading = false,
                draft = AssessmentDraft.from(loaded.assessment),
                homeTeam = fixture?.homeTeam?.name.orEmpty(),
                awayTeam = fixture?.awayTeam?.name.orEmpty(),
            )
    }

    fun onEvent(event: AssessmentEvent) {
        when (event) {
            is AssessmentEvent.TeamEdited -> edit { it.with(event.side, event.draft) }
            is AssessmentEvent.CommentaryEdited -> edit { it.copy(commentary = event.text) }
            AssessmentEvent.ContinuePressed -> _state.update { it.copy(readyToContinue = true) }
        }
    }

    fun continued() {
        _state.update { it.copy(readyToContinue = false) }
    }

    private fun edit(change: (AssessmentDraft) -> AssessmentDraft) {
        val updated = change(_state.value.draft)
        _state.update { it.copy(draft = updated) }
        viewModelScope.launch {
            match?.let { match = saveAssessment(it, updated) }
        }
    }
}
