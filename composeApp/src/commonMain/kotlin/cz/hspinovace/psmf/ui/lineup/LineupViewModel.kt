package cz.hspinovace.psmf.ui.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class LineupUiState(
    val loading: Boolean = true,
    val entry: LineupEntry? = null,
    val selectedSide: TeamSide = TeamSide.HOME,
    /** Open only while the referee is adding someone; null otherwise. */
    val newPlayer: NewPlayerRequest? = null,
    val newPlayerRejected: Boolean = false,
    /** Which team's captain is being asked to confirm, if any. */
    val confirmingSide: TeamSide? = null,
    val confirmedParties: Set<ConfirmingParty> = emptySet(),
    val problems: List<LineupProblem> = emptyList(),
    val showProblems: Boolean = false,
    /**
     * Continue was pressed and nothing was missing. The route turns this
     * into navigation to screen 4 and then clears it.
     */
    val readyToContinue: Boolean = false,
) {
    val selected: TeamLineupEntry? get() = entry?.side(selectedSide)

    fun confirmed(side: TeamSide): Boolean = side.captain() in confirmedParties

    fun problem(kind: LineupProblem): Boolean = showProblems && kind in problems
}

/** Which confirmation belongs to which side of the report. */
fun TeamSide.captain(): ConfirmingParty =
    when (this) {
        TeamSide.HOME -> ConfirmingParty.HOME_CAPTAIN
        TeamSide.AWAY -> ConfirmingParty.AWAY_CAPTAIN
    }

sealed interface LineupEvent {
    data class SideSelected(
        val side: TeamSide,
    ) : LineupEvent

    /**
     * Tapping a player marks them **absent**, not present.
     *
     * The squad is already known and most of it turns up, so the work is
     * three to five taps rather than ten names (analysis section 5.1).
     */
    data class AbsenceToggled(
        val playerId: PlayerId,
    ) : LineupEvent

    data class JerseyNumberChanged(
        val playerId: PlayerId,
        val raw: String,
    ) : LineupEvent

    /** The player did not bring their registration card. */
    data class RegistrationCardToggled(
        val playerId: PlayerId,
    ) : LineupEvent

    data class KitSelected(
        val kitId: KitId,
    ) : LineupEvent

    data object AddPlayerOpened : LineupEvent

    data object AddPlayerDismissed : LineupEvent

    data class NewPlayerEdited(
        val request: NewPlayerRequest,
    ) : LineupEvent

    data object NewPlayerSubmitted : LineupEvent

    data class CaptainConfirmationOpened(
        val side: TeamSide,
    ) : LineupEvent

    data object CaptainConfirmationDismissed : LineupEvent

    data class CaptainConfirmed(
        val side: TeamSide,
        val playerId: PlayerId?,
        val name: String,
        val asDeputy: Boolean,
    ) : LineupEvent

    data object ContinuePressed : LineupEvent
}

/**
 * Screen 3. The squad list, inverted.
 *
 * Every change writes through, so a report on disk keeps up with the
 * screen — except while a block is momentarily invalid; see [SaveLineup].
 */
@OptIn(ExperimentalTime::class)
class LineupViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildLineupEntry: BuildLineupEntry,
    private val saveLineup: SaveLineup,
    private val addPlayerToLineup: AddPlayerToLineup,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(LineupUiState())
    val state: StateFlow<LineupUiState> = _state.asStateFlow()

    private var match: Match? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = matches.load(matchId)
        match = loaded
        val entry = loaded?.let { buildLineupEntry(it) }
        _state.value =
            LineupUiState(
                loading = false,
                entry = entry,
                problems = entry?.problems().orEmpty(),
                confirmedParties =
                    loaded
                        ?.confirmations
                        ?.map { it.party }
                        ?.toSet()
                        .orEmpty(),
            )
    }

    fun onEvent(event: LineupEvent) {
        when (event) {
            is LineupEvent.SideSelected -> {
                _state.update { it.copy(selectedSide = event.side) }
            }

            is LineupEvent.AbsenceToggled -> {
                editTeam { team -> team.withMember(event.playerId) { it.copy(absent = !it.absent) } }
            }

            is LineupEvent.JerseyNumberChanged -> {
                editTeam { team ->
                    team.withMember(event.playerId) {
                        it.copy(jerseyNumber = JerseyNumber.orNull(event.raw.trim().toIntOrNull()))
                    }
                }
            }

            is LineupEvent.RegistrationCardToggled -> {
                editTeam { team ->
                    team.withMember(event.playerId) {
                        it.copy(registrationCardPresent = !it.registrationCardPresent)
                    }
                }
            }

            is LineupEvent.KitSelected -> {
                editTeam { it.copy(kitId = event.kitId) }
            }

            LineupEvent.AddPlayerOpened -> {
                openAddPlayer()
            }

            LineupEvent.AddPlayerDismissed -> {
                _state.update { it.copy(newPlayer = null, newPlayerRejected = false) }
            }

            is LineupEvent.NewPlayerEdited -> {
                _state.update { it.copy(newPlayer = event.request, newPlayerRejected = false) }
            }

            LineupEvent.NewPlayerSubmitted -> {
                submitNewPlayer()
            }

            is LineupEvent.CaptainConfirmationOpened -> {
                _state.update { it.copy(confirmingSide = event.side) }
            }

            LineupEvent.CaptainConfirmationDismissed -> {
                _state.update { it.copy(confirmingSide = null) }
            }

            is LineupEvent.CaptainConfirmed -> {
                confirm(event)
            }

            LineupEvent.ContinuePressed -> {
                commitBothLineups()
            }
        }
    }

    private fun openAddPlayer() {
        val team = _state.value.selected ?: return
        _state.update { it.copy(newPlayer = NewPlayerRequest(teamId = team.team.id), newPlayerRejected = false) }
    }

    private fun submitNewPlayer() {
        val request = _state.value.newPlayer ?: return
        if (request.problems().isNotEmpty()) {
            _state.update { it.copy(newPlayerRejected = true) }
            return
        }
        val current = match ?: return
        viewModelScope.launch {
            // Returns null only if the request was unusable, which the check
            // above has already ruled out -- but the use case is the
            // authority, not the form.
            val updated = addPlayerToLineup(current, request)
            if (updated == null) {
                _state.update { it.copy(newPlayerRejected = true) }
                return@launch
            }
            match = updated
            _state.update { it.copy(newPlayer = null, newPlayerRejected = false) }
            // Rebuild rather than patch, so the new player joins the squad in
            // the same shape as everyone else.
            reload()
        }
    }

    private fun confirm(event: LineupEvent.CaptainConfirmed) {
        val current = match ?: return
        val who = PersonName.orNull(event.name) ?: return
        viewModelScope.launch {
            val confirmed =
                current.confirmedBy(
                    Confirmation(
                        party = event.side.captain(),
                        at = clock.now(),
                        confirmedBy = who,
                        asDeputy = event.asDeputy,
                    ),
                )
            matches.save(confirmed)
            match = confirmed
            _state.update {
                it.copy(
                    confirmingSide = null,
                    confirmedParties = confirmed.confirmations.map { c -> c.party }.toSet(),
                )
            }
        }
    }

    /**
     * Writes both blocks, then lets the route move on.
     *
     * Both, not just the one on screen: a team with nobody absent and every
     * number already correct is never edited, so its block has never been
     * written through.
     */
    private fun commitBothLineups() {
        val entry = _state.value.entry
        val problems = entry?.problems().orEmpty()
        _state.update { it.copy(showProblems = true, problems = problems) }
        if (entry == null || problems.isNotEmpty()) return

        viewModelScope.launch {
            match?.let { match = saveLineup(it, entry) }
            _state.update { it.copy(readyToContinue = true) }
        }
    }

    /** Called by the route once it has acted on [LineupUiState.readyToContinue]. */
    fun continued() {
        _state.update { it.copy(readyToContinue = false) }
    }

    private fun editTeam(change: (TeamLineupEntry) -> TeamLineupEntry) {
        val entry = _state.value.entry ?: return
        val updated = entry.with(change(entry.side(_state.value.selectedSide)))
        _state.update { it.copy(entry = updated, problems = updated.problems()) }

        viewModelScope.launch {
            val current = match ?: return@launch
            match = saveLineup(current, updated.side(_state.value.selectedSide))
        }
    }

    private suspend fun reload() {
        val current = matches.load(matchId) ?: return
        match = current
        val entry = buildLineupEntry(current)
        _state.update { it.copy(entry = entry, problems = entry?.problems().orEmpty()) }
    }
}
