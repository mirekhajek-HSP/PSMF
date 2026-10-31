package cz.hspinovace.psmf.ui.reports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ReportContents
import cz.hspinovace.psmf.usecase.ReportListing
import cz.hspinovace.psmf.usecase.ReportRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Zápis tab's first screen, from state (prompt 17, Gate 1). What the
 * list holds and in what order is `ObserveReports`'s, tested in `shared`;
 * this is what a row says, and the order the screen keeps.
 *
 * And the way a report is discarded from it (Gate 2): a control of its own on
 * the row, and the question it asks first, in its words.
 */
@OptIn(ExperimentalTestApi::class)
class ReportsScreenTest {
    private fun row(
        id: String,
        status: MatchStatus,
        fixture: Fixture = UiTestData.fixture,
    ) = ReportRow(
        matchId = MatchId(id),
        status = status,
        fixture = fixture,
        group = UiTestData.group,
        homeTeam = UiTestData.homeTeam,
        awayTeam = UiTestData.awayTeam,
    )

    private val earlier =
        UiTestData.fixture.copy(
            id = FixtureId("6k-r0-01"),
            ref = "6k-r0-01",
            date = LocalDate(2026, 8, 24),
            time = LocalTime(18, 30),
        )

    private fun ComposeUiTest.show(
        listing: ReportListing?,
        onOpenReport: (ReportRow) -> Unit = {},
        onPickFixture: () -> Unit = {},
        discarding: DiscardQuestion? = null,
        onEvent: (ReportsEvent) -> Unit = {},
    ) {
        setContent {
            PsmfTheme {
                ReportsScreen(
                    ReportsUiState(listing, discarding),
                    onOpenReport = onOpenReport,
                    onPickFixture = onPickFixture,
                    onEvent = onEvent,
                )
            }
        }
    }

    /** What a report holds, as `DiscardReport` would say: nothing, unless told. */
    private fun contents(
        row: ReportRow,
        header: Boolean = false,
        lineups: Int = 0,
        goals: Int = 0,
        cards: Int = 0,
        assessment: Boolean = false,
        signatures: Int = 0,
    ) = ReportContents(row.matchId, row.status, header, lineups, goals, cards, assessment, signatures)

    /** The list with one report on it, and the question about that report asked. */
    private fun ComposeUiTest.asking(
        row: ReportRow,
        contents: ReportContents,
        onEvent: (ReportsEvent) -> Unit = {},
    ) = show(ReportListing(working = listOf(row)), discarding = DiscardQuestion(row, contents), onEvent = onEvent)

    private fun ComposeUiTest.top(text: String): Float =
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aRowSaysTheTeamsTheGroupTheDateTheTimeAndTheStatus() =
        runComposeUiTest {
            withLanguage("cs") {
                show(ReportListing(working = listOf(row("m1", MatchStatus.SETUP))))

                listOf("Kominíci", "Sp. Sumýš", "6K", "31. 8.", "19:00", "Rozepsáno").forEach {
                    onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
                }
            }
        }

    @Test
    fun eachStatusIsInTheWordsOfTheBadgeInZapasy() =
        runComposeUiTest {
            withLanguage("cs") {
                show(
                    ReportListing(
                        working =
                            listOf(
                                row("m1", MatchStatus.SETUP),
                                row("m2", MatchStatus.IN_PROGRESS),
                                row("m3", MatchStatus.FINISHED),
                            ),
                        confirmed = listOf(row("m4", MatchStatus.CONFIRMED, earlier)),
                    ),
                )

                listOf("Rozepsáno", "Probíhá", "Dohráno", "Potvrzeno").forEach {
                    onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
                }
            }
        }

    @Test
    fun whatIsBeingWorkedOnIsAboveWhatIsSignedEachUnderItsHeading() =
        runComposeUiTest {
            withLanguage("cs") {
                // The signed one kicked off later, so a list sorted by date
                // alone would put it first.
                val laterSigned = earlier.copy(date = LocalDate(2026, 9, 30))
                show(
                    ReportListing(
                        working = listOf(row("m1", MatchStatus.FINISHED)),
                        confirmed = listOf(row("m2", MatchStatus.CONFIRMED, laterSigned)),
                    ),
                )

                val tops = listOf("Rozpracované", "Dohráno", "Potvrzené", "Potvrzeno").map { top(it) }
                assertEquals(tops.sorted(), tops, "headings and rows out of order: $tops")
            }
        }

    @Test
    fun rowsKeepTheOrderTheyAreGivenIn() =
        runComposeUiTest {
            // Latest kickoff first is decided in `shared`; the screen must
            // not undo it.
            withLanguage("cs") {
                show(
                    ReportListing(
                        working = listOf(row("m1", MatchStatus.SETUP), row("m2", MatchStatus.FINISHED, earlier)),
                    ),
                )

                assertTrue(top("31. 8.") < top("24. 8."))
            }
        }

    @Test
    fun aGroupWithNothingInItHasNoHeading() =
        runComposeUiTest {
            withLanguage("cs") {
                show(ReportListing(confirmed = listOf(row("m1", MatchStatus.CONFIRMED))))

                onNodeWithText("Potvrzené").assertIsDisplayed()
                onNodeWithText("Rozpracované").assertDoesNotExist()
            }
        }

    @Test
    fun tappingARowOpensThatReport() =
        runComposeUiTest {
            val opened = mutableListOf<ReportRow>()
            val played = row("m2", MatchStatus.FINISHED, earlier)
            withLanguage("cs") {
                show(
                    ReportListing(working = listOf(row("m1", MatchStatus.SETUP), played)),
                    onOpenReport = { opened += it },
                )

                onNodeWithText("Dohráno").performClick()

                assertEquals(listOf(played), opened)
            }
        }

    @Test
    fun withNoReportItSaysWhatZadnyZapisSaidAndLeadsToZapasy() =
        runComposeUiTest {
            var picked = 0
            withLanguage("cs") {
                show(ReportListing(), onPickFixture = { picked++ })

                onNodeWithText("Žádný zápis").assertIsDisplayed()
                onNodeWithText("Zápis začíná výběrem zápasu v Zápasech.").assertIsDisplayed()
                onNodeWithText("Vybrat zápas").performClick()

                assertEquals(1, picked)
            }
        }

    @Test
    fun beforeTheFirstAnswerItDoesNotSayThereIsNoReport() =
        runComposeUiTest {
            // A flash of "Žádný zápis" over a list about to arrive is the
            // tester's complaint for a frame.
            withLanguage("cs") {
                show(listing = null)

                onNodeWithText("Žádný zápis").assertDoesNotExist()
                onNodeWithText("Vybrat zápas").assertDoesNotExist()
            }
        }

    @Test
    fun inUkrainianTheHeadingsAndTheStatusesAreUkrainian() =
        runComposeUiTest {
            withLanguage("uk") {
                show(
                    ReportListing(
                        working = listOf(row("m1", MatchStatus.SETUP), row("m2", MatchStatus.IN_PROGRESS)),
                        confirmed = listOf(row("m3", MatchStatus.CONFIRMED, earlier)),
                    ),
                )

                listOf("У роботі", "Підтверджені", "Розпочато", "Триває", "Підтверджено").forEach {
                    onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
                }
            }
        }

    // ---------------------------------------------------------------------
    // Discarding (Gate 2)
    // ---------------------------------------------------------------------

    @Test
    fun zahoditIsAControlOfItsOwnAndTappingTheRowNeverAsksToDiscard() =
        runComposeUiTest {
            // Opening a report must never be able to remove one.
            val opened = mutableListOf<ReportRow>()
            val events = mutableListOf<ReportsEvent>()
            val started = row("m1", MatchStatus.SETUP)
            val played = row("m2", MatchStatus.FINISHED, earlier)
            withLanguage("cs") {
                show(
                    ReportListing(working = listOf(started, played)),
                    onOpenReport = { opened += it },
                    onEvent = { events += it },
                )

                onNodeWithText("Dohráno").performClick()
                assertEquals(listOf(played), opened)
                assertEquals(emptyList(), events, "tapping the row asked to discard")

                onAllNodesWithText("Zahodit")[1].performClick()
                assertEquals(listOf<ReportsEvent>(ReportsEvent.DiscardRequested(played)), events)
                assertEquals(listOf(played), opened, "Zahodit opened the report")
            }
        }

    @Test
    fun theQuestionNamesTheMatchSaysWhatGoesAndThatItCannotBeUndone() =
        runComposeUiTest {
            val played = row("m1", MatchStatus.FINISHED)
            withLanguage("cs") {
                asking(played, contents(played, header = true, lineups = 2, goals = 3, cards = 1))

                onNodeWithText("Zahodit zápis?").assertIsDisplayed()
                onNodeWithText("Kominíci – Sp. Sumýš, 31. 8. v 19:00").assertIsDisplayed()
                onNodeWithText("Zahodí se: hlavička, obě sestavy, 3 góly, 1 karta.").assertIsDisplayed()
                onNodeWithText("Zahození nejde vrátit zpět.").assertIsDisplayed()
                // Not signed, so nothing about having been signed or sent.
                onNodeWithText("podepsali", substring = true).assertDoesNotExist()
                onNodeWithText("odeslán", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun everyCountIsInWords() =
        runComposeUiTest {
            // Czech has three forms for a count, and the list names each part
            // only when the report holds it.
            val played = row("m1", MatchStatus.FINISHED)
            withLanguage("cs") {
                asking(played, contents(played, lineups = 1, goals = 5, cards = 2, assessment = true, signatures = 1))

                onNodeWithText("Zahodí se: jedna sestava, 5 gólů, 2 karty, hodnocení, 1 podpis.").assertIsDisplayed()
            }
        }

    @Test
    fun aReportWithNothingInItSaysSo() =
        runComposeUiTest {
            // The case the question is mostly for: a tap on the wrong fixture.
            val started = row("m1", MatchStatus.SETUP)
            withLanguage("cs") {
                asking(started, contents(started))

                onNodeWithText("Zatím v něm nic není vyplněné.").assertIsDisplayed()
                onNodeWithText("Zahodí se:", substring = true).assertDoesNotExist()
                onNodeWithText("Zahození nejde vrátit zpět.").assertIsDisplayed()
            }
        }

    @Test
    fun aSignedReportsQuestionSaysItIsSignedMayHaveBeenSentAndThatCopiesStay() =
        runComposeUiTest {
            val signed = row("m1", MatchStatus.CONFIRMED)
            withLanguage("cs") {
                asking(signed, contents(signed, header = true, lineups = 2, goals = 1, signatures = 3))

                onNodeWithText(SIGNED_CS).assertIsDisplayed()
                onNodeWithText("Zahodí se: hlavička, obě sestavy, 1 gól, 3 podpisy.").assertIsDisplayed()
            }
        }

    @Test
    fun theAnswersAreKeepAndYes() =
        runComposeUiTest {
            val events = mutableListOf<ReportsEvent>()
            val signed = row("m1", MatchStatus.CONFIRMED)
            withLanguage("cs") {
                asking(signed, contents(signed, signatures = 3), onEvent = { events += it })

                onNodeWithText("Ponechat zápis").performClick()
                mainClock.advanceTimeBy(ARMING.inWholeMilliseconds)
                onNodeWithText("Ano, zahodit").performClick()

                assertEquals(listOf(ReportsEvent.DiscardDismissed, ReportsEvent.DiscardConfirmed), events)
            }
        }

    @Test
    fun theYesCannotBeTheTapThatComesAsTheQuestionAppears() =
        runComposeUiTest {
            // The second tap of a double tap on Zahodit, or a reflex: wherever
            // the yes is drawn, it is not armed yet. Keep is.
            val events = mutableListOf<ReportsEvent>()
            val started = row("m1", MatchStatus.SETUP)
            mainClock.autoAdvance = false
            withLanguage("cs") {
                asking(started, contents(started), onEvent = { events += it })
                mainClock.advanceTimeByFrame()

                onNodeWithText("Ano, zahodit").assertIsNotEnabled().performClick()
                onNodeWithText("Ponechat zápis").assertIsEnabled()
                assertEquals(emptyList(), events, "a tap as the question appeared discarded the report")

                mainClock.advanceTimeBy(ARMING.inWholeMilliseconds)
                onNodeWithText("Ano, zahodit").assertIsEnabled().performClick()
                assertEquals(listOf<ReportsEvent>(ReportsEvent.DiscardConfirmed), events)
            }
        }

    @Test
    fun keepIsAtTheEndWhereTheOtherQuestionsPutTheirYes() =
        runComposeUiTest {
            // Side by side, as on a wide screen: the end of the row is where
            // a tap out of habit goes, so keep is there.
            val signed = row("m1", MatchStatus.CONFIRMED)
            withLanguage("cs") {
                asking(signed, contents(signed, signatures = 3))

                val keep = onNodeWithText("Ponechat zápis").fetchSemanticsNode().boundsInRoot
                val yes = onNodeWithText("Ano, zahodit").fetchSemanticsNode().boundsInRoot
                assertTrue(keep.left > yes.right, "keep at $keep is not after the yes at $yes")
            }
        }

    @Test
    fun atPhoneWidthKeepIsOnTopWhereTheOtherQuestionsPutTheirYes() =
        runDesktopComposeUiTest(width = PHONE_WIDTH_PX, height = PHONE_HEIGHT_PX) {
            // 360 dp: the two do not fit side by side, Material stacks them and
            // puts its confirm slot on top. Keep is in that slot.
            val signed = row("m1", MatchStatus.CONFIRMED)
            withLanguage("cs") {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        PsmfTheme {
                            ReportsScreen(
                                ReportsUiState(
                                    ReportListing(confirmed = listOf(signed)),
                                    DiscardQuestion(signed, contents(signed, signatures = 3)),
                                ),
                                onOpenReport = {},
                                onPickFixture = {},
                                onEvent = {},
                            )
                        }
                    }
                }

                val keep = onNodeWithText("Ponechat zápis").fetchSemanticsNode().boundsInRoot
                val yes = onNodeWithText("Ano, zahodit").fetchSemanticsNode().boundsInRoot
                assertTrue(keep.bottom <= yes.top, "keep at $keep is not above the yes at $yes")
            }
        }

    @Test
    fun tappingBesideTheQuestionKeepsTheReport() =
        runComposeUiTest {
            // The corner of the window, well clear of a question drawn in its
            // middle. Back on a phone is the same dismissal; the harness's
            // Escape reaches no dialog, so that is the phone's to show.
            val events = mutableListOf<ReportsEvent>()
            val started = row("m1", MatchStatus.SETUP)
            withLanguage("cs") {
                asking(started, contents(started), onEvent = { events += it })

                onAllNodes(isRoot())[0].performTouchInput { click(Offset(3f, 3f)) }

                assertEquals(listOf<ReportsEvent>(ReportsEvent.DiscardDismissed), events)
            }
        }

    @Test
    fun inUkrainianTheQuestionIsUkrainian() =
        runComposeUiTest {
            val signed = row("m1", MatchStatus.CONFIRMED)
            withLanguage("uk") {
                asking(signed, contents(signed, header = true, lineups = 2, goals = 3, cards = 1, signatures = 3))

                listOf(
                    "Видалити протокол?",
                    "Kominíci – Sp. Sumýš, 31. 8. о 19:00",
                    "Буде видалено: заголовок, обидва склади, 3 голи, 1 картка, 3 підписи.",
                    "Видалення не можна скасувати.",
                    "Так, видалити",
                    "Залишити протокол",
                ).forEach { onNodeWithText(it).assertIsDisplayed() }
                onNodeWithText("можливо, вже надіслано до PSMF", substring = true).assertIsDisplayed()
            }
        }

    private companion object {
        /** 360 × 720 dp at density 2. */
        const val PHONE_WIDTH_PX = 720
        const val PHONE_HEIGHT_PX = 1440

        const val SIGNED_CS =
            "Zápis podepsali oba kapitáni i rozhodčí a mohl už být odeslán na PSMF. " +
                "Soubory uložené do složky v zařízení a už odeslané e-maily zůstanou, jak jsou."
    }
}
