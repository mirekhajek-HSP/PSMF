package cz.hspinovace.psmf.ui.lineup

import cz.hspinovace.psmf.data.match.DraftedMember
import cz.hspinovace.psmf.data.match.LineupDraft
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.fixtures.GatedMatchRepository
import cz.hspinovace.psmf.ui.fixtures.InMemoryLeague
import cz.hspinovace.psmf.usecase.AddPlayerAtThePitch
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.LineupProblem
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.RememberCardNumber
import cz.hspinovace.psmf.usecase.RememberDateOfBirth
import cz.hspinovace.psmf.usecase.RestoreLineupDraft
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.SaveLineupDraft
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The captain's tap under a lineup checks that team's block first (prompt 17, Gate 3,
 * the testers' point 3), with the real view model.
 *
 * Written first against 0.3.1's code, where the tap recorded a confirmation on a team with
 * no goalkeeper, or with a present player who had nothing in Číslo RP, without asking.
 * Nothing here is about eligibility: the check is exactly [TeamLineupEntry.problems].
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CaptainsTapChecksTheTeamTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun player(
        ref: String,
        surname: String,
        number: Int,
        team: TeamId = UiTestData.homeTeamId,
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

    private val novak = player("novak", "Novák", 9)
    private val poupe = player("poupe", "Poupě", 11)
    private val blaha = player("blaha", "Bláha", 4, team = UiTestData.awayTeamId)

    private val league =
        InMemoryLeague(
            listOf(
                LeagueGroup(
                    season = UiTestData.season,
                    group = UiTestData.group,
                    teams = listOf(UiTestData.homeTeam, UiTestData.awayTeam),
                    players = listOf(novak, poupe, blaha),
                    fixtures = listOf(UiTestData.fixture),
                    venues = listOf(UiTestData.venue),
                ),
            ),
        )
    private val added = InMemoryAddedPlayers()
    private val rememberedDates = InMemoryRememberedDates()
    private val rememberedNumbers = InMemoryRememberedNumbers()
    private val drafts = InMemoryLineupDrafts()
    private val matchId = MatchId("m1")
    private val matches = GatedMatchRepository(listOf(Match(matchId, UiTestData.fixtureId, UiTestData.groupId)))
    private var counter = 0
    private val tapAt = Instant.parse("2026-10-31T18:55:00Z")

    private fun lineupViewModel(): LineupViewModel {
        val newId = NewId { "id-${counter++}" }
        val save = SaveLineup(matches)
        return LineupViewModel(
            matchId = matchId,
            matches = matches,
            buildLineupEntry = BuildLineupEntry(league, added, rememberedDates, rememberedNumbers, newId),
            restoreLineupDraft = RestoreLineupDraft(drafts),
            saveLineup = save,
            saveLineupDraft = SaveLineupDraft(drafts),
            addPlayerToLineup = AddPlayerToLineup(AddPlayerAtThePitch(added, newId), save, newId),
            rememberDateOfBirth = RememberDateOfBirth(rememberedDates),
            rememberCardNumber = RememberCardNumber(rememberedNumbers),
            clock =
                object : Clock {
                    override fun now(): Instant = tapAt
                },
        )
    }

    /** Home: both marked, both with a card number, Novák captain; no goalkeeper yet. */
    private fun LineupViewModel.homeWithACaptainAndNoGoalkeeper() {
        onEvent(LineupEvent.PresenceToggled(novak.id))
        onEvent(LineupEvent.PresenceToggled(poupe.id))
        onEvent(LineupEvent.CardNumberTyped(novak.id, "59001"))
        onEvent(LineupEvent.CardNumberTyped(poupe.id, "59002"))
        onEvent(LineupEvent.CaptainChosen(novak.id))
    }

    private fun LineupViewModel.homeIsWhole() {
        homeWithACaptainAndNoGoalkeeper()
        onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
    }

    private fun LineupViewModel.homeConfirmation() =
        state.value.entry!!
            .home.confirmation

    @Test
    fun aCaptainAndNoGoalkeeperRecordsNothing() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeWithACaptainAndNoGoalkeeper()

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertNull(lineup.homeConfirmation(), "the tap recorded a confirmation on a block with no goalkeeper")
            // Nor on disk, in the draft that survives a kill: a new view model reads it back.
            assertNull(lineupViewModel().homeConfirmation(), "the draft kept a confirmation the screen refused")
        }

    @Test
    fun aPresentPlayerWithNothingInCisloRpRecordsNothing() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()
            lineup.onEvent(LineupEvent.CardNumberTyped(poupe.id, ""))

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertNull(lineup.homeConfirmation(), "the tap recorded a confirmation with a row's Číslo RP empty")
        }

    @Test
    fun twoPlayersSharingAShirtNumberRecordNothing() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()
            lineup.onEvent(LineupEvent.JerseyNumberChanged(poupe.id, "9"))

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertNull(lineup.homeConfirmation(), "the tap recorded a confirmation on a shared shirt number")
        }

    @Test
    fun aWholeTeamRecordsAsBeforeWithItsTimeAndTheCaptainsName() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()

            lineup.onEvent(LineupEvent.CaptainConfirms)

            val confirmation = assertNotNull(lineup.homeConfirmation(), "a whole team's tap was refused")
            assertEquals(tapAt, confirmation.at)
            assertEquals("Novák Jan", confirmation.by.value)
            assertEquals(confirmation, matches.load(matchId)?.homeLineup?.confirmation)
        }

    @Test
    fun aProblemInTheOtherTeamDoesNotStopThisTeamsTap() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()
            // Away: somebody present, no captain, no goalkeeper, nothing in their Číslo RP.
            lineup.onEvent(LineupEvent.SideSelected(TeamSide.AWAY))
            lineup.onEvent(LineupEvent.PresenceToggled(blaha.id))
            lineup.onEvent(LineupEvent.SideSelected(TeamSide.HOME))
            assertTrue(
                lineup.state.value.entry!!
                    .away
                    .problems()
                    .isNotEmpty(),
            )

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertNotNull(lineup.homeConfirmation(), "the other team's problems stopped this team's captain")
            assertNull(
                lineup.state.value.entry!!
                    .away.confirmation,
            )
        }

    @Test
    fun theOtherTeamsCaptainIsCheckedOnItsOwnBlock() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()
            lineup.onEvent(LineupEvent.SideSelected(TeamSide.AWAY))
            lineup.onEvent(LineupEvent.PresenceToggled(blaha.id))
            lineup.onEvent(LineupEvent.CardNumberTyped(blaha.id, "59003"))
            lineup.onEvent(LineupEvent.CaptainChosen(blaha.id))

            lineup.onEvent(LineupEvent.CaptainConfirms)
            assertNull(
                lineup.state.value.entry!!
                    .away.confirmation,
                "away has no goalkeeper, and was confirmed",
            )

            lineup.onEvent(LineupEvent.GoalkeeperChosen(blaha.id))
            lineup.onEvent(LineupEvent.CaptainConfirms)
            assertNotNull(
                lineup.state.value.entry!!
                    .away.confirmation,
                "a whole away block was refused",
            )
        }

    @Test
    fun aTapThatWasRefusedGoesThroughOnceTheBlockIsWhole() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeWithACaptainAndNoGoalkeeper()
            lineup.onEvent(LineupEvent.CaptainConfirms)
            assertNull(lineup.homeConfirmation(), "the tap recorded a confirmation on a block with no goalkeeper")

            lineup.onEvent(LineupEvent.GoalkeeperChosen(poupe.id))
            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertNotNull(lineup.homeConfirmation())
        }

    @Test
    fun aRefusedTapMarksThatTeamsRowsAndNobodyElses() =
        runTest {
            val lineup = lineupViewModel()
            lineup.homeIsWhole()
            lineup.onEvent(LineupEvent.CardNumberTyped(poupe.id, ""))
            lineup.onEvent(LineupEvent.SideSelected(TeamSide.AWAY))
            lineup.onEvent(LineupEvent.PresenceToggled(blaha.id))
            lineup.onEvent(LineupEvent.SideSelected(TeamSide.HOME))
            val homeRow = LineupProblem.NoIdentification(TeamSide.HOME, poupe.id)
            val awayRow = LineupProblem.NoIdentification(TeamSide.AWAY, blaha.id)
            assertFalse(lineup.state.value.problem(homeRow), "the row was marked before anything was tapped")

            lineup.onEvent(LineupEvent.CaptainConfirms)

            assertTrue(lineup.state.value.problem(homeRow), "a refused tap did not mark the row that needs a number")
            assertFalse(lineup.state.value.problem(awayRow), "the other team's row was marked by this team's tap")
        }

    @Test
    fun aConfirmationAnEarlierBuildRecordedOnAnIncompleteDraftStaysAsItIs() =
        runTest {
            val old = LineupConfirmation(Instant.parse("2026-10-30T18:00:00Z"), PersonName.of("Novák Jan"))
            drafts.save(
                LineupDraft(
                    matchId = matchId,
                    side = TeamSide.HOME,
                    kitId = UiTestData.homeTeam.primaryKit.id,
                    captain = novak.id,
                    goalkeeper = null,
                    confirmation = old,
                    members = listOf(draftedAndPresent(novak, "59001")),
                ),
            )

            val lineup = lineupViewModel()
            assertEquals(old, lineup.homeConfirmation())
            // Some other change to the block does not take it away.
            lineup.onEvent(LineupEvent.JerseyNumberChanged(novak.id, "10"))

            assertEquals(old, lineup.homeConfirmation(), "a change withdrew a confirmation the lineup keeps")
            assertTrue(
                lineup.state.value.entry!!
                    .home
                    .problems()
                    .isNotEmpty(),
            )
        }

    private fun draftedAndPresent(
        p: Player,
        card: String,
    ) = DraftedMember(
        playerId = p.id,
        present = true,
        jerseyNumber = p.defaultJerseyNumber,
        registrationCardPresent = true,
        cardNumberTyped = card,
        cardNumberWritten = card,
        dateOfBirthTyped = "",
        dateOfBirthWritten = null,
    )
}
