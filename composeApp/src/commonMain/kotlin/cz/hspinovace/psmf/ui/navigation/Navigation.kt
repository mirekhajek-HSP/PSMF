package cz.hspinovace.psmf.ui.navigation

import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.usecase.ResumePoint
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
     * The report tab's root: the reports on the device.
     *
     * Every report opened in the tab sits on top of it, so back from a
     * report's first step comes here. Until 0.4.0 this was "no report
     * open", replaced by the first report opened; after a restart it said
     * so over reports the database still held (prompt 17, Gate 1). With
     * none on the device it still says that, and how to start one.
     */
    data object Reports : Destination {
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

    /**
     * One team's roster, pushed on top of the Týmy tab.
     *
     * The only destination in the app that is a genuine drill-down, which
     * is why re-selecting its tab pops back to the directory: unlike the
     * report, there is nothing in progress here to throw away.
     */
    data class TeamRoster(
        val teamId: TeamId,
    ) : Destination {
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

    /**
     * Whether the title bar offers Zpět.
     *
     * Every step of a report does, including the first. The header was the
     * root of the report tab's stack, and when this said only "is there
     * something below me in the tab" it was the one screen of the wizard
     * with no way back: iOS has no system back button, and the tab bar does
     * not say it means *leave this report* (the tester's point 2). Since
     * 0.4.0 the list of reports is beneath every step, and Zpět on the
     * header goes to it. The roots of the four tabs, that list included,
     * stay without it -- the tab bar is how you leave those, and there is
     * nothing in them that was left.
     */
    val offersBack: Boolean get() = canGoBackWithinTab || current.matchId != null
}

/**
 * The back stacks.
 *
 * A Koin `single`, so it survives an Android configuration change. It
 * deliberately does **not** survive process death: after a kill the app
 * opens on the fixture list, where the report is waiting with its badge,
 * and in the Zápis tab's list; tapping either resumes. That is the
 * recovery path the referee would take anyway, and it means no screen
 * state has to be serialised — the report itself is in the database,
 * which is where recoverability belongs.
 */
class AppNavigator {
    private val _state = MutableStateFlow(NavigationState(tab = Tab.FIXTURES, stacks = ROOTS))

    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /** Whether a report has been opened or adopted in the report tab since launch. */
    private var reportTabUsed = false

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
     * Opens a report at the step the row tapped decided on.
     *
     * Not a push: the report tab holds one report at a time, so entering a
     * *different* one starts its stack afresh rather than piling it on top
     * of the last. Entering the one already open only switches tab — the
     * referee tapped the row to get back to their report, not to be
     * rewound to its resume point.
     *
     * **The stack is the list of reports, then the steps that come before
     * the one opened.** A match under way opens on the console, and its
     * stack used to be the console alone, so back from it left the report
     * instead of going to the lineup; back from the first step comes to the
     * list. Only the last step is ever composed, so the ones beneath it cost
     * nothing but the history that back walks through.
     */
    fun openReport(destination: Destination) {
        require(destination.tab == Tab.REPORT) { "$destination is not part of the report" }
        reportTabUsed = true
        _state.update { state ->
            val stack = state.stacks.getValue(Tab.REPORT)
            val alreadyOpen = stack.any { it.matchId == destination.matchId }
            state.copy(
                tab = Tab.REPORT,
                stacks = state.stacks + (Tab.REPORT to if (alreadyOpen) stack else destination.onTheList()),
            )
        }
    }

    /**
     * Opens a report where tapping its row lands -- the same from Zápasy
     * and from the Zápis tab's list: a match under way on the console, a
     * finished one on the recap, rather than on a header filled in an hour
     * ago.
     */
    fun openReport(
        matchId: MatchId,
        resumePoint: ResumePoint,
    ) {
        openReport(
            when (resumePoint) {
                ResumePoint.HEADER -> Destination.MatchHeader(matchId)
                ResumePoint.CONSOLE -> Destination.Console(matchId)
                ResumePoint.RECAP -> Destination.Recap(matchId)
            },
        )
    }

    /**
     * Puts a report into the report tab without switching to it.
     *
     * The navigator deliberately does not survive process death, so on the
     * launch after a kill the report tab holds only its list while the
     * database still holds a match in progress -- and the badge, which
     * comes from the database, would then land on the list rather than on
     * the console. A referee who opens Settings mid-half and whose phone
     * dies taps the badge to get back to the clock.
     *
     * **Only once, into a tab nothing has been opened in since launch.** A
     * report the referee is already working on is never displaced, however
     * far into it they are. And a referee who went back from the adopted
     * report to the list stays there: the shell adopts whenever it is
     * composed afresh, which an Android configuration change also does.
     */
    fun adoptReport(destination: Destination) {
        require(destination.tab == Tab.REPORT) { "$destination is not part of the report" }
        if (reportTabUsed) return
        reportTabUsed = true
        _state.update { state -> state.copy(stacks = state.stacks + (Tab.REPORT to destination.onTheList())) }
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

    /**
     * The list of reports, then the report's steps in the order the form is
     * worked, up to and including this one -- or the list and this one
     * alone if it is not a step of a report.
     */
    private fun Destination.onTheList(): List<Destination> {
        val match = matchId ?: return ROOTS.getValue(Tab.REPORT) + this
        val steps =
            listOf(
                Destination.MatchHeader(match),
                Destination.Lineup(match),
                Destination.Console(match),
                Destination.Assessment(match),
                Destination.Recap(match),
                Destination.Export(match),
            )
        val here = steps.indexOf(this)
        return ROOTS.getValue(Tab.REPORT) + if (here < 0) listOf(this) else steps.take(here + 1)
    }

    private companion object {
        val ROOTS: Map<Tab, List<Destination>> =
            mapOf(
                Tab.FIXTURES to listOf(Destination.Fixtures),
                Tab.REPORT to listOf(Destination.Reports),
                Tab.TEAMS to listOf(Destination.Teams),
                Tab.SETTINGS to listOf(Destination.Settings),
            )
    }
}
