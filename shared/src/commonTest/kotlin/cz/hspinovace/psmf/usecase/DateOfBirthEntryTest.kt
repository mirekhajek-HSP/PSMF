package cz.hspinovace.psmf.usecase

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A date of birth typed at a pitch, in the dark, one-handed.
 *
 * Forgiving about separators because people are, and unforgiving about
 * dates that do not exist because those are typos — and a typo here
 * becomes six digits in the `Číslo RP` column of a report that goes to
 * PSMF.
 */
class DateOfBirthEntryTest {
    private val born = LocalDate(1992, 5, 18)

    @Test
    fun acceptsTheWayCzechDatesAreWritten() {
        assertEquals(born, parseDateOfBirth("18.5.1992"))
        assertEquals(born, parseDateOfBirth("18. 5. 1992"))
        assertEquals(born, parseDateOfBirth("18.05.1992"))
        assertEquals(born, parseDateOfBirth("18/5/1992"))
    }

    @Test
    fun acceptsEightDigitsWithNoSeparatorsBecauseItIsTheFastestToType() {
        assertEquals(born, parseDateOfBirth("18051992"))
        assertEquals(LocalDate(1999, 1, 21), parseDateOfBirth("21011999"))
    }

    @Test
    fun acceptsIsoForAnyonePastingFromElsewhere() {
        assertEquals(born, parseDateOfBirth("1992-05-18"))
    }

    @Test
    fun trimsWhitespaceRatherThanRefusingOverIt() {
        assertEquals(born, parseDateOfBirth("  18.5.1992  "))
    }

    @Test
    fun refusesADateThatDoesNotExist() {
        // 31 February is a typo, not a birthday.
        assertNull(parseDateOfBirth("31.2.1992"))
        assertNull(parseDateOfBirth("31021992"))
        assertNull(parseDateOfBirth("32.1.1992"))
        assertNull(parseDateOfBirth("18.13.1992"))
    }

    @Test
    fun refusesAYearThatIsObviouslyATypo() {
        // A mistyped two-digit year, or a day and a year swapped. Rejecting
        // it is kinder than writing 0018 on a report.
        assertNull(parseDateOfBirth("18.5.92"))
        assertNull(parseDateOfBirth("18.5.0018"))
        assertNull(parseDateOfBirth("1992.5.18"))
    }

    @Test
    fun refusesAnythingThatIsNotADate() {
        assertNull(parseDateOfBirth(""))
        assertNull(parseDateOfBirth("   "))
        assertNull(parseDateOfBirth("nonsense"))
        assertNull(parseDateOfBirth("18.5"))
        assertNull(parseDateOfBirth("18.5.1992.1"))
        assertNull(parseDateOfBirth("180592"))
    }
}
