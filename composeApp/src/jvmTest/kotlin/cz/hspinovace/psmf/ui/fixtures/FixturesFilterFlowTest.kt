package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.format.asDayAndMonth
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.shell.AppShell
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.DateWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Zápasy tab as a referee uses it, at the size of the real league
 * (DECISIONS 2026-10-08): the ViewModel and the use case are real, the
 * league is [LeagueSix] and today is pinned to its Thursday.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FixturesFilterFlowTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------
    // The defect, in the owner's words: no matches at all
    // -----------------------------------------------------------------

    @Test
    fun onASmallPhoneTheTabOpensWithAMatchInView() = aMatchIsInViewOnASmallPhone("cs")

    @Test
    fun onASmallPhoneTheTabOpensWithAMatchInViewInUkrainianToo() = aMatchIsInViewOnASmallPhone("uk")

    /**
     * The whole shell -- top bar, tab bar -- on a 360 × 640 dp phone less
     * its status and navigation bars, with the default filter. The first
     * row must lie entirely inside the list's viewport: on 0.2.0 the filter
     * alone was taller than the screen.
     */
    private fun aMatchIsInViewOnASmallPhone(language: String) =
        runComposeUiTest {
            withLanguage(language) {
                val viewModel =
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today)
                val navigation = AppNavigator().state.value
                setContent {
                    PsmfTheme {
                        Box(Modifier.size(SMALL_PHONE_WIDTH, SMALL_PHONE_HEIGHT)) {
                            AppShell(
                                navigation = navigation,
                                title = "Zápasy",
                                reportInProgress = false,
                                onSelectTab = {},
                                onBack = {},
                            ) { modifier -> Fixtures(viewModel, modifier) }
                        }
                    }
                }
                settle()

                val first =
                    viewModel.state.value.listing!!
                        .sections
                        .first()
                        .rows
                        .first()
                val row = onAllNodesWithText(first.homeTeam.name)[0].getBoundsInRoot()
                val list = onNode(hasScrollAction()).getBoundsInRoot()
                assertTrue(
                    row.top >= list.top && row.bottom <= list.bottom,
                    "first row $row is not inside the list $list",
                )
            }
        }

    // -----------------------------------------------------------------
    // The cascade
    // -----------------------------------------------------------------

    @Test
    fun theCascadeAsARefereeDrivesIt() =
        runComposeUiTest {
            withLanguage("cs") {
                val viewModel = leagueSixWithASeventh()
                show(viewModel)
                val onlyInK = (LeagueSix.pitchesOf('K') - LeagueSix.pitchesOf('J') - LeagueSix.pitchesOf('L')).first()
                val pitch = LeagueSix.pitches.single { it.code == onlyInK }

                // No league, no group to choose.
                onNodeWithText("Skupina").assertDoesNotExist()

                choose("Liga", "6. liga")
                onNodeWithText("Skupina").assertTextContains("Celá 6. liga")

                choose("Skupina", "6. liga K")
                choose("Hřiště", pitch.name!!)
                onNodeWithText("Hřiště").assertTextContains("${onlyInK.value} · ${pitch.name}")

                // A different league: the group and the pitch both clear.
                choose("Liga", "7. liga")
                onNodeWithText("Skupina").assertTextContains("Celá 7. liga")
                onNodeWithText("Hřiště").assertTextContains("Všechna hřiště")
                assertNull(viewModel.state.value.filter.groupId)
                assertNull(viewModel.state.value.filter.venue)
            }
        }

    // -----------------------------------------------------------------
    // Dates
    // -----------------------------------------------------------------

    @Test
    fun upcomingHidesYesterdaysFixturesButNotYesterdaysUnconfirmedReport() =
        runComposeUiTest {
            withLanguage("cs") {
                // Group C plays three fixtures yesterday; one has a report under way.
                val yesterdays = LeagueSix.group('C').fixtures.filter { it.date == LeagueSix.yesterday }
                val started = yesterdays.first()
                val report = Match(MatchId("m1"), started.id, started.groupId, status = MatchStatus.IN_PROGRESS)
                val viewModel =
                    fixturesViewModel(
                        InMemoryLeague(LeagueSix.groups),
                        GatedMatchRepository(listOf(report)),
                        LeagueSix.today,
                    )
                show(viewModel)
                choose("Liga", "6. liga")
                choose("Skupina", "6. liga C")

                // Nadcházející: of yesterday's three, only the one in progress.
                onAllNodesWithText("7. 10.").assertCountEquals(1)
                onNodeWithText("Probíhá").assertIsDisplayed()
                assertEquals(
                    listOf(started.id),
                    viewModel.rows().filter { it.fixture.date == LeagueSix.yesterday }.map { it.fixture.id },
                )

                // Odehrané: all three, the newest round first.
                choose("Termín", "Odehrané")
                assertEquals(DateWindow.PLAYED, viewModel.state.value.filter.dates)
                onAllNodesWithText("7. 10.").assertCountEquals(yesterdays.size)
            }
        }

    // -----------------------------------------------------------------
    // The shape of the list
    // -----------------------------------------------------------------

    @Test
    fun severalGroupsAreHeadedByDayEachRowWithItsGroupAndOneGroupByRound() =
        runComposeUiTest {
            withLanguage("cs") {
                val viewModel =
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today)
                show(viewModel)

                // Several groups: today first, called today, and the group on each row.
                onNodeWithText("Dnes · čtvrtek 8. 10.").assertIsDisplayed()
                val first = viewModel.rows().first()
                onAllNodesWithText(first.homeTeam.name)[0].assertTextContains(first.group.reportCode)

                // One group: rounds, and no group code on the rows.
                choose("Liga", "6. liga")
                choose("Skupina", "6. liga K")
                val round =
                    viewModel
                        .rows()
                        .first()
                        .fixture.round
                onNodeWithText("$round. kolo").assertIsDisplayed()
                onAllNodesWithText("6K").assertCountEquals(0)
            }
        }

    @Test
    fun anEmptyResultSaysSoWithTheFilterStillThere() =
        runComposeUiTest {
            withLanguage("cs") {
                show(fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today))

                onNodeWithText("Tým").performTextInput("nikdo takový")
                settle()

                onNodeWithText("Filtru neodpovídá žádný zápas.").assertIsDisplayed()
                onNodeWithText("Tým").assertIsDisplayed()
                onNodeWithText("Zrušit filtr").assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // The filter scrolls away, and the button brings it back
    // -----------------------------------------------------------------

    @Test
    fun theButtonBackToTheFilterAppearsOnlyOnceTheFilterIsOutOfView() =
        runComposeUiTest {
            withLanguage("cs") {
                show(
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today),
                    phone = true,
                )
                onNodeWithContentDescription(BACK_TO_FILTER).assertDoesNotExist()

                // A little way down: the filter is partly in view, so no button.
                onNode(hasScrollAction()).performTouchInput { swipeUp(startY = centerY, endY = centerY - SHORT_SWIPE) }
                settle()
                onNodeWithText("Tým").assertExists()
                onNodeWithContentDescription(BACK_TO_FILTER).assertDoesNotExist()

                // Past it: the button, and the line saying what the list is narrowed to.
                onNode(hasScrollAction()).performScrollToIndex(PAST_THE_FILTER)
                settle()
                onNodeWithContentDescription(BACK_TO_FILTER).assertIsDisplayed()
                onNodeWithText("Nadcházející").assertIsDisplayed()

                onNodeWithContentDescription(BACK_TO_FILTER).performClick()
                settle()
                onNodeWithText("Liga").assertIsDisplayed()
                onNodeWithContentDescription(BACK_TO_FILTER).assertDoesNotExist()
            }
        }

    @Test
    fun theLineAtTheTopSaysWhatTheListIsNarrowedTo() =
        runComposeUiTest {
            withLanguage("cs") {
                show(
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today),
                    phone = true,
                )
                choose("Liga", "6. liga")
                choose("Skupina", "6. liga K")

                onNode(hasScrollAction()).performScrollToIndex(PAST_THE_FILTER)
                settle()

                onNodeWithText("Nadcházející · 6. liga K").assertIsDisplayed()
            }
        }

    @Test
    fun theButtonNeverCoversTheLastRow() =
        runComposeUiTest {
            withLanguage("cs") {
                val viewModel =
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today)
                show(viewModel, phone = true)
                // One team's whole season: eleven rounds, a heading and a row each.
                choose("Termín", "Všechny termíny")
                onNodeWithText("Tým").performTextInput("Krabice K")
                settle()
                val last = viewModel.rows().last()
                assertEquals(SEASON_ROUNDS, viewModel.rows().size)

                onNode(hasScrollAction()).performScrollToIndex(SEASON_ROUNDS * 2)
                settle()

                // The row's date is its own: one team plays once a week.
                val row = onNodeWithText(last.fixture.date.asDayAndMonth()).getBoundsInRoot()
                val button = onNodeWithContentDescription(BACK_TO_FILTER).getBoundsInRoot()
                assertTrue(row.bottom <= button.top, "the button ($button) covers the last row ($row)")
            }
        }

    // -----------------------------------------------------------------
    // Kept from 0.2.0
    // -----------------------------------------------------------------

    @Test
    fun theFilterSurvivesGoingIntoAReportAndBack() =
        runComposeUiTest {
            withLanguage("cs") {
                val viewModel =
                    fixturesViewModel(InMemoryLeague(LeagueSix.groups), GatedMatchRepository(), LeagueSix.today)
                var onThisTab by mutableStateOf(true)
                setContent {
                    PsmfTheme {
                        if (onThisTab) Fixtures(viewModel) else Text("Zápis")
                    }
                }
                settle()
                choose("Liga", "6. liga")
                choose("Skupina", "6. liga K")
                choose("Termín", "Odehrané")

                // Into a report: the tab's screen leaves the composition entirely.
                onThisTab = false
                settle()
                onThisTab = true
                settle()

                onNodeWithText("Skupina").assertTextContains("6. liga K")
                onNodeWithText("Termín").assertTextContains("Odehrané")
            }
        }

    @Test
    fun aReportStartedFromTheListComesBackWithItsBadge() =
        runComposeUiTest {
            withLanguage("cs") {
                val league =
                    LeagueGroup(
                        season = UiTestData.season,
                        group = UiTestData.group,
                        teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                        players = emptyList(),
                        fixtures = listOf(UiTestData.fixture),
                        venues = listOf(UiTestData.venue),
                    )
                show(fixturesViewModel(InMemoryLeague(listOf(league)), GatedMatchRepository()))
                onNodeWithText("Rozepsáno").assertDoesNotExist()

                // Tapping the row starts the report, and the app goes to it.
                onNodeWithText(UiTestData.homeTeam.name).performClick()
                settle()

                // Back on the tab with no filter changed: the row says so.
                onNodeWithText("Rozepsáno").assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    @Composable
    private fun Fixtures(
        viewModel: FixturesViewModel,
        modifier: Modifier = Modifier,
    ) {
        val state by viewModel.state.collectAsState()
        FixturesScreen(
            state = state,
            onFixtureSelected = viewModel::onFixtureSelected,
            onRetry = viewModel::load,
            onFilterChanged = viewModel::onFilterChanged,
            modifier = modifier,
        )
    }

    private fun ComposeUiTest.show(
        viewModel: FixturesViewModel,
        phone: Boolean = false,
    ) {
        setContent {
            PsmfTheme {
                if (phone) {
                    Box(Modifier.size(SMALL_PHONE_WIDTH, SMALL_PHONE_HEIGHT)) { Fixtures(viewModel) }
                } else {
                    Fixtures(viewModel)
                }
            }
        }
        settle()
    }

    private fun leagueSixWithASeventh() =
        fixturesViewModel(InMemoryLeague(LeagueSix.groups + LeagueSix.seventh), GatedMatchRepository(), LeagueSix.today)

    private fun FixturesViewModel.rows() =
        state.value.listing!!
            .sections
            .flatMap { it.rows }

    /** Opens the select labelled [label] and taps [option] in it. */
    private fun ComposeUiTest.choose(
        label: String,
        option: String,
    ) {
        onNodeWithText(label).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(option).performScrollTo().performClick()
        settle()
    }

    /** A second on the test clock, past any pause the ViewModel waits on, then idle. */
    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        waitForIdle()
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val BACK_TO_FILTER = "Zpět na filtr"
        const val SHORT_SWIPE = 120f
        const val PAST_THE_FILTER = 4
        const val SEASON_ROUNDS = 11

        /** 360 × 640 dp, less a 24 dp status bar and a 24 dp navigation bar. */
        val SMALL_PHONE_WIDTH = 360.dp
        val SMALL_PHONE_HEIGHT = 592.dp
    }
}
