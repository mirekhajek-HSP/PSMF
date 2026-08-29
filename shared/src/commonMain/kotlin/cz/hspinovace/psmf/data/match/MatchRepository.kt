package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus

/**
 * Where a match report in progress lives.
 *
 * Behind an interface, always: it is what makes a hand-written fake cheap,
 * and shared tests use fakes rather than mocks because MockK is JVM-only.
 */
interface MatchRepository {
    /** Writes the whole report through. Called on every change. */
    suspend fun save(match: Match)

    suspend fun load(id: MatchId): Match?

    /**
     * Reports the app was in the middle of. This is what the app offers to
     * resume after being killed.
     */
    suspend fun findByStatus(status: MatchStatus): List<Match>

    suspend fun delete(id: MatchId)
}
