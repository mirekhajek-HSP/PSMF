package cz.hspinovace.psmf.usecase

import app.cash.turbine.test
import cz.hspinovace.psmf.domain.Fixture
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.TeamSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Zápis tab's list (prompt 17, Gate 1): every report on the device that
 * has a fixture to show, what is being worked on before what is signed, the
 * latest kickoff first in each, and following the database while it is
 * observed.
 *
 * The three fixtures of [TestLeague]: 31. 8. 19:00 (round 1), 7. 9. 18:00
 * and 7. 9. 20:30 (round 2, the late one first in the group's own list).
 */
class ObserveReportsTest {
    private val august = Fixtures.fixture
    private val septemberEarly = TestLeague.secondRoundEarly
    private val septemberLate = TestLeague.secondRoundLate

    private fun report(
        id: String,
        fixture: Fixture,
        status: MatchStatus,
    ) = Match(MatchId(id), fixture.id, fixture.groupId, status = status)

    private fun ReportListing.ids(rows: ReportListing.() -> List<ReportRow>) = rows().map { it.matchId.value }

    @Test
    fun whatIsBeingWorkedOnComesFirstAndWhatIsSignedAfter() =
        runTest {
            val matches =
                FakeMatchRepository(
                    listOf(
                        report("setup", august, MatchStatus.SETUP),
                        report("signed", septemberLate, MatchStatus.CONFIRMED),
                        report("played", septemberEarly, MatchStatus.FINISHED),
                    ),
                )

            val listing = ObserveReports(matches, TestLeague.repository())().first()

            assertEquals(listOf("played", "setup"), listing.ids { working })
            assertEquals(listOf("signed"), listing.ids { confirmed })
        }

    @Test
    fun everyUnsignedStatusIsBeingWorkedOn() =
        runTest {
            // Rozepsáno, Probíhá, Dohráno: none of them is finished with.
            val matches =
                FakeMatchRepository(
                    listOf(
                        report("setup", august, MatchStatus.SETUP),
                        report("live", septemberEarly, MatchStatus.IN_PROGRESS),
                        report("played", septemberLate, MatchStatus.FINISHED),
                    ),
                )

            val listing = ObserveReports(matches, TestLeague.repository())().first()

            assertEquals(setOf("setup", "live", "played"), listing.ids { working }.toSet())
            assertTrue(listing.confirmed.isEmpty())
        }

    @Test
    fun theLatestKickoffComesFirstByDateAndThenByTime() =
        runTest {
            // 21:00 at ZAKOS, the last kickoff of 7. 9., on the pitch that
            // sorts after METE1's 20:30: a list ordered by pitch rather than
            // by time would put it second.
            val latest =
                septemberEarly.copy(id = FixtureId("6k-r2-latest"), ref = "6k-r2-latest", time = LocalTime(21, 0))
            val league =
                FakeLeagueRepository(listOf(TestLeague.group.copy(fixtures = TestLeague.group.fixtures + latest)))
            // Saved in the order a sort by date alone, or by the group's own
            // list, would leave unchanged.
            val matches =
                FakeMatchRepository(
                    listOf(
                        report("august", august, MatchStatus.FINISHED),
                        report("early", septemberEarly, MatchStatus.SETUP),
                        report("late", septemberLate, MatchStatus.IN_PROGRESS),
                        report("latest", latest, MatchStatus.SETUP),
                    ),
                )

            val listing = ObserveReports(matches, league)().first()

            assertEquals(listOf("latest", "late", "early", "august"), listing.ids { working })
        }

    @Test
    fun signedReportsAreInKickoffOrderToo() =
        runTest {
            val matches =
                FakeMatchRepository(
                    listOf(
                        report("august", august, MatchStatus.CONFIRMED),
                        report("late", septemberLate, MatchStatus.CONFIRMED),
                        report("early", septemberEarly, MatchStatus.CONFIRMED),
                    ),
                )

            val listing = ObserveReports(matches, TestLeague.repository())().first()

            assertTrue(listing.working.isEmpty())
            assertEquals(listOf("late", "early", "august"), listing.ids { confirmed })
        }

    @Test
    fun aRowCarriesTheTeamsTheGroupTheKickoffTheStatusAndWhereItOpens() =
        runTest {
            val matches = FakeMatchRepository(listOf(report("live", august, MatchStatus.IN_PROGRESS)))

            val row = ObserveReports(matches, TestLeague.repository())().first().working.single()

            assertEquals("Kominíci", row.homeTeam.name)
            assertEquals("Sp. Sumýš", row.awayTeam.name)
            assertEquals("6K", row.group.reportCode)
            assertEquals(august, row.fixture)
            assertEquals(MatchStatus.IN_PROGRESS, row.status)
            // Where tapping its fixture in Zápasy lands, and no other place.
            assertEquals(MatchStatus.IN_PROGRESS.resumePoint(), row.resumePoint)
        }

    @Test
    fun eachRowOpensWhereItsFixtureInZapasyWould() =
        runTest {
            val matches =
                FakeMatchRepository(
                    listOf(
                        report("setup", august, MatchStatus.SETUP),
                        report("live", septemberEarly, MatchStatus.IN_PROGRESS),
                        report("played", septemberLate, MatchStatus.FINISHED),
                    ),
                )

            val listing = ObserveReports(matches, TestLeague.repository())().first()

            assertEquals(
                mapOf("setup" to ResumePoint.HEADER, "live" to ResumePoint.CONSOLE, "played" to ResumePoint.RECAP),
                listing.working.associate { it.matchId.value to it.resumePoint },
            )
        }

    @Test
    fun aReportWhoseFixtureIsNoLongerInLeagueDataIsNotListed() =
        runTest {
            // As ObserveReportInProgress does not badge one: a report saved
            // against the invented 6-K before 0.2.0 has nothing to draw.
            val stray =
                Match(MatchId("old"), FixtureId("6k-placeholder"), Fixtures.groupId, status = MatchStatus.FINISHED)
            val matches = FakeMatchRepository(listOf(stray, report("setup", august, MatchStatus.SETUP)))

            val listing = ObserveReports(matches, TestLeague.repository())().first()

            assertEquals(listOf("setup"), listing.ids { working })
            assertTrue(listing.confirmed.isEmpty())
        }

    @Test
    fun noReportsIsAnEmptyListing() =
        runTest {
            val listing = ObserveReports(FakeMatchRepository(), TestLeague.repository())().first()

            assertTrue(listing.isEmpty)
        }

    @Test
    fun aReportStartedWhileTheListIsObservedAppearsInIt() =
        runTest {
            // Observed, not read once on entry: the bug shape TODO's "Known
            // gaps" names, which a list read on entry would repeat.
            val matches = FakeMatchRepository(listOf(report("setup", august, MatchStatus.SETUP)))

            ObserveReports(matches, TestLeague.repository())().test {
                assertEquals(listOf("setup"), awaitItem().ids { working })

                StartOrResumeMatch(TestLeague.repository(), matches) { "started" }(septemberLate.id)

                assertEquals(listOf("started", "setup"), awaitItem().ids { working })
            }
        }

    @Test
    fun aReportThatMovesOnMovesInTheList() =
        runTest {
            val played = report("played", august, MatchStatus.FINISHED)
            val matches = FakeMatchRepository(listOf(played))

            ObserveReports(matches, TestLeague.repository())().test {
                assertEquals(listOf("played"), awaitItem().ids { working })

                matches.save(played.copy(status = MatchStatus.CONFIRMED))

                val after = awaitItem()
                assertTrue(after.working.isEmpty())
                assertEquals(listOf("played"), after.ids { confirmed })
            }
        }

    @Test
    fun aGoalDoesNotRedrawTheList() =
        runTest {
            // Every change to a report is written through, a goal included;
            // none of them changes a row, so none of them is a new list.
            val live = report("live", august, MatchStatus.IN_PROGRESS)
            val matches = FakeMatchRepository(listOf(live))

            ObserveReports(matches, TestLeague.repository())().test {
                awaitItem()

                LogGoal(matches)(live, TeamSide.HOME, null, Minute.Played(5))

                expectNoEvents()
            }
        }
}
