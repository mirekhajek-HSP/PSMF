package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.domain.yellowsAccumulatedBy
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.CompleteReport
import cz.hspinovace.psmf.export.NoAddedPlayers
import cz.hspinovace.psmf.export.ZouCsv
import cz.hspinovace.psmf.export.ZouJson
import cz.hspinovace.psmf.export.ZouLabels
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The card and clock review, docs/DECISIONS.md 2026-10-07: five defects, one
// class each. Every test whose name says what the referee sees failed on
// 8bd3c11, the commit before the fix; the few that passed there are marked
// as guards, written so the fix could not break what was already right.

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/** Both lineups in, whistle gone. Bača plays for the away side. */
private fun underway(): Match =
    Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId).copy(
        homeLineup = Fixtures.homeLineup,
        awayLineup = Fixtures.awayLineup,
        status = MatchStatus.IN_PROGRESS,
        kickoffAt = KICKOFF,
    )

private val baca = Fixtures.bacaAppearance.id

private suspend fun console(match: Match) =
    assertNotNull(BuildConsoleEntry(TestLeague.repository(), FakeAddedPlayerRepository())(match))

private fun yellowFor(
    minute: String,
    reason: String = "podražení",
) = CardDraft(side = TeamSide.AWAY, appearance = baca, reason = reason, minute = MinuteDraft(minute))

private fun redFor(
    minute: MinuteDraft,
    dismissal: Dismissal = Dismissal.STRAIGHT,
) = CardDraft(
    side = TeamSide.AWAY,
    appearance = baca,
    colour = CardColour.RED,
    dismissal = dismissal,
    reason = "hrubost",
    minute = minute,
)

/** 1. A second yellow sends the player off, as one action. */
class SecondYellowTest {
    private suspend fun bookedThenCardedAgain(matches: FakeMatchRepository): Match {
        val booked = assertNotNull(LogCard(matches)(underway(), yellowFor("12"), KICKOFF + 12.minutes))
        return assertNotNull(
            LogCard(matches)(booked, yellowFor("40", reason = "nesp. chování"), KICKOFF + 40.minutes),
        )
    }

    @Test
    fun aBookedPlayerCardedAgainIsShownSentOffWithTheirSidePlayingShort() =
        runTest {
            val match = bookedThenCardedAgain(FakeMatchRepository())

            // Both of the form's entries, at the same minute: the second ŽK,
            // and the ČK it turns into.
            val atForty = match.cardEvents.filter { it.minute == Minute.Played(40) }
            assertEquals(2, atForty.size, "expected ŽK + ČK at 40´, got $atForty")
            assertTrue(atForty.any { it is YellowCard })
            assertEquals(Dismissal.SECOND_YELLOW, atForty.filterIsInstance<RedCard>().single().dismissal)

            val entry = console(match)
            assertTrue(assertNotNull(entry.away.row(baca)).dismissed, "the row still shows them on the pitch")
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, KICKOFF + 41.minutes))
            // Two yellows in one match count nothing towards the season.
            assertEquals(0, match.cardEvents.yellowsAccumulatedBy(CardSubject.Player(baca)))
        }

    @Test
    fun oneUndoTakesTheSecondYellowAndTheDismissalBackTogether() =
        runTest {
            val matches = FakeMatchRepository()
            val sentOff = bookedThenCardedAgain(matches)
            assertEquals(3, sentOff.cardEvents.size, "the second yellow did not send them off")
            assertEquals(1, sentOff.powerPlays.size)

            val undone = UndoLastEvent(matches)(sentOff)

            // Back to exactly where they were: booked once, on the pitch,
            // nobody short.
            assertEquals(listOf(Minute.Played(12)), undone.cardEvents.map { it.minute })
            assertTrue(undone.cardEvents.single() is YellowCard)
            assertTrue(undone.powerPlays.isEmpty())
            val row = assertNotNull(console(undone).away.row(baca))
            assertFalse(row.dismissed)
            assertEquals(1, row.yellowsInThisMatch)
        }

    @Test
    fun choosingRedAndSecondYellowForABookedPlayerAlsoCountsNothingThisSeason() =
        runTest {
            // The other way to say the same thing on the sheet. On 8bd3c11 it
            // recorded yellow + red and no second yellow, so the season
            // count got 1 where the rule says 0.
            val matches = FakeMatchRepository()
            val booked = assertNotNull(LogCard(matches)(underway(), yellowFor("12"), KICKOFF + 12.minutes))

            val sentOff =
                assertNotNull(
                    LogCard(matches)(
                        booked,
                        redFor(MinuteDraft("40"), Dismissal.SECOND_YELLOW),
                        KICKOFF + 40.minutes,
                    ),
                )

            assertEquals(2, sentOff.cardEvents.count { it is YellowCard })
            assertEquals(0, sentOff.cardEvents.yellowsAccumulatedBy(CardSubject.Player(baca)))
            assertEquals(1, sentOff.powerPlays.size)
        }

    @Test
    fun aRedForAPlayerWithNoYellowIsStraight() =
        runTest {
            // 2. ŽK is offered only for a player already booked in this
            // match. For anyone else a red can only be straight.
            val matches = FakeMatchRepository()

            val sentOff =
                assertNotNull(
                    LogCard(matches)(
                        underway(),
                        redFor(MinuteDraft("40"), Dismissal.SECOND_YELLOW),
                        KICKOFF + 40.minutes,
                    ),
                )

            assertEquals(
                Dismissal.STRAIGHT,
                sentOff.cardEvents
                    .filterIsInstance<RedCard>()
                    .single()
                    .dismissal,
            )
            assertEquals(0, sentOff.cardEvents.count { it is YellowCard })
        }

    @Test
    fun guardANamedPersonGetsNoAutomaticSecondYellow() =
        runTest {
            // Not on the pitch, not in the lineup: two yellows stay two yellows
            // and the referee chooses red themselves if it is one.
            val matches = FakeMatchRepository()
            val toLepis =
                CardDraft(
                    side = TeamSide.AWAY,
                    namedPerson = "Lepis A.",
                    reason = "nesp. chování",
                    minute = MinuteDraft(mark = MinuteMark.HALF_TIME),
                )
            var match = assertNotNull(LogCard(matches)(underway(), toLepis, KICKOFF + 31.minutes))
            match = assertNotNull(LogCard(matches)(match, toLepis, KICKOFF + 32.minutes))

            assertEquals(2, match.cardEvents.size)
            assertTrue(match.cardEvents.all { it is YellowCard })
            assertTrue(match.powerPlays.isEmpty())
        }
}

/** 2. The report says 2. ŽK from the stored kind, never from free text. */
class SecondYellowOnTheReportTest {
    private suspend fun reportWithRed(
        reason: String,
        dismissal: Dismissal,
    ): ZouReport {
        val cards =
            CompleteReport.match.cardEvents.map {
                if (it is RedCard) it.copy(reason = CardReason(reason), dismissal = dismissal) else it
            }
        val match = CompleteReport.match.copy(cards = CardsSection.Issued(cards))
        return assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(match))
    }

    @Test
    fun aSecondYellowRedReadsTwoZkInEveryFileWhateverTheRefereeTyped() =
        runTest {
            // The referee typed the reason first, so nothing pre-filled
            // `2. ŽK`. On 8bd3c11 all three files printed only "podražení",
            // indistinguishable from a straight red.
            val report = reportWithRed("podražení", Dismissal.SECOND_YELLOW)

            val redBlock = ZouText.format(report).substringAfter(ZouLabels.Cards.RED)
            assertContains(redBlock, ZouLabels.Cards.SECOND_YELLOW)
            assertContains(redBlock, "podražení")

            val redRow = ZouCsv.format(report).lines().single { it.endsWith(";ČK") }
            assertContains(redRow, ZouLabels.Cards.SECOND_YELLOW)
            assertContains(redRow, "podražení")

            val json = ZouJson.format(report)
            assertContains(json, "\"dismissal\": \"${ZouLabels.Cards.SECOND_YELLOW}\"")
            assertContains(json, "\"reason\": \"podražení\"")
        }

    @Test
    fun aStraightRedSaysSoInTheJsonAndNeverReadsTwoZk() =
        runTest {
            val report = reportWithRed("hrubost", Dismissal.STRAIGHT)

            assertFalse(ZouText.format(report).substringAfter(ZouLabels.Cards.RED).contains("2. ŽK"))
            assertFalse(
                ZouCsv
                    .format(report)
                    .lines()
                    .single { it.endsWith(";ČK") }
                    .contains("2. ŽK"),
            )
            val json = ZouJson.format(report)
            assertContains(json, "\"dismissal\": \"přímá ČK\"")
            assertFalse(json.contains("\"dismissal\": \"${ZouLabels.Cards.SECOND_YELLOW}\""))
        }
}

/** 3. The power play is ten minutes of play. */
class PowerPlayIsPlayTimeTest {
    // The first half runs two minutes over; then a fifteen-minute interval.
    private val firstHalfEnds = KICKOFF + 32.minutes
    private val secondHalfStarts = KICKOFF + 47.minutes

    @Test
    fun aRedAtTwentyFiveStillLeavesTheSideShortWhenTheSecondHalfKicksOff() =
        runTest {
            val matches = FakeMatchRepository()
            var match = assertNotNull(LogCard(matches)(underway(), redFor(MinuteDraft("25")), KICKOFF + 25.minutes))
            match = EndPeriod(matches)(match, firstHalfEnds)
            match = StartNextPeriod(matches)(match, secondHalfStarts)

            val entry = console(match)
            // Seven of the ten were played before the break. Three are left.
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, firstHalfEnds + 10.minutes))
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts))
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 2.minutes + 59.seconds))
            assertEquals(0, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 3.minutes))
        }

    @Test
    fun aRedShownDuringTheIntervalStartsCountingWhenPlayResumes() =
        runTest {
            val matches = FakeMatchRepository()
            var match = EndPeriod(matches)(underway(), firstHalfEnds)
            match =
                assertNotNull(
                    LogCard(matches)(match, redFor(MinuteDraft(mark = MinuteMark.HALF_TIME)), KICKOFF + 40.minutes),
                )
            match = StartNextPeriod(matches)(match, secondHalfStarts)

            val entry = console(match)
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 9.minutes + 59.seconds))
            assertEquals(0, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 10.minutes))
        }

    @Test
    fun aRedAfterTheFinalWhistleLeavesNobodyShort() =
        runTest {
            val matches = FakeMatchRepository()
            val finished =
                underway().copy(
                    status = MatchStatus.FINISHED,
                    periodBreaks = listOf(PeriodBreak(firstHalfEnds, secondHalfStarts)),
                )

            val match =
                assertNotNull(
                    LogCard(matches)(
                        finished,
                        redFor(MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE)),
                        KICKOFF + 80.minutes,
                    ),
                )

            assertTrue(match.cardEvents.single() is RedCard, "the card itself is still recorded")
            assertTrue(match.powerPlays.isEmpty())
        }

    @Test
    fun aRedWrittenAtSixtyPlusLeavesNobodyShortEvenBeforeTheMatchIsEnded() =
        runTest {
            val matches = FakeMatchRepository()

            val match =
                assertNotNull(
                    LogCard(matches)(
                        underway(),
                        redFor(MinuteDraft(mark = MinuteMark.AFTER_FINAL_WHISTLE)),
                        KICKOFF + 62.minutes,
                    ),
                )

            assertTrue(match.powerPlays.isEmpty())
        }

    @Test
    fun aRedToSomeoneNotInTheLineupLeavesNobodyShort() =
        runTest {
            // A team official is not on the pitch to be replaced.
            val matches = FakeMatchRepository()
            val toAnOfficial =
                CardDraft(
                    side = TeamSide.AWAY,
                    namedPerson = "Lepis A.",
                    colour = CardColour.RED,
                    dismissal = Dismissal.STRAIGHT,
                    reason = "urážka rozhodčího",
                    minute = MinuteDraft("20"),
                )

            val match = assertNotNull(LogCard(matches)(underway(), toAnOfficial, KICKOFF + 20.minutes))

            assertTrue(match.powerPlays.isEmpty())
            assertEquals(0, console(match).playersShortAt(TeamSide.AWAY, KICKOFF + 21.minutes))
        }

    @Test
    fun nothingCountsDownOnTheScoreboardOnceTheMatchIsOver() =
        runTest {
            val beforeTheWhistle =
                underway().copy(
                    periodBreaks = listOf(PeriodBreak(KICKOFF + 30.minutes, KICKOFF + 35.minutes)),
                    powerPlays = listOf(PowerPlay(TeamSide.AWAY, KICKOFF + 60.minutes, Minute.Played(55))),
                )
            val afterTheWhistle = beforeTheWhistle.copy(status = MatchStatus.FINISHED)

            assertEquals(1, console(beforeTheWhistle).powerPlaysRunningAt(KICKOFF + 63.minutes).size)
            assertEquals(emptyList(), console(afterTheWhistle).powerPlaysRunningAt(KICKOFF + 63.minutes))
        }

    @Test
    fun aRedLoggedLateRunsFromTheMinuteTheRefereeWrote() =
        runTest {
            // The judgement call: written at 20´, saved at 24:00. The side has
            // been short since 20´, so the ten minutes end at 30´, not 34´.
            val matches = FakeMatchRepository()

            val match = assertNotNull(LogCard(matches)(underway(), redFor(MinuteDraft("20")), KICKOFF + 24.minutes))

            val entry = console(match)
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, KICKOFF + 29.minutes + 59.seconds))
            assertEquals(0, entry.playersShortAt(TeamSide.AWAY, KICKOFF + 30.minutes))
        }

    @Test
    fun aFirstHalfRedLoggedInTheSecondHalfStillSkipsTheInterval() =
        runTest {
            // Written at 28´, saved six minutes into the second half. Four of
            // the ten fell in the first half, the break counts for nothing,
            // and the last six run from the restart.
            val matches = FakeMatchRepository()
            var match = EndPeriod(matches)(underway(), firstHalfEnds)
            match = StartNextPeriod(matches)(match, secondHalfStarts)

            match =
                assertNotNull(LogCard(matches)(match, redFor(MinuteDraft("28")), secondHalfStarts + 3.minutes))

            val entry = console(match)
            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 5.minutes + 59.seconds))
            assertEquals(0, entry.playersShortAt(TeamSide.AWAY, secondHalfStarts + 6.minutes))
        }
}

/** 4. The second period starts at 30´. */
class SecondHalfStartsAtThirtyTest {
    private val halfTime = PeriodBreak(endedAt = KICKOFF + 32.minutes, nextStartedAt = KICKOFF + 35.minutes)

    @Test
    fun theSecondHalfKicksOffAtThirtyWhateverTheFirstHalfAdded() =
        runTest {
            // Krabice–Hustec on psmf.cz, 1:1 (0:0), has a goal at 32.: the
            // second half restarts at 30. On 8bd3c11 it restarted at 32´.
            val entry = console(underway().copy(periodBreaks = listOf(halfTime)))

            assertEquals(Minute.Played(30), entry.minuteAt(KICKOFF + 35.minutes))
            assertEquals(Minute.Played(42), entry.minuteAt(KICKOFF + 47.minutes))
            assertEquals(Minute.Played(61), entry.minuteAt(KICKOFF + 66.minutes))
        }

    @Test
    fun firstHalfAddedTimeComesBeforeTheSecondHalfsThirtiethMinuteInTheLog() =
        runTest {
            // A card in the first half's added time, then a goal in the
            // first minute of the second half.
            val matches = FakeMatchRepository()
            val atHalfTime =
                CardDraft(
                    side = TeamSide.AWAY,
                    appearance = baca,
                    reason = "podražení",
                    minute = MinuteDraft(mark = MinuteMark.HALF_TIME),
                )
            var match = assertNotNull(LogCard(matches)(underway(), atHalfTime, KICKOFF + 31.minutes))
            match = match.copy(periodBreaks = listOf(halfTime))
            match = LogGoal(matches)(match, TeamSide.HOME, null, Minute.Played(30))

            assertEquals(listOf(Minute.HalfTime, Minute.Played(30)), match.timeline().map { it.minute })
            // Newest first.
            assertEquals(listOf(Minute.Played(30), Minute.HalfTime), console(match).log.map { it.minute })
        }

    @Test
    fun firstHalfAddedTimeComesBeforeTheSecondHalfsThirtiethMinuteOnTheReport() =
        runTest {
            // A second-half yellow, then a half-time one the referee wrote up
            // afterwards. The report reads in match order, not in the order
            // they were typed.
            val secondHalf =
                YellowCard(Minute.Played(31), TeamSide.HOME, CardSubject.Player(Fixtures.poupeAppearance.id), REASON)
            val atHalfTime = YellowCard(Minute.HalfTime, TeamSide.AWAY, CardSubject.Player(baca), REASON)
            val match = CompleteReport.match.copy(cards = CardsSection.Issued(listOf(secondHalf, atHalfTime)))

            val report = assertNotNull(BuildZouReport(CompleteReport.league(), NoAddedPlayers())(match))

            assertEquals(listOf("30´+", "31´"), report.cards.yellow.map { it.minute })
        }

    @Test
    fun theHalfTimeScoreIsTheFirstHalfsGoalsAndNoneFromTheSecond() =
        runTest {
            val match =
                underway().copy(
                    periodBreaks = listOf(halfTime),
                    goals =
                        listOf(
                            GoalEvent(Minute.Played(10), TeamSide.HOME, null, Score(1, 0)),
                            // The second half's first minute.
                            GoalEvent(Minute.Played(30), TeamSide.AWAY, null, Score(1, 1)),
                        ),
                )

            assertEquals(Score(1, 0), match.scoreAtEndOfFirstPeriod())
            val suggested = ResultDraft.suggestedFrom(match)
            assertEquals("1" to "0", suggested.halfTimeHome to suggested.halfTimeAway)
        }

    private companion object {
        val REASON = CardReason("podražení")
    }
}

/** 5. Undo takes back the last thing recorded. */
class UndoByRecordingOrderTest {
    @Test
    fun aGoalScoredInTheSameMinuteAsACardIsWhatUndoTakesBack() =
        runTest {
            // Card at 20´, then a goal at 20´. On 8bd3c11 Undo took the card.
            val matches = FakeMatchRepository()
            var match = assertNotNull(LogCard(matches)(underway(), yellowFor("20"), KICKOFF + 20.minutes))
            match = LogGoal(matches)(match, TeamSide.HOME, null, Minute.Played(20))

            val undone = UndoLastEvent(matches)(match)

            assertTrue(undone.goals.isEmpty(), "the goal is still there")
            assertEquals(1, undone.cardEvents.size)
        }

    @Test
    fun aCardWrittenUpLateIsWhatUndoTakesBackEvenAfterALaterGoal() =
        runTest {
            // Goal at 25´, then a card the referee typed in as 22´. On
            // 8bd3c11 Undo took the goal, because 25 sorts after 22.
            val matches = FakeMatchRepository()
            var match = LogGoal(matches)(underway(), TeamSide.HOME, null, Minute.Played(25))
            match = assertNotNull(LogCard(matches)(match, yellowFor("22"), KICKOFF + 26.minutes))

            val undone = UndoLastEvent(matches)(match)

            assertEquals(1, undone.goals.size, "the goal went instead of the card")
            assertTrue(undone.cardEvents.isEmpty())
        }

    @Test
    fun guardUndoingAGoalLeavesAnAffirmedNoCardsAlone() =
        runTest {
            // The referee struck the boxes through, then logged a goal and
            // took it back. "Bez karet" was an affirmation and stays one.
            val matches = FakeMatchRepository()
            val affirmed = underway().copy(cards = CardsSection.NoneIssued)
            val scored = LogGoal(matches)(affirmed, TeamSide.HOME, null, Minute.Played(10))

            val undone = UndoLastEvent(matches)(scored)

            assertEquals(CardsSection.NoneIssued, undone.cards)
        }
}
