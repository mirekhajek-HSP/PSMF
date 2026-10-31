package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.ConfirmingParty
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
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.CardColour
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.CorrectGoal
import cz.hspinovace.psmf.usecase.EndPeriod
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.GoalCorrections
import cz.hspinovace.psmf.usecase.GoalDraft
import cz.hspinovace.psmf.usecase.GoalProblem
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.MinuteDraft
import cz.hspinovace.psmf.usecase.RemoveGoal
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.StartNextPeriod
import cz.hspinovace.psmf.usecase.UndoLastEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * A goal already in the log is corrected or removed from the console (0.4.0, Gate 5;
 * the testers' point 5): the log row opens the goal sheet filled in with it, the sheet
 * saves the scorer and the minute or removes the goal after asking, and on a signed
 * report a change asks first and takes the signatures back. Whistles in the log stay
 * as they are: only Undo takes one back.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalTestApi::class)
class GoalCorrectionFromTheLogTest {
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

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                    players = listOf(novak, poupe, vlk),
                    fixtures = listOf(UiTestData.fixture),
                    venues = listOf(UiTestData.venue),
                ),
            ),
        )
    private val matchId = MatchId("m1")

    private fun appearance(player: Player) =
        Appearance(
            id = AppearanceId("a-${player.ref}"),
            playerId = player.id,
            jerseyNumber = player.defaultJerseyNumber,
            reportedIdentification =
                ReportedIdentification("59${player.defaultJerseyNumber?.value}", IdentificationSource.RP),
        )

    private val aNovak = appearance(novak)
    private val aPoupe = appearance(poupe)
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
                    listOf(aNovak, aPoupe),
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
        goalCorrections = GoalCorrections(CorrectGoal(matches), RemoveGoal(matches)),
        clock = clock,
    )

    private fun ConsoleViewModel.goals() = assertNotNull(state.value.entry).log.filterIsInstance<GoalEvent>().reversed()

    private fun ConsoleViewModel.goalAt(minute: String) =
        assertNotNull(
            goals()
                .single {
                    it.minute.written == minute
                }.sequence,
        )

    /** Kicked off; 10´ Novák (home), then 20´ Vlk (away), one tap each. The clock at 25:00. */
    private fun twoGoals(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ): ConsoleViewModel {
        val console = viewModel(matches, clock)
        console.onEvent(ConsoleEvent.StartPressed)
        clock.at = KICKOFF + 10.minutes
        console.onEvent(ConsoleEvent.GoalScoredBy(aNovak.id))
        clock.at = KICKOFF + 20.minutes
        console.onEvent(ConsoleEvent.GoalScoredBy(aVlk.id))
        clock.at = KICKOFF + 25.minutes
        return console
    }

    /** [twoGoals], the halves whistled, the final whistle at 75:00, and all three signatures; opened again. */
    private fun signedConsole(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ): ConsoleViewModel {
        val console = twoGoals(matches, clock)
        clock.at = KICKOFF + 30.minutes
        console.onEvent(ConsoleEvent.EndPeriodPressed)
        clock.at = KICKOFF + 45.minutes
        console.onEvent(ConsoleEvent.StartNextPeriodPressed)
        clock.at = KICKOFF + 75.minutes
        console.onEvent(ConsoleEvent.FinishPressed)
        runBlocking {
            var current = assertNotNull(matches.load(matchId))
            ConfirmingParty.entries.forEach {
                current = ConfirmReport(matches)(current, it, PersonName.of("Vlk Jiří"), clock.at)
            }
            assertEquals(MatchStatus.CONFIRMED, current.status)
        }
        clock.at = KICKOFF + 90.minutes
        return viewModel(matches, clock)
    }

    private fun stored(matches: GatedMatchRepository) = assertNotNull(runBlocking { matches.load(matchId) })

    // ------------------------------------------------------------------
    // The view model
    // ------------------------------------------------------------------

    @Test
    fun aGoalInTheLogOpensTheSheetFilledInWithIt() {
        val console = twoGoals(GatedMatchRepository(listOf(report)), SteppingClock(KICKOFF))
        val ten = console.goalAt("10´")

        console.onEvent(ConsoleEvent.GoalInLogTapped(ten))

        assertEquals(
            GoalDraft(TeamSide.HOME, aNovak.id, MinuteDraft("10"), corrects = ten),
            console.state.value.goal,
            "the sheet is not the goal tapped",
        )
    }

    @Test
    fun theScorerIsCorrectedAndUndoStillTakesBackTheLastThingRecorded() {
        val matches = GatedMatchRepository(listOf(report))
        val console = twoGoals(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))
        val opened = assertNotNull(console.state.value.goal)

        console.onEvent(ConsoleEvent.GoalEdited(opened.copy(scorer = aPoupe.id, minute = MinuteDraft("12"))))
        console.onEvent(ConsoleEvent.GoalSubmitted)

        assertNull(console.state.value.goal, "the sheet stayed open")
        assertEquals(listOf(aPoupe.id, aVlk.id), console.goals().map { it.scorer }, "the scorer was not corrected")
        assertEquals(listOf("12´", "20´"), console.goals().map { it.minute.written })
        assertEquals(2, stored(matches).goals.size)

        // The last thing recorded is still Vlk's goal.
        console.onEvent(ConsoleEvent.UndoPressed)
        assertEquals(listOf(aPoupe.id), console.goals().map { it.scorer })
    }

    @Test
    fun aMinuteTheClockHasNotReachedIsRefusedAndTheSheetStaysOpen() {
        val console = twoGoals(GatedMatchRepository(listOf(report)), SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))
        val opened = assertNotNull(console.state.value.goal)

        console.onEvent(ConsoleEvent.GoalEdited(opened.copy(minute = MinuteDraft("26"))))
        console.onEvent(ConsoleEvent.GoalSubmitted)

        assertTrue(console.state.value.goalProblem(GoalProblem.LATER_THAN_THE_CLOCK), "26´ at 25:00 was not refused")
        assertNotNull(console.state.value.goal, "the sheet closed on a refused minute")
        assertEquals(listOf("10´", "20´"), console.goals().map { it.minute.written })
    }

    @Test
    fun removingAGoalAsksFirstAndANoKeepsIt() {
        val matches = GatedMatchRepository(listOf(report))
        val console = twoGoals(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))

        console.onEvent(ConsoleEvent.GoalRemovePressed)
        assertTrue(console.state.value.removingGoal, "removing did not ask")
        assertEquals(2, stored(matches).goals.size, "removed before the answer")

        console.onEvent(ConsoleEvent.GoalRemoveDismissed)
        assertEquals(false, console.state.value.removingGoal)
        assertNotNull(console.state.value.goal, "a No closed the sheet")
        assertEquals(2, stored(matches).goals.size)

        console.onEvent(ConsoleEvent.GoalRemovePressed)
        console.onEvent(ConsoleEvent.GoalRemoveConfirmed)
        assertEquals(listOf(aVlk.id), console.goals().map { it.scorer }, "the goal was not removed")
        assertEquals(
            "0:1",
            console
                .goals()
                .single()
                .scoreAfter.asWrittenOnReport,
        )
        assertNull(console.state.value.goal)
        assertEquals(false, console.state.value.removingGoal)
    }

    @Test
    fun aCorrectionOnASignedReportAsksFirstAndTheSignaturesGoOnlyOnYes() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))
        val opened = assertNotNull(console.state.value.goal)
        console.onEvent(ConsoleEvent.GoalEdited(opened.copy(scorer = null)))

        console.onEvent(ConsoleEvent.GoalSubmitted)
        assertEquals(ConsoleEvent.GoalSubmitted, console.state.value.withdrawing, "it did not ask")
        assertEquals(aNovak.id, stored(matches).goals.first().scorer, "changed before the answer")
        assertEquals(3, stored(matches).confirmations.size)

        console.onEvent(ConsoleEvent.WithdrawalDismissed)
        assertNotNull(console.state.value.goal, "a No closed the sheet")
        assertEquals(aNovak.id, stored(matches).goals.first().scorer)
        assertEquals(3, stored(matches).confirmations.size)

        console.onEvent(ConsoleEvent.GoalSubmitted)
        console.onEvent(ConsoleEvent.WithdrawalConfirmed)
        assertEquals(null, stored(matches).goals.first().scorer, "the correction was not made")
        assertEquals(emptyList(), stored(matches).confirmations)
        assertEquals(MatchStatus.FINISHED, stored(matches).status)
        assertNull(console.state.value.goal)
    }

    @Test
    fun aRemovalOnASignedReportIsOneQuestionThatAlsoTakesTheSignaturesBack() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))

        console.onEvent(ConsoleEvent.GoalRemovePressed)
        assertTrue(console.state.value.removingGoal)
        assertTrue(console.state.value.signed)
        console.onEvent(ConsoleEvent.GoalRemoveConfirmed)

        assertNull(console.state.value.withdrawing, "a second question on top of the first")
        assertEquals(1, stored(matches).goals.size, "the goal was not removed")
        assertEquals(emptyList(), stored(matches).confirmations)
    }

    @Test
    fun aCorrectionThatChangesNothingAsksNothingAndKeepsTheSignatures() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("20´")))

        console.onEvent(ConsoleEvent.GoalSubmitted)

        assertNull(console.state.value.withdrawing, "it asked about a change that changes nothing")
        assertNull(console.state.value.goal, "the sheet stayed open")
        assertEquals(3, stored(matches).confirmations.size)
    }

    // ------------------------------------------------------------------
    // The screen
    // ------------------------------------------------------------------

    /** The console, wired to [console], small enough (density 0.5) that the log is inside the test window. */
    private fun ComposeUiTest.showConsole(
        console: ConsoleViewModel,
        clock: SteppingClock,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 0.5f, fontScale = 1f)) {
                Box(modifier = Modifier.requiredSize(width = 360.dp, height = 1500.dp)) {
                    val state by console.state.collectAsState()
                    PsmfTheme { ConsoleScreen(state = state, now = clock.at, onEvent = console::onEvent) }
                }
            }
        }
    }

    private fun editableTextStartingWith(text: String) =
        SemanticsMatcher("EditableText starts with '$text'") {
            it.config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
                ?.startsWith(text) == true
        }

    @Test
    fun tappingAGoalRowOpensTheSheetFilledIn() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = twoGoals(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                onNodeWithText("10´ · Novák Jan · 1:0").assert(hasClickAction()).performClick()

                onNodeWithText("Opravit gól").assertIsDisplayed()
                // The scorer field and the minute field hold the goal tapped.
                onNode(editableTextStartingWith("Novák Jan")).assertIsDisplayed()
                onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("10")))
                    .assertIsDisplayed()
                onNodeWithText("Odebrat gól").assertIsDisplayed()
            }
        }

    @Test
    fun aScorerSentOffSinceIsStillOfferedAsTheGoalsScorer() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = twoGoals(GatedMatchRepository(listOf(report)), clock)
                // Novák scored at 10´ and was sent off at 22´; a new goal could not be his, this one is.
                console.onEvent(ConsoleEvent.CardOpened(aNovak.id, TeamSide.HOME))
                val card = assertNotNull(console.state.value.card)
                val red = card.copy(colour = CardColour.RED, reason = "hrubost", minute = MinuteDraft("22"))
                console.onEvent(ConsoleEvent.CardEdited(red))
                console.onEvent(ConsoleEvent.CardSubmitted)
                assertNull(console.state.value.card, "the red was not recorded")
                showConsole(console, clock)

                onNodeWithText("10´ · Novák Jan · 1:0").performClick()

                onNode(editableTextStartingWith("Novák Jan")).assertIsDisplayed()
            }
        }

    @Test
    fun aGoalRowIsATouchTargetTallAndShowsMoreThanColourThatItOpens() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = twoGoals(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                val row = onNodeWithText("10´ · Novák Jan · 1:0")
                val bounds = row.getBoundsInRoot()
                assertTrue(bounds.bottom - bounds.top >= 48.dp, "the row is ${bounds.bottom - bounds.top} tall")

                // Something is drawn at the row's far end, where the text of a short line never reaches.
                val pixels = row.captureToImage().toPixelMap()
                val background = pixels[pixels.width - 1, 0]
                val band = (pixels.width * 7 / 8) until pixels.width
                val inked =
                    band.sumOf { x ->
                        (0 until pixels.height).count { y ->
                            val p = pixels[x, y]
                            abs(p.red - background.red) + abs(p.green - background.green) +
                                abs(p.blue - background.blue) >
                                0.3f
                        }
                    }
                assertTrue(inked > 0, "nothing at the end of the row says it opens")
            }
        }

    @Test
    fun aWhistleInTheLogIsNotATarget() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = twoGoals(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                onNodeWithText("0´ · Výkop").assert(!hasClickAction())
            }
        }

    @Test
    fun aGoalFromBeforeTheRecordingOrderIsNotATarget() =
        runComposeUiTest {
            withLanguage("cs") {
                // Written by 0.1.x: no place in the recording order, so nothing to correct it by.
                val old =
                    report.copy(
                        status = MatchStatus.IN_PROGRESS,
                        kickoffAt = KICKOFF,
                        goals = listOf(GoalEvent(Minute.Played(10), TeamSide.HOME, aNovak.id, Score(1, 0))),
                    )
                val clock = SteppingClock(KICKOFF + 25.minutes)
                showConsole(viewModel(GatedMatchRepository(listOf(old)), clock), clock)

                onNodeWithText("10´ · Novák Jan · 1:0").assert(!hasClickAction())
            }
        }

    @Test
    fun theSheetAndTheRemovalQuestionNameTheGoalInCzech() =
        theRemovalQuestion("cs", "Opravit gól", "Odebrat gól?", "Zpráva je podepsaná", signed = false)

    @Test
    fun theSheetAndTheRemovalQuestionNameTheGoalInUkrainian() =
        theRemovalQuestion("uk", "Виправити гол", "Видалити гол?", "Протокол підписано", signed = false)

    @Test
    fun onASignedReportTheRemovalQuestionSaysTheSignaturesGo() =
        theRemovalQuestion("cs", "Opravit gól", "Odebrat gól?", "Zpráva je podepsaná", signed = true)

    private fun theRemovalQuestion(
        language: String,
        sheetTitle: String,
        question: String,
        signedWords: String,
        signed: Boolean,
    ) = runComposeUiTest {
        withLanguage(language) {
            val clock = SteppingClock(KICKOFF)
            val matches = GatedMatchRepository(listOf(report))
            val console = if (signed) signedConsole(matches, clock) else twoGoals(matches, clock)
            console.onEvent(ConsoleEvent.GoalInLogTapped(console.goalAt("10´")))
            showConsole(console, clock)
            onNodeWithText(sheetTitle).assertIsDisplayed()

            console.onEvent(ConsoleEvent.GoalRemovePressed)

            onNodeWithText(question).assertIsDisplayed()
            // The goal, as the log line has it: the line itself, and the question.
            assertEquals(
                2,
                onAllNodesWithText(
                    "10´ · Novák Jan · 1:0",
                    substring = true,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size,
            )
            assertEquals(
                signed,
                onAllNodesWithText(signedWords, substring = true).fetchSemanticsNodes().isNotEmpty(),
                "the question ${if (signed) "does not say" else "says"} the signatures go",
            )
        }
    }
}
