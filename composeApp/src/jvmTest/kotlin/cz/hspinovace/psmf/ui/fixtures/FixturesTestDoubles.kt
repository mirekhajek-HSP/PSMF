package cz.hspinovace.psmf.ui.fixtures

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.LoadedFixture
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.MatchSummary
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * League data held in memory.
 *
 * Hand-written for the same reason as `shared`'s fakes, which this module
 * cannot see: they live in `shared`'s own `commonTest`.
 */
class InMemoryLeague(
    private val groups: List<LeagueGroup>,
) : LeagueRepository {
    override suspend fun groups(): List<LeagueGroup> = groups

    override suspend fun group(id: GroupId): LeagueGroup? = groups.firstOrNull { it.group.id == id }

    override suspend fun fixture(id: FixtureId): LoadedFixture? =
        groups.firstNotNullOfOrNull { leagueGroup ->
            val fixture = leagueGroup.fixtures.firstOrNull { it.id == id } ?: return@firstNotNullOfOrNull null
            LoadedFixture(
                leagueGroup = leagueGroup,
                fixture = fixture,
                homeTeam = leagueGroup.team(fixture.homeTeamId) ?: return@firstNotNullOfOrNull null,
                awayTeam = leagueGroup.team(fixture.awayTeamId) ?: return@firstNotNullOfOrNull null,
                venue = leagueGroup.venue(fixture.venue),
            )
        }
}

/**
 * Reports in memory, with a hand on the tap.
 *
 * After [hold], every read of the summaries waits until [release]. That is
 * how a test catches the screen *between* a filter change and the new list
 * being ready -- the moment the old screen went blank in -- without racing
 * a real database.
 */
class GatedMatchRepository(
    initial: List<Match> = emptyList(),
) : MatchRepository {
    private val stored = initial.associateBy { it.id }.toMutableMap()
    private val writes = MutableStateFlow(0)
    private var gate: CompletableDeferred<Unit>? = null

    /** How many times the summaries have been read -- once per list built. */
    var reads: Int = 0
        private set

    /** Reads from now on wait for [release]. */
    fun hold() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun save(match: Match) {
        stored[match.id] = match
        writes.value++
    }

    override suspend fun load(id: MatchId): Match? = stored[id]

    override suspend fun summaries(): List<MatchSummary> {
        reads++
        gate?.await()
        return stored.values.map { MatchSummary(it.id, it.fixtureId, it.status) }
    }

    override fun observeSummaries(): Flow<List<MatchSummary>> =
        writes.map { stored.values.map { MatchSummary(it.id, it.fixtureId, it.status) } }

    override suspend fun findByStatus(status: MatchStatus): List<Match> = stored.values.filter { it.status == status }

    override suspend fun delete(id: MatchId) {
        stored.remove(id)
        writes.value++
    }
}

/**
 * The ViewModel over [league] and [matches], wired the way the Koin module
 * wires it -- except that the list is built on the test's Main dispatcher
 * rather than a background one, so the test clock decides when it lands.
 */
fun fixturesViewModel(
    league: LeagueRepository,
    matches: MatchRepository,
): FixturesViewModel =
    FixturesViewModel(
        listFixtures = ListFixtures(league, matches),
        startOrResumeMatch = StartOrResumeMatch(league, matches) { "new-match" },
        work = Dispatchers.Main,
    )
