package cz.hspinovace.psmf.ui.fixtures

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
import kotlinx.datetime.plus

/**
 * A league the size of the real league 6, invented, for the screen tests.
 *
 * Twelve groups of twelve teams, eleven rounds each: 792 fixtures. 43
 * pitches, five per group shared with the neighbouring groups, five never
 * used. The season runs either side of [today], a Thursday in round 6.
 *
 * The same league as `shared`'s `LeagueSix`, which this module cannot see
 * (it lives in `shared`'s own `commonTest`). **Never read from the shipped
 * files** (prompt 10's rule): they change with every import.
 *
 * [seventh] is one more group, a different league level, playing only at
 * the five pitches league 6 never uses -- so changing the league has a
 * group and a pitch to clear.
 */
object LeagueSix {
    val today: LocalDate = LocalDate(2026, 10, 8)
    val yesterday: LocalDate = LocalDate(2026, 10, 7)

    /** Round 1 of group A. Declared before anything built from it. */
    private val seasonStart = LocalDate(2026, 8, 31)

    val season = Season(SeasonId("2026-podzim"), "Hanspaulská liga podzim 2026")

    val pitches: List<Venue> =
        (1..PITCHES).map { Venue(VenueCode("H" + it.toString().padStart(2, '0')), "Hřiště číslo $it") }

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

    val groups: List<LeagueGroup> =
        ('A'..'L').mapIndexed { index, letter ->
            group(
                level = 6,
                letter = letter,
                played = (0 until PITCHES_PER_GROUP).map { pitches[index * PITCH_STEP + it].code },
                firstDay = seasonStart.plus(DatePeriod(days = index % DAY_SPREAD)),
            )
        }

    val seventh: LeagueGroup =
        group(
            level = 7,
            letter = 'A',
            played = pitches.takeLast(PITCHES_PER_GROUP).map { it.code },
            firstDay = seasonStart,
        )

    fun group(letter: Char): LeagueGroup = groups.single { it.group.groupLetter == letter.toString() }

    fun pitchesOf(letter: Char): Set<VenueCode> = group(letter).fixtures.mapTo(mutableSetOf()) { it.venue }

    val fixtures: List<Fixture> get() = groups.flatMap { it.fixtures }

    private fun group(
        level: Int,
        letter: Char,
        played: List<VenueCode>,
        firstDay: LocalDate,
    ): LeagueGroup {
        val id = GroupId("$level${letter.lowercaseChar()}")
        val teams =
            clubs.mapIndexed { n, club ->
                val ref = "${id.value}-${n + 1}"
                val name = if (level == 6) "$club $letter" else "$club $level$letter"
                Team(TeamId(ref), ref, id, name, listOf(Kit(KitId("$ref-kit"), "bílá")))
            }
        return LeagueGroup(
            season = season,
            group = Group(id, season.id, "$level. liga $letter", "$level$letter"),
            teams = teams,
            players = emptyList(),
            fixtures = roundRobin(id, teams, played, firstDay),
            venues = pitches,
        )
    }

    /** The circle method: every pair meets once in eleven weekly rounds, three fixtures a day. */
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
