package cz.hspinovace.psmf.ui

import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    private val m1 = MatchId("m1")
    private val m2 = MatchId("m2")

    private fun AppNavigator.stack(tab: Tab) = state.value.stacks.getValue(tab)

    @Test
    fun startsOnTheFixtureList() {
        val navigator = AppNavigator()

        assertEquals(Tab.FIXTURES, navigator.state.value.tab)
        assertEquals(Destination.Fixtures, navigator.state.value.current)
    }

    @Test
    fun backAtTheRootIsTheePlatformsToHandle() {
        // False means "not handled", which on Android leaves the app rather
        // than doing nothing visible.
        assertFalse(AppNavigator().back())
    }

    @Test
    fun goingToAScreenAndBackAgainReturnsToTheList() {
        val navigator = AppNavigator()

        navigator.openReport(Destination.MatchHeader(m1))
        assertEquals(Tab.REPORT, navigator.state.value.tab)

        // The first step of a report is the root of its tab, so back out of
        // it leaves the tab rather than popping within it.
        assertTrue(navigator.back())
        assertEquals(Tab.FIXTURES, navigator.state.value.tab)
        assertEquals(Destination.Fixtures, navigator.state.value.current)
    }

    @Test
    fun tappingTheSameFixtureTwiceDoesNotStackTwoCopies() {
        // Otherwise back would appear not to work: the referee would press
        // it once and stay on the same screen.
        val navigator = AppNavigator()
        val lineup = Destination.Lineup(m1)

        navigator.openReport(Destination.MatchHeader(m1))
        navigator.goTo(lineup)
        navigator.goTo(lineup)

        assertEquals(2, navigator.stack(Tab.REPORT).size)
    }

    @Test
    fun adifferentMatchIsADifferentDestination() {
        val navigator = AppNavigator()

        navigator.openReport(Destination.MatchHeader(m1))
        navigator.goTo(Destination.Lineup(m1))
        // A different report starts the tab afresh: the report tab holds one
        // report, and piling m2 on top of m1 would make back walk into
        // someone else's match.
        navigator.openReport(Destination.MatchHeader(m2))

        assertEquals(listOf(Destination.MatchHeader(m2)), navigator.stack(Tab.REPORT))
    }

    @Test
    fun openingTheReportAlreadyOpenDoesNotRewindIt() {
        // The referee tapped the row to get back to their report, not to be
        // sent back to its first step.
        val navigator = AppNavigator()
        navigator.openReport(Destination.Console(m1))
        navigator.goTo(Destination.Assessment(m1))

        navigator.select(Tab.FIXTURES)
        navigator.openReport(Destination.Console(m1))

        assertEquals(Destination.Assessment(m1), navigator.state.value.current)
    }

    @Test
    fun everyTabKeepsItsOwnStack() {
        val navigator = AppNavigator()

        navigator.openReport(Destination.MatchHeader(m1))
        navigator.goTo(Destination.Lineup(m1))
        navigator.select(Tab.SETTINGS)

        assertEquals(Destination.Settings, navigator.state.value.current)
        assertEquals(2, navigator.stack(Tab.REPORT).size)

        navigator.select(Tab.REPORT)
        assertEquals(Destination.Lineup(m1), navigator.state.value.current)
    }

    @Test
    fun switchingTabsDoesNotResetTheTabBeingLeft() {
        val navigator = AppNavigator()

        navigator.openReport(Destination.Console(m1))
        navigator.goTo(Destination.Assessment(m1))
        navigator.select(Tab.TEAMS)
        navigator.select(Tab.SETTINGS)
        navigator.select(Tab.REPORT)

        assertEquals(Destination.Assessment(m1), navigator.state.value.current)
    }

    @Test
    fun reSelectingATabPopsItToItsRoot() {
        val navigator = AppNavigator()

        navigator.goTo(Destination.Fixtures)
        navigator.select(Tab.SETTINGS)
        navigator.select(Tab.SETTINGS)

        assertEquals(listOf(Destination.Settings), navigator.stack(Tab.SETTINGS))
    }

    @Test
    fun reSelectingTheReportTabLeavesTheReportWhereItIs() {
        // Deliberately not pop-to-root. The report is one ordered task, not
        // a drill-down: throwing a referee from the console back to the
        // match header because they tapped the tab twice is the opposite of
        // what the tab bar is for. Nothing would be lost -- the report is in
        // the database -- but getting back would mean going out to the
        // fixture list and tapping the row again.
        val navigator = AppNavigator()

        navigator.openReport(Destination.Console(m1))
        navigator.goTo(Destination.Assessment(m1))
        navigator.select(Tab.REPORT)

        assertEquals(Destination.Assessment(m1), navigator.state.value.current)
    }

    @Test
    fun backWithinATabPopsOneStep() {
        val navigator = AppNavigator()

        navigator.openReport(Destination.MatchHeader(m1))
        navigator.goTo(Destination.Lineup(m1))
        navigator.goTo(Destination.Console(m1))

        assertTrue(navigator.back())
        assertEquals(Destination.Lineup(m1), navigator.state.value.current)
    }

    @Test
    fun backAtATabRootGoesToTheFixtureList() {
        val navigator = AppNavigator()

        navigator.select(Tab.SETTINGS)

        assertTrue(navigator.back())
        assertEquals(Tab.FIXTURES, navigator.state.value.tab)
        // And leaving a tab did not empty it.
        assertEquals(listOf(Destination.Settings), navigator.stack(Tab.SETTINGS))
    }

    @Test
    fun theReportTabHasSomethingToShowBeforeAnyReportIsOpened() {
        val navigator = AppNavigator()

        navigator.select(Tab.REPORT)

        assertEquals(Destination.NoReport, navigator.state.value.current)
    }

    @Test
    fun openingAReportReplacesTheEmptyStateRatherThanStackingOnIt() {
        // Or back out of the match header would land on "no report open",
        // which is not a screen anyone came from.
        val navigator = AppNavigator()

        navigator.select(Tab.REPORT)
        navigator.openReport(Destination.MatchHeader(m1))

        assertEquals(listOf(Destination.MatchHeader(m1)), navigator.stack(Tab.REPORT))
    }

    @Test
    fun aMatchAlreadyUnderWayIsAdoptedIntoTheEmptyReportTab() {
        // Found on a device, not here: the badge is read from the database
        // and the stacks are not, so after a kill the tab bar said a report
        // was in progress and the tab it badged said "no report open".
        val navigator = AppNavigator()

        navigator.adoptReport(Destination.Console(m1))

        // Adopting does not change tab -- the app still opens on fixtures.
        assertEquals(Tab.FIXTURES, navigator.state.value.tab)
        assertEquals(listOf(Destination.Console(m1)), navigator.stack(Tab.REPORT))
    }

    @Test
    fun adoptingNeverDisplacesAReportTheRefereeIsWorkingOn() {
        val navigator = AppNavigator()
        navigator.openReport(Destination.Console(m1))
        navigator.goTo(Destination.Assessment(m1))

        navigator.adoptReport(Destination.Console(m2))

        assertEquals(
            listOf(Destination.Console(m1), Destination.Assessment(m1)),
            navigator.stack(Tab.REPORT),
        )
    }

    @Test
    fun everyDestinationKnowsWhichTabItBelongsTo() {
        // What lets the fixture list send the referee into the report
        // without knowing that the report is a tab.
        assertEquals(Tab.FIXTURES, Destination.Fixtures.tab)
        assertEquals(Tab.TEAMS, Destination.Teams.tab)
        assertEquals(Tab.SETTINGS, Destination.Settings.tab)
        listOf(
            Destination.NoReport,
            Destination.MatchHeader(m1),
            Destination.Lineup(m1),
            Destination.Console(m1),
            Destination.Assessment(m1),
            Destination.Recap(m1),
            Destination.Export(m1),
        ).forEach { assertEquals(Tab.REPORT, it.tab, "$it") }
    }
}

/**
 * Dates keep their Czech shape whatever the UI language is: the referee is
 * reading a Czech league schedule, and `2/29/24` would match neither the
 * paper in their hand nor the fixture list on psmf.cz.
 */
class FormatsTest {
    @Test
    fun aDateReadsAsTheFixtureListWritesIt() {
        assertEquals("31. 8.", LocalDate(2026, 8, 31).asDayAndMonth())
        assertEquals("1. 9.", LocalDate(2026, 9, 1).asDayAndMonth())
    }

    @Test
    fun theHeaderCarriesTheYearAsWell() {
        assertEquals("31. 8. 2026", LocalDate(2026, 8, 31).asFullDate())
    }

    @Test
    fun minutesKeepTheirLeadingZeroAndHoursDoNotGainOne() {
        // 19:00 in the worked example, and the case a naive formatter gets
        // wrong is the minute rather than the hour.
        assertEquals("19:00", LocalTime(19, 0).asClockTime())
        assertEquals("9:05", LocalTime(9, 5).asClockTime())
        assertEquals("18:30", LocalTime(18, 30).asClockTime())
    }
}
