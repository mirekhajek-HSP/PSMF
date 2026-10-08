package cz.hspinovace.psmf.usecase

import app.cash.turbine.test
import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.domain.VenueCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Zápasy filter against a league the size of league 6 (DECISIONS
 * 2026-10-08): twelve groups, 792 fixtures, 43 pitches, and a today in the
 * middle of the season. [LeagueSix] invents it; nothing here reads the
 * shipped files.
 */
class FixtureListAtLeagueScaleTest {
    private fun FixtureListing.rows(): List<FixtureRow> = sections.flatMap { it.rows }

    private fun FixtureListing.ids(): Set<String> = rows().mapTo(mutableSetOf()) { it.fixture.id.value }

    private fun yesterdays(): List<Fixture> = LeagueSix.fixtures.filter { it.date == LeagueSix.yesterday }

    private fun reportFor(
        fixture: Fixture,
        status: MatchStatus,
    ) = Match(MatchId("m-${fixture.ref}"), fixture.id, fixture.groupId, status = status)

    @Test
    fun theTestLeagueIsTheSizeOfLeagueSix() {
        assertEquals(12, LeagueSix.groups.size)
        assertEquals(144, LeagueSix.groups.sumOf { it.teams.size })
        assertEquals(792, LeagueSix.fixtures.size)
        assertEquals(43, LeagueSix.pitches.size)
        assertTrue(yesterdays().isNotEmpty() && LeagueSix.fixtures.any { it.date == LeagueSix.today })
    }

    // -----------------------------------------------------------------
    // Pitches, in scope
    // -----------------------------------------------------------------

    @Test
    fun withNoLeagueChosenEveryPitchWithAMatchIsOfferedAndNoOther() =
        runTest {
            val offered = LeagueSix.listFixtures()().options.venuesInScope(FixtureFilter())

            // 38 of the 43 have a fixture. Five never do, and are not offered.
            assertEquals(38, offered.size)
            assertTrue(offered.none { it.code == VenueCode("H43") })
            // By name as well as code: the option shows both.
            assertTrue(offered.all { it.name?.startsWith("Hřiště číslo") == true })
        }

    @Test
    fun aGroupIsOfferedOnlyThePitchesItPlaysAt() =
        runTest {
            val options = LeagueSix.listFixtures()().options
            val sixK = LeagueSix.group('K').group

            val offered = options.venuesInScope(FixtureFilter(leagueLevel = 6, groupId = sixK.id))

            assertEquals(LeagueSix.pitchesOf('K'), offered.mapTo(mutableSetOf()) { it.code })
            assertEquals(5, offered.size)
        }

    @Test
    fun changingTheLeagueClearsTheGroupAndAPitchTheNewLeagueDoesNotPlayAt() =
        runTest {
            val options = LeagueSix.listFixtures(groups = LeagueSix.groups + seventhLeague)().options
            val onlyInK = (LeagueSix.pitchesOf('K') - LeagueSix.pitchesOf('L') - LeagueSix.pitchesOf('J')).first()
            val chosen = FixtureFilter(leagueLevel = 6, groupId = LeagueSix.group('K').group.id, venue = onlyInK)

            // The referee picks 7. liga: 6K is not in it, nor is the pitch.
            val after = options.normalise(chosen.copy(leagueLevel = 7))

            assertEquals(7, after.leagueLevel)
            assertNull(after.groupId, "changing the league did not clear the group")
            assertNull(after.venue, "a pitch 7. liga never plays at was kept")
        }

    @Test
    fun changingTheGroupKeepsAPitchTheNewGroupAlsoPlaysAt() =
        runTest {
            val options = LeagueSix.listFixtures()().options
            val shared = (LeagueSix.pitchesOf('K') intersect LeagueSix.pitchesOf('L')).first()
            val chosen = FixtureFilter(leagueLevel = 6, groupId = LeagueSix.group('K').group.id, venue = shared)

            val after = options.normalise(chosen.copy(groupId = LeagueSix.group('L').group.id))

            assertEquals(shared, after.venue, "a pitch still in scope was cleared")
        }

    @Test
    fun aChosenPitchOutsideTheGroupIsNotAppliedEvenIfItArrivesAnyway() =
        runTest {
            // The ViewModel normalises before asking; this is the use case not
            // trusting that, and saying in its result what it actually applied.
            val notInK = (LeagueSix.pitchesOf('A') - LeagueSix.pitchesOf('K')).first()
            val sixK = LeagueSix.group('K').group.id
            val listing =
                LeagueSix.listFixtures()(
                    FixtureFilter(leagueLevel = 6, groupId = sixK, venue = notInK, dates = DateWindow.ALL),
                )

            assertNull(listing.filter.venue)
            assertEquals(66, listing.rows().size)
        }

    // -----------------------------------------------------------------
    // The date window
    // -----------------------------------------------------------------

    @Test
    fun theTabOpensOnUpcomingTodayIncluded() =
        runTest {
            val listing = LeagueSix.listFixtures()()

            assertEquals(DateWindow.UPCOMING, listing.filter.dates)
            assertTrue(listing.rows().all { it.fixture.date >= LeagueSix.today })
            assertEquals(LeagueSix.fixtures.count { it.date >= LeagueSix.today }, listing.rows().size)
            assertEquals(LeagueSix.today, listing.today)
        }

    @Test
    fun upcomingHidesYesterdaysFixtureButNotYesterdaysUnconfirmedReport() =
        runTest {
            val yesterday = yesterdays()
            val setUp = yesterday[0]
            val inProgress = yesterday[1]
            val played = yesterday[2]
            val confirmed = yesterday[3]
            val untouched = yesterday[4]
            val matches =
                FakeMatchRepository(
                    listOf(
                        reportFor(setUp, MatchStatus.SETUP),
                        reportFor(inProgress, MatchStatus.IN_PROGRESS),
                        reportFor(played, MatchStatus.FINISHED),
                        reportFor(confirmed, MatchStatus.CONFIRMED),
                    ),
                )

            val ids = LeagueSix.listFixtures(matches)().ids()

            // Started and not confirmed: never hidden by the date.
            assertTrue(setUp.id.value in ids, "a report being set up vanished")
            assertTrue(inProgress.id.value in ids, "a match in progress vanished")
            assertTrue(played.id.value in ids, "a played, unconfirmed report vanished")
            // Done with, or never started: yesterday is not upcoming.
            assertFalse(confirmed.id.value in ids, "a confirmed report from yesterday is still listed")
            assertFalse(untouched.id.value in ids, "yesterday's fixture is listed as upcoming")
        }

    @Test
    fun theDateNeverHidesAnUnconfirmedReportButLeaguePitchAndTeamStillDo() =
        runTest {
            val fixture = yesterdays().first()
            val group = LeagueSix.groups.single { it.group.id == fixture.groupId }
            val matches = FakeMatchRepository(listOf(reportFor(fixture, MatchStatus.IN_PROGRESS)))
            val list = LeagueSix.listFixtures(matches)
            val otherGroup = LeagueSix.groups.first { it.group.id != fixture.groupId }.group
            val otherPitch = group.fixtures.map { it.venue }.first { it != fixture.venue }

            assertTrue(fixture.id.value in list(FixtureFilter(dates = DateWindow.TODAY)).ids())
            assertTrue(fixture.id.value in list(FixtureFilter(dates = DateWindow.PLAYED)).ids())
            assertFalse(fixture.id.value in list(FixtureFilter(groupId = otherGroup.id)).ids(), "league ignored")
            assertFalse(fixture.id.value in list(FixtureFilter(venue = otherPitch)).ids(), "pitch ignored")
            assertFalse(fixture.id.value in list(FixtureFilter(teamQuery = "no such team")).ids(), "team ignored")
        }

    @Test
    fun todayIsTodayOnly() =
        runTest {
            val listing = LeagueSix.listFixtures()(FixtureFilter(dates = DateWindow.TODAY))

            assertEquals(LeagueSix.fixtures.count { it.date == LeagueSix.today }, listing.rows().size)
            assertTrue(listing.rows().all { it.fixture.date == LeagueSix.today })
        }

    @Test
    fun playedIsEverythingBeforeTodayNewestFirst() =
        runTest {
            val listing = LeagueSix.listFixtures()(FixtureFilter(dates = DateWindow.PLAYED))
            val days = listing.sections.map { (it.heading as SectionHeading.Day).date }

            assertEquals(LeagueSix.fixtures.count { it.date < LeagueSix.today }, listing.rows().size)
            assertEquals(LeagueSix.yesterday, days.first(), "the newest day is not first")
            assertEquals(days.sortedDescending(), days)
            // Within a day, the latest kickoff first.
            val firstDay =
                listing.sections
                    .first()
                    .rows
                    .map { it.fixture.time }
            assertEquals(firstDay.sortedDescending(), firstDay)
        }

    @Test
    fun playedInOneGroupListsTheLatestRoundFirst() =
        runTest {
            val sixK = LeagueSix.group('K').group.id
            val listing = LeagueSix.listFixtures()(FixtureFilter(groupId = sixK, dates = DateWindow.PLAYED))
            val rounds = listing.sections.map { (it.heading as SectionHeading.Round).round }

            assertEquals(rounds.sortedDescending(), rounds)
        }

    @Test
    fun allDatesIsTheWholeSeason() =
        runTest {
            assertEquals(792, LeagueSix.listFixtures()(FixtureFilter(dates = DateWindow.ALL)).rows().size)
        }

    // -----------------------------------------------------------------
    // The shape of the list
    // -----------------------------------------------------------------

    @Test
    fun oneGroupInViewIsListedByRound() =
        runTest {
            val listing = LeagueSix.listFixtures()(FixtureFilter(groupId = LeagueSix.group('K').group.id))

            assertFalse(listing.byDay)
            val rounds = listing.sections.map { (it.heading as SectionHeading.Round).round }
            assertEquals(rounds.sorted(), rounds)
            assertTrue(listing.rows().all { it.group.reportCode == "6K" })
        }

    @Test
    fun severalGroupsInViewAreListedByDayAndEachRowSaysWhichGroup() =
        runTest {
            val listing = LeagueSix.listFixtures()(FixtureFilter(leagueLevel = 6))

            assertTrue(listing.byDay)
            val days = listing.sections.map { (it.heading as SectionHeading.Day).date }
            assertEquals(days.sorted(), days)
            assertEquals(days.distinct(), days, "a day appears under two headings")
            listing.sections.forEach { section ->
                assertTrue(section.rows.all { it.fixture.date == (section.heading as SectionHeading.Day).date })
            }
            assertTrue(
                listing
                    .rows()
                    .map { it.group.reportCode }
                    .distinct()
                    .size > 1,
            )
        }

    @Test
    fun aTeamPlayingInOneGroupIsListedByRoundEvenWithNoLeagueChosen() =
        runTest {
            // The shape follows what is in view, not what was chosen: one team
            // is one group, and its season reads by round.
            val listing = LeagueSix.listFixtures()(FixtureFilter(teamQuery = "krabice k", dates = DateWindow.ALL))

            assertFalse(listing.byDay)
            assertEquals(11, listing.rows().size)
        }

    @Test
    fun aFilterThatMatchesNothingSaysSoWhileTheLeagueHasData() =
        runTest {
            val listing = LeagueSix.listFixtures()(FixtureFilter(teamQuery = "no such team"))

            assertTrue(listing.isEmpty)
            assertTrue(listing.filteredToNothing)
        }

    // -----------------------------------------------------------------
    // Reports changing under the list
    // -----------------------------------------------------------------

    @Test
    fun aReportStartedOrChangingStateIsAnnouncedAndOtherSavesAreNot() =
        runTest {
            val fixture = LeagueSix.fixtures.first { it.date == LeagueSix.today }
            val matches = FakeMatchRepository()

            LeagueSix.listFixtures(matches).reportsChanged().test {
                val started = reportFor(fixture, MatchStatus.SETUP)
                matches.save(started)
                awaitItem()

                // A save that changes nothing the list shows: no rebuild.
                matches.save(started.copy())
                expectNoEvents()

                matches.save(started.copy(status = MatchStatus.IN_PROGRESS))
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    private val seventhLeague: LeagueGroup =
        LeagueSix.group('A').let { a ->
            val id = GroupId("7a")
            a.copy(
                group = Group(id, LeagueSix.season.id, "7. liga A", "7A"),
                teams = a.teams.map { it.copy(id = TeamId("7-" + it.id.value), groupId = id) },
                fixtures =
                    a.fixtures.map {
                        it.copy(
                            id = FixtureId("7-" + it.id.value),
                            groupId = id,
                            homeTeamId = TeamId("7-" + it.homeTeamId.value),
                            awayTeamId = TeamId("7-" + it.awayTeamId.value),
                            // Only the last five pitches, which league 6 never uses.
                            venue = LeagueSix.pitches[LeagueSix.pitches.size - 1 - it.round % 5].code,
                        )
                    },
            )
        }
}
