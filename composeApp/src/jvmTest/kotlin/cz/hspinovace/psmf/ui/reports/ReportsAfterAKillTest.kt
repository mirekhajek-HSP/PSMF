package cz.hspinovace.psmf.ui.reports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cz.hspinovace.psmf.App
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.di.appModule
import cz.hspinovace.psmf.di.platformModule
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Zápis tab after the app was killed (prompt 17, Gate 1, the tester's
 * point 2), driven through the real [App].
 *
 * A kill is a new process: a new Koin graph, so a new [AppNavigator] that
 * remembers nothing, over a database that still holds the reports. The
 * whole app is composed rather than a harness of it, because what was wrong
 * was the app as a whole: the reports were in the database, and the tab's
 * root said *Žádný zápis* over them.
 *
 * Koin's JVM module is the app's own (an in-memory database); only the
 * league and the reports are put in by hand, so the test does not read the
 * shipped league files.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ReportsAfterAKillTest {
    private val hustec =
        Team(TeamId("hustec"), "hustec", UiTestData.groupId, "Hustec", listOf(Kit(KitId("k3"), "zelená")))

    /** 31. 8. 19:00, Kominíci -- Sp. Sumýš. */
    private val august = UiTestData.fixture

    /** 7. 9. 18:00, Hustec -- Kominíci. */
    private val september =
        Fixture(
            id = FixtureId("6k-r2-01"),
            ref = "6k-r2-01",
            groupId = UiTestData.groupId,
            round = 2,
            date = LocalDate(2026, 9, 7),
            time = LocalTime(18, 0),
            venue = UiTestData.venue.code,
            homeTeamId = hustec.id,
            awayTeamId = UiTestData.homeTeamId,
        )

    /** 14. 9. 20:30, Sp. Sumýš -- Hustec: no report yet. */
    private val later =
        Fixture(
            id = FixtureId("6k-r3-01"),
            ref = "6k-r3-01",
            groupId = UiTestData.groupId,
            round = 3,
            date = LocalDate(2026, 9, 14),
            time = LocalTime(20, 30),
            venue = UiTestData.venue.code,
            homeTeamId = UiTestData.awayTeamId,
            awayTeamId = hustec.id,
        )

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam, hustec),
                    players = emptyList(),
                    fixtures = listOf(august, september, later),
                    venues = listOf(UiTestData.venue),
                ),
            ),
        )

    /** Rozepsáno: the fixture tapped, nothing filled in. Opens on the header. */
    private val started = Match(MatchId("m-started"), august.id, UiTestData.groupId)

    /** Dohráno: played and not signed. Opens on the recap. */
    private val played = Match(MatchId("m-played"), september.id, UiTestData.groupId, status = MatchStatus.FINISHED)

    private val viewModels = ViewModelStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        stopKoin()
        Dispatchers.resetMain()
    }

    /** A new process over [matches]: a new graph, and with it a new navigator. */
    private fun launch(matches: MatchRepository): AppNavigator {
        startKoin {
            allowOverride(true)
            modules(
                appModule,
                platformModule(),
                module {
                    single<LeagueRepository> { league }
                    single<MatchRepository> { matches }
                },
            )
        }
        return GlobalContext.get().get()
    }

    private fun ComposeUiTest.showApp() {
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = viewModels
            }
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { App() }
        }
        waitUntil(timeoutMillis = TIMEOUT) { onAllNodes(hasText("Zápasy")).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Waits for [text] to be on screen, then asserts it is, so a miss says
     * what was looked for. The first such node: a screen's title may say
     * the same words as the screen.
     */
    private fun ComposeUiTest.awaitText(text: String) {
        runCatching {
            waitUntil(
                timeoutMillis = TIMEOUT,
            ) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        }
        onAllNodes(hasText(text)).onFirst().assertIsDisplayed()
    }

    @Test
    fun afterAKillTheZapisTabListsAStartedAndAPlayedReportAndEachOpensWhereItsFixtureWould() =
        runComposeUiTest {
            val navigator = launch(GatedMatchRepository(listOf(started, played)))
            withLanguage("cs") {
                showApp()

                onNodeWithText("Zápis").performClick()
                waitForIdle()

                // What the tester saw: two reports on the device, and a tab
                // that said there were none.
                onAllNodes(hasText("Žádný zápis")).assertCountEquals(0)
                awaitText("Rozepsáno")
                awaitText("Dohráno")

                // Rozepsáno opens on the header, as its fixture in Zápasy does.
                onNodeWithText("Rozepsáno").performClick()
                waitForIdle()
                assertEquals(Destination.MatchHeader(started.id), navigator.state.value.current)

                // Back from the report's first step is the list, in the tab.
                onNodeWithText("Zpět").performClick()
                awaitText("Dohráno")
                assertEquals(Tab.REPORT, navigator.state.value.tab)
                assertEquals(null, navigator.state.value.current.matchId)

                // Dohráno opens on the recap.
                onNodeWithText("Dohráno").performClick()
                waitForIdle()
                assertEquals(Destination.Recap(played.id), navigator.state.value.current)
            }
        }

    @Test
    fun zapasyAndTheListOpenTheSameReportAtTheSameStep() =
        runComposeUiTest {
            // Both lists open a report through one call, so one cannot come
            // to land somewhere the other does not.
            val navigator = launch(GatedMatchRepository(listOf(played)))
            withLanguage("cs") {
                showApp()

                // Zápasy, which the app opens on: the badge on its fixture.
                awaitText("Dohráno")
                onNodeWithText("Dohráno").performClick()
                waitUntil(timeoutMillis = TIMEOUT) { navigator.state.value.tab == Tab.REPORT }
                val fromZapasy = navigator.state.value.current

                // Back through the report -- recap, assessment, console,
                // lineup, header -- to the list, and the same row there.
                // Counted, not "until the list": a loop waiting for a list
                // that is not beneath the report never ends.
                repeat(STEPS_TO_THE_RECAP) { navigator.back() }
                waitForIdle()
                assertEquals(Destination.Reports, navigator.state.value.current)
                awaitText("Dohráno")
                onNodeWithText("Dohráno").performClick()
                waitForIdle()

                assertEquals(Destination.Recap(played.id), fromZapasy)
                assertEquals(fromZapasy, navigator.state.value.current)
            }
        }

    @Test
    fun aReportWhoseFixtureIsNotInLeagueDataIsNotListed() =
        runComposeUiTest {
            // A report from the invented 6-K, which 0.2.0 retired: nothing
            // to draw, so nothing offers it -- here as in the badge.
            val stray =
                Match(MatchId("m-old"), FixtureId("6k-placeholder"), UiTestData.groupId, status = MatchStatus.FINISHED)
            launch(GatedMatchRepository(listOf(stray, started)))
            withLanguage("cs") {
                showApp()

                onNodeWithText("Zápis").performClick()

                awaitText("Rozepsáno")
                onNodeWithText("Dohráno").assertDoesNotExist()
            }
        }

    @Test
    fun aReportStartedFromZapasyWhileTheListIsOnScreenAppearsInIt() =
        runComposeUiTest {
            // What Zápasy does with a tap, done to the same repository while
            // the list is composed. The list is not reopened.
            val matches = GatedMatchRepository(listOf(played))
            launch(matches)
            withLanguage("cs") {
                showApp()
                onNodeWithText("Zápis").performClick()
                awaitText("Dohráno")

                runBlocking { StartOrResumeMatch(league, matches) { "m-new" }(later.id) }

                awaitText("Rozepsáno")
                onNodeWithText("Dohráno").assertIsDisplayed()
            }
        }

    @Test
    fun withNoReportTheTabSaysWhatItSaidAndLeadsToZapasy() =
        runComposeUiTest {
            launch(GatedMatchRepository())
            withLanguage("cs") {
                showApp()

                onNodeWithText("Zápis").performClick()

                awaitText("Žádný zápis")
                onNodeWithText("Vybrat zápas").performClick()
                waitForIdle()
                assertEquals(
                    Tab.FIXTURES,
                    GlobalContext
                        .get()
                        .get<AppNavigator>()
                        .state.value.tab,
                )
            }
        }

    private companion object {
        const val TIMEOUT = 5_000L

        /** Header, lineup, console, assessment, recap. */
        const val STEPS_TO_THE_RECAP = 5
    }
}
