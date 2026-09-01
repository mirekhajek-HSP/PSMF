package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode

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
 * What the referee has narrowed the list to. All empty means everything.
 *
 * # Why this exists at all
 *
 * The list is flat and shows every fixture of every bundled group. That is
 * fine for one group of twelve teams and it does not survive contact with
 * the real competition: nine divisions is on the order of nine hundred
 * teams and several thousand fixtures, and a referee officiates a handful.
 *
 * # Four dimensions, not one picker each
 *
 * - [leagueLevel] alone is a valid filter -- a referee narrowing to "6.
 *   liga" and nothing more is asking for the whole level, not a single
 *   group, so [groupId] stays null until a letter is actually picked.
 * - [groupId], once set, narrows within that level. Picking a group without
 *   [leagueLevel] also set would be a contradiction the UI does not allow:
 *   choosing a letter always sets both.
 * - [venue] and [teamQuery] are independent of the league dimension and of
 *   each other -- a referee can be looking for "every match at ZAKOS" or
 *   "every match Kominíci play" without narrowing the league at all.
 */
data class FixtureFilter(
    val leagueLevel: Int? = null,
    val groupId: GroupId? = null,
    val venue: VenueCode? = null,
    /** Matched against a team name, diacritics folded. Blank means any. */
    val teamQuery: String = "",
) {
    val isEmpty: Boolean
        get() = leagueLevel == null && groupId == null && venue == null && teamQuery.isBlank()
}

/** One league level (e.g. `6`) and the groups -- letters -- it holds. */
data class LeagueLevelOption(
    val level: Int,
    val groups: List<Group>,
)

/**
 * Everything the filter row can offer, whatever the current filter is.
 *
 * Built from the unfiltered data on purpose: options that disappear as
 * soon as they are used leave the referee with no way back except knowing
 * to clear the filter first.
 *
 * No team list here: at league scale (~900 teams) a list to pick from is
 * itself the problem the text field replaces. The followed-team shortcut
 * this used to carry now lives entirely in the Týmy tab.
 */
data class FilterOptions(
    val leagueLevels: List<LeagueLevelOption>,
    val venues: List<Venue>,
)

/** Every fixture the app knows about, grouped the way the season is. */
data class FixtureListing(
    val groups: List<GroupFixtures>,
    val filter: FixtureFilter = FixtureFilter(),
    val options: FilterOptions = FilterOptions(emptyList(), emptyList()),
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
) {
    private val byKickoff =
        compareBy<FixtureRow>({ it.fixture.date }, { it.fixture.time }, { it.fixture.ref })

    suspend operator fun invoke(filter: FixtureFilter = FixtureFilter()): FixtureListing {
        val statusByFixture = matches.summaries().associate { it.fixtureId to it.status }
        val leagueGroups = league.groups()
        val needle = filter.teamQuery.foldForSearch()

        fun matchesLeague(leagueGroup: LeagueGroup): Boolean =
            when {
                filter.groupId != null -> leagueGroup.group.id == filter.groupId
                filter.leagueLevel != null -> leagueGroup.group.leagueLevel == filter.leagueLevel
                else -> true
            }

        fun matchesTeamQuery(
            leagueGroup: LeagueGroup,
            fixture: Fixture,
        ): Boolean {
            if (needle.isEmpty()) return true
            val home = leagueGroup.team(fixture.homeTeamId)?.name?.foldForSearch()
            val away = leagueGroup.team(fixture.awayTeamId)?.name?.foldForSearch()
            return home?.contains(needle) == true || away?.contains(needle) == true
        }

        return FixtureListing(
            groups =
                leagueGroups
                    .filter { matchesLeague(it) }
                    .map { leagueGroup ->
                        val rows =
                            leagueGroup.fixtures
                                .filter { filter.venue == null || it.venue == filter.venue }
                                .filter { matchesTeamQuery(leagueGroup, it) }
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
                    // Only groups with a level participate in the cascade -- a
                    // parallel competition outside the numbered hierarchy
                    // (veteran, futsal, ...) has no level to group under.
                    leagueLevels =
                        leagueGroups
                            .mapNotNull { it.group.leagueLevel?.let { level -> level to it.group } }
                            .groupBy({ it.first }, { it.second })
                            .entries
                            .sortedBy { it.key }
                            .map { (level, groups) ->
                                LeagueLevelOption(level, groups.sortedBy { it.groupLetter })
                            },
                    // League-wide and identical on every LeagueGroup (loaded
                    // once from venues.json), so the first one carries them.
                    venues = leagueGroups.firstOrNull()?.venues.orEmpty(),
                ),
        )
    }
}
