package cz.hspinovace.psmf.ui.lineup

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.player.RememberedCardNumberRepository
import cz.hspinovace.psmf.data.player.RememberedDateOfBirthRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.ui.recap.RecapViewModel
import cz.hspinovace.psmf.usecase.AddPlayerAtThePitch
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.AffirmNoCards
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.RecordResult
import cz.hspinovace.psmf.usecase.RememberCardNumber
import cz.hspinovace.psmf.usecase.RememberDateOfBirth
import cz.hspinovace.psmf.usecase.SaveLineup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The lineup and the recap, over one report, with the real view models.
 *
 * The defect these two screens had between them (DECISIONS 2026-10-17,
 * *The captain and the goalkeeper are marked in the lineup, and a captain
 * confirms twice*): the lineup's captain tap and the recap's captain
 * signature were **one record**, so after the lineup the recap already showed
 * both captains as confirmed -- and a report reached CONFIRMED with neither
 * captain having seen it finished.
 *
 * Written first against the code as it was, where the first test failed with
 * *the recap already shows the home captain confirmed, from the lineup's tap*.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class LineupToRecapFlowTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val novak = player("novak", "Novák", "Jan", 9)
    private val poupe = player("poupe", "Poupě", "Petr", 11)

    private fun player(
        ref: String,
        surname: String,
        first: String,
        number: Int,
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = UiTestData.homeTeamId,
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        rpNumber = null,
        dateOfBirth = null,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber(number),
    )

    private val group =
        LeagueGroup(
            season = UiTestData.season,
            group = UiTestData.group,
            teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
            players = listOf(novak, poupe),
            fixtures = listOf(UiTestData.fixture),
            venues = listOf(UiTestData.venue),
        )

    private val league = InMemoryLeague(listOf(group))
    private val added = InMemoryAddedPlayers()
    private val rememberedDates = InMemoryRememberedDates()
    private val rememberedNumbers = InMemoryRememberedNumbers()
    private val matchId = MatchId("m1")
    private var counter = 0

    private fun reportOf(kickoff: Instant? = null) =
        Match(matchId, UiTestData.fixtureId, UiTestData.groupId, kickoffAt = kickoff)

    private fun lineupViewModel(
        matches: GatedMatchRepository,
        now: Instant = Instant.parse("2026-08-31T18:55:00Z"),
    ): LineupViewModel {
        val newId = NewId { "id-${counter++}" }
        val build = BuildLineupEntry(league, added, rememberedDates, rememberedNumbers, newId)
        val save = SaveLineup(matches)
        return LineupViewModel(
            matchId = matchId,
            matches = matches,
            buildLineupEntry = build,
            saveLineup = save,
            addPlayerToLineup = AddPlayerToLineup(AddPlayerAtThePitch(added, newId), save, newId),
            rememberDateOfBirth = RememberDateOfBirth(rememberedDates),
            rememberCardNumber = RememberCardNumber(rememberedNumbers),
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )
    }

    private fun recapViewModel(matches: GatedMatchRepository) =
        RecapViewModel(
            matchId = matchId,
            matches = matches,
            buildZouReport = BuildZouReport(league, added),
            recordResult = RecordResult(matches),
            confirmReport = ConfirmReport(matches),
            affirmNoCards = AffirmNoCards(matches),
        )

    /** Home: both players marked, numbers from their cards, a captain and a goalkeeper chosen. */
    private fun LineupViewModel.homeIsFinished() {
        onEvent(LineupEvent.PresenceToggled(novak.id))
        onEvent(LineupEvent.PresenceToggled(poupe.id))
        onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
        onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
        onEvent(LineupEvent.CaptainChosen(novak.id))
        onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
    }

    @Test
    fun aCaptainWhoTappedOnTheLineupIsStillAskedToConfirmAtTheRecap() =
        runTest {
            val matches = GatedMatchRepository(listOf(reportOf()))
            val lineup = lineupViewModel(matches)
            lineup.homeIsFinished()

            lineup.onEvent(LineupEvent.CaptainConfirms)

            val recap = recapViewModel(matches).state.value

            // Confirmed before kickoff is not confirmed at the end: this is
            // a different signature on the form, and the recap has to ask.
            assertFalse(
                ConfirmingParty.HOME_CAPTAIN in recap.confirmed,
                "the recap already shows the home captain confirmed, from the lineup's tap",
            )
            assertTrue(
                ReportProblem.MissingConfirmation(ConfirmingParty.HOME_CAPTAIN) in recap.problems,
                "the report would reach CONFIRMED without the captain having seen it finished",
            )
        }

    @Test
    fun theLineupsTapIsRecordedUnderTheLineupWithTheCaptainsOwnName() =
        runTest {
            val matches = GatedMatchRepository(listOf(reportOf()))
            val lineup = lineupViewModel(matches, now = Instant.parse("2026-08-31T18:55:00Z"))
            lineup.homeIsFinished()

            lineup.onEvent(LineupEvent.CaptainConfirms)

            val saved = assertNotNull(matches.load(matchId)?.homeLineup)
            val confirmation = assertNotNull(saved.confirmation, "the tap was not recorded")
            // Nobody was picked and nobody typed: it is the marked captain.
            assertEquals("Novák Jan", confirmation.by.value)
            assertFalse(confirmation.asDeputy)
            assertEquals(Instant.parse("2026-08-31T18:55:00Z"), confirmation.at)
            // And nothing else: the signature under the finished report is
            // not a thing this screen can write.
            assertEquals(emptyList(), matches.load(matchId)?.confirmations)
            // The report says so, as the captain's confirmation under the lineup.
            val report = assertNotNull(recapViewModel(matches).state.value.report)
            assertEquals(
                "Novák Jan",
                report.lineups
                    .single { it.side.name == "HOME" }
                    .confirmation
                    ?.by,
            )
        }

    @Test
    fun aLateConfirmationIsAllowedAndCarriesItsRealTime() =
        runTest {
            // The whistle went at 19:00. The captain taps at 19:20, when the
            // referee notices it was never done: allowed, and not backdated.
            val kickoff = Instant.parse("2026-08-31T19:00:00Z")
            val late = Instant.parse("2026-08-31T19:20:00Z")
            val matches = GatedMatchRepository(listOf(reportOf(kickoff)))
            val lineup = lineupViewModel(matches, now = late)
            lineup.homeIsFinished()

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertEquals(
                late,
                matches
                    .load(matchId)
                    ?.homeLineup
                    ?.confirmation
                    ?.at,
            )
        }

    @Test
    fun aMissingConfirmationIsListedButNeverStopsAnyoneStartingTheMatch() =
        runTest {
            val matches = GatedMatchRepository(listOf(reportOf()))
            val lineup = lineupViewModel(matches)
            lineup.homeIsFinished()
            // The away team has nobody yet, so only the home block is written;
            // that is enough to ask what the report says is missing.

            val problems = assertNotNull(matches.load(matchId)).reportProblems()

            assertTrue(ReportProblem.LineupNotConfirmed(TeamSide.HOME) in problems)
            // Continue is gated on the block being whole, not on the tap.
            assertNotNull(matches.load(matchId)?.homeLineup)
            assertNull(matches.load(matchId)?.homeLineup?.confirmation)
        }

    @Test
    fun aNumberTypedFromTheCardIsRememberedOnTheDeviceAndNeverBecomesThePlayersRp() =
        runTest {
            val matches = GatedMatchRepository(listOf(reportOf()))
            val lineup = lineupViewModel(matches)
            lineup.homeIsFinished()

            // Offered next time, as the date of birth is...
            assertEquals("59001", rememberedNumbers.all()[novak.id])
            // ...and not written anywhere that could mistake it for PSMF's.
            val player = league.group(UiTestData.groupId)!!.players.single { it.id == novak.id }
            assertNull(player.rpNumber)
            assertNull(player.birthNumber)
            assertEquals(novak, player)
        }
}

private class InMemoryAddedPlayers : AddedPlayerRepository {
    private val stored = mutableListOf<Pair<MatchId, Player>>()

    override suspend fun add(
        matchId: MatchId,
        player: Player,
    ) {
        stored += matchId to player
    }

    override suspend fun forMatch(matchId: MatchId): List<Player> =
        stored.filter { it.first == matchId }.map { it.second }

    override suspend fun remove(playerId: PlayerId) {
        stored.removeAll { it.second.id == playerId }
    }
}

private class InMemoryRememberedDates : RememberedDateOfBirthRepository {
    private val dates = mutableMapOf<PlayerId, LocalDate>()

    override suspend fun all(): Map<PlayerId, LocalDate> = dates.toMap()

    override suspend fun remember(
        playerId: PlayerId,
        dateOfBirth: LocalDate,
    ) {
        dates[playerId] = dateOfBirth
    }
}

private class InMemoryRememberedNumbers : RememberedCardNumberRepository {
    private val numbers = mutableMapOf<PlayerId, String>()

    override suspend fun all(): Map<PlayerId, String> = numbers.toMap()

    override suspend fun remember(
        playerId: PlayerId,
        cardNumber: String,
    ) {
        numbers[playerId] = cardNumber
    }
}
