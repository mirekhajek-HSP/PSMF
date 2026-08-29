package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RULE: **the minute is not an integer.**
 *
 * `30´+` (half-time) and `60´+` (after the final whistle, before the
 * captains sign) are valid values on the form — analysis section 2.5, and
 * the worked example contains `30´+ Lepiš A. - nesp. chování`.
 *
 * Every test here fails if someone "simplifies" Minute back to an Int.
 */
class MinuteTest {

    @Test
    fun halfTimeAndAfterFinalWhistleAreRepresentableAtAll() {
        // The rule in one assertion: these two exist and are not numbers.
        val halfTime: Minute = Minute.HalfTime
        val afterWhistle: Minute = Minute.AfterFinalWhistle

        assertEquals("30´+", halfTime.written)
        assertEquals("60´+", afterWhistle.written)
    }

    @Test
    fun aPlayedMinuteIsWrittenWithTheFormsOwnAcuteAccent() {
        // U+00B4, not an ASCII apostrophe. The export must reproduce the
        // character the form uses.
        assertEquals("5´", Minute.Played(5).written)
        assertEquals("´", Minute.MARK)
    }

    @Test
    fun halfTimeSortsAfterMinuteThirtyAndBeforeMinuteThirtyOne() {
        val ordered = listOf(
            Minute.Played(31),
            Minute.HalfTime,
            Minute.Played(30),
        ).sorted()

        assertEquals(listOf(Minute.Played(30), Minute.HalfTime, Minute.Played(31)), ordered)
    }

    @Test
    fun afterTheFinalWhistleSortsLastEvenBeyondAddedTime() {
        // The clock runs continuously and the referee may add time, so a
        // played minute can exceed 60. `60´+` still means "after the whistle".
        val ordered = listOf(
            Minute.AfterFinalWhistle,
            Minute.Played(65),
            Minute.Played(60),
        ).sorted()

        assertEquals(
            listOf(Minute.Played(60), Minute.Played(65), Minute.AfterFinalWhistle),
            ordered,
        )
    }

    @Test
    fun roundTripsThroughTheFormsNotation() {
        listOf(Minute.Played(0), Minute.Played(5), Minute.Played(49), Minute.HalfTime, Minute.AfterFinalWhistle)
            .forEach { minute ->
                assertEquals(minute, Minute.parse(minute.written), "round trip failed for ${minute.written}")
            }
    }

    @Test
    fun parsesTheMinutesFromTheWorkedExample() {
        assertEquals(Minute.Played(20), Minute.parse("20´"))
        assertEquals(Minute.HalfTime, Minute.parse("30´+"))
        assertEquals(Minute.Played(49), Minute.parse("49´"))
        assertEquals(Minute.AfterFinalWhistle, Minute.parse("60´+"))
    }

    @Test
    fun acceptsAPlainNumberAndAnAsciiApostropheWhenReading() {
        // Tolerant on input, exact on output.
        assertEquals(Minute.Played(20), Minute.parse("20"))
        assertEquals(Minute.Played(20), Minute.parse("20'"))
        assertEquals(Minute.Played(20), Minute.parse(" 20´ "))
    }

    @Test
    fun rejectsNotationTheFormDoesNotDefine() {
        // A "+" belongs only to 30 and 60. `45´+` is not a thing here.
        assertNull(Minute.parse("45´+"))
        assertNull(Minute.parse(""))
        assertNull(Minute.parse("abc"))
    }

    @Test
    fun rejectsANegativeMinute() {
        assertFailsWith<IllegalArgumentException> { Minute.Played(-1) }
    }

    @Test
    fun halfTimeIsNotEqualToMinuteThirty() {
        // The mistake this whole type prevents: treating half-time as "30".
        assertTrue(Minute.HalfTime != Minute.Played(30))
        assertTrue(Minute.AfterFinalWhistle != Minute.Played(60))
    }
}
