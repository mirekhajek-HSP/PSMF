package cz.hspinovace.psmf.ui.navigation

import cz.hspinovace.psmf.domain.MatchId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The four tabs, in the order they appear left to right.
 *
 * The report is one of them rather than the whole app. It shipped as an
 * eight-stop wizard and on a device that reads as one long form: the
 * referee only ever presses back and forward, and Settings and the fixture
 * list are unreachable without abandoning the report.
 */
enum class Tab {
    FIXTURES,
    REPORT,
    TEAMS,
    SETTINGS,
}

/**
 * Where the app can be.
 *
 * Every destination knows which tab's stack it belongs to, which is what
 * lets one `goTo` both push and switch tab — the fixture list can send the
 * referee into the report without knowing that the report is a tab.
 *
 * Still no navigation library. A map of stacks of sealed values is the
 * whole requirement, it is testable on the JVM without a host, and it adds
 * nothing to the version matrix that `docs/BUILD_MATRIX.md` works to keep
 * down to one real constraint.
 */
sealed interface Destination {
    val tab: Tab

    /** The report this destination is part of, where there is one. */
    val matchId: MatchId? get() = null

    data object Fixtures : Destination {
        override val tab: Tab get() = Tab.FIXTURES
    }

    /**
     * The report tab with nothing open in it.
     *
     * Only ever seen before the first report of a session is opened: once
     * one is, it replaces this as the tab's root and re-selecting the tab
     * does not pop back to it. A tab that is always present needs
     * *something* to show, and "there is no report, here is how to start
     * one" is more use than a blank screen.
     */
    data object NoReport : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class MatchHeader(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class Lineup(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class Console(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class Assessment(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class Recap(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data class Export(
        override val matchId: MatchId,
    ) : Destination {
        override val tab: Tab get() = Tab.REPORT
    }

    data object Teams : Destination {
        override val tab: Tab get() = Tab.TEAMS
    }

    data object Settings : Destination {
        override val tab: Tab get() = Tab.SETTINGS
    }
}

/**
 * Which tab is showing, and where each tab has got to.
 *
 * One stack per tab, not one stack with tabs on top of it: switching to
 * Settings mid-report and back has to return to the same step of the
 * report, and a single stack cannot express that without remembering what
 * to throw away.
 */
data class NavigationState(
    val tab: Tab,
    val stacks: Map<Tab, List<Destination>>,
) {
    val current: Destination get() = stacks.getValue(tab).last()

    /** Whether there is a previous screen *within* the current tab. */
    val canGoBackWithinTab: Boolean get() = stacks.getValue(tab).size > 1
}

/**
 * The back stacks.
 *
 * A Koin `single`, so it survives an Android configuration change. It
 * deliberately does **not** survive process death: after a kill the app
 * opens on the fixture list, where the report is waiting with its badge,
 * and tapping it resumes. That is the recovery path the referee would take
 * anyway, and it means no screen state has to be serialised — the report
 * itself is in the database, which is where recoverability belongs.
 */
class AppNavigator {
    private val _state = MutableStateFlow(NavigationState(tab = Tab.FIXTURES, stacks = ROOTS))

    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /**
     * Tapping a tab.
     *
     * Re-selecting the tab already showing pops that tab to its root —
     * except the report. **The report is not a drill-down**; it is one
     * ordered task, and throwing a referee from the console back to the
     * match header because they tapped the tab twice is the opposite of
     * what the tab bar is for. Nothing would be lost — the report is in
     * the database — but they would have to go out to the fixture list and
     * tap the row again to get back to where they were standing.
     */
    fun select(tab: Tab) {
        _state.update { state ->
            when {
                state.tab != tab -> state.copy(tab = tab)
                tab == Tab.REPORT -> state
                else -> state.copy(stacks = state.stacks + (tab to ROOTS.getValue(tab)))
            }
        }
    }

    /** Pushes onto the destination's own tab, and shows that tab. */
    fun goTo(destination: Destination) {
        _state.update { state ->
            val stack = state.stacks.getValue(destination.tab)
            state.copy(
                tab = destination.tab,
                // Tapping the same thing twice must not stack two copies;
                // back would then appear not to work.
                stacks = state.stacks + (destination.tab to stack.pushing(destination)),
            )
        }
    }

    /**
     * Opens a report at the step the fixture list decided on.
     *
     * Not a push: the report tab holds one report at a time, so entering a
     * *different* one starts its stack afresh rather than piling it on top
     * of the last. Entering the one already open only switches tab — the
     * referee tapped the row to get back to their report, not to be
     * rewound to its resume point.
     */
    fun openReport(destination: Destination) {
        require(destination.tab == Tab.REPORT) { "$destination is not part of the report" }
        _state.update { state ->
            val stack = state.stacks.getValue(Tab.REPORT)
            val alreadyOpen = stack.any { it.matchId == destination.matchId }
            state.copy(
                tab = Tab.REPORT,
                stacks = state.stacks + (Tab.REPORT to if (alreadyOpen) stack else listOf(destination)),
            )
        }
    }

    /**
     * Puts a report into the report tab without switching to it.
     *
     * The navigator deliberately does not survive process death, so on the
     * launch after a kill the report tab's root is "no report open" while
     * the database still holds a match in progress -- and the badge, which
     * comes from the database, then points at a tab that says there is
     * nothing there. A referee who opens Settings mid-half and whose phone
     * dies would tap the badge and be told to go and pick a fixture.
     *
     * Only fills an empty tab. A report the referee is already working on
     * is never displaced, however far into it they are.
     */
    fun adoptReport(destination: Destination) {
        require(destination.tab == Tab.REPORT) { "$destination is not part of the report" }
        _state.update { state ->
            if (state.stacks.getValue(Tab.REPORT) != listOf(Destination.NoReport)) {
                state
            } else {
                state.copy(stacks = state.stacks + (Tab.REPORT to listOf(destination)))
            }
        }
    }

    /**
     * System back.
     *
     * Pops the current tab; at a tab root it goes to the fixture list; at
     * the fixture list it returns false so the platform handles it, which
     * on Android means leaving the app. Going to the fixture list does not
     * reset the tab being left — coming back to it returns to the same
     * screen.
     */
    fun back(): Boolean {
        val state = _state.value
        val stack = state.stacks.getValue(state.tab)
        return when {
            stack.size > 1 -> {
                _state.value = state.copy(stacks = state.stacks + (state.tab to stack.dropLast(1)))
                true
            }

            state.tab != Tab.FIXTURES -> {
                _state.value = state.copy(tab = Tab.FIXTURES)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun List<Destination>.pushing(destination: Destination): List<Destination> =
        if (lastOrNull() == destination) this else this + destination

    private companion object {
        val ROOTS: Map<Tab, List<Destination>> =
            mapOf(
                Tab.FIXTURES to listOf(Destination.Fixtures),
                Tab.REPORT to listOf(Destination.NoReport),
                Tab.TEAMS to listOf(Destination.Teams),
                Tab.SETTINGS to listOf(Destination.Settings),
            )
    }
}
