package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Screen 1's data: seed fixtures joined to whatever reports exist. */
class ListFixturesTest {
    private fun listFixtures(matches: FakeMatchRepository = FakeMatchRepository()) =
        ListFixtures(TestLeague.repository(), matches)

    @Test
    fun groupsFixturesByRoundInOrder() =
        runTest {
            val listing = listFixtures()()

            val rounds = listing.groups.single().rounds
            assertEquals(listOf(1, 2), rounds.map { it.round })
        }

    @Test
    fun ordersFixturesWithinARoundByKickoff() =
        runTest {
            // The seed file lists the late kickoff first. A referee scanning
            // for their own match reads by time, so the list is by time.
            val listing = listFixtures()()

            val secondRound =
                listing.groups
                    .single()
                    .rounds
                    .first { it.round == 2 }
            assertEquals(
                listOf(TestLeague.secondRoundEarly.id, TestLeague.secondRoundLate.id),
                secondRound.fixtures.map { it.fixture.id },
            )
        }

    @Test
    fun resolvesTeamsAndVenueSoTheRowNeedsNoFurtherLookups() =
        runTest {
            val row =
                listFixtures()()
                    .groups
                    .single()
                    .rounds
                    .first()
                    .fixtures
                    .single()

            assertEquals(Fixtures.homeTeam.name, row.homeTeam.name)
            assertEquals(Fixtures.awayTeam.name, row.awayTeam.name)
            assertEquals(TestLeague.venue, row.venue)
        }

    @Test
    fun aFixtureWithNoReportCarriesNoStatus() =
        runTest {
            assertTrue(
                listFixtures()()
                    .groups
                    .single()
                    .rounds
                    .flatMap { it.fixtures }
                    .all { it.reportStatus == null },
            )
        }

    @Test
    fun aFixtureWithAReportUnderWayCarriesItsStatus() =
        runTest {
            // The only route back into a match the app was killed in the
            // middle of, so the badge is not decoration.
            val inProgress =
                Fixtures.matchInSetup().copy(id = MatchId("m1"), status = MatchStatus.IN_PROGRESS)
            val listing = listFixtures(FakeMatchRepository(listOf(inProgress)))()

            val rows =
                listing.groups
                    .single()
                    .rounds
                    .flatMap { it.fixtures }
            val started = rows.single { it.fixture.id == Fixtures.fixtureId }
            assertEquals(MatchStatus.IN_PROGRESS, started.reportStatus)
            assertTrue(rows.filterNot { it.fixture.id == Fixtures.fixtureId }.all { it.reportStatus == null })
        }

    @Test
    fun oneGroupNeedsNoGroupHeadings() =
        runTest {
            // Whether the list shows group headings is asked of the data, not
            // hardcoded: dropping a second file into files/leagues/ has to
            // change the UI without anyone editing a screen.
            assertTrue(!listFixtures()().hasSeveralGroups)
        }

    @Test
    fun anEmptyCatalogueIsEmptyRatherThanBroken() =
        runTest {
            val empty = ListFixtures(FakeLeagueRepository(emptyList()), FakeMatchRepository())
            assertTrue(empty().isEmpty)
        }

    @Test
    fun filteringByVenueLeavesOnlyFixturesAtThatPitch() =
        runTest {
            // TestLeague.group plays two fixtures at ZAKOS and one at
            // METE1 -- the pitch that is not the group's default, so a
            // filter that quietly ignored venue would still pass by luck.
            val listing = listFixtures()(FixtureFilter(venue = TestLeague.otherVenue.code))

            val fixtures = listing.groups.flatMap { it.rounds.flatMap { round -> round.fixtures } }
            assertEquals(listOf(TestLeague.secondRoundLate.id), fixtures.map { it.fixture.id })
        }

    @Test
    fun theOptionsCarryEveryPitchInTheLeague() =
        runTest {
            // Built from the unfiltered data, same reasoning as leagues: a
            // chip that vanished as soon as it was picked would leave no
            // way back to the pitches not currently selected.
            val venues = listFixtures()().options.venues.map { it.code }

            assertEquals(listOf(TestLeague.venue.code, TestLeague.otherVenue.code), venues)
        }
}

/**
 * Narrowing the list.
 *
 * The list is flat and shows every bundled fixture. One group of twelve
 * teams fits on a screen; nine divisions is on the order of nine hundred
 * teams and several thousand fixtures, and a referee officiates a handful.
 */
class FixtureFilterTest {
    /** A second league, so filtering by one has something to exclude. */
    private val otherGroupId = GroupId("7a")

    private val otherTeam =
        Team(
            id = TeamId("fk-letna"),
            ref = "fk-letna",
            groupId = otherGroupId,
            name = "FK Letná",
            kits = listOf(Kit(KitId("kit-letna-1"), "žlutá", listOf("žlutá"))),
        )

    private val otherOpponent =
        Team(
            id = TeamId("ac-stromovka"),
            ref = "ac-stromovka",
            groupId = otherGroupId,
            name = "AC Stromovka",
            kits = listOf(Kit(KitId("kit-stromovka-1"), "červená", listOf("červená"))),
        )

    private val otherGroup =
        LeagueGroup(
            season = TestLeague.season,
            group = Group(id = otherGroupId, seasonId = Fixtures.seasonId, name = "7. liga A", reportCode = "7A"),
            teams = listOf(otherTeam, otherOpponent),
            players = emptyList(),
            fixtures =
                listOf(
                    Fixture(
                        id = FixtureId("7a-r1-01"),
                        ref = "7a-r1-01",
                        groupId = otherGroupId,
                        round = 1,
                        date = LocalDate(2026, 9, 2),
                        time = LocalTime(19, 0),
                        venue = TestLeague.venue.code,
                        homeTeamId = otherTeam.id,
                        awayTeamId = otherOpponent.id,
                    ),
                ),
            venues = listOf(TestLeague.venue),
        )

    /** Same level as TestLeague.group (6), a different letter. */
    private val anotherSixthLevelGroupId = GroupId("6l")

    private val anotherSixthLevelTeam =
        Team(
            id = TeamId("dynamo-kotelna"),
            ref = "dynamo-kotelna",
            groupId = anotherSixthLevelGroupId,
            name = "Dynamo Kotelna",
            kits = listOf(Kit(KitId("kit-dynamo-1"), "černá", listOf("černá"))),
        )

    private val anotherSixthLevelOpponent =
        Team(
            id = TeamId("sk-vinohrady"),
            ref = "sk-vinohrady",
            groupId = anotherSixthLevelGroupId,
            name = "SK Vinohrady",
            kits = listOf(Kit(KitId("kit-vinohrady-1"), "zelená", listOf("zelená"))),
        )

    private val anotherSixthLevelGroup =
        LeagueGroup(
            season = TestLeague.season,
            group =
                Group(
                    id = anotherSixthLevelGroupId,
                    seasonId = Fixtures.seasonId,
                    name = "6. liga L",
                    reportCode = "6L",
                ),
            teams = listOf(anotherSixthLevelTeam, anotherSixthLevelOpponent),
            players = emptyList(),
            fixtures =
                listOf(
                    Fixture(
                        id = FixtureId("6l-r1-01"),
                        ref = "6l-r1-01",
                        groupId = anotherSixthLevelGroupId,
                        round = 1,
                        date = LocalDate(2026, 9, 2),
                        time = LocalTime(19, 0),
                        venue = TestLeague.venue.code,
                        homeTeamId = anotherSixthLevelTeam.id,
                        awayTeamId = anotherSixthLevelOpponent.id,
                    ),
                ),
            venues = listOf(TestLeague.venue),
        )

    private fun listFixtures(groups: List<LeagueGroup> = listOf(TestLeague.group, otherGroup)) =
        ListFixtures(FakeLeagueRepository(groups), FakeMatchRepository())

    private fun FixtureListing.teamNames(): List<String> =
        groups
            .flatMap { group -> group.rounds.flatMap { it.fixtures } }
            .flatMap { listOf(it.homeTeam.name, it.awayTeam.name) }
            .distinct()

    @Test
    fun withNoFilterEveryLeagueIsListed() =
        runTest {
            val listing = listFixtures()()

            assertEquals(listOf("6. liga K", "7. liga A"), listing.groups.map { it.group.name })
            assertTrue(listing.hasSeveralGroups)
            assertTrue(listing.filter.isEmpty)
        }

    @Test
    fun filteringByLeagueLeavesOnlyThatLeague() =
        runTest {
            val listing = listFixtures()(FixtureFilter(groupId = otherGroupId))

            assertEquals(listOf("7. liga A"), listing.groups.map { it.group.name })
            assertFalse(listing.hasSeveralGroups)
        }

    @Test
    fun filteringByTeamLeavesOnlyThatTeamsFixtures() =
        runTest {
            // Diacritics folded, the same rule the Týmy tab's own search
            // uses: typing "letna" has to find "FK Letná".
            val listing = listFixtures()(FixtureFilter(teamQuery = "letna"))

            val fixtures = listing.groups.flatMap { group -> group.rounds.flatMap { it.fixtures } }
            assertEquals(1, fixtures.size)
            assertTrue(listing.teamNames().contains("FK Letná"))
            // The other league's rounds are still there as empty groups,
            // which is why `isEmpty` asks about fixtures and not groups.
            assertFalse(listing.isEmpty)
        }

    @Test
    fun filteringByTeamMatchesEitherSideOfTheFixture() =
        runTest {
            // "AC Stromovka" is the away side of the one fixture in
            // otherGroup -- a query that only checked the home team would
            // miss it.
            val listing = listFixtures()(FixtureFilter(teamQuery = "stromovka"))

            assertTrue(listing.teamNames().contains("AC Stromovka"))
        }

    @Test
    fun aFilterThatExcludesEverythingSaysSoRatherThanLookingLikeNoData() =
        runTest {
            // "No fixtures" and "no fixtures matching what you asked for"
            // are different sentences, and only one of them is alarming.
            val listing = listFixtures()(FixtureFilter(groupId = otherGroupId, teamQuery = "no such team"))

            assertTrue(listing.isEmpty)
            assertTrue(listing.filteredToNothing)
        }

    @Test
    fun aLeagueLevelAloneFiltersToEveryGroupUnderIt() =
        runTest {
            // "A league alone is a valid filter" -- picking level 6 with no
            // letter must keep both of that level's groups, not force one.
            val listing =
                listFixtures(listOf(TestLeague.group, otherGroup, anotherSixthLevelGroup))(
                    FixtureFilter(leagueLevel = 6),
                )

            assertEquals(
                setOf("6. liga K", "6. liga L"),
                listing.groups.map { it.group.name }.toSet(),
            )
        }

    @Test
    fun pickingASpecificGroupNarrowsPastTheLevelItIsIn() =
        runTest {
            // The more specific dimension wins: a groupId always means
            // exactly that group, whatever leagueLevel also says.
            val listing =
                listFixtures(listOf(TestLeague.group, otherGroup, anotherSixthLevelGroup))(
                    FixtureFilter(leagueLevel = 6, groupId = anotherSixthLevelGroup.group.id),
                )

            assertEquals(listOf("6. liga L"), listing.groups.map { it.group.name })
        }

    @Test
    fun theOptionsCascadeFromLevelToLetter() =
        runTest {
            // Row one is levels, row two is that level's letters -- built
            // from whatever groups are loaded, not a hardcoded count.
            val options =
                listFixtures(listOf(TestLeague.group, otherGroup, anotherSixthLevelGroup))().options

            assertEquals(listOf(6, 7), options.leagueLevels.map { it.level })
            assertEquals(
                listOf("K", "L"),
                options.leagueLevels
                    .first { it.level == 6 }
                    .groups
                    .map { it.groupLetter },
            )
            assertEquals(
                listOf("A"),
                options.leagueLevels
                    .first { it.level == 7 }
                    .groups
                    .map { it.groupLetter },
            )
        }

    @Test
    fun theOptionsStayCompleteWhileAFilterIsInForce() =
        runTest {
            // Options built from the filtered data would vanish as soon as
            // they were used, leaving no way back except knowing to clear
            // the filter first.
            val options = listFixtures()(FixtureFilter(groupId = otherGroupId)).options

            assertEquals(2, options.leagueLevels.sumOf { it.groups.size })
        }
}

/**
 * RULE: **tapping a fixture is the only way in, whether or not a report
 * already exists.**
 *
 * There is deliberately no separate "resume" affordance. A referee whose
 * phone died mid-match taps the row they tapped the first time.
 */
class StartOrResumeMatchTest {
    private var minted = 0

    private fun startOrResume(matches: FakeMatchRepository) =
        StartOrResumeMatch(TestLeague.repository(), matches) { "new-${++minted}" }

    @Test
    fun startingAFixtureCreatesAReportInSetup() =
        runTest {
            val matches = FakeMatchRepository()

            val match = startOrResume(matches)(Fixtures.fixtureId)

            assertNotNull(match)
            assertEquals(MatchStatus.SETUP, match.status)
            assertEquals(Fixtures.fixtureId, match.fixtureId)
            assertEquals(Fixtures.groupId, match.groupId)
        }

    @Test
    fun theReportIsOnDiskBeforeAnyFieldIsFilledIn() =
        runTest {
            // What makes every later screen survivable: they all write
            // through to a row that already exists, so there is no window in
            // which the match lives only in memory.
            val matches = FakeMatchRepository()

            val match = startOrResume(matches)(Fixtures.fixtureId)!!

            assertEquals(1, matches.saves)
            assertEquals(match, matches.load(match.id))
        }

    @Test
    fun tappingTheSameFixtureAgainResumesRatherThanStartingOver() =
        runTest {
            val matches = FakeMatchRepository()
            val first = startOrResume(matches)(Fixtures.fixtureId)!!

            val second = startOrResume(matches)(Fixtures.fixtureId)

            assertEquals(first.id, second?.id)
            // The second tap must not have overwritten anything.
            assertEquals(1, matches.saves)
        }

    @Test
    fun aMatchKilledInProgressComesBackInProgress() =
        runTest {
            val killed =
                Fixtures.matchInSetup().copy(id = MatchId("m1"), status = MatchStatus.IN_PROGRESS)
            val matches = FakeMatchRepository(listOf(killed))

            val resumed = startOrResume(matches)(Fixtures.fixtureId)

            assertEquals(MatchStatus.IN_PROGRESS, resumed?.status)
            assertEquals(MatchId("m1"), resumed?.id)
        }

    @Test
    fun aFixtureThatIsNotInTheLeagueDataStartsNothing() =
        runTest {
            val matches = FakeMatchRepository()

            assertNull(startOrResume(matches)(FixtureId("not-a-fixture")))
            assertEquals(0, matches.saves)
        }

    @Test
    fun twoDifferentFixturesGetTwoDifferentReports() =
        runTest {
            val matches = FakeMatchRepository()
            val first = startOrResume(matches)(Fixtures.fixtureId)!!
            val second = startOrResume(matches)(TestLeague.secondRoundEarly.id)!!

            assertTrue(first.id != second.id)
            assertEquals(2, matches.saves)
        }
}

/** The header itself, once it is complete, is what reaches the database. */
class SaveMatchHeaderTest {
    private val complete =
        MatchHeaderEntry(
            refereeName = "Jiri Vlk",
            assistantName = "Roman Liska",
            assistantLicensedHire = true,
            delegatingTeam = "Kominici",
        )

    @Test
    fun acompleteHeaderIsWrittenThrough() =
        runTest {
            val match = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
            val matches = FakeMatchRepository(listOf(match))

            val updated = SaveMatchHeader(matches)(match, complete)

            val officials = assertNotNull(updated.officials)
            assertEquals("Jiri Vlk", officials.main.name.value)
            assertTrue(officials.assistant?.licensedHire == true)
            assertEquals("Kominici", officials.delegatingTeam)
            assertEquals(updated, matches.load(match.id))
        }

    @Test
    fun anIncompleteHeaderWritesNothingAndLosesNothing() =
        runTest {
            // RefereeAssignment is all-or-nothing by construction, so there
            // is no partial value to store. What is at stake is a couple of
            // words for a couple of seconds -- see the note on SaveMatchHeader.
            val match = Match(MatchId("m1"), Fixtures.fixtureId, Fixtures.groupId)
            val matches = FakeMatchRepository(listOf(match))

            val unchanged = SaveMatchHeader(matches)(match, complete.copy(delegatingTeam = ""))

            assertEquals(match, unchanged)
            assertEquals(0, matches.saves)
            assertNull(matches.load(match.id)?.officials)
        }
}

/**
 * RULE: **the delegating team is neither of the teams playing.**
 *
 * The screen says so in prose; this is the same rule where it can be
 * checked. Offering the two playing teams as taps, directly under that
 * prose, would make the likeliest mistake also the easiest one.
 */
class DelegatingTeamOptionsTest {
    @Test
    fun neitherPlayingTeamIsOffered() =
        runTest {
            val loaded = assertNotNull(TestLeague.repository().fixture(Fixtures.fixtureId))

            val options = loaded.delegatingTeamOptions()

            assertTrue(Fixtures.homeTeam.name !in options)
            assertTrue(Fixtures.awayTeam.name !in options)
        }

    @Test
    fun everyOtherTeamInTheGroupIs() =
        runTest {
            // The test league holds only the two playing teams, so adding a
            // third is what shows the filter keeps the rest.
            val loaded = assertNotNull(TestLeague.repository().fixture(Fixtures.fixtureId))
            val third = Fixtures.homeTeam.copy(id = TeamId("third"), ref = "third", name = "Sokol")
            val withAThirdTeam =
                loaded.copy(leagueGroup = loaded.leagueGroup.copy(teams = loaded.leagueGroup.teams + third))

            assertEquals(listOf("Sokol"), withAThirdTeam.delegatingTeamOptions())
        }
}

/**
 * RULE: **tapping the row is the recovery gesture, so it lands where the
 * work is.**
 *
 * A referee whose phone died at minute 40 must come back to the console,
 * not to a header they filled in an hour earlier. Found on a device: the
 * data all survived, and the referee was still put two screens away from
 * it.
 */
class ResumePointTest {
    @Test
    fun aReportWithNothingRecordedStartsAtTheTop() {
        assertEquals(ResumePoint.HEADER, MatchStatus.SETUP.resumePoint())
    }

    @Test
    fun aMatchUnderWayResumesAtTheConsole() {
        assertEquals(ResumePoint.CONSOLE, MatchStatus.IN_PROGRESS.resumePoint())
    }

    @Test
    fun aPlayedMatchResumesAtTheRecapWhereItIsFinishedOff() {
        // The recap holds the result, the confirmations and the way to the
        // export, so a report that is over but not sent belongs there.
        assertEquals(ResumePoint.RECAP, MatchStatus.FINISHED.resumePoint())
        assertEquals(ResumePoint.RECAP, MatchStatus.CONFIRMED.resumePoint())
    }
}
