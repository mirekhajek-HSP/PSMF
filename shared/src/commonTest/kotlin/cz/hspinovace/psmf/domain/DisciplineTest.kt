package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * RULE: **yellow cards accumulate per group per season and trigger a
 * suspension on even totals** — 2nd, 4th, 6th, 8th (analysis section 2.6).
 *
 * Two counting rules make this not a simple tally, and both are tested
 * here by name because both are easy to get wrong.
 */
class YellowCardAccumulationTest {
    private val baca = CardSubject.Player(Fixtures.bacaAppearance.id)
    private val houzev = CardSubject.Player(Fixtures.houzevAppearance.id)

    private fun yellow(minute: Int) = YellowCard(Minute.Played(minute), TeamSide.AWAY, baca, CardReason("podražení"))

    private fun red(
        minute: Int,
        dismissal: Dismissal,
    ) = RedCard(Minute.Played(minute), TeamSide.AWAY, baca, CardReason("oplácení"), dismissal)

    @Test
    fun oneYellowInAMatchAddsOne() {
        assertEquals(1, listOf(yellow(20)).yellowsAccumulatedBy(baca))
    }

    @Test
    fun twoYellowsInOneMatchAddZero() {
        // THE AWKWARD CASE. That player was dismissed for `2. ŽK`, and the
        // dismissal is dealt with in its own right -- the two yellows do not
        // also feed the season accumulation.
        val cards = listOf(yellow(20), yellow(49), red(49, Dismissal.SECOND_YELLOW))
        assertEquals(0, cards.yellowsAccumulatedBy(baca))
    }

    @Test
    fun aYellowFollowedByAStraightRedAddsOne() {
        // THE OTHER AWKWARD CASE. The red is a separate matter for STDK;
        // the yellow still accumulates. Contrast with the test above, where
        // the same number of cards adds nothing.
        val cards = listOf(yellow(20), red(40, Dismissal.STRAIGHT))
        assertEquals(1, cards.yellowsAccumulatedBy(baca))
    }

    @Test
    fun aStraightRedOnItsOwnAddsNothing() {
        // Red cards are NOT computed: a red carries suspension until STDK
        // decides, with no fixed ban, so there is no number to accumulate.
        assertEquals(0, listOf(red(40, Dismissal.STRAIGHT)).yellowsAccumulatedBy(baca))
    }

    @Test
    fun cardsShownToOtherPeopleDoNotCount() {
        val cards =
            listOf(
                yellow(20),
                YellowCard(Minute.Played(25), TeamSide.HOME, houzev, CardReason("zdržování")),
                YellowCard(
                    Minute.HalfTime,
                    TeamSide.AWAY,
                    CardSubject.NamedPerson(PersonName.of("Lepis A.")),
                    CardReason("nesp. chování"),
                ),
            )
        assertEquals(1, cards.yellowsAccumulatedBy(baca))
        assertEquals(1, cards.yellowsAccumulatedBy(houzev))
    }

    @Test
    fun aCardShownToSomeoneWithNoNumberStillAccumulatesAgainstThem() {
        // The deputy captain from the worked example.
        val lepis = CardSubject.NamedPerson(PersonName.of("Lepis A."))
        val cards = listOf(YellowCard(Minute.HalfTime, TeamSide.AWAY, lepis, CardReason("nesp. chování")))
        assertEquals(1, cards.yellowsAccumulatedBy(lepis))
    }
}

/**
 * THE HARD CONSTRAINT, and the reason this whole area stays weak:
 *
 * > **The app must never claim a player is eligible.** It may warn that one
 * > might not be. Absence of a warning must not read as clearance.
 *
 * Fielding an ineligible player is a **technical forfeit** under analysis
 * section 2.6 — the result is voided on different terms from an ordinary
 * one. If the app showed "clear" and the player was banned, the app caused
 * that.
 */
class SuspensionAdvisoryTest {
    private val asOf = LocalDate(2026, 10, 5)

    private fun record(yellows: Int) = DisciplinaryRecord(yellows, asOf)

    @Test
    fun evenTotalsWarn() {
        // 2nd, 4th, 6th, 8th.
        listOf(2, 4, 6, 8).forEach { total ->
            assertNotNull(record(total).suspensionWarning(), "$total yellows should warn")
        }
    }

    @Test
    fun oddTotalsDoNotWarnAndThatIsNotAClearance() {
        // Null means "nothing to show", NOT "cleared to play". The player is
        // between bans, which is a different thing from being known
        // available -- and the count is stale anyway.
        listOf(1, 3, 5, 7).forEach { total ->
            assertNull(record(total).suspensionWarning(), "$total yellows should not warn")
        }
    }

    @Test
    fun aPlayerWithNoYellowsDoesNotWarn() {
        assertNull(record(0).suspensionWarning())
    }

    @Test
    fun theWarningCarriesTheAsOfDateSoTheRefereeCanJudgeIt() {
        // A count without a date cannot be reasoned about: matches played
        // since are not in it. That is why asOf is mandatory on the record
        // and why it is repeated on the warning rather than dropped.
        val warning = record(4).suspensionWarning()
        assertEquals(asOf, warning?.asOf)
        assertEquals(4, warning?.yellowsThisSeason)
    }

    @Test
    fun playingAMatchDoesNotMakeAStaleCountFresh() {
        val afterOneMore = record(3).after(yellowsInThisMatch = 1)
        assertEquals(4, afterOneMore.yellowsThisSeason)
        // The date does not move: we still only know the league's position
        // as of the original date plus what we just saw ourselves.
        assertEquals(asOf, afterOneMore.asOf)
        assertNotNull(afterOneMore.suspensionWarning())
    }

    @Test
    fun twoYellowsInThisMatchDoNotTipAPlayerOverTheEdge() {
        // Ties the two rules together: a player on 3 who collects two
        // yellows today is still on 3, not 5 and not 4.
        val cards =
            listOf(
                YellowCard(
                    Minute.Played(20),
                    TeamSide.AWAY,
                    CardSubject.Player(Fixtures.bacaAppearance.id),
                    CardReason("podražení"),
                ),
                YellowCard(
                    Minute.Played(49),
                    TeamSide.AWAY,
                    CardSubject.Player(Fixtures.bacaAppearance.id),
                    CardReason("zakopnutí míče"),
                ),
            )
        val accumulated = cards.yellowsAccumulatedBy(CardSubject.Player(Fixtures.bacaAppearance.id))
        assertEquals(0, accumulated)
        assertEquals(3, record(3).after(accumulated).yellowsThisSeason)
    }

    @Test
    fun aNegativeCountIsRejected() {
        assertFailsWith<IllegalArgumentException> { DisciplinaryRecord(-1, asOf) }
    }
}
