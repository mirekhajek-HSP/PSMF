package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.YellowCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF_AT = Instant.parse("2026-08-31T19:00:00Z")

/**
 * What screen 4 draws.
 *
 * The clock is the interesting part: it is a subtraction from a stored
 * instant, so it can be asked for any moment without anything ticking.
 */
class ConsoleEntryTest {
    private var minted = 0

    private suspend fun matchWithLineups(added: FakeAddedPlayerRepository = FakeAddedPlayerRepository()): Match {
        val build = BuildLineupEntry(TestLeague.repository(), added) { "id-${++minted}" }
        val plain = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
        val entry = assertNotNull(build(plain))
        return plain.copy(
            homeLineup = assertNotNull(entry.home.toLineup()),
            awayLineup = assertNotNull(entry.away.toLineup()),
        )
    }

    private suspend fun console(
        match: Match,
        added: FakeAddedPlayerRepository = FakeAddedPlayerRepository(),
    ) = assertNotNull(BuildConsoleEntry(TestLeague.repository(), added)(match))

    @Test
    fun rowsCarryNumbersAndNamesAndAreOrderedByShirt() =
        runTest {
            val entry = console(matchWithLineups())

            val numbers = entry.home.rows.mapNotNull { it.jerseyNumber?.value }
            assertEquals(numbers.sorted(), numbers)
            assertEquals(TestLeague.homeSquad.size, entry.home.rows.size)
            assertTrue(entry.home.rows.all { it.name.asWrittenOnReport.isNotBlank() })
        }

    @Test
    fun beforeKickoffThereIsNoClockAtAll() =
        runTest {
            val entry = console(matchWithLineups())

            assertFalse(entry.started)
            assertNull(entry.minuteAt(KICKOFF_AT))
        }

    @Test
    fun theMinuteIsASubtractionFromTheStoredKickoff() =
        runTest {
            // Nothing ticks. This is the whole clock, and it is why the
            // console survives the process dying.
            val entry = console(matchWithLineups().copy(kickoffAt = KICKOFF_AT))

            assertEquals(Minute.Played(0), entry.minuteAt(KICKOFF_AT))
            assertEquals(Minute.Played(29), entry.minuteAt(KICKOFF_AT + 29.minutes))
        }

    @Test
    fun pastTheFirstPeriodsNominalLengthTheMinuteIsHalfTimeUntilTheRefereeSaysOtherwise() =
        runTest {
            // Found by using the app on a physical phone: there was no way
            // to end the first half at all, so added time inside it had
            // nowhere to go but an ordinary, ever-climbing integer. The
            // form has no notation for "31'" -- only for the break itself
            // -- so this now reads 30'+ even before anyone presses "end of
            // period".
            val entry = console(matchWithLineups().copy(kickoffAt = KICKOFF_AT))

            assertEquals(Minute.HalfTime, entry.minuteAt(KICKOFF_AT + 30.minutes))
            assertEquals(Minute.HalfTime, entry.minuteAt(KICKOFF_AT + 31.minutes))
        }

    @Test
    fun onceTheSecondPeriodIsRunningAddedTimeIsAnOrdinaryMinuteAgain() =
        runTest {
            // 60'+ is the final whistle, not merely the last period running
            // long -- past the nominal 60 here, nothing stops, exactly as
            // it always has.
            val match =
                matchWithLineups().copy(
                    kickoffAt = KICKOFF_AT,
                    periodBreaks =
                        listOf(
                            PeriodBreak(endedAt = KICKOFF_AT + 30.minutes, nextStartedAt = KICKOFF_AT + 30.minutes),
                        ),
                )
            val entry = console(match)

            assertEquals(Minute.Played(64), entry.minuteAt(KICKOFF_AT + 64.minutes))
        }

    @Test
    fun duringTheIntervalTheMinuteHoldsAtHalfTime() =
        runTest {
            val match =
                matchWithLineups().copy(
                    kickoffAt = KICKOFF_AT,
                    periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF_AT + 32.minutes)),
                )
            val entry = console(match)

            assertTrue(entry.inPeriodInterval)
            assertEquals(Minute.HalfTime, entry.minuteAt(KICKOFF_AT + 32.minutes))
            assertEquals(Minute.HalfTime, entry.minuteAt(KICKOFF_AT + 38.minutes))
        }

    @Test
    fun afterTheFinalWhistleEveryEventIsSixtyPlus() =
        runTest {
            val match =
                matchWithLineups().copy(kickoffAt = KICKOFF_AT, status = MatchStatus.FINISHED)
            val entry = console(match)

            assertEquals(Minute.AfterFinalWhistle, entry.minuteAt(KICKOFF_AT + 61.minutes))
        }

    @Test
    fun theConsoleOffersToEndTheFirstPeriodThenToStartTheSecond() =
        runTest {
            val started = matchWithLineups().copy(kickoffAt = KICKOFF_AT)
            assertEquals(PeriodAction.END_PERIOD, console(started).periodAction)

            val onBreak = started.copy(periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF_AT + 30.minutes)))
            assertEquals(PeriodAction.START_NEXT_PERIOD, console(onBreak).periodAction)

            // The second period is the last one for HL's 2 x 30: nothing
            // more to offer here, "Ukončit utkání" is unaffected by this.
            val secondHalf =
                started.copy(
                    periodBreaks =
                        listOf(
                            PeriodBreak(endedAt = KICKOFF_AT + 30.minutes, nextStartedAt = KICKOFF_AT + 30.minutes),
                        ),
                )
            assertEquals(PeriodAction.NONE, console(secondHalf).periodAction)
        }

    @Test
    fun aSentOffPlayerIsMarkedRatherThanRemoved() =
        runTest {
            val base = matchWithLineups()
            val victim = console(base).home.rows.first()
            val match =
                base.copy(
                    cards =
                        CardsSection.Issued(
                            listOf(
                                RedCard(
                                    Minute.Played(40),
                                    TeamSide.HOME,
                                    CardSubject.Player(victim.appearanceId),
                                    CardReason("oplácení"),
                                    Dismissal.STRAIGHT,
                                ),
                            ),
                        ),
                )

            val entry = console(match)

            val row = assertNotNull(entry.home.row(victim.appearanceId))
            assertTrue(row.dismissed)
            // Still on the list: the referee needs to see who is off while
            // the power play beside it counts down.
            assertEquals(TestLeague.homeSquad.size, entry.home.rows.size)
        }

    @Test
    fun yellowsInThisMatchAreCountedSoASecondOneIsNoSurprise() =
        runTest {
            val base = matchWithLineups()
            val booked = console(base).home.rows.first()
            val match =
                base.copy(
                    cards =
                        CardsSection.Issued(
                            listOf(
                                YellowCard(
                                    Minute.Played(20),
                                    TeamSide.HOME,
                                    CardSubject.Player(booked.appearanceId),
                                    CardReason("podražení"),
                                ),
                            ),
                        ),
                )

            val entry = console(match)

            assertEquals(1, assertNotNull(entry.home.row(booked.appearanceId)).yellowsInThisMatch)
            assertFalse(assertNotNull(entry.home.row(booked.appearanceId)).dismissed)
        }

    @Test
    fun aPlayerAddedAtThePitchAppearsWithTheirName() =
        runTest {
            val added = FakeAddedPlayerRepository()
            val plain = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
            val player =
                assertNotNull(
                    AddPlayerAtThePitch(added) { "added-${++minted}" }(
                        plain,
                        NewPlayerRequest(
                            teamId = Fixtures.homeTeamId,
                            firstName = "Petr",
                            surname = "Hlok",
                            dateOfBirth = "21.1.1999",
                        ),
                    ),
                )
            val match = matchWithLineups(added)

            val entry = console(match, added)

            assertTrue(entry.home.rows.any { it.name.asWrittenOnReport == "Hlok Petr" })
            assertEquals(TestLeague.homeSquad.size + 1, entry.home.rows.size)
            assertTrue(player.rpNumber == null)
        }

    @Test
    fun theLogReadsNewestFirstBecauseThatIsWhatIsBeingChecked() =
        runTest {
            val matches = FakeMatchRepository()
            var match = matchWithLineups().copy(kickoffAt = KICKOFF_AT)
            match = LogGoal(matches)(match, TeamSide.HOME, null, Minute.Played(11))
            match = LogGoal(matches)(match, TeamSide.AWAY, null, Minute.Played(29))

            val entry = console(match)

            assertEquals(listOf(Minute.Played(29), Minute.Played(11)), entry.log.map { it.minute })
        }

    @Test
    fun aRunningPowerPlayIsReportedForTheRightSideAndThenStops() =
        runTest {
            val match =
                matchWithLineups().copy(
                    kickoffAt = KICKOFF_AT,
                    powerPlays =
                        listOf(
                            cz.hspinovace.psmf.domain.PowerPlay(
                                shortHandedSide = TeamSide.AWAY,
                                startedAt = KICKOFF_AT + 40.minutes,
                                dismissedAtMinute = Minute.Played(40),
                            ),
                        ),
                )
            val entry = console(match)

            assertEquals(1, entry.playersShortAt(TeamSide.AWAY, KICKOFF_AT + 45.minutes))
            assertEquals(0, entry.playersShortAt(TeamSide.HOME, KICKOFF_AT + 45.minutes))
            // Ten minutes, and not a second more.
            assertEquals(0, entry.playersShortAt(TeamSide.AWAY, KICKOFF_AT + 51.minutes))
        }
}
