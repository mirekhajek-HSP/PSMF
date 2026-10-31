package cz.hspinovace.psmf.ui.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.IdentificationSource
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.Official
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.RefereeAssignment
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.ExportZou
import cz.hspinovace.psmf.export.ZouAppearance
import cz.hspinovace.psmf.export.ZouAssessment
import cz.hspinovace.psmf.export.ZouCards
import cz.hspinovace.psmf.export.ZouConfirmation
import cz.hspinovace.psmf.export.ZouFormat
import cz.hspinovace.psmf.export.ZouGoal
import cz.hspinovace.psmf.export.ZouHeader
import cz.hspinovace.psmf.export.ZouIdentificationKind
import cz.hspinovace.psmf.export.ZouLineup
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouResult
import cz.hspinovace.psmf.export.ZouSide
import cz.hspinovace.psmf.export.ZouTeamAssessment
import cz.hspinovace.psmf.export.ZouText
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.breaksInsideAWord
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.textLayout
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * *Zkopírovat text zápisu* (0.4.0, Gate 4; the testers' point 6: a button to copy the
 * report to the clipboard).
 *
 * The text report (the TXT) is what goes on the clipboard, whichever format the preview
 * shows, and only when the report is ready: copying it is one more way of sending it, so
 * it is offered exactly when Save and Send are.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ExportCopyTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // The state: what is copied, and when
    // ------------------------------------------------------------------

    @Test
    fun theCopyIsTheTextReportWhicheverFormatThePreviewShows() {
        val text = textOf(report)
        ZouFormat.entries.forEach { shown ->
            assertEquals(text, state(selected = shown).copyText, "with ${shown.extension} in the preview")
        }
    }

    @Test
    fun nothingIsToBeCopiedWhileSomethingMandatoryIsMissing() {
        assertNull(state(problems = listOf(ReportProblem.MissingCommentary)).copyText)
    }

    // ------------------------------------------------------------------
    // The view model: the press, over the real use cases
    // ------------------------------------------------------------------

    @Test
    fun aReadyReportCopiesExactlyTheTextTheMailBodyCarriesWhateverTheFormatShown() =
        runTest {
            val clipboard = RecordingClipboard()
            val export = viewModel(GatedMatchRepository(listOf(ready())), clipboard)
            export.onEvent(ExportEvent.FormatSelected(ZouFormat.CSV))

            export.onEvent(ExportEvent.CopyPressed)

            val state = export.state.value
            assertTrue(state.ready, "the fixture is not a ready report: ${state.problems}")
            val txt = state.documents.single { it.format == ZouFormat.TEXT }.content
            val body = ZouText.format(assertNotNull(state.report))
            assertEquals(listOf(txt), clipboard.copied, "what is on the clipboard is not the TXT")
            assertEquals(body, txt, "the TXT is not what the mail body carries")
            assertEquals(CopyOutcome.Copied, state.copyOutcome)
        }

    @Test
    fun copyingLeavesWhatWasSavedStanding() =
        runTest {
            val clipboard = RecordingClipboard()
            val export = viewModel(GatedMatchRepository(listOf(ready())), clipboard)
            export.saveHandled(SaveOutcome.Saved)

            export.onEvent(ExportEvent.CopyPressed)

            assertEquals(SaveOutcome.Saved, export.state.value.saveOutcome, "copying wiped the save outcome")
            assertEquals(CopyOutcome.Copied, export.state.value.copyOutcome)
            assertNull(export.state.value.sendOutcome)
        }

    @Test
    fun aClipboardThatRefusesIsSaidSoAndNothingClaimsTheTextIsThere() =
        runTest {
            val clipboard = RecordingClipboard(works = false)
            val export = viewModel(GatedMatchRepository(listOf(ready())), clipboard)

            export.onEvent(ExportEvent.CopyPressed)

            assertEquals(CopyOutcome.Failed, export.state.value.copyOutcome)
        }

    @Test
    fun aReportThatIsNotReadyCopiesNothingEvenIfThePressGetsThrough() =
        runTest {
            val clipboard = RecordingClipboard()
            val export = viewModel(GatedMatchRepository(listOf(ready().copy(assessment = Assessment()))), clipboard)

            export.onEvent(ExportEvent.CopyPressed)

            assertFalse(export.state.value.ready)
            assertEquals(emptyList(), clipboard.copied, "an unfinished report went to the clipboard")
            assertNull(export.state.value.copyOutcome)
        }

    // ------------------------------------------------------------------
    // The screen
    // ------------------------------------------------------------------

    @Test
    fun theControlIsOfferedWhenTheReportIsReadyAndSaysItIsTheText() =
        runComposeUiTest {
            val events = mutableListOf<ExportEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = events::add) } }
            }

            page().performScrollToNode(hasText("Zkopírovat text zápisu"))
            onNodeWithText("Zkopírovat text zápisu").performClick()

            assertEquals(listOf<ExportEvent>(ExportEvent.CopyPressed), events)
        }

    @Test
    fun theControlIsWithheldUnderTheSameRuleAsSaveAndSend() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ExportScreen(state = state(problems = listOf(ReportProblem.MissingCommentary)), onEvent = {})
                    }
                }
            }

            onNodeWithText("Zkopírovat text zápisu").assertDoesNotExist()
        }

    @Test
    fun theOutcomeSaysThatTheTextIsOnTheClipboardInEveryLanguage() {
        mapOf(
            "cs" to "Text zápisu je ve schránce.",
            "en" to "The report text is on the clipboard.",
            "uk" to "Текст протоколу в буфері обміну.",
        ).forEach { (language, sentence) ->
            runComposeUiTest {
                withLanguage(language) {
                    setContent {
                        PsmfTheme { ExportScreen(state = state(copyOutcome = CopyOutcome.Copied), onEvent = {}) }
                    }
                }
                page().performScrollToNode(hasText(sentence))
                onNodeWithText(sentence).assertExists("$language: no \"$sentence\"")
            }
        }
    }

    @Test
    fun aFailedCopyIsNamedRatherThanSilent() {
        mapOf(
            "cs" to "Text zápisu se nepodařilo zkopírovat.",
            "en" to "The report text could not be copied.",
            "uk" to "Не вдалося скопіювати текст протоколу.",
        ).forEach { (language, sentence) ->
            runComposeUiTest {
                withLanguage(language) {
                    setContent {
                        PsmfTheme { ExportScreen(state = state(copyOutcome = CopyOutcome.Failed), onEvent = {}) }
                    }
                }
                page().performScrollToNode(hasText(sentence))
                onNodeWithText(sentence).assertExists("$language: no \"$sentence\"")
            }
        }
    }

    @Test
    fun theCopyOutcomeSitsBesideTheSaveAndSendOutcomesNotInsteadOfThem() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ExportScreen(
                            state =
                                state(
                                    saveOutcome = SaveOutcome.Saved,
                                    sendOutcome = SendOutcome.DraftOpened,
                                    copyOutcome = CopyOutcome.Copied,
                                ),
                            onEvent = {},
                        )
                    }
                }
            }

            page().performScrollToNode(hasText("Text zápisu je ve schránce."))
            onNodeWithText("Zápis uložen. K souborům se lze vrátit i bez aplikace.").assertExists()
            onNodeWithText("Text zápisu je ve schránce.").assertExists()
            onAllNodes(hasText("Otevřen e-mail", substring = true)).onFirst().assertExists()
        }

    // ------------------------------------------------------------------
    // Where it sits: apart from Save and Send, and quieter than them
    // ------------------------------------------------------------------

    @Test
    fun theControlHasALineOfItsOwnUnderSaveAndSendAtTheirStartEdge() {
        val problems = mutableListOf<String>()
        for ((language, labels) in LABELS) {
            for (width in listOf(360.dp, 411.dp)) {
                for (fontScale in listOf(1f, 1.3f)) {
                    runComposeUiTest {
                        withLanguage(language) {
                            setContent {
                                CompositionLocalProvider(LocalDensity provides Density(2.625f, fontScale)) {
                                    Box(Modifier.requiredSize(width, 6000.dp)) {
                                        PsmfTheme { ExportScreen(state(), {}) }
                                    }
                                }
                            }
                            val save = onNodeWithText(labels.save).getBoundsInRoot()
                            val send = onNodeWithText(labels.send).getBoundsInRoot()
                            val copy = onNodeWithText(labels.copy).getBoundsInRoot()
                            val where = "$language ${width.value.toInt()}dp x$fontScale"
                            if (copy.top < maxOf(save.bottom, send.bottom)) {
                                problems += "$where: the copy starts at ${copy.top}, the row ends ${save.bottom}"
                            }
                            if (abs((copy.left - save.left).value) > 1f) {
                                problems += "$where: the copy's left edge is ${copy.left}, Save's ${save.left}"
                            }
                            if (copy.height < PsmfDimens.minTouchTarget - 1.dp) {
                                problems += "$where: the copy is only ${copy.height} tall"
                            }
                            if (copy.height >= send.height) {
                                problems += "$where: the copy (${copy.height}) is as tall as Send (${send.height})"
                            }
                            if (copy.width > (send.right - save.left) + 1.dp) {
                                problems += "$where: the copy is ${copy.width}, wider than the row"
                            }
                            if (onNodeWithText(labels.copy).textLayout().breaksInsideAWord()) {
                                problems += "$where: \"${labels.copy}\" is broken inside a word"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "${problems.size} problems:\n" + problems.take(8).joinToString("\n"))
    }

    @Test
    fun theControlIsOutlinedAndNeverFilledInLightAndInDark() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = PIXELS_AT_360, height = 4500) {
                withLanguage("cs") {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                            PsmfTheme(darkTheme = dark) { ExportScreen(state(), {}) }
                        }
                    }
                    val image = onNodeWithText("Zkopírovat text zápisu").captureToImage().toPixelMap()
                    val background = image[0, 0]
                    val middle = image.height / 2
                    val where = if (dark) "dark" else "light"
                    assertTrue((0 until 3).any { image[it, middle] != background }, "$where: no outline")
                    assertEquals(
                        background,
                        image[image.width - 12, middle],
                        "$where: the copy is filled like a primary button",
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private fun ComposeUiTest.page() = onAllNodes(hasScrollAction()).onFirst()

    private class RecordingClipboard(
        private val works: Boolean = true,
    ) : ReportClipboard {
        val copied = mutableListOf<String>()

        override fun copy(text: String): Boolean {
            if (works) copied += text
            return works
        }
    }

    private class Labels(
        val save: String,
        val send: String,
        val copy: String,
    )

    private fun state(
        problems: List<ReportProblem> = emptyList(),
        selected: ZouFormat = ZouFormat.TEXT,
        saveOutcome: SaveOutcome? = null,
        sendOutcome: SendOutcome? = null,
        copyOutcome: CopyOutcome? = null,
    ) = ExportUiState(
        loading = false,
        report = report,
        documents = ExportZou()(report),
        problems = problems,
        selected = selected,
        saveOutcome = saveOutcome,
        sendOutcome = sendOutcome,
        copyOutcome = copyOutcome,
    )

    private fun textOf(report: ZouReport) = ExportZou()(report).single { it.format == ZouFormat.TEXT }.content

    private val report =
        ZouReport(
            header =
                ZouHeader(
                    "ZAKOS",
                    LocalDate(2026, 8, 31),
                    LocalTime(19, 0),
                    "6K",
                    "Kominíci",
                    "Sp. Sumýš",
                    "Jiri Vlk",
                    false,
                    "Roman Liska",
                    true,
                    "Celtic THK",
                ),
            lineups =
                listOf(
                    ZouLineup(
                        ZouSide.HOME,
                        "Kominíci",
                        "modrá",
                        listOf(ZouAppearance(9, "900615", ZouIdentificationKind.DATE_OF_BIRTH, "Poupě Petr")),
                    ),
                ),
            goals = listOf(ZouGoal(ZouSide.HOME, "13´", null, null, "1:0")),
            cards = ZouCards(true, emptyList(), emptyList()),
            result = ZouResult("1:0", "1:0", "Kominíci"),
            assessment = ZouAssessment(ZouTeamAssessment(9, 0, true, true), ZouTeamAssessment(13, 5, false, true), "x"),
            confirmations =
                listOf(ZouConfirmation("Podpis rozhodčího", "Jiri Vlk", Instant.parse("2026-08-31T20:05:00Z"), false)),
        )

    // A report the view model finds ready: officials, a lineup each, cards affirmed, the
    // assessment and the three signatures.

    private val matchId = MatchId("m1")

    private fun player(
        ref: String,
        surname: String,
        team: TeamId,
        number: Int,
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

    private val novak = player("novak", "Novák", UiTestData.homeTeamId, 9)
    private val vlk = player("vlk", "Vlk", UiTestData.awayTeamId, 4)

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                    players = listOf(novak, vlk),
                    fixtures = listOf(UiTestData.fixture),
                    venues = listOf(UiTestData.venue),
                ),
            ),
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

    private fun lineup(
        side: TeamSide,
        player: Player,
    ): Lineup {
        val team = if (side == TeamSide.HOME) UiTestData.homeTeam else UiTestData.awayTeam
        val appearance =
            Appearance(
                id = AppearanceId("a-${player.ref}"),
                playerId = player.id,
                jerseyNumber = player.defaultJerseyNumber,
                reportedIdentification =
                    ReportedIdentification("59${player.defaultJerseyNumber?.value}", IdentificationSource.RP),
            )
        // No captain marked, so nobody's confirmation of the lineup is owed.
        return Lineup.wearing(side, team.id, listOf(appearance), team.primaryKit, captain = null, goalkeeper = null)
    }

    private fun ready(): Match {
        val at = Instant.parse("2026-08-31T20:05:00Z")
        return Match(
            id = matchId,
            fixtureId = UiTestData.fixtureId,
            groupId = UiTestData.groupId,
            status = MatchStatus.CONFIRMED,
            officials = RefereeAssignment(Official(PersonName.of("Vlk Jiří")), null, "Celtic THK"),
            homeLineup = lineup(TeamSide.HOME, novak),
            awayLineup = lineup(TeamSide.AWAY, vlk),
            goals = listOf(GoalEvent(Minute.Played(10), TeamSide.HOME, null, Score(1, 0), sequence = 1)),
            cards = CardsSection.NoneIssued,
            assessment =
                Assessment(
                    home = TeamAssessment(JerseyNumber(9), 0, true, true),
                    away = TeamAssessment(JerseyNumber(4), 0, true, true),
                    commentary = "Bez připomínek.",
                ),
            confirmations =
                ConfirmingParty.entries.map { Confirmation(it, at, PersonName.of("Vlk Jiří"), false) },
        )
    }

    private fun viewModel(
        matches: GatedMatchRepository,
        clipboard: ReportClipboard,
    ) = ExportViewModel(
        matchId = matchId,
        matches = matches,
        buildZouReport = BuildZouReport(league, noAddedPlayers),
        exportZou = ExportZou(),
        reportSender = UnavailableReportSender(),
        reportClipboard = clipboard,
    )

    private companion object {
        const val PIXELS_AT_360 = 945

        val LABELS =
            mapOf(
                "cs" to Labels("Uložit do zařízení", "Odeslat na PSMF", "Zkopírovat text zápisu"),
                "uk" to Labels("Зберегти на пристрій", "Надіслати до PSMF", "Скопіювати текст протоколу"),
            )
    }
}
