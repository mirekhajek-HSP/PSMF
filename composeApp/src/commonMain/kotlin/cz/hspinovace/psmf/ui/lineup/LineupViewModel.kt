package cz.hspinovace.psmf.ui.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.isSigned
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.RememberCardNumber
import cz.hspinovace.psmf.usecase.RememberDateOfBirth
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import cz.hspinovace.psmf.usecase.parseCardNumber
import cz.hspinovace.psmf.usecase.parseDateOfBirth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class LineupUiState(
    val loading: Boolean = true,
    /**
     * The report has its final confirmations, and a change to the lineups takes
     * them back (prompt 13, part 4). The lineups save as you edit, so there is
     * no moment to ask; the screen says so before the first edit.
     */
    val signed: Boolean = false,
    val entry: LineupEntry? = null,
    val selectedSide: TeamSide = TeamSide.HOME,
    /** Open only while the referee is adding someone; null otherwise. */
    val newPlayer: NewPlayerRequest? = null,
    val newPlayerRejected: Boolean = false,
    val problems: List<LineupProblem> = emptyList(),
    val showProblems: Boolean = false,
    /**
     * Continue was pressed and nothing was missing. The route turns this
     * into navigation to screen 4 and then clears it.
     */
    val readyToContinue: Boolean = false,
) {
    val selected: TeamLineupEntry? get() = entry?.side(selectedSide)

    fun problem(kind: LineupProblem): Boolean = showProblems && kind in problems

    /** A squad member's name as the report writes it, for naming them in a problem. */
    fun nameOf(
        side: TeamSide,
        playerId: PlayerId,
    ): String =
        entry
            ?.side(side)
            ?.members
            ?.firstOrNull { it.player.id == playerId }
            ?.player
            ?.name
            ?.asWrittenOnReport
            .orEmpty()

    /** A team's own name, for a problem that is about the team and not a player. */
    fun teamName(side: TeamSide): String =
        entry
            ?.side(side)
            ?.team
            ?.name
            .orEmpty()
}

sealed interface LineupEvent {
    data class SideSelected(
        val side: TeamSide,
    ) : LineupEvent

    /**
     * Tapping a player marks them **present**, and tapping again takes it
     * back (DECISIONS 2026-10-17, reversing 2026-08-29).
     *
     * The squad from psmf.cz is everyone who has played this season, and a
     * forgotten tap should leave a name missing -- which the captain sees at
     * once -- not a no-show on the report, where it counts as an appearance.
     */
    data class PresenceToggled(
        val playerId: PlayerId,
    ) : LineupEvent

    data class JerseyNumberChanged(
        val playerId: PlayerId,
        val raw: String,
    ) : LineupEvent

    /** The player did not bring their registration card, or did after all. */
    data class RegistrationCardToggled(
        val playerId: PlayerId,
    ) : LineupEvent

    /**
     * The referee typed into a row's `Číslo RP` field: the number copied from
     * the player's card. The whole field, not a keystroke.
     */
    data class CardNumberTyped(
        val playerId: PlayerId,
        val raw: String,
    ) : LineupEvent

    /**
     * The referee typed into a row's date-of-birth field: with no card, and
     * none on file, a player is fielded by writing it. The whole field.
     */
    data class DateOfBirthTyped(
        val playerId: PlayerId,
        val raw: String,
    ) : LineupEvent

    data class KitSelected(
        val kitId: KitId,
    ) : LineupEvent

    /** The captain of the team on screen, from those present; null clears it. */
    data class CaptainChosen(
        val playerId: PlayerId?,
    ) : LineupEvent

    /** The goalkeeper of the team on screen. The same person may be both. */
    data class GoalkeeperChosen(
        val playerId: PlayerId?,
    ) : LineupEvent

    data object AddPlayerOpened : LineupEvent

    data object AddPlayerDismissed : LineupEvent

    data class NewPlayerEdited(
        val request: NewPlayerRequest,
    ) : LineupEvent

    data object NewPlayerSubmitted : LineupEvent

    /**
     * The marked captain's one tap under the team on screen, before kickoff:
     * "Potvrzuje <name>". Nobody is picked and nothing is typed, and it is
     * **not** the captain's signature under the finished report.
     */
    data object CaptainConfirms : LineupEvent

    data object ContinuePressed : LineupEvent
}

/**
 * Screen 3. The squad list, marked with who came.
 *
 * Every change writes through, so a report on disk keeps up with the
 * screen -- except while a block is not whole: a team is written once it
 * has somebody present, a captain, a goalkeeper and an answer in every
 * row's `Číslo RP`. See [SaveLineup].
 */
@OptIn(ExperimentalTime::class)
class LineupViewModel(
    private val matchId: MatchId,
    private val matches: MatchRepository,
    private val buildLineupEntry: BuildLineupEntry,
    private val saveLineup: SaveLineup,
    private val addPlayerToLineup: AddPlayerToLineup,
    private val rememberDateOfBirth: RememberDateOfBirth,
    private val rememberCardNumber: RememberCardNumber,
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
                signed = loaded?.isSigned == true,
                entry = entry,
                problems = entry?.problems().orEmpty(),
            )
    }

    fun onEvent(event: LineupEvent) {
        if (handleRowEvent(event)) return
        when (event) {
            is LineupEvent.SideSelected -> {
                _state.update { it.copy(selectedSide = event.side) }
            }

            is LineupEvent.KitSelected -> {
                editTeam { it.copy(kitId = event.kitId) }
            }

            is LineupEvent.CaptainChosen -> {
                editTeam { it.withCaptain(event.playerId) }
            }

            is LineupEvent.GoalkeeperChosen -> {
                editTeam { it.copy(goalkeeper = event.playerId) }
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

            LineupEvent.CaptainConfirms -> {
                confirm()
            }

            LineupEvent.ContinuePressed -> {
                commitBothLineups()
            }

            // Handled by handleRowEvent, which already returned.
            else -> {}
        }
    }

    /**
     * The things done to one squad member's row. Split from [onEvent] once the
     * number of event types alone pushed it over detekt's complexity limit,
     * the same split the console made.
     */
    private fun handleRowEvent(event: LineupEvent): Boolean {
        when (event) {
            is LineupEvent.PresenceToggled -> {
                editTeam { team ->
                    val now =
                        team.members.firstOrNull { it.player.id == event.playerId }?.present ?: return@editTeam team
                    team.withPresence(event.playerId, !now)
                }
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

            is LineupEvent.CardNumberTyped -> {
                editTeam { team -> team.withMember(event.playerId) { it.withCardNumberTyped(event.raw) } }
                // Once it is a usable number, remembered on this device so the
                // next match offers it. A pre-fill, and never the player's RP.
                parseCardNumber(event.raw)?.let { number ->
                    viewModelScope.launch { rememberCardNumber(event.playerId, number) }
                }
            }

            is LineupEvent.DateOfBirthTyped -> {
                editTeam { team -> team.withMember(event.playerId) { it.withDateOfBirthTyped(event.raw) } }
                // Once it is a whole, real date, remembered on this device so
                // the next match offers it.
                parseDateOfBirth(event.raw)?.let { date ->
                    viewModelScope.launch { rememberDateOfBirth(event.playerId, date) }
                }
            }

            else -> {
                return false
            }
        }
        return true
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
        val team = _state.value.selected ?: return
        viewModelScope.launch {
            // Returns null only if the request was unusable, which the check
            // above has already ruled out -- but the use case is the
            // authority, not the form.
            val added = addPlayerToLineup(current, team, request)
            if (added == null) {
                _state.update { it.copy(newPlayerRejected = true) }
                return@launch
            }
            match = added.match
            _state.update { it.copy(signed = added.match.isSigned) }
            // The entry the screen holds, with the player in it -- not one
            // rebuilt from the database, which has none of the marks that are
            // not saved yet.
            _state.update { state ->
                val entry = state.entry?.with(added.team)
                state.copy(
                    entry = entry,
                    problems = entry?.problems().orEmpty(),
                    newPlayer = null,
                    newPlayerRejected = false,
                )
            }
        }
    }

    /**
     * The marked captain's tap under this lineup.
     *
     * Records the captain's own name, as the report writes it, and the real
     * time -- late or not. Nothing here touches the match's confirmations:
     * that is the signature under the finished report, and the recap still
     * asks for it (DECISIONS 2026-10-17).
     */
    private fun confirm() {
        val captain = _state.value.selected?.markedCaptain ?: return
        val who = PersonName.orNull(captain.player.name.asWrittenOnReport) ?: return
        editTeam { it.copy(confirmation = LineupConfirmation(at = clock.now(), by = who)) }
    }

    /**
     * Writes both blocks, then lets the route move on.
     *
     * Both, not just the one on screen: a team that was not edited since it
     * was last whole may never have been written through.
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
            // The first change took the signatures back, and the notice goes.
            _state.update { it.copy(signed = match?.isSigned == true) }
        }
    }
}
