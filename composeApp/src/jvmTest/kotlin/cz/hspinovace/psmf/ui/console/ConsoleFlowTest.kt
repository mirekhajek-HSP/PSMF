package cz.hspinovace.psmf.ui.console

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.EndPeriod
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.GoalDraft
import cz.hspinovace.psmf.usecase.GoalProblem
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.MinuteDraft
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.StartNextPeriod
import cz.hspinovace.psmf.usecase.UndoLastEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * The console's view model over the real use cases (prompt 13, part 3).
 *
 * What the referee can do that they could not: write a goal with a minute of
 * its own, and take a whistle back -- which asks first, because it changes
 * the clock. The clock here is one the test moves, because the clock is the
 * point.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ConsoleFlowTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class SteppingClock(
        var at: Instant,
    ) : Clock {
        override fun now(): Instant = at
    }

    private fun player(
        ref: String,
        surname: String,
        number: Int,
        team: TeamId,
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = team,
        name = PlayerName(PersonName.of(surname), PersonName.of("Jan")),
        rpNumber = null,
        dateOfBirth = null,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber(number),
    )

    private val novak = player("novak", "Novák", 9, UiTestData.homeTeamId)
    private val poupe = player("poupe", "Poupě", 11, UiTestData.homeTeamId)
    private val vlk = player("vlk", "Vlk", 4, UiTestData.awayTeamId)

    private val group =
        LeagueGroup(
            season = UiTestData.season,
            group = UiTestData.group,
            teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
            players = listOf(novak, poupe, vlk),
            fixtures = listOf(UiTestData.fixture),
            venues = listOf(UiTestData.venue),
        )

    private val league = InMemoryLeague(listOf(group))
    private val matchId = MatchId("m1")

    private fun appearance(player: Player) =
        Appearance(
            id = AppearanceId("a-${player.ref}"),
            playerId = player.id,
            jerseyNumber = player.defaultJerseyNumber,
            reportedIdentification =
                ReportedIdentification(
                    "59${player.defaultJerseyNumber?.value}",
                    IdentificationSource.RP,
                ),
        )

    private val aNovak = appearance(novak)
    private val aVlk = appearance(vlk)

    private val report =
        Match(
            id = matchId,
            fixtureId = UiTestData.fixtureId,
            groupId = UiTestData.groupId,
            status = MatchStatus.SETUP,
            homeLineup =
                Lineup.wearing(
                    TeamSide.HOME,
                    UiTestData.homeTeamId,
                    listOf(aNovak, appearance(poupe)),
                    UiTestData.homeTeam.primaryKit,
                ),
            awayLineup =
                Lineup.wearing(TeamSide.AWAY, UiTestData.awayTeamId, listOf(aVlk), UiTestData.awayTeam.primaryKit),
        )

    private val noAddedPlayers =
        object : AddedPlayerRepository {
            override suspend fun add(
                matchId: MatchId,
                player: Player,
            ) = Unit

            override suspend fun forMatch(matchId: MatchId): List<Player> = emptyList()

            override suspend fun remove(playerId: PlayerId) = Unit
        }

    private fun viewModel(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ) = ConsoleViewModel(
        matchId = matchId,
        matches = matches,
        buildConsoleEntry = BuildConsoleEntry(league, noAddedPlayers),
        startMatch = StartMatch(matches),
        finishMatch = FinishMatch(matches),
        logGoal = LogGoal(matches),
        logCard = LogCard(matches),
        undoLastEvent = UndoLastEvent(matches),
        endPeriod = EndPeriod(matches),
        startNextPeriod = StartNextPeriod(matches),
        clock = clock,
    )

    private fun ConsoleViewModel.entry() = assertNotNull(state.value.entry)

    private fun ConsoleViewModel.goalMinutes() = entry().log.filterIsInstance<GoalEvent>().map { it.minute.written }

    // ------------------------------------------------------------------
    // Taking a whistle back
    // ------------------------------------------------------------------

    @Test
    fun aMisTappedKickoffIsTakenBackOnlyAfterTheRefereeSaysYes() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            assertTrue(console.entry().started)

            console.onEvent(ConsoleEvent.UndoPressed)

            // Asked, and nothing has changed yet.
            assertEquals(UndoStep.TakesBackWhistle(Whistle.KICKOFF, 1), console.state.value.takeBack)
            assertTrue(console.entry().started, "the kickoff went before the referee answered")

            // No: it stays, and the question goes away.
            console.onEvent(ConsoleEvent.TakeBackDismissed)
            assertNull(console.state.value.takeBack)
            assertTrue(console.entry().started)

            // Yes: it goes, the match has not started, and the whistle can be pressed again.
            console.onEvent(ConsoleEvent.UndoPressed)
            console.onEvent(ConsoleEvent.TakeBackConfirmed)
            assertNull(console.state.value.takeBack)
            assertTrue(!console.entry().started)
            clock.at = KICKOFF + 3.minutes
            console.onEvent(ConsoleEvent.StartPressed)
            assertEquals(KICKOFF + 3.minutes, console.entry().kickoffAt)
        }

    @Test
    fun undoOfAGoalIsStillOneTapWithNoQuestion() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            clock.at = KICKOFF + 12.minutes + 40.seconds
            console.onEvent(ConsoleEvent.GoalScoredBy(aNovak.id))
            assertEquals(listOf("12´"), console.goalMinutes())

            console.onEvent(ConsoleEvent.UndoPressed)

            assertNull(console.state.value.takeBack)
            assertEquals(emptyList(), console.goalMinutes())
        }

    @Test
    fun takingBackTheEndOfTheFirstHalfLeavesTheClockContinuous() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            clock.at = KICKOFF + 31.minutes
            console.onEvent(ConsoleEvent.EndPeriodPressed)
            clock.at = KICKOFF + 33.minutes
            // In the interval the clock holds at what had been played.
            assertEquals(31.minutes, console.entry().readingAt(clock.at).shown)

            console.onEvent(ConsoleEvent.UndoPressed)
            assertEquals(UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, 1), console.state.value.takeBack)
            console.onEvent(ConsoleEvent.TakeBackConfirmed)

            // Play went on, and the clock says so.
            assertEquals(33.minutes, console.entry().readingAt(clock.at).shown)
            clock.at = KICKOFF + 34.minutes
            assertEquals(34.minutes, console.entry().readingAt(clock.at).shown)
        }

    // ------------------------------------------------------------------
    // A goal with its own minute
    // ------------------------------------------------------------------

    @Test
    fun aGoalIsWrittenWithTheMinuteTheRefereeGivesAndListsAtIt() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            clock.at = KICKOFF + 20.minutes + 10.seconds
            console.onEvent(ConsoleEvent.GoalScoredBy(aNovak.id))

            console.onEvent(ConsoleEvent.GoalWithMinuteOpened(TeamSide.AWAY))
            val opened = assertNotNull(console.state.value.goal)
            console.onEvent(ConsoleEvent.GoalEdited(opened.copy(scorer = aVlk.id, minute = MinuteDraft("12"))))
            console.onEvent(ConsoleEvent.GoalSubmitted)

            assertNull(console.state.value.goal)
            // The log lists newest first, in report order: 20´ then 12´.
            assertEquals(listOf("20´", "12´"), console.goalMinutes())
            assertEquals("1:1", console.entry().score.asWrittenOnReport)
        }

    @Test
    fun aMinuteTheClockHasNotReachedIsRefusedAndTheSheetStaysOpen() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            clock.at = KICKOFF + 20.minutes

            console.onEvent(ConsoleEvent.GoalWithMinuteOpened(TeamSide.HOME))
            val opened = assertNotNull(console.state.value.goal)
            console.onEvent(ConsoleEvent.GoalEdited(opened.copy(minute = MinuteDraft("21"))))
            console.onEvent(ConsoleEvent.GoalSubmitted)

            assertTrue(console.state.value.goalProblem(GoalProblem.LATER_THAN_THE_CLOCK))
            assertNotNull(console.state.value.goal, "the sheet closed on a refused goal")
            assertEquals(emptyList(), console.goalMinutes())

            // Corrected, it is written.
            console.onEvent(ConsoleEvent.GoalEdited(opened.copy(minute = MinuteDraft("20"))))
            console.onEvent(ConsoleEvent.GoalSubmitted)
            assertEquals(listOf("20´"), console.goalMinutes())
        }

    @Test
    fun afterTheFinalWhistleTheOneTapGoalOpensTheSheetAndNeverWritesSixtyPlus() =
        runTest {
            val matches = GatedMatchRepository(listOf(report))
            val clock = SteppingClock(KICKOFF)
            val console = viewModel(matches, clock)
            console.onEvent(ConsoleEvent.StartPressed)
            clock.at = KICKOFF + 30.minutes
            console.onEvent(ConsoleEvent.EndPeriodPressed)
            clock.at = KICKOFF + 45.minutes
            console.onEvent(ConsoleEvent.StartNextPeriodPressed)
            clock.at = KICKOFF + 75.minutes
            console.onEvent(ConsoleEvent.FinishPressed)
            clock.at = KICKOFF + 80.minutes

            console.onEvent(ConsoleEvent.GoalScoredBy(aNovak.id))

            // The tap chose the scorer and asked for the minute; nothing was written.
            assertEquals(GoalDraft(TeamSide.HOME, aNovak.id), console.state.value.goal)
            assertEquals(emptyList(), console.goalMinutes())

            console.onEvent(ConsoleEvent.GoalSubmitted)
            assertTrue(console.state.value.goalProblem(GoalProblem.NO_MINUTE))

            val draft = assertNotNull(console.state.value.goal)
            console.onEvent(ConsoleEvent.GoalEdited(draft.copy(minute = MinuteDraft("47"))))
            console.onEvent(ConsoleEvent.GoalSubmitted)
            assertEquals(listOf("47´"), console.goalMinutes())
            assertEquals(
                Minute.Played(47),
                console
                    .entry()
                    .log
                    .first()
                    .minute,
            )

            // Undo takes the goal first, with no question; the whistle comes next, with one.
            console.onEvent(ConsoleEvent.UndoPressed)
            assertNull(console.state.value.takeBack)
            assertEquals(emptyList(), console.goalMinutes())
            console.onEvent(ConsoleEvent.UndoPressed)
            assertEquals(UndoStep.TakesBackWhistle(Whistle.FINAL, 2), console.state.value.takeBack)
        }
}
