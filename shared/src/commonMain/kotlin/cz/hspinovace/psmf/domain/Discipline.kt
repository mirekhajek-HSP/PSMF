package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Yellow-card accumulation and what may be said about it.
//
// THE CONSTRAINT THAT SHAPES THIS WHOLE FILE:
//
//   The app must NEVER claim a player is eligible. It may warn that one
//   might not be. Absence of a warning must not read as clearance.
//
// Fielding an ineligible player is a TECHNICAL FORFEIT under analysis
// section 2.6 -- the result is voided on different terms from an ordinary
// one. If the app displayed "clear" and the player was in fact banned, the
// app caused that outcome.
//
// So everything here is one-sided on purpose. suspensionWarning() returns a
// warning or nothing, and NOTHING IS NOT A CLEARANCE; there is no
// isEligible, no boolean, and no value in this file that a screen could
// render as a green tick. That is a deliberate omission, not an oversight --
// do not add one.
//
// The counts are also always stale. They come from seed data with an asOf
// date, and matches played since then are not in them, which is the second
// reason a positive claim would be unsafe.

/**
 * A player's yellow-card count within one group in one season, as known on
 * a given date (analysis section 2.6).
 *
 * [asOf] is **not optional**. A count without a date cannot be reasoned
 * about: it may be a week old and two matches behind, and the referee needs
 * to see how much to trust it.
 *
 * Red cards are deliberately absent. A red carries immediate suspension
 * until STDK decides, with **no fixed ban**, so there is nothing to compute
 * and computing something would be inventing a number.
 */
@Serializable
data class DisciplinaryRecord(
    val yellowsThisSeason: Int,
    val asOf: LocalDate,
) {
    init {
        require(yellowsThisSeason >= 0) { "A yellow-card count cannot be negative" }
    }
}

/**
 * An advisory that a player **might** be serving a suspension.
 *
 * Carries its [asOf] so the screen can show it. A badge, never a block:
 * the referee decides, and the word "eligible" appears nowhere.
 */
@Serializable
data class SuspensionWarning(
    val yellowsThisSeason: Int,
    val asOf: LocalDate,
)

/**
 * A warning if the accumulated total is even, or null.
 *
 * **Null means "no warning to show", not "cleared to play".** Suspensions
 * trigger on even-numbered totals — 2nd, 4th, 6th, 8th (analysis section
 * 2.6) — so an odd total means the player is between bans, which is not the
 * same as being known to be available.
 */
fun DisciplinaryRecord.suspensionWarning(): SuspensionWarning? =
    if (yellowsThisSeason > 0 && yellowsThisSeason % 2 == 0) {
        SuspensionWarning(yellowsThisSeason, asOf)
    } else {
        null
    }

/**
 * How many yellows one match adds to a player's season total.
 *
 * Two rules from analysis section 2.6 make this not a simple count:
 *
 * - **Two yellows in one match contribute zero.** That player was dismissed
 *   for `2. ŽK` and the dismissal is dealt with in its own right, so the
 *   yellows do not also feed the accumulation.
 * - **A yellow followed by a straight red counts as one yellow.** The red
 *   is a separate matter for STDK; the yellow still accumulates.
 *
 * Red cards never contribute on their own — there is no fixed ban to
 * accumulate towards.
 */
fun List<CardEvent>.yellowsAccumulatedBy(subject: CardSubject): Int {
    val yellows = count { it is YellowCard && it.subject == subject }
    // A third yellow cannot happen: the second is itself a dismissal. The
    // >= is defensive rather than expected.
    return if (yellows >= YELLOWS_THAT_CANCEL_IN_ONE_MATCH) 0 else yellows
}

/** Two yellows in one match are a dismissal, and contribute nothing. */
private const val YELLOWS_THAT_CANCEL_IN_ONE_MATCH = 2

/**
 * The record a player would hold after this match, for showing a referee
 * what a card just did. Still advisory, and still carries the original
 * [DisciplinaryRecord.asOf] — playing a match does not make a stale count
 * fresh.
 */
fun DisciplinaryRecord.after(yellowsInThisMatch: Int): DisciplinaryRecord =
    copy(yellowsThisSeason = yellowsThisSeason + yellowsInThisMatch)
