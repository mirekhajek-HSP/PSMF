package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.FixtureFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * RULE: **after the first load, nothing on the Zápasy tab blanks the
 * screen.** The filter and the current list stay on screen until the new
 * list is ready, then the list is swapped.
 *
 * The 0.2.0 build swapped the whole screen to *Načítání dat ligy…* on every
 * filter change. On a phone that was a flash on every tap; in the team
 * field it was worse, because the field is part of the screen that was
 * swapped out -- every keystroke threw away the field being typed in, and
 * the keyboard with it.
 *
 * The summaries read is held open with [GatedMatchRepository], so these
 * tests look at the screen at the exact moment the old one went blank:
 * after the change, before the new list exists.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FixturesNoFlashTest {
    @BeforeTest
    fun setUp() {
        // There is no platform Main on this host, and the ViewModel's scope
        // needs one. runComposeUiTest shares this dispatcher's scheduler, so
        // the ViewModel's delays run on the test's own clock.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typingInTheTeamFieldKeepsTheFieldTheKeyboardAndTheList() =
        runComposeUiTest {
            withLanguage("cs") {
                val matches = GatedMatchRepository()
                showFixtures(fixturesViewModel(InMemoryLeague(listOf(league)), matches))
                onNodeWithText("Kominíci").assertIsDisplayed()

                matches.hold()
                onNodeWithText("Tým").performTextInput("Kom")
                settle()

                // The new list is not ready: the old one is still there, and
                // so is the field, still focused -- which on a phone is what
                // keeps the keyboard up.
                onNodeWithText("Načítání dat ligy…").assertDoesNotExist()
                onNodeWithText("Kominíci").assertIsDisplayed()
                onNode(hasSetTextAction()).assertIsFocused().assertTextContains("Kom")

                matches.release()
                settle()

                onNode(hasSetTextAction()).assertIsFocused().assertTextContains("Kom")
                onNodeWithText("Kominíci").assertIsDisplayed()
            }
        }

    @Test
    fun choosingAPitchKeepsTheListUntilTheNewOneIsReadyThenSwapsIt() =
        runComposeUiTest {
            withLanguage("cs") {
                val matches = GatedMatchRepository()
                val viewModel = fixturesViewModel(InMemoryLeague(listOf(league)), matches)
                showFixtures(viewModel)
                onNodeWithText("Bohemians").assertIsDisplayed()

                matches.hold()
                viewModel.onFilterChanged(FixtureFilter(venue = UiTestData.venue.code))
                settle()

                onNodeWithText("Načítání dat ligy…").assertDoesNotExist()
                // Still the old list, including the row the new one drops.
                onNodeWithText("Bohemians").assertIsDisplayed()
                onNodeWithText("Tým").assertIsDisplayed()
                assertNotNull(viewModel.state.value.listing, "the list was taken away while the next one was built")

                matches.release()
                settle()

                onNodeWithText("Kominíci").assertIsDisplayed()
                onNodeWithText("Bohemians").assertDoesNotExist()
            }
        }

    @Test
    fun aTeamNameTypedAtSpeedBuildsOneListNotOnePerLetter() =
        runComposeUiTest {
            withLanguage("cs") {
                val matches = GatedMatchRepository()
                showFixtures(fixturesViewModel(InMemoryLeague(listOf(league)), matches))
                val before = matches.reads

                // Three keystrokes, closer together than the typing pause.
                onNodeWithText("Tým").performTextInput("K")
                mainClock.advanceTimeBy(KEYSTROKE_GAP_MILLIS)
                onNode(hasSetTextAction()).performTextInput("o")
                mainClock.advanceTimeBy(KEYSTROKE_GAP_MILLIS)
                onNode(hasSetTextAction()).performTextInput("m")
                settle()

                assertEquals(1, matches.reads - before, "one list per pause in the typing, not one per letter")
                onNode(hasSetTextAction()).assertTextContains("Kom")
            }
        }

    @Test
    fun aWaitShorterThanTheThresholdShowsNoSpinnerAndALongerOneDoes() =
        runComposeUiTest {
            withLanguage("cs") {
                val matches = GatedMatchRepository()
                val viewModel = fixturesViewModel(InMemoryLeague(listOf(league)), matches)
                showFixtures(viewModel)

                matches.hold()
                viewModel.onFilterChanged(FixtureFilter(venue = UiTestData.venue.code))
                mainClock.advanceTimeBy(FixturesViewModel.VISIBLE_WAIT.inWholeMilliseconds / 2)
                waitForIdle()
                onNodeWithContentDescription(LOADING).assertDoesNotExist()

                settle()
                onNodeWithContentDescription(LOADING).assertExists()
                // Veiled, not replaced: the list is still under the spinner.
                onNodeWithText("Bohemians").assertIsDisplayed()

                matches.release()
                settle()
                onNodeWithContentDescription(LOADING).assertDoesNotExist()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    private fun ComposeUiTest.showFixtures(viewModel: FixturesViewModel) {
        setContent {
            PsmfTheme {
                val state by viewModel.state.collectAsState()
                FixturesScreen(
                    state = state,
                    onFixtureSelected = viewModel::onFixtureSelected,
                    onRetry = viewModel::load,
                    onFilterChanged = viewModel::onFilterChanged,
                )
            }
        }
        settle()
    }

    /**
     * A second on the test clock, then idle: long enough for any pause the
     * ViewModel waits on to run out, so nothing below is passing only
     * because the work had not started yet.
     */
    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        waitForIdle()
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L

        /** Well inside [FixturesViewModel.TYPING_PAUSE]: a quick typist. */
        const val KEYSTROKE_GAP_MILLIS = 80L

        const val LOADING = "Načítání dat ligy…"

        val otherVenue = Venue(VenueCode("MIK"))

        val bohemians =
            UiTestData.awayTeam.copy(id = TeamId("bohemians"), ref = "bohemians", name = "Bohemians")

        val otherFixture: Fixture =
            UiTestData.fixture.copy(
                id = FixtureId("6k-r1-02"),
                ref = "6k-r1-02",
                venue = otherVenue.code,
                homeTeamId = bohemians.id,
                awayTeamId = UiTestData.awayTeamId,
            )

        val teams: List<Team> = listOf(UiTestData.homeTeam, UiTestData.awayTeam, bohemians)

        val league =
            LeagueGroup(
                season = UiTestData.season,
                group = UiTestData.group,
                teams = teams,
                players = emptyList(),
                fixtures = listOf(UiTestData.fixture, otherFixture),
                venues = listOf(UiTestData.venue, otherVenue),
            )
    }
}
