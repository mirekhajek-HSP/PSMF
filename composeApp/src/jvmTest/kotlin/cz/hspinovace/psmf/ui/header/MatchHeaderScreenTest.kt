package cz.hspinovace.psmf.ui.header

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.HeaderProblem
import cz.hspinovace.psmf.usecase.MatchHeaderEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Screen 2's smoke test. Page 1 of the ZoU: what comes from the fixture,
 * what the referee supplies, and the one field that costs money.
 */
@OptIn(ExperimentalTestApi::class)
class MatchHeaderScreenTest {
    private val headline =
        FixtureHeadline(
            venueCode = "ZAKOS",
            date = UiTestData.fixture.date,
            time = UiTestData.fixture.time,
            leagueCode = "6K",
            groupName = "6. liga K",
            homeTeamName = UiTestData.homeTeam.name,
            awayTeamName = UiTestData.awayTeam.name,
        )

    private fun state(
        entry: MatchHeaderEntry = MatchHeaderEntry(),
        problems: List<HeaderProblem> = emptyList(),
        otherTeamSelected: Boolean = false,
    ) = MatchHeaderUiState(
        loading = false,
        fixture = headline,
        entry = entry,
        teamOptions = listOf(UiTestData.homeTeam.name, UiTestData.awayTeam.name, "Sokol"),
        otherTeamSelected = otherTeamSelected,
        problems = problems,
    )

    @Test
    fun showsWhatTheFixtureAlreadyKnowsAndTheRefereeNeverTypes() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Kominíci – Sp. Sumýš").assertIsDisplayed()
            onNodeWithText("ZAKOS").assertIsDisplayed()
            onNodeWithText("31. 8. 2026").assertIsDisplayed()
            onNodeWithText("19:00").assertIsDisplayed()
            onNodeWithText("6K · 6. liga K").assertIsDisplayed()
        }

    @Test
    fun theDelegatingTeamIsExplainedRatherThanJustLabelled() =
        runComposeUiTest {
            // The field most easily mistaken for "one of the teams playing",
            // and the one whose consequence lands on somebody else.
            withLanguage("en") {
                setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = {}) } }
            }

            // A fragment, not the whole paragraph: the wording will be
            // rewritten, the fact that it names the consequence should not be.
            onNodeWithText("charged to this team", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        }

    @Test
    fun eachLicensedHireSwitchSaysWhichOfficialItBelongsTo() =
        runComposeUiTest {
            // Two switches reading "Placený rozhodčí (R)" one under the
            // other say nothing about which of the names above them they
            // are for. The emulator showed that; no earlier test could.
            withLanguage("cs") {
                setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Rozhodčí je placený (R)").performScrollTo().assertIsDisplayed()
            onNodeWithText("Asistent je placený (R)").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun neitherPlayingTeamAppearsAmongTheDelegatingTeamChips() =
        runComposeUiTest {
            // The state a real fixture produces. Tapping one of the two
            // teams on screen is the likeliest mistake on this screen, and
            // it lands a fine on the wrong club.
            setContent {
                PsmfTheme {
                    MatchHeaderScreen(
                        state = state().copy(teamOptions = listOf("Sokol", "AC Stromovka")),
                        onEvent = {},
                    )
                }
            }

            onNodeWithText("Sokol").performScrollTo().assertIsDisplayed()
            onNodeWithText(UiTestData.homeTeam.name).assertDoesNotExist()
        }

    @Test
    fun tappingATeamChipReportsIt() =
        runComposeUiTest {
            val events = mutableListOf<MatchHeaderEvent>()
            setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = events::add) } }

            onNodeWithText("Sokol").performScrollTo().performClick()

            assertEquals<List<MatchHeaderEvent>>(listOf(MatchHeaderEvent.DelegatingTeamPicked("Sokol")), events)
        }

    @Test
    fun continueAlwaysWorksAndReportsItself() =
        runComposeUiTest {
            // Never disabled: a greyed-out button with no explanation is the
            // worst thing to meet in the dark.
            val events = mutableListOf<MatchHeaderEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = events::add) } }
            }

            onNodeWithText("Pokračovat na sestavy").performScrollTo().performClick()

            assertEquals<List<MatchHeaderEvent>>(listOf(MatchHeaderEvent.ContinuePressed), events)
        }

    @Test
    fun aMissingDelegatingTeamIsSaidOutLoud() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        MatchHeaderScreen(
                            state = state(problems = listOf(HeaderProblem.DelegatingTeamMissing)),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Vyberte delegující tým.").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun aCyrillicNameIsExplainedRatherThanSilentlyRefused() =
        runComposeUiTest {
            withLanguage("uk") {
                setContent {
                    PsmfTheme {
                        MatchHeaderScreen(
                            state =
                                state(
                                    entry = MatchHeaderEntry(refereeName = "Юрій Вовк"),
                                    problems = listOf(HeaderProblem.RefereeNameNotLatin),
                                ),
                            onEvent = {},
                        )
                    }
                }
            }

            // The app reads in Ukrainian; the report does not.
            onNodeWithText("Імена записуються латиницею, як в обліку PSMF.").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun choosingAnotherTeamOpensATextFieldBecauseSubstitutesWriteTheirOwn() =
        runComposeUiTest {
            val events = mutableListOf<MatchHeaderEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        MatchHeaderScreen(
                            state = state(otherTeamSelected = true),
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNodeWithText("Název týmu").performScrollTo().performTextInput("Kominici B")

            assertTrue(events.any { it == MatchHeaderEvent.DelegatingTeamTyped("Kominici B") })
        }

    @Test
    fun typingTheRefereeNameReportsEveryChange() =
        runComposeUiTest {
            // Every keystroke is an event, because the header is written
            // through as it is typed rather than on a save button.
            val events = mutableListOf<MatchHeaderEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { MatchHeaderScreen(state = state(), onEvent = events::add) } }
            }

            onNodeWithText("Rozhodčí").performScrollTo().performTextInput("Vlk")

            assertTrue(events.any { it == MatchHeaderEvent.RefereeNameChanged("Vlk") })
        }
}
