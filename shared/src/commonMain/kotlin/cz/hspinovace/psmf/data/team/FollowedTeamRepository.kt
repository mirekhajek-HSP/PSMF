package cz.hspinovace.psmf.data.team

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.TeamId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * The teams the referee has chosen to keep to hand.
 *
 * **Followed, not owned, and not downloaded.** The app has one persona —
 * the referee's — and following a team says "I officiate these", not "these
 * are mine". Nothing is fetched: every team is already in the bundled seed
 * data, so the list is a shortcut through it and not a cache of it.
 *
 * A `Set<TeamId>` rather than a list of teams: the teams themselves come
 * from [cz.hspinovace.psmf.data.league.LeagueRepository], which is where
 * league data belongs. This holds the referee's opinion about them and
 * nothing else — which is also why following a team that later disappears
 * from the seed files is harmless, since the id then simply matches
 * nothing.
 */
interface FollowedTeamRepository {
    /** Emits the current set, then again on every follow and unfollow. */
    fun observe(): Flow<Set<TeamId>>

    suspend fun followed(): Set<TeamId>

    suspend fun setFollowed(
        teamId: TeamId,
        followed: Boolean,
    )
}

class SqlDelightFollowedTeamRepository(
    private val database: PsmfDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FollowedTeamRepository {
    private val queries get() = database.teamRecordQueries

    override fun observe(): Flow<Set<TeamId>> =
        queries
            .selectFollowedTeams()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(::TeamId).toSet() }

    override suspend fun followed(): Set<TeamId> =
        withContext(dispatcher) {
            queries
                .selectFollowedTeams()
                .executeAsList()
                .map(::TeamId)
                .toSet()
        }

    override suspend fun setFollowed(
        teamId: TeamId,
        followed: Boolean,
    ): Unit =
        withContext(dispatcher) {
            if (followed) {
                queries.followTeam(teamId.value, clock.now().toString())
            } else {
                queries.unfollowTeam(teamId.value)
            }
        }
}
