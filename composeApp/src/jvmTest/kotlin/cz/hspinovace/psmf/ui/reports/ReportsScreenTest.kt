package cz.hspinovace.psmf.ui.reports

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
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
    ) {
        setContent {
            PsmfTheme {
                ReportsScreen(ReportsUiState(listing), onOpenReport = onOpenReport, onPickFixture = onPickFixture)
            }
        }
    }

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
}
