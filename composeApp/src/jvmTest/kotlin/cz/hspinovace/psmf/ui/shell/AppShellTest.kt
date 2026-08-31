package cz.hspinovace.psmf.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.console.ConsoleScreen
import cz.hspinovace.psmf.ui.console.ConsoleUiState
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")
private val MATCH = MatchId("m1")
private const val BADGE = "Zápis právě probíhá"

/**
 * The frame, driven by the real [AppNavigator].
 *
 * The demo shipped as an eight-stop wizard, so these are the properties the
 * frame exists to add: that Settings and the fixture list are reachable
 * without abandoning the report, and that going to them and coming back
 * costs the referee nothing.
 *
 * **`withLanguage` wraps the whole test, not just `setContent`.** Compose
 * resources resolve each string the first time it is composed, so anything
 * that appears *after* a tap resolves in whatever locale the host happens
 * to be in by then. Asserting Czech outside the block passes for the tab
 * bar, which was composed inside it, and fails for the screen the tap
 * opened — which looks exactly like a navigation bug and is not one.
 */
@OptIn(ExperimentalTestApi::class)
class AppShellTest {
    // -----------------------------------------------------------------
    // The bar
    // -----------------------------------------------------------------

    @Test
    fun allFourTabsAreThere() =
        runComposeUiTest {
            val navigator = AppNavigator()
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("Zápasy").assertIsDisplayed()
                onNodeWithText("Zápis").assertIsDisplayed()
                onNodeWithText("Týmy").assertIsDisplayed()
                onNodeWithText("Nastavení").assertIsDisplayed()
            }
        }

    @Test
    fun theTabsAreTranslatedAndTheLongestLanguageIsUkrainian() =
        runComposeUiTest {
            // `Налаштування` against `Nastavení` is why the bar is not
            // Material's, which gives a label one line in a quarter of the
            // screen width and clips whatever does not fit.
            val navigator = AppNavigator()
            withLanguage("uk") {
                setContent { Harness(navigator) }

                onNodeWithText("Матчі").assertIsDisplayed()
                onNodeWithText("Протокол").assertIsDisplayed()
                onNodeWithText("Команди").assertIsDisplayed()
                onNodeWithText("Налаштування").assertIsDisplayed()
            }
        }

    @Test
    fun theReportTabIsNotBadgedWhenNothingIsUnderWay() =
        runComposeUiTest {
            val navigator = AppNavigator()
            withLanguage("cs") {
                setContent { Harness(navigator, reportInProgress = false) }

                // Asserted against a bar that is definitely on screen, or
                // this would pass just as well with no tab bar at all.
                onNodeWithText("Zápis").assertIsDisplayed()
                onNodeWithContentDescription(BADGE, useUnmergedTree = true).assertDoesNotExist()
            }
        }

    @Test
    fun aMatchUnderWayIsVisibleFromEveryTab() =
        runComposeUiTest {
            // The reason the badge exists: a referee who opens Settings
            // mid-half has to see that there is a report to go back to.
            val navigator = AppNavigator()
            withLanguage("cs") {
                setContent { Harness(navigator, reportInProgress = true) }

                onNodeWithContentDescription(BADGE, useUnmergedTree = true).assertIsDisplayed()

                onNodeWithText("Nastavení").performClick()
                onNodeWithText("SETTINGS").assertIsDisplayed()
                onNodeWithContentDescription(BADGE, useUnmergedTree = true).assertIsDisplayed()

                onNodeWithText("Týmy").performClick()
                onNodeWithContentDescription(BADGE, useUnmergedTree = true).assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // What the tabs are for
    // -----------------------------------------------------------------

    @Test
    fun settingsIsReachableWithoutAbandoningTheReport() =
        runComposeUiTest {
            val navigator = AppNavigator()
            navigator.openReport(Destination.Console(MATCH))
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("34´").assertIsDisplayed()

                onNodeWithText("Nastavení").performClick()
                onNodeWithText("SETTINGS").assertIsDisplayed()

                onNodeWithText("Zápis").performClick()
                onNodeWithText("34´").assertIsDisplayed()
            }
        }

    @Test
    fun switchingTabsMidMatchAndComingBackShowsTheSameClock() =
        runComposeUiTest {
            // Nothing ticks. The reading is `now - kickoffAt`, so leaving
            // the console cannot drift it and returning cannot reset it —
            // the same property that makes the clock survive process death,
            // which is what tabs needed and got for free.
            val navigator = AppNavigator()
            navigator.openReport(Destination.Console(MATCH))
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("34´").assertIsDisplayed()

                onNodeWithText("Týmy").performClick()
                onNodeWithText("Nastavení").performClick()
                onNodeWithText("Zápasy").performClick()
                onNodeWithText("Zápis").performClick()

                // Only the console draws a minute, so this also says the
                // console itself came back rather than some other screen.
                onNodeWithText("34´").assertIsDisplayed()
            }
        }

    @Test
    fun theReportTabSaysWhatItIsForBeforeAReportIsOpened() =
        runComposeUiTest {
            val navigator = AppNavigator()
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("Zápis").performClick()

                onNodeWithText("Žádný zápis").assertIsDisplayed()
                onNodeWithText("Vybrat zápas").assertIsDisplayed()
            }
        }

    @Test
    fun theEmptyReportTabLeadsBackToTheFixtureList() =
        runComposeUiTest {
            val navigator = AppNavigator()
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("Zápis").performClick()
                onNodeWithText("Vybrat zápas").performClick()

                onNodeWithText("FIXTURES").assertIsDisplayed()
            }
        }

    @Test
    fun theFirstStepOfAReportOffersNoBackButton() =
        runComposeUiTest {
            // It is the root of its tab; the tab bar is the way out.
            val navigator = AppNavigator()
            navigator.openReport(Destination.Console(MATCH))
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("34´").assertIsDisplayed()
                onNodeWithText("Zpět").assertDoesNotExist()
            }
        }

    @Test
    fun backWithinTheReportReturnsToThePreviousStep() =
        runComposeUiTest {
            val navigator = AppNavigator()
            navigator.openReport(Destination.Console(MATCH))
            navigator.goTo(Destination.Assessment(MATCH))
            withLanguage("cs") {
                setContent { Harness(navigator) }

                onNodeWithText("Zpět").assertIsDisplayed()
                onNodeWithText("Zpět").performClick()

                onNodeWithText("34´").assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    /**
     * The shell wired to the real navigator, with stand-ins for the screens
     * that are not what is being tested. The console is real, because the
     * clock is the thing tabs could plausibly have broken.
     *
     * The navigator's `StateFlow` is mirrored into Compose state by hand
     * rather than through `collectAsState`. That is a test concern, not a
     * design one: collecting a flow puts a coroutine hop between a tap and
     * the frame that reflects it, and the test host does not reliably
     * advance past it. `App` uses `collectAsStateWithLifecycle`, which is
     * right on a device and would make these assertions race.
     */
    @Composable
    private fun Harness(
        navigator: AppNavigator,
        reportInProgress: Boolean = false,
    ) {
        var navigation by remember { mutableStateOf(navigator.state.value) }
        PsmfTheme {
            AppShell(
                navigation = navigation,
                title = navigation.current::class.simpleName.orEmpty(),
                reportInProgress = reportInProgress,
                onSelectTab = {
                    navigator.select(it)
                    navigation = navigator.state.value
                },
                onBack = {
                    navigator.back()
                    navigation = navigator.state.value
                },
            ) { modifier ->
                when (navigation.current) {
                    is Destination.Console -> {
                        ConsoleScreen(
                            state = consoleState(),
                            now = KICKOFF + 34.minutes,
                            onEvent = {},
                            modifier = modifier,
                        )
                    }

                    Destination.NoReport -> {
                        NoReportScreen(
                            onPickFixture = {
                                navigator.select(Tab.FIXTURES)
                                navigation = navigator.state.value
                            },
                            modifier = modifier,
                        )
                    }

                    Destination.Fixtures -> {
                        Text("FIXTURES", modifier)
                    }

                    Destination.Settings -> {
                        Text("SETTINGS", modifier)
                    }

                    else -> {
                        Text("OTHER", modifier)
                    }
                }
            }
        }
    }

    private fun consoleState(): ConsoleUiState =
        ConsoleUiState(
            loading = false,
            entry =
                ConsoleEntry(
                    home =
                        ConsoleTeam(
                            side = TeamSide.HOME,
                            teamName = UiTestData.homeTeam.name,
                            rows =
                                listOf(
                                    ConsoleRow(
                                        appearanceId = AppearanceId("a-novak"),
                                        jerseyNumber = JerseyNumber(9),
                                        name = PlayerName(PersonName.of("Novák"), PersonName.of("Jan")),
                                        dismissed = false,
                                        yellowsInThisMatch = 0,
                                    ),
                                ),
                        ),
                    away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
                    score = Score.GOALLESS,
                    kickoffAt = KICKOFF,
                    status = MatchStatus.IN_PROGRESS,
                    log = emptyList(),
                    powerPlays = emptyList(),
                ),
        )
}
