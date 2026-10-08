package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * One row of the fixture list, with everything it displays resolved.
 *
 * A view of data, not a UI type: it holds domain values and no strings
 * that would need translating. Formatting a date and choosing a badge are
 * the screen's job.
 */
data class FixtureRow(
    val fixture: Fixture,
    /**
     * The group it is played in. The row names it (`6K`) whenever the list
     * holds more than one group, because then nothing else on screen does.
     */
    val group: Group,
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

/** What a run of rows in the list is headed by. */
sealed interface SectionHeading {
    /** One group in view: its rounds, the way its season is played. */
    data class Round(
        val round: Int,
    ) : SectionHeading

    /**
     * More than one group in view: days. Round 6 of 6A and round 6 of 6K
     * are different evenings, so a round heading over both would be a
     * heading over nothing in particular.
     */
    data class Day(
        val date: LocalDate,
    ) : SectionHeading
}

data class FixtureSection(
    val heading: SectionHeading,
    val rows: List<FixtureRow>,
)

/**
 * Which fixtures to show by date, relative to the device's today.
 *
 * Whatever the window, **a fixture whose report is started and not yet
 * confirmed is shown** — see [ListFixtures].
 */
enum class DateWindow {
    /** Today onwards. What the tab opens on: a referee comes here for this week's match, not September's. */
    UPCOMING,

    /** Today only. */
    TODAY,

    /** Before today, newest first. */
    PLAYED,

    /** The whole season. */
    ALL,
}

/**
 * What the referee has narrowed the list to.
 *
 * # Five dimensions
 *
 * - [leagueLevel] alone is a valid filter -- a referee narrowing to "6.
 *   liga" and nothing more is asking for the whole level, not a single
 *   group, so [groupId] stays null until a group is actually picked.
 * - [groupId], once set, narrows within that level. A group chosen
 *   without its level gets it from [FilterOptions.normalise]; a group
 *   under a different level is dropped by it.
 * - [venue] is offered only within the league and group chosen
 *   ([FilterOptions.venuesInScope]), and cleared when they no longer play
 *   there.
 * - [teamQuery] is independent of the others: "every match Kominíci play"
 *   is a question on its own.
 * - [dates] defaults to [DateWindow.UPCOMING], and is the one dimension a
 *   cleared filter does not empty: clearing returns it to upcoming.
 */
data class FixtureFilter(
    val leagueLevel: Int? = null,
    val groupId: GroupId? = null,
    val venue: VenueCode? = null,
    /** Matched against a team name, diacritics folded. Blank means any. */
    val teamQuery: String = "",
    val dates: DateWindow = DateWindow.UPCOMING,
) {
    /**
     * Whether this is what the tab opens with. Anything else is what
     * "Zrušit filtr" offers to undo.
     */
    val isDefault: Boolean
        get() =
            leagueLevel == null &&
                groupId == null &&
                venue == null &&
                teamQuery.isBlank() &&
                dates == DateWindow.UPCOMING
}

/** One league level (e.g. `6`) and the groups it holds. */
data class LeagueLevelOption(
    val level: Int,
    val groups: List<Group>,
) {
    /**
     * `6. liga`: the groups' own name less their letter. Taken from the data,
     * like the group names themselves, so it reads the same in every
     * language -- it is a name, not a label.
     */
    val name: String
        get() =
            groups
                .firstOrNull()
                ?.let { it.name.removeSuffix(it.groupLetter).trim() }
                ?.takeIf { it.isNotEmpty() }
                ?: level.toString()
}

/**
 * Everything the filter can offer.
 *
 * **Leagues and groups come from all the data**, whatever the current
 * filter: a league that vanished as soon as another was picked would leave
 * no way back except clearing the whole filter.
 *
 * **Pitches are offered in scope**: only those with at least one fixture in
 * the league and group chosen. Forty-three pitches is a list to scroll;
 * one group plays at a handful. A pitch nobody plays at is never offered.
 *
 * No team list here: at league scale a list to pick from is itself the
 * problem the text field replaces.
 */
data class FilterOptions(
    val leagueLevels: List<LeagueLevelOption>,
    /** Every pitch with at least one fixture, by code. */
    val venues: List<Venue>,
    /** The pitches each group's fixtures are played at. */
    val venuesByGroup: Map<GroupId, Set<VenueCode>> = emptyMap(),
) {
    /** The pitches with at least one fixture in [filter]'s league and group. */
    fun venuesInScope(filter: FixtureFilter): List<Venue> {
        val groups = groupsInScope(filter) ?: return venues
        val codes = groups.flatMapTo(mutableSetOf()) { venuesByGroup[it].orEmpty() }
        return venues.filter { it.code in codes }
    }

    /**
     * [filter], made consistent with the cascade: a group with no league
     * gets its own; a group in a different league from the one chosen is
     * taken out; and so is a pitch that no fixture in the chosen league
     * and group is played at.
     *
     * Choosing a league therefore clears the group, and clears the pitch
     * only when the new league does not play there.
     */
    fun normalise(filter: FixtureFilter): FixtureFilter {
        val levelOfGroup =
            filter.groupId?.let { id -> leagueLevels.firstOrNull { level -> level.groups.any { it.id == id } }?.level }
        val level = filter.leagueLevel ?: levelOfGroup
        val narrowed =
            filter.copy(
                leagueLevel = level,
                groupId = filter.groupId?.takeIf { levelOfGroup != null && levelOfGroup == level },
            )
        val venue = narrowed.venue?.takeIf { code -> venuesInScope(narrowed).any { it.code == code } }
        return narrowed.copy(venue = venue)
    }

    /** Null means every group: no league chosen. */
    private fun groupsInScope(filter: FixtureFilter): List<GroupId>? =
        when {
            filter.groupId != null -> {
                listOf(filter.groupId)
            }

            filter.leagueLevel != null -> {
                leagueLevels
                    .firstOrNull {
                        it.level == filter.leagueLevel
                    }?.groups
                    .orEmpty()
                    .map { it.id }
            }

            else -> {
                null
            }
        }

    companion object {
        val NONE: FilterOptions = FilterOptions(emptyList(), emptyList())
    }
}

/** The fixture list as the screen draws it. */
data class FixtureListing(
    val sections: List<FixtureSection>,
    /** The filter this list was built for, after [FilterOptions.normalise]. */
    val filter: FixtureFilter = FixtureFilter(),
    val options: FilterOptions = FilterOptions.NONE,
    /** The device's date the list was built against: the day the screen calls today. */
    val today: LocalDate? = null,
    /** Whether the league data holds any fixture at all, whatever the filter. */
    val hasFixtures: Boolean = sections.any { it.rows.isNotEmpty() },
) {
    val isEmpty: Boolean get() = sections.all { it.rows.isEmpty() }

    /** Empty because of the filter, rather than because there is no data. */
    val filteredToNothing: Boolean get() = isEmpty && hasFixtures

    /**
     * Headed by day rather than by round, because more than one group is in
     * view. Each row then has to say which group it belongs to.
     */
    val byDay: Boolean get() = sections.firstOrNull()?.heading is SectionHeading.Day
}

/**
 * Builds the fixture list: seed data joined to whatever reports already
 * exist on the device, narrowed by [FixtureFilter].
 *
 * Reads match *summaries* rather than whole reports. A hydrated match
 * pulls in every goal, card and appearance, and this screen shows a badge.
 *
 * Filtering happens here rather than in the ViewModel because it changes
 * which rows are built, not merely which are drawn.
 *
 * # The date window never hides a report in progress
 *
 * A fixture whose report is started and not CONFIRMED is listed whatever
 * [FixtureFilter.dates] says -- a referee finishing yesterday's report this
 * morning must not find the match gone from "Nadcházející". The league,
 * pitch and team still apply to it as to any other row.
 *
 * "Today" is [clock]'s date in [timeZone], the device's own, so tests can
 * pin it.
 */
class ListFixtures(
    private val league: LeagueRepository,
    private val matches: MatchRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(filter: FixtureFilter = FixtureFilter()): FixtureListing {
        val statusByFixture = matches.summaries().associate { it.fixtureId to it.status }
        val leagueGroups = league.groups()
        val today = clock.todayIn(timeZone)
        val options = optionsOf(leagueGroups)
        val applied = options.normalise(filter)
        val needle = applied.teamQuery.foldForSearch()

        val rows =
            leagueGroups
                .filter { applied.admitsGroup(it.group) }
                .flatMap { leagueGroup ->
                    leagueGroup.fixtures
                        .filter { applied.venue == null || it.venue == applied.venue }
                        .filter { leagueGroup.isPlaying(it, needle) }
                        .filter { applied.dates.shows(it, statusByFixture[it.id], today) }
                        .mapNotNull { fixture ->
                            FixtureRow(
                                fixture = fixture,
                                group = leagueGroup.group,
                                homeTeam = leagueGroup.team(fixture.homeTeamId) ?: return@mapNotNull null,
                                awayTeam = leagueGroup.team(fixture.awayTeamId) ?: return@mapNotNull null,
                                venue = leagueGroup.venue(fixture.venue),
                                reportStatus = statusByFixture[fixture.id],
                            )
                        }
                }

        return FixtureListing(
            sections = arrange(rows, newestFirst = applied.dates == DateWindow.PLAYED),
            filter = applied,
            options = options,
            today = today,
            hasFixtures = leagueGroups.any { it.fixtures.isNotEmpty() },
        )
    }

    /** The league and group, the more specific winning: a group always means exactly that group. */
    private fun FixtureFilter.admitsGroup(group: Group): Boolean =
        when {
            groupId != null -> group.id == groupId
            leagueLevel != null -> group.leagueLevel == leagueLevel
            else -> true
        }

    /** Whether either side's name contains [needle], diacritics folded. An empty needle matches all. */
    private fun LeagueGroup.isPlaying(
        fixture: Fixture,
        needle: String,
    ): Boolean {
        if (needle.isEmpty()) return true
        val home = team(fixture.homeTeamId)?.name?.foldForSearch()
        val away = team(fixture.awayTeamId)?.name?.foldForSearch()
        return home?.contains(needle) == true || away?.contains(needle) == true
    }

    /**
     * Whether this window shows [fixture] -- always, when its report is
     * started and not confirmed ([status]): see the class comment.
     */
    private fun DateWindow.shows(
        fixture: Fixture,
        status: MatchStatus?,
        today: LocalDate,
    ): Boolean {
        if (status != null && status != MatchStatus.CONFIRMED) return true
        return when (this) {
            DateWindow.UPCOMING -> fixture.date >= today
            DateWindow.TODAY -> fixture.date == today
            DateWindow.PLAYED -> fixture.date < today
            DateWindow.ALL -> true
        }
    }

    /**
     * Fires whenever a report is started or changes state, from now on.
     *
     * The list is built once per filter, and the referee leaves this tab to
     * start a report and comes back to it. Without this the row they just
     * started would come back without its badge -- and a report started
     * from "Odehrané" depends on its status to stay listed under
     * "Nadcházející".
     */
    fun reportsChanged(): Flow<Unit> =
        matches
            .observeSummaries()
            .map { summaries -> summaries.associate { it.fixtureId to it.status } }
            .distinctUntilChanged()
            .drop(1)
            .map { }

    /**
     * One group in view: by round, as the season is played. More than one:
     * by day. [newestFirst] for played matches, where the latest is the one
     * being looked for.
     */
    private fun arrange(
        rows: List<FixtureRow>,
        newestFirst: Boolean,
    ): List<FixtureSection> {
        val sorted = rows.sortedWith(if (newestFirst) latestKickoffFirst else byKickoff)
        val oneGroup = rows.distinctBy { it.group.id }.size <= 1
        return if (oneGroup) {
            sorted
                .groupBy { it.fixture.round }
                .entries
                .sortedWith(if (newestFirst) compareByDescending { it.key } else compareBy { it.key })
                .map { (round, inRound) -> FixtureSection(SectionHeading.Round(round), inRound) }
        } else {
            // Already in date order, and groupBy keeps first-seen order.
            sorted
                .groupBy { it.fixture.date }
                .map { (date, onDay) -> FixtureSection(SectionHeading.Day(date), onDay) }
        }
    }

    private fun optionsOf(leagueGroups: List<LeagueGroup>): FilterOptions =
        FilterOptions(
            // Only groups with a level take part in the cascade -- a parallel
            // competition outside the numbered hierarchy (veteran, futsal,
            // ...) has no level to sit under.
            leagueLevels =
                leagueGroups
                    .mapNotNull { it.group.leagueLevel?.let { level -> level to it.group } }
                    .groupBy({ it.first }, { it.second })
                    .entries
                    .sortedBy { it.key }
                    .map { (level, groups) -> LeagueLevelOption(level, groups.sortedBy { it.groupLetter }) },
            venues =
                leagueGroups
                    .flatMap { leagueGroup ->
                        leagueGroup.fixtures.map { leagueGroup.venue(it.venue) ?: Venue(it.venue) }
                    }.distinctBy { it.code }
                    .sortedBy { it.code.value },
            venuesByGroup =
                leagueGroups.associate { leagueGroup ->
                    leagueGroup.group.id to leagueGroup.fixtures.mapTo(mutableSetOf()) { it.venue }
                },
        )

    private companion object {
        /** By kickoff, then group, then pitch: the order a referee scans a day in. */
        val byKickoff: Comparator<FixtureRow> =
            compareBy<FixtureRow>(
                { it.fixture.date },
                { it.fixture.time },
                { it.group.reportCode },
                { it.fixture.venue.value },
                { it.fixture.ref },
            )

        val latestKickoffFirst: Comparator<FixtureRow> =
            compareByDescending<FixtureRow> { it.fixture.date }
                .thenByDescending { it.fixture.time }
                .thenBy { it.group.reportCode }
                .thenBy { it.fixture.venue.value }
                .thenBy { it.fixture.ref }
    }
}
