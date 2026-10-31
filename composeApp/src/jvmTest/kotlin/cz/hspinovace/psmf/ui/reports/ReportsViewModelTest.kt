package cz.hspinovace.psmf.ui.reports

import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.data.match.LineupDraftRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.usecase.DiscardReport
import cz.hspinovace.psmf.usecase.ObserveReports
import cz.hspinovace.psmf.usecase.ReportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Discarding from the Zápis tab's list (prompt 17, Gate 2), in the view
 * model: **nothing removes a report without the referee's yes to the
 * question about it.** A yes with no question on screen -- a second tap on
 * the answer, an event from nowhere -- removes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val later =
        Fixture(
            id = FixtureId("6k-r2-01"),
            ref = "6k-r2-01",
            groupId = UiTestData.groupId,
            round = 2,
            date = UiTestData.fixture.date,
            time = UiTestData.fixture.time,
            venue = UiTestData.venue.code,
            homeTeamId = UiTestData.awayTeamId,
            awayTeamId = UiTestData.homeTeamId,
        )

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                    players = emptyList(),
                    fixtures = listOf(UiTestData.fixture, later),
                    venues = listOf(UiTestData.venue),
                ),
            ),
        )

    private val stray = Match(MatchId("m-stray"), UiTestData.fixture.id, UiTestData.groupId)
    private val played = Match(MatchId("m-played"), later.id, UiTestData.groupId, status = MatchStatus.FINISHED)

    private object NoDrafts : LineupDraftRepository {
        override suspend fun save(draft: LineupDraft) = Unit

        override suspend fun forMatch(matchId: MatchId): List<LineupDraft> = emptyList()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribed(matches: GatedMatchRepository): ReportsViewModel {
        val viewModel =
            ReportsViewModel(
                ObserveReports(matches, league),
                DiscardReport(matches, NoDrafts),
                UnconfinedTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun ReportsViewModel.row(id: MatchId): ReportRow =
        assertNotNull(state.value.listing).working.single { it.matchId == id }

    private suspend fun GatedMatchRepository.ids() = summaries().map { it.id }.toSet()

    @Test
    fun zahoditAsksAboutThatReportAndRemovesNothing() =
        runTest {
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)

            viewModel.onEvent(ReportsEvent.DiscardRequested(viewModel.row(stray.id)))

            val question = assertNotNull(viewModel.state.value.discarding)
            assertEquals(stray.id, question.contents.matchId)
            assertEquals(stray.id, question.row.matchId)
            assertEquals(setOf(stray.id, played.id), matches.ids())
        }

    @Test
    fun keepingTheReportLeavesItAndTakesTheQuestionAway() =
        runTest {
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)
            viewModel.onEvent(ReportsEvent.DiscardRequested(viewModel.row(stray.id)))

            viewModel.onEvent(ReportsEvent.DiscardDismissed)

            assertNull(viewModel.state.value.discarding)
            assertEquals(setOf(stray.id, played.id), matches.ids())
            assertNull(viewModel.discarded.value)
        }

    @Test
    fun yesRemovesTheReportAskedAboutAndOnlyThatOne() =
        runTest {
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)
            viewModel.onEvent(ReportsEvent.DiscardRequested(viewModel.row(stray.id)))

            viewModel.onEvent(ReportsEvent.DiscardConfirmed)

            assertEquals(setOf(played.id), matches.ids())
            assertNull(viewModel.state.value.discarding)
            assertEquals(
                listOf(played.id),
                viewModel.state.value.listing
                    ?.working
                    ?.map { it.matchId },
            )
            // For the navigator, which the route tells.
            assertEquals(stray.id, viewModel.discarded.value)
            viewModel.discardHandled()
            assertNull(viewModel.discarded.value)
        }

    @Test
    fun aYesWithNoQuestionOnScreenRemovesNothing() =
        runTest {
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)

            viewModel.onEvent(ReportsEvent.DiscardConfirmed)

            assertEquals(setOf(stray.id, played.id), matches.ids())
        }

    @Test
    fun aYesAfterTheQuestionWasDismissedRemovesNothing() =
        runTest {
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)
            viewModel.onEvent(ReportsEvent.DiscardRequested(viewModel.row(stray.id)))
            viewModel.onEvent(ReportsEvent.DiscardDismissed)

            viewModel.onEvent(ReportsEvent.DiscardConfirmed)

            assertEquals(setOf(stray.id, played.id), matches.ids())
        }

    @Test
    fun aSecondYesRemovesNothingMore() =
        runTest {
            // A double tap on the answer: the first yes took the question away.
            val matches = GatedMatchRepository(listOf(stray, played))
            val viewModel = subscribed(matches)
            viewModel.onEvent(ReportsEvent.DiscardRequested(viewModel.row(stray.id)))

            viewModel.onEvent(ReportsEvent.DiscardConfirmed)
            viewModel.onEvent(ReportsEvent.DiscardConfirmed)

            assertEquals(setOf(played.id), matches.ids())
        }
}
