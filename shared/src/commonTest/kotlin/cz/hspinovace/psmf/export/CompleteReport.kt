package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Assessment
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchResult
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.TeamAssessment
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import kotlin.time.Instant

/**
 * A report with everything in it, including the awkward cases the worked
 * example in analysis section 2.5 contains: a goal with no scorer, a card
 * at `30´+`, a second-yellow dismissal, and a deputy captain confirming.
 *
 * Used by every formatter test, so all three are checked against the same
 * report and cannot quietly disagree about what it says.
 */
object CompleteReport {
    private val poupe = Fixtures.poupeAppearance
    private val baca = Fixtures.bacaAppearance

    val confirmedAt: Instant = Instant.parse("2026-08-31T20:05:00Z")

    val match: Match =
        Match(
            id = MatchId("m1"),
            fixtureId = Fixtures.fixtureId,
            groupId = Fixtures.groupId,
            status = MatchStatus.FINISHED,
            officials = Fixtures.officials,
            homeLineup = Fixtures.homeLineup,
            awayLineup = Fixtures.awayLineup,
            kickoffAt = Instant.parse("2026-08-31T19:00:00Z"),
            goals =
                listOf(
                    GoalEvent(Minute.Played(5), TeamSide.AWAY, baca.id, Score(0, 1)),
                    // The worked example's `13´ — 2:1`: no scorer.
                    GoalEvent(Minute.Played(13), TeamSide.HOME, null, Score(1, 1)),
                    GoalEvent(Minute.Played(45), TeamSide.HOME, poupe.id, Score(2, 1)),
                ),
            cards =
                CardsSection.Issued(
                    listOf(
                        YellowCard(
                            Minute.Played(20),
                            TeamSide.AWAY,
                            CardSubject.Player(baca.id),
                            CardReason("podražení"),
                        ),
                        // At half-time, and to somebody with no number.
                        YellowCard(
                            Minute.HalfTime,
                            TeamSide.AWAY,
                            CardSubject.NamedPerson(PersonName.of("Lepis A.")),
                            CardReason("nesp. chování"),
                        ),
                        RedCard(
                            Minute.Played(49),
                            TeamSide.AWAY,
                            CardSubject.Player(baca.id),
                            CardReason(ZouLabels.Cards.SECOND_YELLOW),
                            Dismissal.SECOND_YELLOW,
                        ),
                    ),
                ),
            result = MatchResult(halfTime = Score(1, 1), fullTime = Score(2, 1)),
            assessment =
                Assessment(
                    home =
                        TeamAssessment(
                            bestPlayer = JerseyNumber(9),
                            waitingTimeMinutes = 0,
                            shirtsProperlyNumbered = true,
                            uniformKitColour = true,
                        ),
                    away =
                        TeamAssessment(
                            bestPlayer = JerseyNumber(13),
                            waitingTimeMinutes = 5,
                            shirtsProperlyNumbered = false,
                            uniformKitColour = true,
                        ),
                    commentary = "Nastřelená tyč ve 12. minutě. Vyloučený hráč pokřikoval zpoza plotu.",
                ),
            confirmations =
                listOf(
                    Confirmation(ConfirmingParty.HOME_CAPTAIN, confirmedAt, PersonName.of("Novak"), false),
                    // Captaincy may be delegated: `Lepiš (zást.)`.
                    Confirmation(ConfirmingParty.AWAY_CAPTAIN, confirmedAt, PersonName.of("Lepis"), true),
                    Confirmation(ConfirmingParty.REFEREE, confirmedAt, PersonName.of("Jiri Vlk"), false),
                ),
        )

    /**
     * The squad the appearances resolve against.
     *
     * Ids match what `Fixtures.appearance` points at, because resolving a
     * name is exactly what BuildZouReport is being asked to do.
     */
    val players: List<Player> =
        listOf(
            Fixtures.player("p-houzev", "Houžev", "Karel", 12),
            Fixtures.player("p-poupe", "Poupě", "Petr", 9),
            Fixtures.player("p-baca", "Bača", "Tomáš", 13).copy(teamId = Fixtures.awayTeamId),
        )

    /** A league holding just those players, for the appearance lookup. */
    val leagueGroup =
        LeagueGroup(
            season = Season(Fixtures.seasonId, "Hanspaulská liga podzim 2026"),
            group = Fixtures.group,
            teams = listOf(Fixtures.homeTeam, Fixtures.awayTeam),
            players = players,
            fixtures = listOf(Fixtures.fixture),
            venues = listOf(Venue(VenueCode("ZAKOS"))),
        )

    fun league() = FakeLeagueRepository(listOf(leagueGroup))

    suspend fun report(): ZouReport = requireNotNull(BuildZouReport(league(), NoAddedPlayers())(match))
}

/** An added-player store with nothing in it. */
class NoAddedPlayers : AddedPlayerRepository {
    override suspend fun add(
        matchId: MatchId,
        player: Player,
    ) = Unit

    override suspend fun forMatch(matchId: MatchId): List<Player> = emptyList()

    override suspend fun remove(playerId: PlayerId) = Unit
}
