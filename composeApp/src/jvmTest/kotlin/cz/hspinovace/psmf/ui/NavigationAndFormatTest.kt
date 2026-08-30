package cz.hspinovace.psmf.ui

import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.ui.format.asClockTime
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test
    fun startsOnTheFixtureList() {
        assertEquals(listOf(Destination.Fixtures), AppNavigator().backStack.value)
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

        navigator.goTo(Destination.MatchHeader(MatchId("m1")))
        assertEquals(2, navigator.backStack.value.size)

        assertTrue(navigator.back())
        assertEquals(listOf(Destination.Fixtures), navigator.backStack.value)
    }

    @Test
    fun tappingTheSameFixtureTwiceDoesNotStackTwoCopies() {
        // Otherwise back would appear not to work: the referee would press
        // it once and stay on the same screen.
        val navigator = AppNavigator()
        val header = Destination.MatchHeader(MatchId("m1"))

        navigator.goTo(header)
        navigator.goTo(header)

        assertEquals(2, navigator.backStack.value.size)
    }

    @Test
    fun adifferentMatchIsADifferentDestination() {
        val navigator = AppNavigator()

        navigator.goTo(Destination.MatchHeader(MatchId("m1")))
        navigator.goTo(Destination.MatchHeader(MatchId("m2")))

        assertEquals(3, navigator.backStack.value.size)
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
