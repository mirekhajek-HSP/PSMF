package cz.hspinovace.psmf.ui.fixtures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.hspinovace.psmf.data.seed.SeedException
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.ResumePoint
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import cz.hspinovace.psmf.usecase.resumePoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A report to open, and where in it to land. */
data class OpenMatch(
    val matchId: MatchId,
    val resumePoint: ResumePoint,
)

/**
 * Screen 1's state.
 *
 * **One shape, not Loading / Ready / Failed.** 0.2.0 had three, and a
 * filter change went through Loading: the whole screen, filter included,
 * was swapped for *Načítání dat ligy…* and back. On a phone that was a
 * flash on every tap, and in the team field it threw away the field being
 * typed in, with the keyboard, on every keystroke. Here the list and the
 * filter stay on screen while the next list is built; a wait only shows
 * once it is long enough to be one ([waiting]).
 */
data class FixturesUiState(
    /**
     * What the referee has asked for, as of the last tap or keystroke.
     *
     * The filter is drawn from here, never from [listing]: the listing
     * catches up later, and a text field fed from it would lag behind the
     * keyboard and drop characters.
     */
    val filter: FixtureFilter = FixtureFilter(),
    /** The list on screen. Null only until the first load lands. */
    val listing: FixtureListing? = null,
    /**
     * A load has run longer than [FixturesViewModel.VISIBLE_WAIT], and the
     * screen veils what it has under a spinner. False for every shorter
     * wait, which is all of them after the first on any phone measured.
     */
    val waiting: Boolean = false,
    /**
     * Seed data would not load: the loader's own message.
     *
     * It names the file and the row, and is shown on screen rather than
     * only logged. The data is hand edited, so the person who broke it is
     * the person holding the phone.
     */
    val failure: String? = null,
)

/**
 * Screen 1. A flat list of matches to pick from, and nothing else: no
 * history, no standings, no ongoing-match hero (DEMO_SCOPE screen 1).
 *
 * This is also the screen that first exercises the Compose-resource seed
 * path on a device. Everything below it is tested on the JVM; whether the
 * files are actually *packaged* is only answered by running it.
 *
 * The list is built on [work], off the main thread. A filter change is
 * milliseconds of it, but the first load parses the whole league, and a
 * spinner drawn by the thread doing the parsing does not spin.
 */
class FixturesViewModel(
    private val listFixtures: ListFixtures,
    private val startOrResumeMatch: StartOrResumeMatch,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /**
     * The filter lives here rather than in the screen, so that going into a
     * report and coming back does not silently widen the list again -- the
     * referee who filtered to one team did so because they are officiating
     * that team all evening.
     */
    private val _state = MutableStateFlow(FixturesUiState())
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

    /** The list being built, if one is. A newer change replaces it. */
    private var building: Job? = null

    init {
        load()
        // A report started, or moved on, while the referee was in another
        // tab: rebuild, so its row comes back with its badge -- and, for a
        // report on an earlier day, still listed under "Nadcházející".
        viewModelScope.launch {
            listFixtures.reportsChanged().collect { rebuild(pause = Duration.ZERO) }
        }
    }

    /**
     * Narrow the list, and remember it.
     *
     * The filter changes at once, so the control shows it at once. The list
     * follows: straight away for a choice, and after [TYPING_PAUSE] for a
     * keystroke, so a team name typed at speed builds one list rather than
     * one per letter.
     *
     * The cascade is applied here as well as in the filter's own controls:
     * a group outside the chosen league, or a pitch outside the chosen
     * league and group, never reaches the state.
     */
    fun onFilterChanged(filter: FixtureFilter) {
        val applied =
            _state.value.listing
                ?.options
                ?.normalise(filter) ?: filter
        val typing = applied.teamQuery != _state.value.filter.teamQuery
        _state.update { it.copy(filter = applied) }
        rebuild(pause = if (typing) TYPING_PAUSE else Duration.ZERO)
    }

    /** Build the list again for the current filter. Also the retry after a failure. */
    fun load() {
        rebuild(pause = Duration.ZERO)
    }

    private fun rebuild(pause: Duration) {
        building?.cancel()
        building =
            viewModelScope.launch {
                delay(pause)
                val filter = _state.value.filter
                // Only a wait long enough to notice gets the spinner; one that
                // ends sooner never shows anything at all.
                val veil =
                    launch {
                        delay(VISIBLE_WAIT)
                        _state.update { it.copy(waiting = true) }
                    }
                val built =
                    try {
                        Result.success(withContext(work) { listFixtures(filter) })
                    } catch (e: SeedException) {
                        // Only the loader's own failure is caught. Anything else
                        // is a defect and should not be dressed up as bad data.
                        Result.failure(e)
                    }
                veil.cancel()
                _state.update { current ->
                    current.copy(
                        // A failed rebuild keeps the last list rather than
                        // blanking it; the failure is shown instead of it.
                        listing = built.getOrNull() ?: current.listing,
                        waiting = false,
                        failure = built.exceptionOrNull()?.message,
                    )
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

    companion object {
        /**
         * How long a load runs before the screen admits it is waiting.
         *
         * Measured on 2026-10-08 against all of league 6: a filter change
         * takes 1–6 ms on the emulator and the cold first load 115–126 ms
         * (report `2026-10-08-fixtures-tab-redesign.md`). 300 ms is above
         * both with room for a phone several times slower than the
         * emulator, and below the point where a blank or unchanged screen
         * reads as a tap that did not register. A spinner for anything
         * shorter would itself be the flash this replaced.
         */
        val VISIBLE_WAIT: Duration = 300.milliseconds

        /**
         * How long the team field waits for the next keystroke before the
         * list follows it. Long enough to cover the gap between letters
         * typed at speed; short enough that the list has caught up by the
         * time the referee looks at it.
         */
        val TYPING_PAUSE: Duration = 250.milliseconds
    }
}
