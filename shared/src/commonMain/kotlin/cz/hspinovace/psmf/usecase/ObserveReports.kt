package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.MatchSummary
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Team
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One report on the device, with everything its row in the Zápis tab says.
 *
 * A view of data, like [FixtureRow]: domain values and no strings. The
 * status is drawn by the same badge as in Zápasy.
 */
data class ReportRow(
    val matchId: MatchId,
    val status: MatchStatus,
    val fixture: Fixture,
    val group: Group,
    val homeTeam: Team,
    val awayTeam: Team,
) {
    /** Where tapping the row lands: the same place as tapping its fixture in Zápasy. */
    val resumePoint: ResumePoint get() = status.resumePoint()
}

/** The reports on the device, as the Zápis tab lists them. */
data class ReportListing(
    /** Rozepsáno, Probíhá and Dohráno: what is still being worked on. Latest kickoff first. */
    val working: List<ReportRow> = emptyList(),
    /** Potvrzeno: signed by both captains and the referee. Latest kickoff first. */
    val confirmed: List<ReportRow> = emptyList(),
) {
    val isEmpty: Boolean get() = working.isEmpty() && confirmed.isEmpty()
}

/**
 * The reports on the device, for the Zápis tab's first screen.
 *
 * Until 0.4.0 the tab held one report and listed none: the navigator does
 * not survive the process, so after a restart a report set up and not
 * kicked off, or played and not signed, was reachable only from Zápasy
 * while the tab said *Žádný zápis* (the tester's point 2, prompt 17).
 *
 * **A stream**, from the summaries alone, like [ObserveReportInProgress]:
 * the list may be on screen when a report is started from Zápasy or moves
 * on in the wizard, and reading it once on entry is the bug shape TODO's
 * "Known gaps" names. A change that moves no report -- a goal, a name in
 * the header -- is not a new list, and is not joined to the league again:
 * every one of them is written through during a match.
 *
 * **A report whose fixture is no longer in league data is not listed**, as
 * the badge does not offer it: a report saved against the invented 6-K
 * before 0.2.0 has nothing to draw. It stays in the database untouched.
 */
class ObserveReports(
    private val matches: MatchRepository,
    private val league: LeagueRepository,
) {
    operator fun invoke(): Flow<ReportListing> =
        matches
            .observeSummaries()
            .map { summaries -> summaries.toSet() }
            .distinctUntilChanged()
            .map { summaries -> listingOf(summaries) }

    private suspend fun listingOf(summaries: Set<MatchSummary>): ReportListing {
        val rows =
            summaries
                .mapNotNull { summary ->
                    val loaded = league.fixture(summary.fixtureId) ?: return@mapNotNull null
                    ReportRow(
                        matchId = summary.id,
                        status = summary.status,
                        fixture = loaded.fixture,
                        group = loaded.leagueGroup.group,
                        homeTeam = loaded.homeTeam,
                        awayTeam = loaded.awayTeam,
                    )
                }.sortedWith(latestKickoffFirst)
        val (confirmed, working) = rows.partition { it.status == MatchStatus.CONFIRMED }
        return ReportListing(working = working, confirmed = confirmed)
    }

    private companion object {
        /**
         * The latest kickoff first, as Zápasy orders played matches; then
         * group, pitch and fixture, and the report's id last, so two
         * reports never swap places between one answer and the next.
         */
        val latestKickoffFirst: Comparator<ReportRow> =
            compareByDescending<ReportRow> { it.fixture.date }
                .thenByDescending { it.fixture.time }
                .thenBy { it.group.reportCode }
                .thenBy { it.fixture.venue.value }
                .thenBy { it.fixture.ref }
                .thenBy { it.matchId.value }
    }
}
