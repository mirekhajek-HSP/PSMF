package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.powerPlaysRunningAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private val houzev = Fixtures.houzevAppearance.id
private val poupe = Fixtures.poupeAppearance.id
private val baca = Fixtures.bacaAppearance.id

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

private fun card(
    side: TeamSide,
    appearance: AppearanceId,
    minute: String,
    reason: String = "podražení",
    colour: CardColour = CardColour.YELLOW,
) = CardDraft(
    side = side,
    appearance = appearance,
    colour = colour,
    dismissal = Dismissal.STRAIGHT.takeIf { colour == CardColour.RED },
    reason = reason,
    minute = MinuteDraft(minute),
)

private fun player(appearance: AppearanceId) = CardSubject.Player(appearance)

private fun Match.cardsOf(appearance: AppearanceId) = cardEvents.filter { it.subject == player(appearance) }

/** The recording-order number of the only card written at [minute] (a second yellow's two share it). */
private fun Match.cardAt(minute: String): Int =
    assertNotNull(
        cardEvents
            .filter { it.minute.written == minute }
            .map { it.sequence }
            .distinct()
            .single(),
    )

/**
 * A card already in the log is corrected or removed (0.4.0, Gate 6; the testers' point 5),
 * as a goal is (Gate 5), with what a card carries.
 *
 * The player, the reason and the minute change in place; the colour, and a red's kind, do
 * not. The player only to another of the same team with no card in this match, and never
 * for a second yellow, which is one action and is corrected or removed whole. A first
 * yellow cannot go while its `2. ŽK` stands. A red's power play follows its minute, and
 * goes with it. Removing the last card leaves the block unaccounted for, as Undo does.
 */
class CardCorrectionTest {
    private val during = KICKOFF + 25.minutes

    /** Kicked off; a yellow for Houžev (home) at 10´, saved at 10:00. */
    private suspend fun aYellow(matches: FakeMatchRepository): Match {
        val started = StartMatch(matches)(match(), KICKOFF)
        return assertNotNull(LogCard(matches)(started, card(TeamSide.HOME, houzev, "10"), KICKOFF + 10.minutes))
    }

    /** Kicked off; a straight red for Bača (away) at 20´, saved at 20:00. */
    private suspend fun aRed(matches: FakeMatchRepository): Match {
        val started = StartMatch(matches)(match(), KICKOFF)
        val red = card(TeamSide.AWAY, baca, "20", "hrubost", CardColour.RED)
        return assertNotNull(LogCard(matches)(started, red, KICKOFF + 20.minutes))
    }

    /** Kicked off; Houžev booked at 10´ and booked again at 22´: a second yellow, one action. */
    private suspend fun aSecondYellow(matches: FakeMatchRepository): Match {
        val booked = aYellow(matches)
        val again = card(TeamSide.HOME, houzev, "22", "nesp. chování")
        return assertNotNull(LogCard(matches)(booked, again, KICKOFF + 22.minutes))
    }

    @Test
    fun aYellowIsMovedToAnotherPlayerOfTheSameTeam() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aYellow(matches)
            val ten = before.cardAt("10´")

            val after =
                CorrectCard(matches)(before, ten, player(poupe), CardReason("podražení"), Minute.Played(10), during)

            assertEquals(emptyList(), after.cardsOf(houzev), "the yellow stayed with Houžev")
            val moved = after.cardsOf(poupe).single()
            assertTrue(moved is YellowCard)
            assertEquals(Minute.Played(10), moved.minute)
            assertEquals(ten, moved.sequence, "the card left its place in the recording order")
            assertEquals(after, matches.load(MatchId("m1")), "the correction was not saved")
        }

    @Test
    fun aPlayerWhoAlreadyHasACardIsRefused() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val yellow = aYellow(matches)
            val before = assertNotNull(LogCard(matches)(yellow, card(TeamSide.HOME, poupe, "15"), KICKOFF + 15.minutes))
            val saves = matches.saves
            val correct = CorrectCard(matches)
            val ten = before.cardAt("10´")
            val reason = CardReason("podražení")

            assertEquals(
                before,
                correct(before, ten, player(poupe), reason, Minute.Played(10), during),
                "Poupě is booked",
            )
            // Not the other team's, not nobody's, and not someone outside the lineup.
            assertEquals(before, correct(before, ten, player(baca), reason, Minute.Played(10), during))
            assertEquals(
                before,
                correct(before, ten, player(AppearanceId("nobody")), reason, Minute.Played(10), during),
            )
            val official = CardSubject.NamedPerson(PersonName.of("Lepiš A."))
            assertEquals(before, correct(before, ten, official, reason, Minute.Played(10), during))
            assertEquals(saves, matches.saves, "a refused correction was saved")
        }

    @Test
    fun theReasonAndTheMinuteAreCorrectedInPlace() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aYellow(matches)

            val after =
                CorrectCard(matches)(
                    before,
                    before.cardAt("10´"),
                    player(houzev),
                    CardReason("hrubé podražení"),
                    Minute.HalfTime,
                    during,
                )

            val corrected = after.cardEvents.single()
            assertEquals("hrubé podražení", corrected.reason.text)
            assertEquals(Minute.HalfTime, corrected.minute)
            assertTrue(corrected is YellowCard, "the colour changed")
        }

    @Test
    fun aRedsCorrectedMinuteMovesItsPowerPlaysStart() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aRed(matches)
            assertEquals(KICKOFF + 20.minutes, before.powerPlays.single().startedAt)

            // It was 15´, not 20´: the side has been short since then.
            val after =
                CorrectCard(matches)(
                    before,
                    before.cardAt("20´"),
                    player(baca),
                    CardReason("hrubost"),
                    Minute.Played(15),
                    during,
                )

            val powerPlay = after.powerPlays.single()
            assertEquals(KICKOFF + 15.minutes, powerPlay.startedAt, "the power play did not move with the minute")
            assertEquals(Minute.Played(15), powerPlay.dismissedAtMinute)
            assertEquals(before.powerPlays.single().sequence, powerPlay.sequence)
            assertTrue(after.powerPlaysRunningAt(KICKOFF + 24.minutes + 59.seconds).isNotEmpty())
            assertTrue(after.powerPlaysRunningAt(KICKOFF + 25.minutes).isEmpty(), "ten minutes from 15´ end at 25´")
        }

    @Test
    fun aRedsReasonOrPlayerCorrectedLeavesItsPowerPlayWhereItWas() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val started = StartMatch(matches)(match(), KICKOFF)
            val red = card(TeamSide.HOME, houzev, "20", "hrubost", CardColour.RED)
            // Saved 40 seconds into the 20th minute, as it happened: the ten minutes run from then.
            val before = assertNotNull(LogCard(matches)(started, red, KICKOFF + 20.minutes + 40.seconds))
            assertEquals(KICKOFF + 20.minutes + 40.seconds, before.powerPlays.single().startedAt)

            val after =
                CorrectCard(matches)(
                    before,
                    before.cardAt("20´"),
                    player(poupe),
                    CardReason("surová hra"),
                    Minute.Played(20),
                    during,
                )

            assertEquals(listOf("surová hra"), after.cardsOf(poupe).map { it.reason.text })
            assertEquals(before.powerPlays, after.powerPlays, "the power play moved with no minute corrected")
        }

    @Test
    fun aRedAtSixtyPlusMovedToAPlayedMinuteLeavesTheSideShortFromIt() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val started = StartMatch(matches)(match(), KICKOFF)
            val red =
                card(TeamSide.AWAY, baca, "", "hrubost", CardColour.RED)
                    .copy(minute = MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE))
            val before = assertNotNull(LogCard(matches)(started, red, KICKOFF + 20.minutes))
            assertEquals(emptyList(), before.powerPlays, "LogCard starts none at 60´+")
            val sixtyPlus = before.cardAt("60´+")

            // It was 18´, during play: as LogCard would have, the side is short from then.
            val after =
                CorrectCard(matches)(before, sixtyPlus, player(baca), CardReason("hrubost"), Minute.Played(18), during)

            assertEquals(1, after.powerPlays.size, "the red moved into play left nobody short")
            assertEquals(KICKOFF + 18.minutes, after.powerPlays.single().startedAt)
            assertEquals(sixtyPlus, after.powerPlays.single().sequence)
        }

    @Test
    fun anotherPlayersSecondYellowDoesNotHoldThisYellow() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val sentOff = aSecondYellow(matches)
            val before =
                assertNotNull(LogCard(matches)(sentOff, card(TeamSide.HOME, poupe, "24"), KICKOFF + 24.minutes))
            val poupes = before.cardAt("24´")

            assertNull(before.cardEvents.secondYellowStandingOn(poupes), "Houžev's 2. ŽK held Poupě's yellow")
            assertEquals(emptyList(), RemoveCard(matches)(before, poupes).cardsOf(poupe))
        }

    @Test
    fun aRedMovedToSixtyPlusLeavesNobodyShort() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aRed(matches)

            val after =
                CorrectCard(matches)(
                    before,
                    before.cardAt("20´"),
                    player(baca),
                    CardReason("hrubost"),
                    Minute.AfterFinalWhistle,
                    during,
                )

            assertEquals(Minute.AfterFinalWhistle, after.cardEvents.single().minute)
            assertEquals(emptyList(), after.powerPlays, "LogCard starts none for a red at 60´+")
        }

    @Test
    fun aRedRemovedTakesItsPowerPlayWithIt() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aRed(matches)

            val after = RemoveCard(matches)(before, before.cardAt("20´"))

            assertEquals(emptyList(), after.cardEvents)
            assertEquals(emptyList(), after.powerPlays, "the power play outlived its red")
            assertEquals(after, matches.load(MatchId("m1")), "the removal was not saved")
        }

    @Test
    fun aSecondYellowRemovedTakesItsYellowAndItsRedAndTheDismissal() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aSecondYellow(matches)
            assertEquals(3, before.cardEvents.size)

            val after = RemoveCard(matches)(before, before.cardAt("22´"))

            assertEquals(1, after.cardEvents.size, "the 2. ŽK did not go whole: ${after.cardEvents}")
            val left = after.cardEvents.single()
            assertTrue(left is YellowCard, "the 2. ŽK left a card behind: $left")
            assertEquals(Minute.Played(10), left.minute)
            assertEquals(emptyList(), after.powerPlays)
        }

    @Test
    fun aSecondYellowIsCorrectedWholeAndKeepsItsPlayer() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aSecondYellow(matches)
            val twentyTwo = before.cardAt("22´")

            val after =
                CorrectCard(matches)(before, twentyTwo, player(houzev), CardReason("urážka"), Minute.Played(21), during)

            val action = after.cardEvents.filter { it.sequence == twentyTwo }
            assertEquals(2, action.size)
            assertEquals(listOf("urážka", "urážka"), action.map { it.reason.text }, "corrected in part")
            assertEquals(listOf(Minute.Played(21), Minute.Played(21)), action.map { it.minute })
            assertEquals(Dismissal.SECOND_YELLOW, action.filterIsInstance<RedCard>().single().dismissal)
            assertEquals(Minute.Played(21), after.powerPlays.single().dismissedAtMinute)

            // Its player does not change.
            val saves = matches.saves
            assertEquals(
                after,
                CorrectCard(matches)(after, twentyTwo, player(poupe), CardReason("urážka"), Minute.Played(21), during),
            )
            assertEquals(saves, matches.saves)
        }

    @Test
    fun aFirstYellowUnderAStandingSecondYellowIsRefusedUntilTheSecondGoes() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aSecondYellow(matches)
            val ten = before.cardAt("10´")
            val saves = matches.saves

            assertNotNull(before.cardEvents.secondYellowStandingOn(ten), "nothing says the 2. ŽK stands on it")
            assertEquals(before, RemoveCard(matches)(before, ten), "the first yellow went from under its 2. ŽK")
            // Nor given to someone else, which would leave the same second yellow without a first.
            assertTrue(before.cardEvents.keepsItsPlayer(ten))
            assertEquals(
                before,
                CorrectCard(matches)(before, ten, player(poupe), CardReason("podražení"), Minute.Played(10), during),
            )
            assertEquals(saves, matches.saves)
            // Its reason and minute can still be put right.
            val reworded =
                CorrectCard(matches)(before, ten, player(houzev), CardReason("držení"), Minute.Played(10), during)
            assertEquals(
                "držení",
                reworded.cardEvents
                    .first { it.sequence == ten }
                    .reason.text,
            )

            // The 2. ŽK out first, and then the yellow can go.
            val withoutTheSecond = RemoveCard(matches)(reworded, reworded.cardAt("22´"))
            assertNull(withoutTheSecond.cardEvents.secondYellowStandingOn(ten))
            assertEquals(emptyList<CardEvent>(), RemoveCard(matches)(withoutTheSecond, ten).cardEvents)
        }

    @Test
    fun theOnlyCardRemovedLeavesTheBlockUnaccountedForNotNoneIssued() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = aYellow(matches)

            val after = RemoveCard(matches)(before, before.cardAt("10´"))

            assertNull(after.cards, "removing a card is not the referee saying none were issued")
            assertFalse(after.cards == CardsSection.NoneIssued)
        }

    @Test
    fun aGoalsOrAWhistlesNumberNeverRemovesOrCorrectsACard() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val yellow = aYellow(matches)
            val before = LogGoal(matches)(yellow, TeamSide.AWAY, baca, Minute.Played(12))
            val goal = assertNotNull(before.goals.single().sequence)
            val kickoff = assertNotNull(before.kickoffSequence)

            assertEquals(before, RemoveCard(matches)(before, goal))
            assertEquals(before, RemoveCard(matches)(before, kickoff))
            assertEquals(
                before,
                CorrectCard(matches)(before, goal, player(baca), CardReason("x"), Minute.Played(12), during),
            )
        }

    @Test
    fun aCardFromBeforeTheRecordingOrderIsNeverTouched() =
        runTest {
            // 0.1.x kept no order: no card of this report has a number to be addressed by.
            val old = aSecondYellow(FakeMatchRepository(listOf(match())))
            val unnumbered =
                old.copy(
                    cards =
                        CardsSection.Issued(
                            old.cardEvents.map {
                                when (it) {
                                    is YellowCard -> it.copy(sequence = null)
                                    is RedCard -> it.copy(sequence = null)
                                }
                            },
                        ),
                    powerPlays = old.powerPlays.map { it.copy(sequence = null) },
                )
            val matches = FakeMatchRepository(listOf(unnumbered))

            (1..5).forEach { n ->
                assertEquals(unnumbered, RemoveCard(matches)(unnumbered, n))
                assertEquals(
                    unnumbered,
                    CorrectCard(matches)(unnumbered, n, player(houzev), CardReason("x"), Minute.Played(3), during),
                )
            }
            assertEquals(0, matches.saves)
        }

    // ------------------------------------------------------------------
    // A signed report
    // ------------------------------------------------------------------

    private val signedBy =
        ConfirmingParty.entries.map {
            Confirmation(
                it,
                KICKOFF + 90.minutes,
                PersonName.of("Vlk Jiří"),
            )
        }

    private suspend fun signed(matches: FakeMatchRepository): Match {
        val finished = FinishMatch(matches)(aRed(matches), KICKOFF + 75.minutes)
        return finished.copy(confirmations = signedBy, status = MatchStatus.CONFIRMED).also { matches.save(it) }
    }

    @Test
    fun aCorrectionOrARemovalOnASignedReportTakesTheSignaturesBack() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = signed(matches)
            val twenty = before.cardAt("20´")
            val later = KICKOFF + 120.minutes

            val corrected =
                CorrectCard(matches)(before, twenty, player(baca), CardReason("surová hra"), Minute.Played(20), later)
            assertEquals(
                "surová hra",
                corrected.cardEvents
                    .single()
                    .reason.text,
            )
            assertEquals(emptyList(), corrected.confirmations, "the signatures outlived the correction")
            assertEquals(MatchStatus.FINISHED, corrected.status)

            val removed = RemoveCard(matches)(before, twenty)
            assertEquals(emptyList(), removed.cardEvents)
            assertEquals(emptyList(), removed.confirmations, "the signatures outlived the removal")
        }

    @Test
    fun aCorrectionThatChangesNothingOrIsRefusedLeavesTheSignaturesAndSavesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val before = signed(matches)
            val saves = matches.saves
            val twenty = before.cardAt("20´")
            val later = KICKOFF + 120.minutes

            assertTrue(
                before.cardCorrectionChangesNothing(twenty, player(baca), CardReason("hrubost"), Minute.Played(20)),
            )
            assertFalse(
                before.cardCorrectionChangesNothing(twenty, player(baca), CardReason("hrubost"), Minute.Played(21)),
            )
            assertEquals(
                before,
                CorrectCard(matches)(before, twenty, player(baca), CardReason("hrubost"), Minute.Played(20), later),
            )
            assertEquals(
                before,
                CorrectCard(matches)(before, twenty, player(houzev), CardReason("hrubost"), Minute.Played(20), later),
            )
            assertEquals(saves, matches.saves)
        }
}
