package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

/** `poločas` and `Konečný výsledek`, entered rather than derived. */
class ResultDraftTest {
    @Test
    fun bothScoresAreNeededBeforeThereIsAResult() {
        assertNull(ResultDraft().toResult())
        assertNull(ResultDraft(halfTimeHome = "1", halfTimeAway = "1").toResult())
        assertEquals(
            ResultProblem.FULL_TIME_MISSING,
            ResultDraft(halfTimeHome = "1", halfTimeAway = "1").problems().single(),
        )
    }

    @Test
    fun aCompleteDraftBecomesAResultWithAWinner() {
        val result = assertNotNull(ResultDraft("1", "1", "2", "1").toResult())

        assertEquals(Score(1, 1), result.halfTime)
        assertEquals(Score(2, 1), result.fullTime)
        assertEquals(TeamSide.HOME, result.winner)
    }

    @Test
    fun goalsCannotBeUnScoredBetweenHalfTimeAndTheEnd() {
        // A full-time score below the half-time one is a typo, and the
        // domain refuses to hold it rather than exporting a contradiction.
        val backwards = ResultDraft("2", "1", "1", "1")

        assertNull(backwards.toResult())
        assertEquals(listOf(ResultProblem.FULL_TIME_BELOW_HALF_TIME), backwards.problems())
    }

    @Test
    fun theFinalScoreIsSuggestedFromTheGoalsAndTheHalfTimeIsNot() =
        runTest {
            // Only the referee knows where the break fell: the clock never
            // stops, so added time makes minute 31 as likely to be first
            // half as second.
            val played =
                match().copy(
                    goals =
                        listOf(
                            GoalEvent(Minute.Played(5), TeamSide.AWAY, null, Score(0, 1)),
                            GoalEvent(Minute.Played(45), TeamSide.HOME, null, Score(1, 1)),
                        ),
                )

            val suggested = ResultDraft.suggestedFrom(played)

            assertEquals("1", suggested.fullTimeHome)
            assertEquals("1", suggested.fullTimeAway)
            assertEquals("", suggested.halfTimeHome)
        }

    @Test
    fun theResultIsWrittenThroughWhenItIsUsable() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val updated = RecordResult(matches)(match(), ResultDraft("1", "1", "2", "1"))

            assertEquals(Score(2, 1), updated.result?.fullTime)
            assertEquals(updated, matches.load(MatchId("m1")))
        }

    @Test
    fun anUnusableResultWritesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            assertNull(RecordResult(matches)(match(), ResultDraft("2", "1", "1", "1")).result)
            assertEquals(0, matches.saves)
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
 * RULE: **one captain per team confirms** — settled 2026-08-30.
 *
 * The captain confirms the lineup before kickoff and the report at the
 * end, and those are one act: re-confirming moves the timestamp rather
 * than adding a second signature.
 */
class ConfirmReportTest {
    @Test
    fun aConfirmationRecordsWhoAndWhen() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val confirmed =
                assertNotNull(
                    ConfirmReport(matches)(
                        match(),
                        ConfirmingParty.HOME_CAPTAIN,
                        "Novak",
                        asDeputy = false,
                        at = CONFIRMED_AT,
                    ),
                )

            val confirmation = confirmed.confirmations.single()
            assertEquals("Novak", confirmation.confirmedBy.value)
            assertEquals(CONFIRMED_AT, confirmation.at)
            assertFalse(confirmation.asDeputy)
        }

    @Test
    fun confirmingAgainReplacesRatherThanAddsASecondSignature() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val confirm = ConfirmReport(matches)
            val first =
                assertNotNull(confirm(match(), ConfirmingParty.HOME_CAPTAIN, "Novak", false, CONFIRMED_AT))

            val later = CONFIRMED_AT + kotlin.time.Duration.parse("1h")
            val second =
                assertNotNull(confirm(first, ConfirmingParty.HOME_CAPTAIN, "Novak", false, later))

            assertEquals(1, second.confirmations.size)
            assertEquals(later, second.confirmations.single().at)
        }

    @Test
    fun aDeputyIsMarkedBecauseCaptaincyCanBeDelegated() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val confirmed =
                assertNotNull(
                    ConfirmReport(matches)(
                        match(),
                        ConfirmingParty.AWAY_CAPTAIN,
                        "Lepis",
                        asDeputy = true,
                        at = CONFIRMED_AT,
                    ),
                )

            assertTrue(confirmed.confirmations.single().asDeputy)
        }

    @Test
    fun allThreeConfirmationsMarkTheReportConfirmed() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val confirm = ConfirmReport(matches)
            var current = match()

            ConfirmingParty.entries.forEach { party ->
                current = assertNotNull(confirm(current, party, "Novak", false, CONFIRMED_AT))
            }

            assertEquals(MatchStatus.CONFIRMED, current.status)
        }

    @Test
    fun aNameThatIsNotALatinNameConfirmsNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            assertNull(
                ConfirmReport(matches)(match(), ConfirmingParty.REFEREE, "", false, CONFIRMED_AT),
            )
            assertNull(
                ConfirmReport(matches)(match(), ConfirmingParty.REFEREE, "Вовк", false, CONFIRMED_AT),
            )
            assertEquals(0, matches.saves)
        }
}
