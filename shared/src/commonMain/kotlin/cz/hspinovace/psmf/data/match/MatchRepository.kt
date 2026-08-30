package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus

/**
 * A report's identity and state, without its contents.
 *
 * The fixture list asks "does this fixture already have a report, and how
 * far along is it" for every row on screen. Answering that with whole
 * [Match] values would read every goal, card and appearance in the
 * database to decide whether to draw a badge.
 */
data class MatchSummary(
    val id: MatchId,
    val fixtureId: FixtureId,
    val status: MatchStatus,
)

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

    /** Every report on the device, as identity and state only. */
    suspend fun summaries(): List<MatchSummary>

    /**
     * Reports the app was in the middle of. This is what the app offers to
     * resume after being killed.
     */
    suspend fun findByStatus(status: MatchStatus): List<Match>

    suspend fun delete(id: MatchId)
}
