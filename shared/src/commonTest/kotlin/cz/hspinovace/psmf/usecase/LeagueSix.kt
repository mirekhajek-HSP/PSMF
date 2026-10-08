package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Season
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * A league the size of the real league 6, invented.
 *
 * Twelve groups of twelve teams, each playing eleven rounds: 792 fixtures,
 * as on psmf.cz on 2026-10-07. 43 pitches, of which each group plays at
 * five, shared with its neighbours, and five pitches see no match at all.
 * Kickoffs run 10:00 to 20:00. The season runs either side of [today].
 *
 * **Built here, never read from the shipped files** (prompt 10's rule):
 * the shipped files change with every import, and a test that leaned on
 * this week's fixtures would fail next week for no reason.
 */
object LeagueSix {
    /** A Thursday, in the middle of the season. Round 6 is under way. */
    val today: LocalDate = LocalDate(2026, 10, 8)
    val yesterday: LocalDate = LocalDate(2026, 10, 7)

    /** Round 1 of group A. Declared before anything built from it. */
    private val SEASON_START = LocalDate(2026, 8, 31)

    val clock = PinnedClock(today)
    val timeZone: TimeZone = TimeZone.UTC

    val season = Season(SeasonId("2026-podzim"), "Hanspaulská liga podzim 2026")

    val pitches: List<Venue> =
        (1..PITCHES).map { Venue(VenueCode("H" + it.toString().padStart(2, '0')), "Hřiště číslo $it") }

    /** Diacritics in some on purpose: the team field folds them. */
    private val clubs =
        listOf(
            "Krabice",
            "Hustec",
            "Kominíci",
            "Sumýš",
            "Bohemka",
            "Dynamo",
            "Sokol",
            "Slavoj",
            "Meteor",
            "Tempo",
            "Admira",
            "Union",
        )

    val groups: List<LeagueGroup> = ('A'..'L').mapIndexed { index, letter -> group(index, letter) }

    fun group(letter: Char): LeagueGroup = groups.single { it.group.groupLetter == letter.toString() }

    /** The five pitches [letter]'s fixtures are played at. */
    fun pitchesOf(letter: Char): Set<VenueCode> = group(letter).fixtures.mapTo(mutableSetOf()) { it.venue }

    val fixtures: List<Fixture> get() = groups.flatMap { it.fixtures }

    fun listFixtures(
        matches: FakeMatchRepository = FakeMatchRepository(),
        groups: List<LeagueGroup> = this.groups,
    ) = ListFixtures(FakeLeagueRepository(groups), matches, clock, timeZone)

    private fun group(
        index: Int,
        letter: Char,
    ): LeagueGroup {
        val id = GroupId("6${letter.lowercaseChar()}")
        val teams =
            clubs.mapIndexed { n, club ->
                val ref = "${id.value}-${n + 1}"
                Team(TeamId(ref), ref, id, "$club $letter", listOf(Kit(KitId("$ref-kit"), "bílá")))
            }
        // Five pitches per group, the window moving three along per group:
        // each group shares two with the next, and the last five are unused.
        val played = (0 until PITCHES_PER_GROUP).map { pitches[index * PITCH_STEP + it].code }
        return LeagueGroup(
            season = season,
            group = Group(id, season.id, "6. liga $letter", "6$letter"),
            teams = teams,
            players = emptyList(),
            fixtures =
                roundRobin(
                    id,
                    teams,
                    played,
                    firstDay = SEASON_START.plus(DatePeriod(days = index % DAY_SPREAD)),
                ),
            venues = pitches,
        )
    }

    /**
     * The circle method: one team fixed, the rest rotating, so every pair
     * meets once in eleven rounds. Round r is played a week after round
     * r-1, its first three fixtures on one day and the last three the next.
     */
    private fun roundRobin(
        groupId: GroupId,
        teams: List<Team>,
        pitches: List<VenueCode>,
        firstDay: LocalDate,
    ): List<Fixture> {
        val rotating = teams.drop(1)
        return (0 until rotating.size).flatMap { round ->
            val circle = listOf(teams.first()) + rotating.indices.map { rotating[(it + round) % rotating.size] }
            (0 until teams.size / 2).map { slot ->
                val one = circle[slot]
                val other = circle[circle.size - 1 - slot]
                val (home, away) = if ((round + slot) % 2 == 0) one to other else other to one
                val ref = "${groupId.value}-r${round + 1}-${slot + 1}"
                Fixture(
                    id = FixtureId(ref),
                    ref = ref,
                    groupId = groupId,
                    round = round + 1,
                    date = firstDay.plus(DatePeriod(days = round * DAYS_IN_WEEK + slot / FIXTURES_PER_DAY)),
                    time = LocalTime(FIRST_KICKOFF_HOUR + slot * 2, 0),
                    venue = pitches[slot % pitches.size],
                    homeTeamId = home.id,
                    awayTeamId = away.id,
                )
            }
        }
    }

    private const val PITCHES = 43
    private const val PITCHES_PER_GROUP = 5
    private const val PITCH_STEP = 3
    private const val DAY_SPREAD = 5
    private const val DAYS_IN_WEEK = 7
    private const val FIXTURES_PER_DAY = 3
    private const val FIRST_KICKOFF_HOUR = 10
}
