package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.LoadedFixture
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.MatchSummary
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Hand-written fakes, not mocks: shared tests compile for iOS and MockK is
 * JVM-only. Repositories are behind interfaces precisely so this is cheap.
 */
class FakeMatchRepository(
    initial: List<Match> = emptyList(),
) : MatchRepository {
    private val stored = initial.associateBy { it.id }.toMutableMap()

    /** How many times the report has been written through. */
    var saves: Int = 0
        private set

    override suspend fun save(match: Match) {
        stored[match.id] = match
        saves++
    }

    override suspend fun load(id: MatchId): Match? = stored[id]

    override suspend fun summaries(): List<MatchSummary> =
        stored.values.map { MatchSummary(it.id, it.fixtureId, it.status) }

    override suspend fun findByStatus(status: MatchStatus): List<Match> = stored.values.filter { it.status == status }

    override suspend fun delete(id: MatchId) {
        stored.remove(id)
    }
}

class FakeLeagueRepository(
    private val groups: List<LeagueGroup>,
) : LeagueRepository {
    override suspend fun groups(): List<LeagueGroup> = groups

    override suspend fun group(id: GroupId): LeagueGroup? = groups.firstOrNull { it.group.id == id }

    override suspend fun fixture(id: FixtureId): LoadedFixture? =
        groups.firstNotNullOfOrNull { leagueGroup ->
            leagueGroup.fixtures
                .firstOrNull { it.id == id }
                ?.let { fixture ->
                    LoadedFixture(
                        leagueGroup = leagueGroup,
                        fixture = fixture,
                        homeTeam = leagueGroup.team(fixture.homeTeamId)!!,
                        awayTeam = leagueGroup.team(fixture.awayTeamId)!!,
                        venue = leagueGroup.venue(fixture.venue),
                    )
                }
        }
}

/**
 * A league group built from the shared [Fixtures], with enough fixtures to
 * have something to sort and group.
 */
object TestLeague {
    val season = Season(Fixtures.seasonId, "Hanspaulská liga podzim 2026")
    val venue = Venue(VenueCode("ZAKOS"))
    val otherVenue = Venue(VenueCode("METE1"))

    /** Round 2, later in the day, so ordering within a round is testable. */
    val secondRoundLate =
        fixture(ref = "6k-r2-late", round = 2, day = 7, time = LocalTime(20, 30), venue = otherVenue)

    /** Round 2, earlier — deliberately listed after the late one. */
    val secondRoundEarly =
        fixture(ref = "6k-r2-early", round = 2, day = 7, time = LocalTime(18, 0), venue = venue)

    private fun fixture(
        ref: String,
        round: Int,
        day: Int,
        time: LocalTime,
        venue: Venue,
    ) = Fixture(
        id = FixtureId(ref),
        ref = ref,
        groupId = Fixtures.groupId,
        round = round,
        date = LocalDate(2026, 9, day),
        time = time,
        venue = venue.code,
        homeTeamId = Fixtures.awayTeamId,
        awayTeamId = Fixtures.homeTeamId,
    )

    val group =
        LeagueGroup(
            season = season,
            group = Fixtures.group,
            teams = listOf(Fixtures.homeTeam, Fixtures.awayTeam),
            players = emptyList(),
            // Out of order on purpose: the use case is what puts them right.
            fixtures = listOf(secondRoundLate, secondRoundEarly, Fixtures.fixture),
            venues = listOf(venue, otherVenue),
        )

    fun repository() = FakeLeagueRepository(listOf(group))
}
