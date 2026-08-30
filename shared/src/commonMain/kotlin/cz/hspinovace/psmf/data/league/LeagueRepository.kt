package cz.hspinovace.psmf.data.league

import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.data.seed.SeedLeagueCatalog
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * League reference data, as the app asks for it.
 *
 * Behind an interface for the usual reason — a screen test wants a handful
 * of teams, not the whole of 6. liga K — and because where the data comes
 * from is expected to change. Today it is files shipped in the app; the
 * analysis (section 5.2) says it eventually arrives on the device before
 * the referee reaches the pitch, which changes this implementation and
 * nothing above it.
 */
interface LeagueRepository {
    /** Every group in the shipped data, fully loaded. */
    suspend fun groups(): List<LeagueGroup>

    suspend fun group(id: GroupId): LeagueGroup?

    suspend fun fixture(id: FixtureId): LoadedFixture?
}

/**
 * A fixture with the things a screen needs alongside it already resolved.
 *
 * Assembled here rather than in a ViewModel: joining a fixture to its
 * teams and its pitch is data work, and a ViewModel that did it would need
 * the whole catalogue in hand to render one row.
 */
data class LoadedFixture(
    val leagueGroup: LeagueGroup,
    val fixture: Fixture,
    val homeTeam: Team,
    val awayTeam: Team,
    /**
     * Null when the pitch code is not in `venues.json`. The seed loader
     * rejects that, so it cannot happen with shipped data — but a screen
     * showing a fixture is not the place to crash over reference data.
     */
    val venue: Venue?,
) {
    fun team(id: TeamId): Team? =
        when (id) {
            homeTeam.id -> homeTeam
            awayTeam.id -> awayTeam
            else -> null
        }

    /**
     * Teams to offer as `Týmy`, the delegating team.
     *
     * **The two playing teams are excluded**, because the delegating team
     * is by definition neither of them (analysis section 2.5) — and they
     * are the two most likely to be tapped by mistake, since they are the
     * two names the referee has just been looking at. Getting it wrong
     * lands a fine on the wrong club.
     *
     * The one case this does not cover is substitute referees from a
     * playing team writing their own; that goes in as free text, and the
     * screen says so.
     */
    fun delegatingTeamOptions(): List<String> {
        val playing = setOf(homeTeam.name, awayTeam.name)
        return leagueGroup.teams.map { it.name }.filterNot { it in playing }
    }
}

/**
 * Reads the shipped seed files once and keeps them.
 *
 * The data is a few tens of kilobytes and never changes while the app is
 * running, so re-reading it per screen would be pure cost. The cache is
 * guarded by a [Mutex] rather than by `lazy`: two screens can ask at the
 * same moment, and parsing the whole catalogue twice on a cold start is
 * exactly the kind of thing that shows up as a slow first frame.
 */
class SeedLeagueRepository(
    private val catalog: SeedLeagueCatalog,
) : LeagueRepository {
    private val lock = Mutex()
    private var cached: List<LeagueGroup>? = null

    override suspend fun groups(): List<LeagueGroup> =
        cached ?: lock.withLock {
            cached ?: catalog.loadAll().also { cached = it }
        }

    override suspend fun group(id: GroupId): LeagueGroup? = groups().firstOrNull { it.group.id == id }

    override suspend fun fixture(id: FixtureId): LoadedFixture? =
        groups().firstNotNullOfOrNull { leagueGroup ->
            leagueGroup.fixtures
                .firstOrNull { it.id == id }
                ?.let { fixture ->
                    val home = leagueGroup.team(fixture.homeTeamId)
                    val away = leagueGroup.team(fixture.awayTeamId)
                    if (home == null || away == null) {
                        null
                    } else {
                        LoadedFixture(
                            leagueGroup = leagueGroup,
                            fixture = fixture,
                            homeTeam = home,
                            awayTeam = away,
                            venue = leagueGroup.venue(fixture.venue),
                        )
                    }
                }
        }
}
