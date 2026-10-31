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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.ConfirmingParty
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
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.CardColour
import cz.hspinovace.psmf.usecase.CardDraft
import cz.hspinovace.psmf.usecase.CardProblem
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.CorrectCard
import cz.hspinovace.psmf.usecase.CorrectGoal
import cz.hspinovace.psmf.usecase.EndPeriod
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogCorrections
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.MinuteDraft
import cz.hspinovace.psmf.usecase.RemoveCard
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * A card already in the log is corrected or removed from the console (0.4.0, Gate 6; the
 * testers' point 5), as a goal is (Gate 5): the log row opens the card sheet filled in with
 * it, the sheet saves the player, the reason and the minute or removes the card after
 * asking, and on a signed report a change asks first. The colour does not change in the
 * sheet, a second yellow keeps its player, and a first yellow its `2. ŽK` stands on is not
 * removed: the sheet says to take the `2. ŽK` out first.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalTestApi::class)
class CardCorrectionFromTheLogTest {
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
    private val bily = player("bily", "Bílý", 7, UiTestData.homeTeamId)
    private val vlk = player("vlk", "Vlk", 4, UiTestData.awayTeamId)

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                    players = listOf(novak, poupe, bily, vlk),
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
    private val aBily = appearance(bily)
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
                    listOf(aNovak, aPoupe, aBily),
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
        corrections =
            LogCorrections(CorrectGoal(matches), RemoveGoal(matches), CorrectCard(matches), RemoveCard(matches)),
        clock = clock,
    )

    private fun ConsoleViewModel.cards() = assertNotNull(state.value.entry).log.filterIsInstance<CardEvent>().reversed()

    private fun ConsoleViewModel.cardAt(minute: String) =
        assertNotNull(
            cards()
                .filter { it.minute.written == minute }
                .map { it.sequence }
                .distinct()
                .single(),
        )

    /** A card from the player's row, at the clock's minute, as the referee writes one. */
    private fun ConsoleViewModel.card(
        appearance: Appearance,
        side: TeamSide,
        reason: String,
        red: Boolean = false,
    ) {
        onEvent(ConsoleEvent.CardOpened(appearance.id, side))
        val opened = assertNotNull(state.value.card)
        val colour = if (red) CardColour.RED else CardColour.YELLOW
        onEvent(ConsoleEvent.CardEdited(opened.copy(colour = colour, reason = reason)))
        onEvent(ConsoleEvent.CardSubmitted)
        assertNull(state.value.card, "the card was not recorded")
    }

    /** Kicked off; a yellow for Novák at 10´, then Vlk's goal at 20´ (the last thing recorded). The clock at 25:00. */
    private fun aYellow(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ): ConsoleViewModel {
        val console = viewModel(matches, clock)
        console.onEvent(ConsoleEvent.StartPressed)
        clock.at = KICKOFF + 10.minutes
        console.card(aNovak, TeamSide.HOME, "podražení")
        clock.at = KICKOFF + 20.minutes
        console.onEvent(ConsoleEvent.GoalScoredBy(aVlk.id))
        clock.at = KICKOFF + 25.minutes
        return console
    }

    /** [aYellow], and Novák booked again at 22´: a second yellow. The clock at 25:00. */
    private fun aSecondYellow(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ): ConsoleViewModel {
        val console = aYellow(matches, clock)
        clock.at = KICKOFF + 22.minutes
        console.card(aNovak, TeamSide.HOME, "nesp. chování")
        clock.at = KICKOFF + 25.minutes
        return console
    }

    /** [aYellow], the halves whistled, the final whistle at 75:00, and all three signatures; opened again. */
    private fun signedConsole(
        matches: GatedMatchRepository,
        clock: SteppingClock,
    ): ConsoleViewModel {
        val console = aYellow(matches, clock)
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
    fun aCardInTheLogOpensTheSheetFilledInWithIt() {
        val console = aYellow(GatedMatchRepository(listOf(report)), SteppingClock(KICKOFF))
        val ten = console.cardAt("10´")

        console.onEvent(ConsoleEvent.CardInLogTapped(ten))

        assertEquals(
            CardDraft(
                side = TeamSide.HOME,
                appearance = aNovak.id,
                colour = CardColour.YELLOW,
                reason = "podražení",
                minute = MinuteDraft("10"),
                corrects = ten,
            ),
            console.state.value.card,
            "the sheet is not the card tapped",
        )
        assertFalse(console.state.value.cardSendsOff, "correcting a booked player's yellow reads as a second one")
    }

    @Test
    fun aYellowMovesToAnotherPlayerAndUndoStillTakesBackTheLastThingRecorded() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aYellow(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
        val opened = assertNotNull(console.state.value.card)

        console.onEvent(ConsoleEvent.CardEdited(opened.copy(appearance = aPoupe.id, reason = "držení")))
        console.onEvent(ConsoleEvent.CardSubmitted)

        assertNull(console.state.value.card, "the sheet stayed open")
        val card = stored(matches).cardEvents.single()
        assertEquals(CardSubject.Player(aPoupe.id), card.subject, "the player was not corrected")
        assertEquals(CardReason("držení"), card.reason)
        assertTrue(card is YellowCard)

        // The last thing recorded is still Vlk's goal.
        console.onEvent(ConsoleEvent.UndoPressed)
        assertEquals(emptyList(), stored(matches).goals)
        assertEquals(1, stored(matches).cardEvents.size)
    }

    @Test
    fun theSheetOffersOnlyPlayersOfTheTeamWithNoCardInThisMatch() {
        val matches = GatedMatchRepository(listOf(report))
        val clock = SteppingClock(KICKOFF)
        val console = aYellow(matches, clock)
        // Poupě is booked at 24´; Bílý has no card; Vlk is the other team's.
        clock.at = KICKOFF + 24.minutes
        console.card(aPoupe, TeamSide.HOME, "držení")

        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))

        assertEquals(
            listOf(aNovak.id, aBily.id).toSet(),
            console.state.value.cardPlayerChoices
                .map { it.appearanceId }
                .toSet(),
            "the sheet offers a booked player, or somebody else's",
        )
    }

    @Test
    fun aSecondYellowKeepsItsPlayerAndIsCorrectedWhole() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aSecondYellow(matches, SteppingClock(KICKOFF))
        val twentyTwo = console.cardAt("22´")

        console.onEvent(ConsoleEvent.CardInLogTapped(twentyTwo))
        assertEquals(
            listOf(aNovak.id),
            console.state.value.cardPlayerChoices
                .map { it.appearanceId },
        )
        val opened = assertNotNull(console.state.value.card)
        assertEquals(CardColour.RED, opened.colour, "a second yellow opens as its red")
        console.onEvent(ConsoleEvent.CardEdited(opened.copy(minute = MinuteDraft("21"), reason = "urážka")))
        console.onEvent(ConsoleEvent.CardSubmitted)

        val action = stored(matches).cardEvents.filter { it.sequence == twentyTwo }
        assertEquals(listOf("urážka", "urážka"), action.map { it.reason.text }, "the 2. ŽK was corrected in part")
        assertEquals(listOf(Minute.Played(21), Minute.Played(21)), action.map { it.minute })
    }

    @Test
    fun aBlankReasonIsRefusedAndTheSheetStaysOpen() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aYellow(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
        val opened = assertNotNull(console.state.value.card)

        console.onEvent(ConsoleEvent.CardEdited(opened.copy(reason = " ")))
        console.onEvent(ConsoleEvent.CardSubmitted)

        assertTrue(console.state.value.cardProblem(CardProblem.NO_REASON), "a card with no reason was accepted")
        assertNotNull(console.state.value.card, "the sheet closed")
        assertEquals(CardReason("podražení"), stored(matches).cardEvents.single().reason)
    }

    @Test
    fun removingACardAsksFirstAndANoKeepsIt() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aYellow(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))

        console.onEvent(ConsoleEvent.CardRemovePressed)
        assertTrue(console.state.value.removingCard, "removing did not ask")
        assertEquals(1, stored(matches).cardEvents.size, "removed before the answer")

        console.onEvent(ConsoleEvent.CardRemoveDismissed)
        assertFalse(console.state.value.removingCard)
        assertNotNull(console.state.value.card, "a No closed the sheet")
        assertEquals(1, stored(matches).cardEvents.size)

        console.onEvent(ConsoleEvent.CardRemovePressed)
        console.onEvent(ConsoleEvent.CardRemoveConfirmed)
        assertEquals(emptyList(), stored(matches).cardEvents, "the card was not removed")
        assertNull(stored(matches).cards, "the only card removed left 'no cards' affirmed")
        assertNull(console.state.value.card)
        assertFalse(console.state.value.removingCard)
    }

    @Test
    fun aFirstYellowUnderItsSecondIsRefusedInWordsAndNothingIsAsked() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aSecondYellow(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
        assertEquals(
            listOf(aNovak.id),
            console.state.value.cardPlayerChoices
                .map { it.appearanceId },
        )

        console.onEvent(ConsoleEvent.CardRemovePressed)

        assertTrue(console.state.value.cardRemovalRefused, "nothing said why it cannot go")
        assertFalse(console.state.value.removingCard, "it asked about a removal that cannot be made")
        assertEquals(3, stored(matches).cardEvents.size)
    }

    @Test
    fun aSecondYellowIsRemovedWholeFromEitherOfItsLines() {
        val matches = GatedMatchRepository(listOf(report))
        val console = aSecondYellow(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("22´")))

        console.onEvent(ConsoleEvent.CardRemovePressed)
        console.onEvent(ConsoleEvent.CardRemoveConfirmed)

        assertEquals(listOf("10´"), stored(matches).cardEvents.map { it.minute.written })
        assertEquals(emptyList(), stored(matches).powerPlays)
        assertFalse(
            assertNotNull(
                console.state.value.entry
                    ?.row(aNovak.id),
            ).dismissed,
            "Novák is still sent off",
        )
    }

    @Test
    fun aCorrectionOnASignedReportAsksFirstAndTheSignaturesGoOnlyOnYes() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
        val opened = assertNotNull(console.state.value.card)
        console.onEvent(ConsoleEvent.CardEdited(opened.copy(reason = "držení")))

        console.onEvent(ConsoleEvent.CardSubmitted)
        assertEquals(ConsoleEvent.CardSubmitted, console.state.value.withdrawing, "it did not ask")
        assertEquals(CardReason("podražení"), stored(matches).cardEvents.single().reason, "changed before the answer")
        assertEquals(3, stored(matches).confirmations.size)

        console.onEvent(ConsoleEvent.WithdrawalDismissed)
        assertNotNull(console.state.value.card, "a No closed the sheet")
        assertEquals(3, stored(matches).confirmations.size)

        console.onEvent(ConsoleEvent.CardSubmitted)
        console.onEvent(ConsoleEvent.WithdrawalConfirmed)
        assertEquals(CardReason("držení"), stored(matches).cardEvents.single().reason, "the correction was not made")
        assertEquals(emptyList(), stored(matches).confirmations)
        assertEquals(MatchStatus.FINISHED, stored(matches).status)
        assertNull(console.state.value.card)
    }

    @Test
    fun aRemovalOnASignedReportIsOneQuestionThatAlsoTakesTheSignaturesBack() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))

        console.onEvent(ConsoleEvent.CardRemovePressed)
        assertTrue(console.state.value.removingCard)
        console.onEvent(ConsoleEvent.CardRemoveConfirmed)

        assertNull(console.state.value.withdrawing, "a second question on top of the first")
        assertEquals(emptyList(), stored(matches).cardEvents, "the card was not removed")
        assertEquals(emptyList(), stored(matches).confirmations)
    }

    @Test
    fun aCorrectionThatChangesNothingAsksNothingAndKeepsTheSignatures() {
        val matches = GatedMatchRepository(listOf(report))
        val console = signedConsole(matches, SteppingClock(KICKOFF))
        console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))

        console.onEvent(ConsoleEvent.CardSubmitted)

        assertNull(console.state.value.withdrawing, "it asked about a change that changes nothing")
        assertNull(console.state.value.card, "the sheet stayed open")
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

    @Test
    fun tappingACardRowOpensTheSheetFilledIn() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = aYellow(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                onNodeWithText("10´ · ŽK · Novák Jan · podražení").assert(hasClickAction()).performClick()

                onNodeWithText("Opravit kartu").assertIsDisplayed()
                onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("podražení")))
                    .assertIsDisplayed()
                onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("10")))
                    .assertIsDisplayed()
                // The colour is said, not offered, and one line says how to change it.
                onNodeWithText("Žlutá karta").assertIsDisplayed().assert(!hasClickAction())
                onNodeWithText("kartu odeberte a zapište znovu", substring = true).assertIsDisplayed()
                onNodeWithText("Odebrat kartu").assertIsDisplayed()
                assertTrue(onAllNodesWithText("Vyloučit (2. ŽK)").fetchSemanticsNodes().isEmpty())
            }
        }

    @Test
    fun bothLinesOfASecondYellowAreTargets() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = aSecondYellow(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                onNodeWithText("22´ · ŽK · Novák Jan · nesp. chování").assert(hasClickAction())
                onNodeWithText("22´ · ČK · Novák Jan · 2. ŽK · nesp. chování").assert(hasClickAction()).performClick()

                onNodeWithText("Opravit kartu").assertIsDisplayed()
            }
        }

    @Test
    fun aCardRowIsATouchTargetTallAndShowsMoreThanColourThatItOpens() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = aYellow(GatedMatchRepository(listOf(report)), clock)
                showConsole(console, clock)

                val row = onNodeWithText("10´ · ŽK · Novák Jan · podražení")
                val bounds = row.getBoundsInRoot()
                assertTrue(bounds.bottom - bounds.top >= 48.dp, "the row is ${bounds.bottom - bounds.top} tall")

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
    fun aCardFromBeforeTheRecordingOrderIsNotATarget() =
        runComposeUiTest {
            withLanguage("cs") {
                // Written by 0.1.x: no place in the recording order, so nothing to correct it by.
                val old =
                    report.copy(
                        status = MatchStatus.IN_PROGRESS,
                        kickoffAt = KICKOFF,
                        cards =
                            CardsSection.Issued(
                                listOf(
                                    YellowCard(
                                        Minute.Played(10),
                                        TeamSide.HOME,
                                        CardSubject.Player(aNovak.id),
                                        CardReason("podražení"),
                                    ),
                                ),
                            ),
                    )
                val clock = SteppingClock(KICKOFF + 25.minutes)
                showConsole(viewModel(GatedMatchRepository(listOf(old)), clock), clock)

                onNodeWithText("10´ · ŽK · Novák Jan · podražení").assert(!hasClickAction())
            }
        }

    @Test
    fun aFirstYellowUnderItsSecondSaysToTakeTheSecondOutFirst() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = aSecondYellow(GatedMatchRepository(listOf(report)), clock)
                console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
                showConsole(console, clock)

                onNodeWithText("Odebrat kartu").performClick()

                onNodeWithText("Nejdřív odeberte 2. ŽK", substring = true).assertIsDisplayed()
                assertTrue(onAllNodesWithText("Odebrat kartu?").fetchSemanticsNodes().isEmpty(), "it asked anyway")
            }
        }

    @Test
    fun onA360DpPhoneTheRefusalIsBroughtIntoViewAboveTheSheetsButtons() =
        // 360 x 720 dp, and Ukrainian, the longest of the three: the refusal falls below the
        // sheet's fold unless the sheet scrolls to it, and a tap that seems to do nothing is
        // what the refusal exists to prevent.
        runDesktopComposeUiTest(width = 720, height = 1440) {
            withLanguage("uk") {
                val clock = SteppingClock(KICKOFF)
                val console = aSecondYellow(GatedMatchRepository(listOf(report)), clock)
                console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 1f)) {
                        val state by console.state.collectAsState()
                        PsmfTheme { ConsoleScreen(state = state, now = clock.at, onEvent = console::onEvent) }
                    }
                }

                onNodeWithText("Видалити картку").performClick()
                waitForIdle()

                val refusal = onNodeWithText("Спершу видаліть 2. ŽK", substring = true).getUnclippedBoundsInRoot()
                val save = onNodeWithText("Зберегти").getUnclippedBoundsInRoot()
                assertTrue(
                    refusal.bottom <= save.top,
                    "the refusal ends at ${refusal.bottom}, under the sheet's buttons at ${save.top}",
                )
            }
        }

    @Test
    fun theSheetAndTheRemovalQuestionNameTheCardInCzech() =
        theRemovalQuestion("cs", "Opravit kartu", "Odebrat kartu?", "Zpráva je podepsaná", signed = false)

    @Test
    fun theSheetAndTheRemovalQuestionNameTheCardInUkrainian() =
        theRemovalQuestion("uk", "Виправити картку", "Видалити картку?", "Протокол підписано", signed = false)

    @Test
    fun onASignedReportTheRemovalQuestionSaysTheSignaturesGo() =
        theRemovalQuestion("cs", "Opravit kartu", "Odebrat kartu?", "Zpráva je podepsaná", signed = true)

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
            val console = if (signed) signedConsole(matches, clock) else aYellow(matches, clock)
            console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("10´")))
            showConsole(console, clock)
            onNodeWithText(sheetTitle).assertIsDisplayed()

            console.onEvent(ConsoleEvent.CardRemovePressed)

            onNodeWithText(question).assertIsDisplayed()
            // The card, as the log line has it: the line itself, and the question.
            val marker = if (language == "uk") "ЖК" else "ŽK"
            assertEquals(
                2,
                onAllNodesWithText(
                    "10´ · $marker · Novák Jan · podražení",
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

    @Test
    fun removingASecondYellowSaysBothCardsAndTheSendingOffGo() =
        runComposeUiTest {
            withLanguage("cs") {
                val clock = SteppingClock(KICKOFF)
                val console = aSecondYellow(GatedMatchRepository(listOf(report)), clock)
                console.onEvent(ConsoleEvent.CardInLogTapped(console.cardAt("22´")))
                showConsole(console, clock)

                console.onEvent(ConsoleEvent.CardRemovePressed)

                onNodeWithText("Odebrat kartu?").assertIsDisplayed()
                onNodeWithText("obě karty 2. ŽK", substring = true).assertIsDisplayed()
                onNodeWithText("vyloučení i oslabení", substring = true).assertIsDisplayed()
            }
        }
}
