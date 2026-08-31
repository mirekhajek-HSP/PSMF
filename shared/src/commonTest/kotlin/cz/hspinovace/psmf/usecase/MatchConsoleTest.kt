package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.cards
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

private fun match() =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        .copy(homeLineup = Fixtures.homeLineup, awayLineup = Fixtures.awayLineup)

private val houzev = Fixtures.houzevAppearance.id
private val poupe = Fixtures.poupeAppearance.id
private val baca = Fixtures.bacaAppearance.id

/**
 * RULE: **the match clock never pauses.**
 *
 * 2 x 30 gross, and the referee adds time rather than stopping anything.
 * There is no pause, stop, resume or adjust operation to test, which is
 * the point — what these tests hold in place is the *absence*.
 */
class StartMatchTest {
    @Test
    fun theWhistleStoresOneInstantAndNothingElseAboutTime() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val started = StartMatch(matches)(match(), KICKOFF)

            assertEquals(KICKOFF, started.kickoffAt)
            assertEquals(MatchStatus.IN_PROGRESS, started.status)
            assertEquals(started, matches.load(MatchId("m1")))
        }

    @Test
    fun kickoffCannotBeMovedByPressingStartAgain() =
        runTest {
            // The clock is derived from this instant. Overwriting it would
            // silently rewind every minute already recorded.
            val matches = FakeMatchRepository(listOf(match()))
            val started = StartMatch(matches)(match(), KICKOFF)

            val again = StartMatch(matches)(started, KICKOFF + 10.minutes)

            assertEquals(KICKOFF, again.kickoffAt)
        }

    @Test
    fun theFinalWhistleDoesNotStopTheClockBecauseThereIsNothingToStop() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val started = StartMatch(matches)(match(), KICKOFF)

            val finished = FinishMatch(matches)(started)

            assertEquals(MatchStatus.FINISHED, finished.status)
            // Still the same one instant: cards may still be issued at 60´+.
            assertEquals(KICKOFF, finished.kickoffAt)
        }
}

/** `Čas | Číslo | Střelec | Stav` — one row of the goals block. */
class LogGoalTest {
    private fun logGoal(matches: FakeMatchRepository) = LogGoal(matches)

    @Test
    fun aGoalCarriesItsMinuteScorerAndRunningScore() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val updated = logGoal(matches)(match(), TeamSide.HOME, poupe, Minute.Played(5))

            val goal = updated.goals.single()
            assertEquals(Minute.Played(5), goal.minute)
            assertEquals(poupe, goal.scorer)
            assertEquals(Score(1, 0), goal.scoreAfter)
            assertEquals(updated, matches.load(MatchId("m1")))
        }

    @Test
    fun aGoalMayHaveNoScorer() =
        runTest {
            // `13´ — 2:1` in the worked example. Demanding a scorer would
            // make the app unable to record a match the paper handles.
            val matches = FakeMatchRepository(listOf(match()))

            val updated = logGoal(matches)(match(), TeamSide.HOME, null, Minute.Played(13))

            assertNull(updated.goals.single().scorer)
            assertEquals(Score(1, 0), updated.goals.single().scoreAfter)
        }

    @Test
    fun theRunningScoreFollowsTheSequenceOfGoals() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            var current = match()
            listOf(
                TeamSide.AWAY to 5,
                TeamSide.HOME to 11,
                TeamSide.HOME to 13,
                TeamSide.AWAY to 29,
            ).forEach { (side, minute) ->
                current = logGoal(matches)(current, side, null, Minute.Played(minute))
            }

            assertEquals(
                listOf(Score(0, 1), Score(1, 1), Score(2, 1), Score(2, 2)),
                current.goals.map { it.scoreAfter },
            )
        }
}

/** `Osobní tresty` — time, number, name and reason, on every card. */
class LogCardTest {
    private val yellow =
        CardDraft(
            side = TeamSide.AWAY,
            appearance = baca,
            reason = "podražení",
            minute = MinuteDraft("20"),
        )

    @Test
    fun aYellowCardCarriesItsMandatoryReason() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val updated = assertNotNull(LogCard(matches)(match(), yellow, KICKOFF))

            val card = updated.cardEvents.single()
            assertTrue(card is YellowCard)
            assertEquals("podražení", card.reason.text)
            assertEquals(Minute.Played(20), card.minute)
            assertTrue(updated.powerPlays.isEmpty())
        }

    @Test
    fun aCardWithNoReasonIsRefused() =
        runTest {
            // Mandatory on every card, and the fine for an incomplete report
            // lands on the delegating team.
            val matches = FakeMatchRepository(listOf(match()))

            assertNull(LogCard(matches)(match(), yellow.copy(reason = "   "), KICKOFF))
            assertEquals(0, matches.saves)
            assertTrue(CardProblem.NO_REASON in yellow.copy(reason = "").problems())
        }

    @Test
    fun aRedCardMustSayWhetherItWasStraightOrASecondYellow() =
        runTest {
            // Not cosmetic: two yellows in one match contribute zero to the
            // season total, a straight red is a different thing entirely.
            val matches = FakeMatchRepository(listOf(match()))
            val red = yellow.copy(colour = CardColour.RED, reason = "oplácení")

            assertNull(LogCard(matches)(match(), red, KICKOFF))
            assertTrue(CardProblem.NO_DISMISSAL_KIND in red.problems())

            val proper = red.copy(dismissal = Dismissal.STRAIGHT)
            val updated = assertNotNull(LogCard(matches)(match(), proper, KICKOFF))
            assertEquals(Dismissal.STRAIGHT, (updated.cardEvents.single() as RedCard).dismissal)
        }

    @Test
    fun aDismissalStartsATenMinutePowerPlayForThatSide() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val red =
                yellow.copy(colour = CardColour.RED, dismissal = Dismissal.STRAIGHT, minute = MinuteDraft("40"))

            val updated = assertNotNull(LogCard(matches)(match(), red, KICKOFF + 40.minutes))

            val powerPlay = updated.powerPlays.single()
            assertEquals(TeamSide.AWAY, powerPlay.shortHandedSide)
            assertEquals(Minute.Played(40), powerPlay.dismissedAtMinute)
            assertEquals(10.minutes, powerPlay.remainingAt(KICKOFF + 40.minutes))
            assertTrue(powerPlay.isRunningAt(KICKOFF + 49.minutes))
            assertTrue(!powerPlay.isRunningAt(KICKOFF + 51.minutes))
        }

    @Test
    fun aSecondDismissalStartsASecondIndependentPeriod() =
        runTest {
            // NOT an extension of the first, and neither is shortened by a
            // goal. That is why a power play stores only its start.
            val matches = FakeMatchRepository(listOf(match()))
            val first =
                yellow.copy(colour = CardColour.RED, dismissal = Dismissal.STRAIGHT, minute = MinuteDraft("40"))
            val second = first.copy(appearance = AppearanceId("app-other"), minute = MinuteDraft("45"))

            var current = assertNotNull(LogCard(matches)(match(), first, KICKOFF + 40.minutes))
            current = assertNotNull(LogCard(matches)(current, second, KICKOFF + 45.minutes))

            assertEquals(2, current.powerPlays.size)
            assertEquals(
                listOf(KICKOFF + 50.minutes, KICKOFF + 55.minutes),
                current.powerPlays.map { it.endsAt },
            )
        }

    @Test
    fun aCardCanBeTimedAtHalfTimeOrAfterTheFinalWhistle() =
        runTest {
            // `30´+` and `60´+` are ordinary values on this form, and no
            // integer holds either.
            val matches = FakeMatchRepository(listOf(match()))

            val atHalfTime =
                assertNotNull(
                    LogCard(matches)(match(), yellow.copy(minute = MinuteDraft(mark = MinuteMark.HALF_TIME)), KICKOFF),
                )
            assertEquals(Minute.HalfTime, atHalfTime.cardEvents.single().minute)

            val afterTheWhistle =
                assertNotNull(
                    LogCard(matches)(
                        match(),
                        yellow.copy(minute = MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE)),
                        KICKOFF,
                    ),
                )
            assertEquals(Minute.AfterFinalWhistle, afterTheWhistle.cardEvents.single().minute)
        }

    @Test
    fun aCardCanBeShownToSomebodyWithNoJerseyNumber() =
        runTest {
            // `30´+ Lepiš A. - nesp. chování`, to a deputy captain.
            val matches = FakeMatchRepository(listOf(match()))
            val toADeputy =
                CardDraft(
                    side = TeamSide.AWAY,
                    appearance = null,
                    namedPerson = "Lepis A.",
                    reason = "nesp. chování",
                    minute = MinuteDraft(mark = MinuteMark.HALF_TIME),
                )

            val updated = assertNotNull(LogCard(matches)(match(), toADeputy, KICKOFF))

            val subject = updated.cardEvents.single().subject
            assertTrue(subject is cz.hspinovace.psmf.domain.CardSubject.NamedPerson)
            assertEquals("Lepis A.", subject.name.value)
        }
}

/** Undo, not editing. Amending a finished report is screen 9, out of the demo. */
class UndoLastEventTest {
    private val yellow =
        CardDraft(side = TeamSide.AWAY, appearance = baca, reason = "podražení", minute = MinuteDraft("20"))

    @Test
    fun takingBackTheLastGoalRestoresTheRunningScore() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            var current = LogGoal(matches)(match(), TeamSide.HOME, poupe, Minute.Played(11))
            current = LogGoal(matches)(current, TeamSide.AWAY, baca, Minute.Played(29))

            val undone = UndoLastEvent(matches)(current)

            assertEquals(1, undone.goals.size)
            assertEquals(Score(1, 0), undone.scoreFromGoals())
            assertEquals(Score(1, 0), undone.goals.single().scoreAfter)
        }

    @Test
    fun takingBackADismissalAlsoTakesBackItsPowerPlay() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            val red =
                yellow.copy(colour = CardColour.RED, dismissal = Dismissal.STRAIGHT, minute = MinuteDraft("40"))
            val current = assertNotNull(LogCard(matches)(match(), red, KICKOFF + 40.minutes))
            assertEquals(1, current.powerPlays.size)

            val undone = UndoLastEvent(matches)(current)

            assertTrue(undone.powerPlays.isEmpty())
            assertNull(undone.cards)
        }

    @Test
    fun takingBackACardLeavesTheBlockUnaccountedForRatherThanAffirmed() =
        runTest {
            // Undoing a card is not the referee saying no cards were issued.
            // Those are different states and the form distinguishes them.
            val matches = FakeMatchRepository(listOf(match()))
            val current = assertNotNull(LogCard(matches)(match(), yellow, KICKOFF))

            val undone = UndoLastEvent(matches)(current)

            assertNull(undone.cards)
            assertTrue(undone.cards !is CardsSection.NoneIssued)
        }

    @Test
    fun undoTakesTheLastEventOfTheMergedTimelineNotOfOneBlock() =
        runTest {
            // The referee thinks in one sequence of events, not in the
            // form's two blocks.
            val matches = FakeMatchRepository(listOf(match()))
            var current = LogGoal(matches)(match(), TeamSide.HOME, poupe, Minute.Played(11))
            current = assertNotNull(LogCard(matches)(current, yellow, KICKOFF))
            current = LogGoal(matches)(current, TeamSide.HOME, houzev, Minute.Played(45))

            val undone = UndoLastEvent(matches)(current)

            assertEquals(1, undone.goals.size)
            assertEquals(1, undone.cardEvents.size)
        }

    @Test
    fun undoWithNothingRecordedChangesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            assertEquals(match(), UndoLastEvent(matches)(match()))
            assertEquals(0, matches.saves)
        }

    @Test
    fun twoIdenticalCardsAreNotBothRemovedByOneUndo() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))
            var current = assertNotNull(LogCard(matches)(match(), yellow, KICKOFF))
            current = assertNotNull(LogCard(matches)(current, yellow, KICKOFF))

            val undone = UndoLastEvent(matches)(current)

            assertEquals(1, undone.cards?.cards()?.size)
        }
}

/** The three kinds of minute the form has, as they are typed. */
class MinuteDraftTest {
    @Test
    fun aTypedMinuteOfPlayBecomesOne() {
        assertEquals(Minute.Played(29), MinuteDraft("29").toMinute())
        assertEquals(Minute.Played(0), MinuteDraft(" 0 ").toMinute())
    }

    @Test
    fun theTwoMarkedValuesIgnoreWhateverIsTyped() {
        assertEquals(Minute.HalfTime, MinuteDraft("29", MinuteMark.HALF_TIME).toMinute())
        assertEquals(Minute.AfterFinalWhistle, MinuteDraft("", MinuteMark.AFTER_FINAL_WHISTLE).toMinute())
    }

    @Test
    fun nonsenseIsNoMinuteAtAll() {
        assertNull(MinuteDraft("").toMinute())
        assertNull(MinuteDraft("abc").toMinute())
        assertNull(MinuteDraft("-3").toMinute())
    }

    @Test
    fun aMinuteRoundTripsBackIntoTheForm() {
        assertEquals(MinuteDraft("29", MinuteMark.PLAYED), MinuteDraft.of(Minute.Played(29)))
        assertEquals(MinuteMark.HALF_TIME, MinuteDraft.of(Minute.HalfTime).mark)
        assertEquals(MinuteMark.AFTER_FINAL_WHISTLE, MinuteDraft.of(Minute.AfterFinalWhistle).mark)
    }
}

/**
 * RULE: **a half-time exists, and ending one is a fact about the match,
 * not a pause.** No pause, stop, resume or adjust operation exists to
 * test -- see StartMatchTest above -- and this adds exactly one more kind
 * of instant to what gets recorded, the same way the whistle already is.
 */
class EndPeriodTest {
    @Test
    fun endingAPeriodRecordsOneInstantAndSavesIt() =
        runTest {
            val started = match().copy(kickoffAt = KICKOFF)
            val matches = FakeMatchRepository(listOf(started))

            val ended = EndPeriod(matches)(started, KICKOFF + 32.minutes)

            assertEquals(listOf(PeriodBreak(endedAt = KICKOFF + 32.minutes)), ended.periodBreaks)
            assertEquals(ended, matches.load(MatchId("m1")))
        }

    @Test
    fun endingAPeriodBeforeKickoffDoesNothing() =
        runTest {
            val matches = FakeMatchRepository(listOf(match()))

            val unchanged = EndPeriod(matches)(match(), KICKOFF)

            assertEquals(emptyList(), unchanged.periodBreaks)
        }

    @Test
    fun endingAPeriodWhileAlreadyOnABreakDoesNothing() =
        runTest {
            val onBreak =
                match().copy(kickoffAt = KICKOFF, periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes)))
            val matches = FakeMatchRepository(listOf(onBreak))

            val unchanged = EndPeriod(matches)(onBreak, KICKOFF + 31.minutes)

            assertEquals(onBreak.periodBreaks, unchanged.periodBreaks)
        }
}

class StartNextPeriodTest {
    @Test
    fun startingTheNextPeriodFillsInWhenPlayResumed() =
        runTest {
            val onBreak =
                match().copy(kickoffAt = KICKOFF, periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes)))
            val matches = FakeMatchRepository(listOf(onBreak))

            val resumed = StartNextPeriod(matches)(onBreak, KICKOFF + 33.minutes)

            assertEquals(
                listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes, nextStartedAt = KICKOFF + 33.minutes)),
                resumed.periodBreaks,
            )
            assertEquals(resumed, matches.load(MatchId("m1")))
        }

    @Test
    fun startingTheNextPeriodWithNoBreakOpenDoesNothing() =
        runTest {
            val started = match().copy(kickoffAt = KICKOFF)
            val matches = FakeMatchRepository(listOf(started))

            val unchanged = StartNextPeriod(matches)(started, KICKOFF + 5.minutes)

            assertEquals(emptyList(), unchanged.periodBreaks)
        }

    @Test
    fun startingTheNextPeriodAgainDoesNotOverwriteAnAlreadyStartedOne() =
        runTest {
            val resumed =
                match().copy(
                    kickoffAt = KICKOFF,
                    periodBreaks =
                        listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes, nextStartedAt = KICKOFF + 33.minutes)),
                )
            val matches = FakeMatchRepository(listOf(resumed))

            val unchanged = StartNextPeriod(matches)(resumed, KICKOFF + 40.minutes)

            assertEquals(resumed.periodBreaks, unchanged.periodBreaks)
        }
}
