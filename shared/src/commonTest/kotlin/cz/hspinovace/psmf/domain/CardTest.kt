package cz.hspinovace.psmf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * RULES covered here, all from analysis sections 2.5 and 2.6:
 *
 * - a red card records **straight vs. second yellow** (`2. ŽK`)
 * - **every** card carries a mandatory free-text reason
 * - a card may be shown to someone with **no jersey number**
 * - "no cards" is an **affirmation**, not an empty list
 */
class CardTest {
    private val reason = CardReason("podražení")

    @Test
    fun aRedCardMustSayWhetherItWasStraightOrASecondYellow() {
        val straight =
            RedCard(
                minute = Minute.Played(40),
                side = TeamSide.HOME,
                subject = CardSubject.Player(Fixtures.houzevAppearance.id),
                reason = CardReason("oplácení, vražení do protihráče v přerušené hře"),
                dismissal = Dismissal.STRAIGHT,
            )
        val secondYellow =
            straight.copy(
                minute = Minute.Played(49),
                reason = CardReason("2. ŽK"),
                dismissal = Dismissal.SECOND_YELLOW,
            )

        // The distinction is not cosmetic: yellows accumulate per group per
        // season and ban on even totals, but two yellows in one match count
        // zero towards that. The two cards must not be interchangeable.
        assertTrue(straight.dismissal != secondYellow.dismissal)
        assertEquals(Dismissal.STRAIGHT, straight.dismissal)
        assertEquals(Dismissal.SECOND_YELLOW, secondYellow.dismissal)
    }

    @Test
    fun everyCardCarriesAReasonAndABlankOneIsRejected() {
        assertFailsWith<IllegalArgumentException> { CardReason("") }
        assertFailsWith<IllegalArgumentException> { CardReason("   ") }
    }

    @Test
    fun theReasonIsRequiredOnYellowCardsToo() {
        // The form asks for "čas, číslo, jméno a důvod" on both blocks, not
        // just on reds. Reason is part of the type, so a card without one
        // cannot be constructed at all.
        val yellow =
            YellowCard(
                minute = Minute.Played(20),
                side = TeamSide.AWAY,
                subject = CardSubject.Player(Fixtures.bacaAppearance.id),
                reason = reason,
            )
        assertEquals("podražení", yellow.reason.text)
    }

    @Test
    fun aCardCanBeShownToSomeoneWithNoJerseyNumber() {
        // From the worked example: `30´+ Lepiš A. - nesp. chování` — a card
        // with a name and no number, shown to a deputy captain. Requiring a
        // lineup appearance would make this unrecordable.
        val card =
            YellowCard(
                minute = Minute.HalfTime,
                side = TeamSide.AWAY,
                subject = CardSubject.NamedPerson(PersonName.of("Lepis A.")),
                reason = CardReason("nesp. chování"),
            )

        val subject = card.subject
        assertTrue(subject is CardSubject.NamedPerson)
        assertEquals("Lepis A.", subject.name.value)
    }

    @Test
    fun noCardsIssuedIsAnAffirmationAndNotAnEmptyList() {
        // The paper form requires the boxes to be struck through, so
        // "none issued" is something the referee actively says.
        val affirmed: CardsSection = CardsSection.NoneIssued
        assertEquals(emptyList(), affirmed.cards())

        // And the ambiguous middle state is unrepresentable: an Issued
        // section cannot be empty.
        assertFailsWith<IllegalArgumentException> { CardsSection.Issued(emptyList()) }
    }

    @Test
    fun anUnaccountedCardBlockIsNotTheSameAsNoCards() {
        val notAccounted = Fixtures.matchInSetup()
        val affirmedNone = notAccounted.copy(cards = CardsSection.NoneIssued)

        // Both have zero cards...
        assertEquals(0, notAccounted.cardEvents.size)
        assertEquals(0, affirmedNone.cardEvents.size)

        // ...but only one of them is a complete report.
        assertTrue(ReportProblem.CardsNotAccountedFor in notAccounted.reportProblems())
        assertTrue(ReportProblem.CardsNotAccountedFor !in affirmedNone.reportProblems())
    }

    @Test
    fun sectionOfChoosesNoneIssuedForAnEmptyList() {
        assertEquals(CardsSection.NoneIssued, CardsSection.of(emptyList()))

        val card = YellowCard(Minute.Played(20), TeamSide.AWAY, CardSubject.Player(Fixtures.bacaAppearance.id), reason)
        assertEquals(CardsSection.Issued(listOf(card)), CardsSection.of(listOf(card)))
    }
}
