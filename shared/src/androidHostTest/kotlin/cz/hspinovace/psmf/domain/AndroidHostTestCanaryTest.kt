package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// THIS FILE'S REAL SUBJECT IS THE BUILD, NOT THE DOMAIN.
//
// The AGP KMP library plugin creates NO Android test compilation unless
// `withHostTestBuilder` is declared in shared/build.gradle.kts. Before it
// was, a test dropped in here was not compiled, not run and not reported —
// it did not fail, it did not pass, it was simply absent. A planted
// `fail()` came back green.
//
// The directory name is the second half of the trap. This compilation's
// source set is **androidHostTest**, not `androidUnitTest`: a file in the
// latter is ignored just as silently, with the builder correctly declared.
//
// To re-prove both in about a minute, drop this next to this file and run
// `./gradlew :shared:allTests`. It must FAIL.
//
//     class PlantedFailureTest {
//         @Test fun thisMustFail() { fail("the compilation is real") }
//     }
//
// Nothing can prove its own absence, so this file cannot detect the
// builder being removed. What it does is give the compilation a reason to
// exist, so that its disappearance from the task list is noticeable.
//
// One consequence worth knowing before writing more tests here: detekt
// 1.23.8 predates this source-set name, so its default "don't apply this
// to test code" excludes — MagicNumber and friends — do not match
// `androidHostTest`. Files here are linted as production code. Hence the
// named constants below, which a commonTest file would not need.

/**
 * Runs the report's date formatting **on the Android target**, where the
 * rest of the shared suite otherwise only runs on the JVM.
 *
 * A thin check, deliberately: it uses `kotlinx.datetime` and `padStart`,
 * both of which have separate implementations per target, and it is the
 * one piece of formatting whose output goes verbatim onto the ZoU.
 */
class AndroidHostTestCanaryTest {
    /** From the worked example in analysis section 2.5: `990121`. */
    private val hlokDateOfBirth = LocalDate(1999, 1, 21)

    /** Chosen so that both the month and the day need a leading zero. */
    private val bornInTheTwoThousands = LocalDate(2005, 4, 3)

    @Test
    fun theRpColumnFallbackFormatsTheSameWayOnAndroid() {
        // Six digits among five-digit RP numbers, which is how the row is
        // recognisable as a date of birth at all.
        assertEquals("990121", hlokDateOfBirth.asWrittenInTheRpColumn())
    }

    @Test
    fun aDateInTheTwoThousandsKeepsItsLeadingZeroes() {
        // The case a naive Int formatter gets wrong, and it would be wrong
        // on the report rather than in an exception.
        assertEquals("050403", bornInTheTwoThousands.asWrittenInTheRpColumn())
    }
}
