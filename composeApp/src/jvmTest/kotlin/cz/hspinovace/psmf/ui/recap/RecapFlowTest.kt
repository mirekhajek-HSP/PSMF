package cz.hspinovace.psmf.ui.recap

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Appearance
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.CardsSection
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
import cz.hspinovace.psmf.domain.ReportedIdentification
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.usecase.AffirmNoCards
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.WithdrawNoCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The recap's view model over the real use cases (prompt 13, part 4): what a
 * signature is signed under, where the name comes from, what the result is,
 * and what taking a signature or an affirmation back does.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RecapFlowTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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

    private fun appearance(player: Player) =
        Appearance(
            id = AppearanceId("a-${player.ref}"),
            playerId = player.id,
            jerseyNumber = player.defaultJerseyNumber,
            reportedIdentification =
                ReportedIdentification(
                    "59${player.defaultJerseyNumber?.value}",
                    IdentificationSource.RP,
                ),
        )

    private fun lineup(
        side: TeamSide,
        player: Player,
        captainMarked: Boolean = true,
    ): Lineup {
        val team = if (side == TeamSide.HOME) UiTestData.homeTeam else UiTestData.awayTeam
        val appearance = appearance(player)
        return Lineup.wearing(
            side = side,
            teamId = team.id,
            appearances = listOf(appearance),
            kit = team.primaryKit,
            captain = appearance.id.takeIf { captainMarked },
            goalkeeper = appearance.id.takeIf { captainMarked },
        )
    }

    /** A finished report: a referee in the header, a captain marked on each side, one goal. */
    private fun finished(
        homeCaptainMarked: Boolean = true,
        cards: CardsSection? = null,
    ) = Match(
        id = matchId,
        fixtureId = UiTestData.fixtureId,
        groupId = UiTestData.groupId,
        status = MatchStatus.FINISHED,
        officials = RefereeAssignment(Official(PersonName.of("Vlk Jiří")), null, "Celtic THK"),
        homeLineup = lineup(TeamSide.HOME, novak, homeCaptainMarked),
        awayLineup = lineup(TeamSide.AWAY, vlk),
        goals = listOf(GoalEvent(Minute.Played(10), TeamSide.HOME, null, Score(1, 0), sequence = 1)),
        cards = cards,
    )

    private fun viewModel(matches: GatedMatchRepository) =
        RecapViewModel(
            matchId = matchId,
            matches = matches,
            buildZouReport = BuildZouReport(league, noAddedPlayers),
            confirmReport = ConfirmReport(matches),
            affirmNoCards = AffirmNoCards(matches),
            withdrawNoCards = WithdrawNoCards(matches),
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-08-31T20:05:00Z")
                },
        )

    // ------------------------------------------------------------------
    // Signatures: three taps, under names already known
    // ------------------------------------------------------------------

    @Test
    fun theRefereeSignsUnderTheHeadersNameWithoutTyping() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished()))
            val recap = viewModel(matches)

            // One tap: no name is typed, because the referee's is in the header.
            recap.onEvent(RecapEvent.ConfirmationGiven(ConfirmingParty.REFEREE))

            val signature = matches.load(matchId)?.confirmations?.singleOrNull { it.party == ConfirmingParty.REFEREE }
            assertEquals("Vlk Jiří", signature?.confirmedBy?.value, "the referee did not sign under the header's name")
        }

    @Test
    fun eachCaptainSignsUnderTheNameOfTheCaptainMarkedInTheirOwnLineup() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished()))
            val recap = viewModel(matches)

            // The names the buttons show are the names the signatures carry.
            assertEquals("Novák Jan", recap.state.value.signsAs[ConfirmingParty.HOME_CAPTAIN])
            assertEquals("Vlk Jan", recap.state.value.signsAs[ConfirmingParty.AWAY_CAPTAIN])
            assertEquals("Vlk Jiří", recap.state.value.signsAs[ConfirmingParty.REFEREE])

            recap.onEvent(RecapEvent.ConfirmationGiven(ConfirmingParty.HOME_CAPTAIN))
            recap.onEvent(RecapEvent.ConfirmationGiven(ConfirmingParty.AWAY_CAPTAIN))

            val saved = assertNotNull(matches.load(matchId)).confirmations.associate { it.party to it }
            assertEquals("Novák Jan", saved.getValue(ConfirmingParty.HOME_CAPTAIN).confirmedBy.value)
            assertEquals("Vlk Jan", saved.getValue(ConfirmingParty.AWAY_CAPTAIN).confirmedBy.value)
            // No deputy: nobody could say they were one.
            assertTrue(saved.values.none { it.asDeputy })
        }

    @Test
    fun allThreeTapsMakeTheReportConfirmedAndTheStateShowsEachWithItsTime() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished()))
            val recap = viewModel(matches)

            ConfirmingParty.entries.forEach { recap.onEvent(RecapEvent.ConfirmationGiven(it)) }

            assertEquals(MatchStatus.CONFIRMED, matches.load(matchId)?.status)
            assertEquals(ConfirmingParty.entries.toSet(), recap.state.value.confirmed.keys)
            assertEquals(
                Instant.parse("2026-08-31T20:05:00Z"),
                recap.state.value.confirmed
                    .getValue(ConfirmingParty.REFEREE)
                    .at,
            )
        }

    @Test
    fun aTeamWithNoCaptainMarkedHasNoNameToSignUnderAndNothingToTypeInstead() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished(homeCaptainMarked = false)))
            val recap = viewModel(matches)

            assertNull(recap.state.value.signsAs[ConfirmingParty.HOME_CAPTAIN])

            recap.onEvent(RecapEvent.ConfirmationGiven(ConfirmingParty.HOME_CAPTAIN))

            assertEquals(emptyList(), matches.load(matchId)?.confirmations)
        }

    // ------------------------------------------------------------------
    // The result is the goals'
    // ------------------------------------------------------------------

    @Test
    fun theResultTheRecapShowsIsWhatTheGoalsMake() =
        runTest {
            val recap = viewModel(GatedMatchRepository(listOf(finished())))

            val result =
                assertNotNull(
                    recap.state.value.report
                        ?.result,
                )

            assertEquals("1:0", result.fullTime)
            assertEquals("1:0", result.halfTime)
        }

    // ------------------------------------------------------------------
    // Taking the "no cards" affirmation back, and what it costs a signed report
    // ------------------------------------------------------------------

    @Test
    fun theNoCardsAffirmationIsMadeAndTakenBackAndTheBlockIsUnaccountedForAgain() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished()))
            val recap = viewModel(matches)
            assertTrue(recap.state.value.cardsUnaccountedFor)

            recap.onEvent(RecapEvent.NoCardsAffirmed)
            assertTrue(recap.state.value.noCardsAffirmed)

            recap.onEvent(RecapEvent.NoCardsWithdrawn)
            assertTrue(recap.state.value.cardsUnaccountedFor)
            assertEquals(false, recap.state.value.noCardsAffirmed)
        }

    @Test
    fun onASignedReportTakingTheAffirmationBackAsksFirstAndSaysNothingUntilAnswered() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished(cards = CardsSection.NoneIssued)))
            val recap = viewModel(matches)
            ConfirmingParty.entries.forEach { recap.onEvent(RecapEvent.ConfirmationGiven(it)) }
            assertEquals(MatchStatus.CONFIRMED, matches.load(matchId)?.status)

            recap.onEvent(RecapEvent.NoCardsWithdrawn)

            // Asked, and nothing has changed yet.
            assertEquals(RecapChange.WITHDRAW_NO_CARDS, recap.state.value.withdrawing)
            assertEquals(CardsSection.NoneIssued, matches.load(matchId)?.cards)
            assertEquals(3, matches.load(matchId)?.confirmations?.size)

            // No: it stays as it was.
            recap.onEvent(RecapEvent.WithdrawalDismissed)
            assertNull(recap.state.value.withdrawing)
            assertEquals(3, matches.load(matchId)?.confirmations?.size)

            // Yes: the affirmation and the signatures go, and the report is no longer confirmed.
            recap.onEvent(RecapEvent.NoCardsWithdrawn)
            recap.onEvent(RecapEvent.WithdrawalConfirmed)
            assertNull(matches.load(matchId)?.cards)
            assertEquals(emptyList(), matches.load(matchId)?.confirmations)
            assertEquals(MatchStatus.FINISHED, matches.load(matchId)?.status)
            assertEquals(emptyMap(), recap.state.value.confirmed)
        }

    @Test
    fun anUnsignedReportAsksNothing() =
        runTest {
            val matches = GatedMatchRepository(listOf(finished(cards = CardsSection.NoneIssued)))
            val recap = viewModel(matches)

            recap.onEvent(RecapEvent.NoCardsWithdrawn)

            assertNull(recap.state.value.withdrawing)
            assertNull(matches.load(matchId)?.cards)
        }
}
