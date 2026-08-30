package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus

/**
 * Where tapping a fixture should put the referee.
 *
 * **The recovery gesture is tapping the row**, so it has to land where the
 * work is. A phone that died at minute 40 must come back to the console,
 * not to a header filled in an hour earlier.
 */
enum class ResumePoint {
    /** Nothing has been recorded yet; start at the top of the report. */
    HEADER,

    /**
     * The whistle has gone, or the match is over. Straight to the console.
     *
     * FINISHED and CONFIRMED will point at the recap once that screen
     * exists; until then the console is the last thing built and the
     * nearest right answer.
     */
    CONSOLE,
}

fun MatchStatus.resumePoint(): ResumePoint =
    when (this) {
        MatchStatus.SETUP -> ResumePoint.HEADER
        MatchStatus.IN_PROGRESS, MatchStatus.FINISHED, MatchStatus.CONFIRMED -> ResumePoint.CONSOLE
    }

/** Mints ids for things the app creates. Injected so tests are deterministic. */
fun interface NewId {
    operator fun invoke(): String
}

/**
 * Tapping a fixture either starts a report or picks up the one already
 * under way. There is no separate "resume" affordance and there must not
 * be one: a referee whose phone died mid-match taps the same row.
 *
 * **The report is created and saved before the first field is filled in.**
 * That is what makes everything after it survivable — every later screen
 * writes through to a row that already exists, so there is no window in
 * which work lives only in memory.
 */
class StartOrResumeMatch(
    private val league: LeagueRepository,
    private val matches: MatchRepository,
    private val newId: NewId,
) {
    suspend operator fun invoke(fixtureId: FixtureId): Match? {
        val loaded = league.fixture(fixtureId) ?: return null

        val existing = matches.summaries().firstOrNull { it.fixtureId == fixtureId }
        if (existing != null) {
            // A row exists. Load it whatever state it is in -- including
            // CONFIRMED, because the demo has no amend screen and landing
            // the referee somewhere is better than silently doing nothing.
            matches.load(existing.id)?.let { return it }
        }

        val started =
            Match(
                id = MatchId(newId()),
                fixtureId = fixtureId,
                groupId = loaded.leagueGroup.group.id,
                status = MatchStatus.SETUP,
            )
        matches.save(started)
        return started
    }
}
