package cz.hspinovace.psmf.ui.reports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.withLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Discarding a report, through the real [App] (prompt 17, Gate 2, the
 * tester's point 1): from the Zápis tab's list, after a question, and gone
 * from everywhere the app shows a report -- the list, the fixture's badge in
 * Zápasy, the tab's badge.
 *
 * Over the app's own Koin module for the JVM, so the reports are in its
 * in-memory SQLite database, where `delete` runs as on a phone; only the
 * league is put in by hand. Zápasy reads the real clock, so the fixtures are
 * placed by it: one long past, one a week ahead.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DiscardAReportTest {
    private val hustec =
        Team(TeamId("hustec"), "hustec", UiTestData.groupId, "Hustec", listOf(Kit(KitId("k3"), "zelená")))

    /** 31. 8. 2026, Kominíci -- Sp. Sumýš: past, so listed under Nadcházející only while it has a report. */
    private val past = UiTestData.fixture

    /** A week from today, Hustec -- Kominíci: under Nadcházející by its date alone. */
    private val ahead =
        Fixture(
            id = FixtureId("6k-r9-01"),
            ref = "6k-r9-01",
            groupId = UiTestData.groupId,
            round = 9,
            date = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(7, DateTimeUnit.DAY),
            time = LocalTime(18, 0),
            venue = UiTestData.venue.code,
            homeTeamId = hustec.id,
            awayTeamId = UiTestData.homeTeamId,
        )

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam, hustec),
                    players = emptyList(),
                    fixtures = listOf(past, ahead),
                    venues = listOf(UiTestData.venue),
                ),
            ),
        )

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

    /** A new process with [reports] in its database. */
    private fun launch(vararg reports: Match): AppNavigator {
        startKoin {
            allowOverride(true)
            modules(appModule, platformModule(), module { single<LeagueRepository> { league } })
        }
        runBlocking { reports.forEach { matches().save(it) } }
        return GlobalContext.get().get()
    }

    private fun matches(): MatchRepository = GlobalContext.get().get()

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

    private fun ComposeUiTest.awaitText(text: String) {
        runCatching {
            waitUntil(timeoutMillis = TIMEOUT) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        }
        onAllNodes(hasText(text)).onFirst().assertIsDisplayed()
    }

    private fun ComposeUiTest.awaitGone(text: String) {
        runCatching {
            waitUntil(timeoutMillis = TIMEOUT) { onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }
        }
        onAllNodes(hasText(text)).fetchSemanticsNodes().let { assertEquals(0, it.size, "'$text' still on screen") }
    }

    private fun ComposeUiTest.badges(): Int =
        onAllNodes(hasContentDescription(BADGE), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Zahodit on the only report in the list, and the question's answer once both answers are armed. */
    private fun ComposeUiTest.discardTheOnlyReport(answer: String) {
        onNodeWithText("Zahodit").performClick()
        awaitText("Zahodit zápis?")
        mainClock.advanceTimeBy(ARMING.inWholeMilliseconds)
        onNodeWithText(answer).performClick()
        waitForIdle()
    }

    private fun summaries() = runBlocking { matches().summaries() }

    @Test
    fun zahoditThenKeepLeavesTheReportWhereItWas() =
        runComposeUiTest {
            val stray = Match(MatchId("m-stray"), past.id, UiTestData.groupId)
            launch(stray)
            withLanguage("cs") {
                showApp()
                onNodeWithText("Zápis").performClick()
                awaitText("Rozepsáno")

                discardTheOnlyReport("Ponechat zápis")

                onNodeWithText("Zahodit zápis?").assertDoesNotExist()
                onNodeWithText("Rozepsáno").assertIsDisplayed()
                assertEquals(listOf(stray.id), summaries().map { it.id })
            }
        }

    @Test
    fun aStrayReportDiscardedIsOffTheListAndItsPastFixtureLeavesNadchazejici() =
        runComposeUiTest {
            // The tester's case: a tap on the wrong fixture, weeks ago.
            val stray = Match(MatchId("m-stray"), past.id, UiTestData.groupId)
            val navigator = launch(stray)
            withLanguage("cs") {
                showApp()
                // Before: the past fixture held under Nadcházející by its report.
                awaitText("Sp. Sumýš")
                onNodeWithText("Rozepsáno").assertIsDisplayed()

                onNodeWithText("Zápis").performClick()
                awaitText("Rozepsáno")
                discardTheOnlyReport("Ano, zahodit")

                awaitText("Žádný zápis")
                assertEquals(emptyList(), summaries())
                // The navigator was told: an adoption of it arriving late, as
                // the shell's can, puts nothing back in the tab.
                navigator.adoptReport(Destination.Console(stray.id))
                assertEquals(
                    listOf(Destination.Reports),
                    navigator.state.value.stacks
                        .getValue(Tab.REPORT),
                )

                onNodeWithText("Zápasy").performClick()
                awaitGone("Sp. Sumýš")
                onNodeWithText("Rozepsáno").assertDoesNotExist()
                awaitText("Hustec")
            }
        }

    @Test
    fun theFixtureTappedAgainAfterItsReportWasDiscardedStartsANewEmptyReport() =
        runComposeUiTest {
            val played =
                Match(
                    MatchId("m-played"),
                    ahead.id,
                    UiTestData.groupId,
                    status = MatchStatus.FINISHED,
                    goals = listOf(GoalEvent(Minute.Played(3), TeamSide.HOME, null, Score(1, 0))),
                )
            val navigator = launch(played)
            withLanguage("cs") {
                showApp()
                awaitText("Dohráno")

                onNodeWithText("Zápis").performClick()
                awaitText("Dohráno")
                discardTheOnlyReport("Ano, zahodit")
                awaitText("Žádný zápis")

                onNodeWithText("Zápasy").performClick()
                awaitText("Hustec")
                awaitGone("Dohráno")

                onNodeWithText("Hustec").performClick()
                waitUntil(timeoutMillis = TIMEOUT) { navigator.state.value.tab == Tab.REPORT }

                val opened = assertNotNull(navigator.state.value.current.matchId)
                assertEquals(Destination.MatchHeader(opened), navigator.state.value.current)
                assertNotEquals(played.id, opened)
                val started = assertNotNull(runBlocking { matches().load(opened) })
                assertEquals(MatchStatus.SETUP, started.status)
                assertEquals(emptyList(), started.goals)
            }
        }

    @Test
    fun theMatchUnderWayDiscardedTakesTheTabsBadgeAndTheTabStaysOnTheList() =
        runComposeUiTest {
            // After a kill, the match under way is put into the tab for the
            // badge to land on; the referee walks back out of it to the list
            // and discards it there.
            val live =
                Match(
                    MatchId("m-live"),
                    past.id,
                    UiTestData.groupId,
                    status = MatchStatus.IN_PROGRESS,
                    kickoffAt = Instant.parse("2026-08-31T17:00:00Z"),
                )
            val navigator = launch(live)
            withLanguage("cs") {
                showApp()
                waitUntil(timeoutMillis = TIMEOUT) { badges() > 0 }

                onNodeWithText("Zápis").performClick()
                waitUntil(timeoutMillis = TIMEOUT) { navigator.state.value.current == Destination.Console(live.id) }
                repeat(STEPS_TO_THE_CONSOLE) {
                    onNodeWithText("Zpět").performClick()
                    waitForIdle()
                }
                assertEquals(Destination.Reports, navigator.state.value.current)
                awaitText("Probíhá")

                discardTheOnlyReport("Ano, zahodit")

                awaitText("Žádný zápis")
                waitUntil(timeoutMillis = TIMEOUT) { badges() == 0 }
                assertEquals(
                    listOf(Destination.Reports),
                    navigator.state.value.stacks
                        .getValue(Tab.REPORT),
                )

                // Away and back: the list, never a step of the match that is gone.
                onNodeWithText("Zápasy").performClick()
                waitForIdle()
                onNodeWithText("Zápis").performClick()
                awaitText("Žádný zápis")
                assertEquals(Destination.Reports, navigator.state.value.current)
            }
        }

    private companion object {
        const val TIMEOUT = 5_000L
        const val BADGE = "Zápis právě probíhá"

        /** Console, lineup, header. */
        const val STEPS_TO_THE_CONSOLE = 3
    }
}
