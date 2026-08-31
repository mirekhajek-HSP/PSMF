package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.team.FollowedTeamRepository
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue

/**
 * One row of the fixture list, with everything it displays resolved.
 *
 * A view of data, not a UI type: it holds domain values and no strings
 * that would need translating. Formatting a date and choosing a badge are
 * the screen's job.
 */
data class FixtureRow(
    val fixture: Fixture,
    val homeTeam: Team,
    val awayTeam: Team,
    val venue: Venue?,
    /**
     * The state of the report for this fixture, or null if none has been
     * started.
     *
     * The fixture list is deliberately not a dashboard (DEMO_SCOPE screen
     * 1) — but crash recovery is in the demo, and this is the only route
     * back into a report the app was killed in the middle of. So it is a
     * property of a row, not a hero panel above the list.
     */
    val reportStatus: MatchStatus?,
)

data class RoundRows(
    val round: Int,
    val fixtures: List<FixtureRow>,
)

data class GroupFixtures(
    val season: Season,
    val group: Group,
    val rounds: List<RoundRows>,
)

/**
 * What the referee has narrowed the list to. Both null means everything.
 *
 * # Why this exists at all
 *
 * The list is flat and shows every fixture of every bundled group. That is
 * fine for one group of twelve teams and it does not survive contact with
 * the real competition: nine divisions is on the order of nine hundred
 * teams and several thousand fixtures, and a referee officiates a handful.
 */
data class FixtureFilter(
    val groupId: GroupId? = null,
    val teamId: TeamId? = null,
) {
    val isEmpty: Boolean get() = groupId == null && teamId == null
}

/**
 * Everything the filter row can offer, whatever the current filter is.
 *
 * Built from the unfiltered data on purpose: options that disappear as
 * soon as they are used leave the referee with no way back except knowing
 * to clear the filter first.
 */
data class FilterOptions(
    val leagues: List<Group>,
    /**
     * **Followed teams first**, then everyone else, each alphabetical.
     *
     * The one ordering decision here, and it is the reason the Týmy tab's
     * follow button earns its place: at league scale this picker is the
     * only part of the app that would otherwise be a nine-hundred-item
     * scroll.
     */
    val followedTeams: List<Team>,
    val otherTeams: List<Team>,
) {
    val teams: List<Team> get() = followedTeams + otherTeams

    fun team(id: TeamId): Team? = teams.firstOrNull { it.id == id }

    fun league(id: GroupId): Group? = leagues.firstOrNull { it.id == id }
}

/** Every fixture the app knows about, grouped the way the season is. */
data class FixtureListing(
    val groups: List<GroupFixtures>,
    val filter: FixtureFilter = FixtureFilter(),
    val options: FilterOptions = FilterOptions(emptyList(), emptyList(), emptyList()),
) {
    val isEmpty: Boolean get() = groups.all { group -> group.rounds.all { it.fixtures.isEmpty() } }

    /**
     * True when the listing holds more than one group, which is what
     * decides whether the list needs group headings at all.
     *
     * Asked rather than assumed because **adding a group is a data change,
     * never a code change** — a second file in `files/leagues/` has to show
     * up in the UI without anyone editing this.
     */
    val hasSeveralGroups: Boolean get() = groups.size > 1

    /** Empty because of the filter, rather than because there is no data. */
    val filteredToNothing: Boolean get() = isEmpty && !filter.isEmpty
}

/**
 * Builds the fixture list: seed data joined to whatever reports already
 * exist on the device, narrowed by [FixtureFilter].
 *
 * Reads match *summaries* rather than whole reports. A hydrated match
 * pulls in every goal, card and appearance, and this screen shows a badge.
 *
 * Filtering happens here rather than in the ViewModel because it changes
 * which rows are built, not merely which are drawn — at league scale the
 * difference is thousands of resolved rows the screen would throw away.
 */
class ListFixtures(
    private val league: LeagueRepository,
    private val matches: MatchRepository,
    private val followedTeams: FollowedTeamRepository,
) {
    private val byKickoff =
        compareBy<FixtureRow>({ it.fixture.date }, { it.fixture.time }, { it.fixture.ref })

    suspend operator fun invoke(filter: FixtureFilter = FixtureFilter()): FixtureListing {
        val statusByFixture = matches.summaries().associate { it.fixtureId to it.status }
        val followed = followedTeams.followed()
        val leagueGroups = league.groups()

        val allTeams = leagueGroups.flatMap { it.teams }.sortedBy { it.name }
        // Lifted out of the loop so the null check reads once rather than
        // once per fixture, and so the lambda takes a non-null value.
        val onlyTeam = filter.teamId

        return FixtureListing(
            groups =
                leagueGroups
                    .filter { filter.groupId == null || it.group.id == filter.groupId }
                    .map { leagueGroup ->
                        val rows =
                            leagueGroup.fixtures
                                .filter { onlyTeam == null || it.involves(onlyTeam) }
                                .mapNotNull { fixture ->
                                    val home = leagueGroup.team(fixture.homeTeamId) ?: return@mapNotNull null
                                    val away = leagueGroup.team(fixture.awayTeamId) ?: return@mapNotNull null
                                    FixtureRow(
                                        fixture = fixture,
                                        homeTeam = home,
                                        awayTeam = away,
                                        venue = leagueGroup.venue(fixture.venue),
                                        reportStatus = statusByFixture[fixture.id],
                                    )
                                }

                        GroupFixtures(
                            season = leagueGroup.season,
                            group = leagueGroup.group,
                            rounds =
                                rows
                                    .groupBy { it.fixture.round }
                                    .entries
                                    // toSortedMap is java.util and does not exist in common code.
                                    .sortedBy { it.key }
                                    .map { (round, fixtures) ->
                                        RoundRows(
                                            round = round,
                                            // Within a round, the order the referee reads them
                                            // in: by kickoff, then by pitch.
                                            fixtures = fixtures.sortedWith(byKickoff),
                                        )
                                    },
                        )
                    },
            filter = filter,
            options =
                FilterOptions(
                    leagues = leagueGroups.map { it.group }.sortedBy { it.name },
                    followedTeams = allTeams.filter { it.id in followed },
                    otherTeams = allTeams.filterNot { it.id in followed },
                ),
        )
    }

    private fun Fixture.involves(teamId: TeamId): Boolean = homeTeamId == teamId || awayTeamId == teamId
}
