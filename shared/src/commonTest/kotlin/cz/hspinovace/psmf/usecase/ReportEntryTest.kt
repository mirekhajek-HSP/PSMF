package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val CONFIRMED_AT = Instant.parse("2026-08-31T20:05:00Z")

private fun match() = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)

/**
 * RULE: **`Č` and `B` start unanswered and stay unanswered.**
 *
 * Both feed straight into fines. A default of "yes" would quietly waive
 * one, and nobody would ever see it happen.
 */
class AssessmentDraftTest {
    @Test
    fun theFineBearingRatingsStartUnanswered() {
        val fresh = TeamAssessmentDraft()

        assertNull(fresh.shirtsProperlyNumbered)
        assertNull(fresh.uniformKitColour)
        assertNull(fresh.toDomain().shirtsProperlyNumbered)
        assertFalse(fresh.toDomain().isComplete)
    }

    @Test
    fun answeringBothMakesTheBlockComplete() {
        val answered =
            TeamAssessmentDraft(shirtsProperlyNumbered = true, uniformKitColour = false).toDomain()

        assertTrue(answered.isComplete)
        assertEquals(true, answered.shirtsProperlyNumbered)
        assertEquals(false, answered.uniformKitColour)
    }

    @Test
    fun waitingTimeDefaultsToZeroBecauseZeroIsTheNormalCase() {
        // And zero is different from unassessed: a team that was ready on
        // time has a waiting time, and it is nought.
        assertEquals(0, TeamAssessmentDraft().toDomain().waitingTimeMinutes)
        assertEquals(5, TeamAssessmentDraft(waitingTimeMinutes = "5").toDomain().waitingTimeMinutes)
        assertEquals(0, TeamAssessmentDraft(waitingTimeMinutes = "nonsense").toDomain().waitingTimeMinutes)
    }

    @Test
    fun theBestPlayerIsAShirtNumberOrNothing() {
        assertEquals(JerseyNumber(9), TeamAssessmentDraft(bestPlayer = "9").toDomain().bestPlayer)
        assertNull(TeamAssessmentDraft(bestPlayer = "").toDomain().bestPlayer)
        assertNull(TeamAssessmentDraft(bestPlayer = "999").toDomain().bestPlayer)
    }

    @Test
    fun theFormReloadsExactlyAsItWasLeft() {
        val draft =
            AssessmentDraft(
                home = TeamAssessmentDraft("9", "0", true, true),
                away = TeamAssessmentDraft("13", "5", false, true),
                commentary = "Nastřelená tyč.",
            )

        assertEquals(draft, AssessmentDraft.from(draft.toDomain()))
    }

    @Test
    fun theAssessmentIsWrittenThroughAsItIsTyped() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val draft = AssessmentDraft(commentary = "Půl věty…")

            val updated = SaveAssessment(matches)(match(), draft)

            assertEquals("Půl věty…", updated.assessment.commentary)
            assertEquals(updated, matches.load(MatchId("m1")))
        }
}

/**
 * RULE: **an empty list is not "no cards".**
 *
 * The paper form requires the boxes to be struck through, which makes
 * "none" an affirmation. Nothing else in the app can make it: the console
 * only ever adds cards, so without this a clean match could not be sent.
 */
class AffirmNoCardsTest {
    @Test
    fun theRefereeCanStrikeTheBoxesThrough() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val affirmed = AffirmNoCards(matches)(match())

            assertEquals(CardsSection.NoneIssued, affirmed.cards)
            assertEquals(affirmed, matches.load(MatchId("m1")))
        }

    @Test
    fun strikingThroughIsNotAWayToDeleteCardsAlreadyRecorded() =
        runTest {
            val withACard =
                match().copy(
                    cards =
                        CardsSection.Issued(
                            listOf(
                                YellowCard(
                                    Minute.Played(20),
                                    TeamSide.AWAY,
                                    CardSubject.Player(Fixtures.bacaAppearance.id),
                                    CardReason("podražení"),
                                ),
                            ),
                        ),
                )
            val matches = FakeMatchRepository(listOf(withACard))

            val unchanged = AffirmNoCards(matches)(withACard)

            assertEquals(1, unchanged.cardEvents.size)
            assertEquals(0, matches.saves)
        }
}

/**
 * RULE: **one captain per team confirms** — settled 2026-08-30 — **by a tap**,
 * under a name the screen showed (prompt 13, part 4): no typing, no deputy.
 *
 * These are the final confirmations, under the finished report. Re-confirming
 * moves the time rather than adding a second signature.
 */
class ConfirmReportTest {
    private val novak = PersonName.of("Novak")

    @Test
    fun aConfirmationRecordsWhoAndWhen() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val confirmed = ConfirmReport(matches)(match(), ConfirmingParty.HOME_CAPTAIN, novak, CONFIRMED_AT)

            val confirmation = confirmed.confirmations.single()
            assertEquals("Novak", confirmation.confirmedBy.value)
            assertEquals(CONFIRMED_AT, confirmation.at)
            // A deputy is not a thing a tap can say: the captain is the one
            // marked in the lineup.
            assertFalse(confirmation.asDeputy)
            assertEquals(confirmed, matches.load(MatchId("m1")))
        }

    @Test
    fun confirmingAgainReplacesRatherThanAddsASecondSignature() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val confirm = ConfirmReport(matches)
            val first = confirm(match(), ConfirmingParty.HOME_CAPTAIN, novak, CONFIRMED_AT)

            val later = CONFIRMED_AT + kotlin.time.Duration.parse("1h")
            val second = confirm(first, ConfirmingParty.HOME_CAPTAIN, novak, later)

            assertEquals(1, second.confirmations.size)
            assertEquals(later, second.confirmations.single().at)
        }

    @Test
    fun allThreeConfirmationsMarkTheReportConfirmed() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val confirm = ConfirmReport(matches)
            var current = match()

            ConfirmingParty.entries.forEach { party ->
                current = confirm(current, party, novak, CONFIRMED_AT)
            }

            assertEquals(MatchStatus.CONFIRMED, current.status)
        }

    @Test
    fun twoOfThreeIsNotYetConfirmed() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val confirm = ConfirmReport(matches)

            val two =
                confirm(
                    confirm(match(), ConfirmingParty.HOME_CAPTAIN, novak, CONFIRMED_AT),
                    ConfirmingParty.AWAY_CAPTAIN,
                    novak,
                    CONFIRMED_AT,
                )

            assertEquals(MatchStatus.SETUP, two.status)
            assertEquals(2, two.confirmations.size)
        }

    @Test
    fun theLineupsConfirmationAndTheFinalOneAreSeparateRecords() =
        runTest {
            // The captain's confirmation before kickoff lives on the lineup;
            // giving the final one writes only here, and the other way round.
            val withLineupConfirmation =
                match().copy(
                    homeLineup =
                        Fixtures.homeLineup.copy(
                            confirmation = LineupConfirmation(CONFIRMED_AT, novak),
                        ),
                )
            val matches = FakeMatchRepository(listOf(withLineupConfirmation))

            val confirmed =
                ConfirmReport(matches)(withLineupConfirmation, ConfirmingParty.HOME_CAPTAIN, novak, CONFIRMED_AT)

            assertEquals(withLineupConfirmation.homeLineup, confirmed.homeLineup)
            assertEquals(1, confirmed.confirmations.size)
        }
}

/** The "no cards" affirmation can be taken back (prompt 13, part 4). */
class WithdrawNoCardsTest {
    @Test
    fun anAffirmationCanBeWithdrawnAndTheBlockIsUnaccountedForAgain() =
        runTest {
            val affirmed = match().copy(cards = CardsSection.NoneIssued)
            val matches = FakeMatchRepository(listOf(affirmed))

            val withdrawn = WithdrawNoCards(matches)(affirmed)

            // Not "none" and not "some": nobody has said anything about the
            // block, which is what the readiness check then asks about.
            assertNull(withdrawn.cards)
            assertEquals(withdrawn, matches.load(MatchId("m1")))
        }

    @Test
    fun andAffirmedAgainAfterwards() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val affirmed = AffirmNoCards(matches)(match())
            val withdrawn = WithdrawNoCards(matches)(affirmed)

            assertEquals(CardsSection.NoneIssued, AffirmNoCards(matches)(withdrawn).cards)
        }

    @Test
    fun recordedCardsAreNotAnAffirmationAndAreNotWithdrawnByThis() =
        runTest {
            val withACard =
                match().copy(
                    cards =
                        CardsSection.Issued(
                            listOf(
                                YellowCard(
                                    Minute.Played(20),
                                    TeamSide.AWAY,
                                    CardSubject.Player(Fixtures.bacaAppearance.id),
                                    CardReason("podražení"),
                                ),
                            ),
                        ),
                )
            val matches = FakeMatchRepository(listOf(withACard))

            assertEquals(withACard, WithdrawNoCards(matches)(withACard))
            assertEquals(0, matches.saves)
        }

    @Test
    fun withdrawingWhenNothingWasAffirmedChangesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            assertEquals(match(), WithdrawNoCards(matches)(match()))
            assertEquals(0, matches.saves)
        }
}
