package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.Team
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

/** Every fixture the app knows about, grouped the way the season is. */
data class FixtureListing(
    val groups: List<GroupFixtures>,
) {
    val isEmpty: Boolean get() = groups.all { group -> group.rounds.all { it.fixtures.isEmpty() } }

    /**
     * True when the shipped data holds more than one group, which is what
     * decides whether the list needs group headings at all.
     *
     * Asked rather than assumed because **adding a group is a data change,
     * never a code change** — a second file in `files/leagues/` has to show
     * up in the UI without anyone editing this.
     */
    val hasSeveralGroups: Boolean get() = groups.size > 1
}

/**
 * Builds the fixture list: seed data joined to whatever reports already
 * exist on the device.
 *
 * Reads match *summaries* rather than whole reports. A hydrated match
 * pulls in every goal, card and appearance, and this screen shows a badge.
 */
class ListFixtures(
    private val league: LeagueRepository,
    private val matches: MatchRepository,
) {
    private val byKickoff =
        compareBy<FixtureRow>({ it.fixture.date }, { it.fixture.time }, { it.fixture.ref })

    suspend operator fun invoke(): FixtureListing {
        val statusByFixture = matches.summaries().associate { it.fixtureId to it.status }

        return FixtureListing(
            groups =
                league.groups().map { leagueGroup ->
                    val rows =
                        leagueGroup.fixtures.mapNotNull { fixture ->
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
                                        // Within a round, the order the referee reads them in:
                                        // by kickoff, then by pitch.
                                        fixtures = fixtures.sortedWith(byKickoff),
                                    )
                                },
                    )
                },
        )
    }
}
