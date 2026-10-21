package cz.hspinovace.psmf.data.match

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.FakeAddedPlayerRepository
import cz.hspinovace.psmf.usecase.FakeRememberedCardNumberRepository
import cz.hspinovace.psmf.usecase.FakeRememberedDateOfBirthRepository
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.RestoreLineupDraft
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.SaveLineupDraft
import cz.hspinovace.psmf.usecase.TestLeague
import cz.hspinovace.psmf.usecase.halfDone
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * **A lineup in progress must outlive the process that was building it**
 * (prompt 15, Gate 3), and must never be mistaken for a lineup.
 *
 * Like `MatchPersistenceTest`, these open a real database file, close the
 * driver -- which is what the app being killed amounts to -- and open a fresh
 * one against the same bytes. The second half of the class is the
 * non-negotiable: a draft sits beside the report and nothing the report is
 * read through can see it.
 */
class LineupDraftPersistenceTest {
    private val databaseFile: File = File.createTempFile("psmf-draft-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        databaseFile.delete()
    }

    private val matchId = MatchId("m1")
    private val match = Match(matchId, Fixtures.fixtureId, Fixtures.groupId)
    private var minted = 0

    /** A separate app run against the same database file. */
    private suspend fun <T> session(block: suspend (Session) -> T): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${databaseFile.absolutePath}").create()
        try {
            val database = PsmfDatabase(driver)
            return block(
                Session(database, SqlDelightMatchRepository(database), SqlDelightLineupDraftRepository(database)),
            )
        } finally {
            driver.close()
        }
    }

    private class Session(
        val database: PsmfDatabase,
        val matches: MatchRepository,
        val drafts: LineupDraftRepository,
    )

    private suspend fun built(): LineupEntry =
        assertNotNull(
            BuildLineupEntry(
                TestLeague.repository(),
                FakeAddedPlayerRepository(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) { "id-${++minted}" }(match),
        )

    // ---------------------------------------------------------------------
    // It survives
    // ---------------------------------------------------------------------

    @Test
    fun everythingDoneToATeamIsThereInTheNextRunOfTheApp() =
        runTest {
            val entry = built()
            val edited = entry.home.halfDone(wearing = Fixtures.homeAlternateKit.id)

            session { SaveLineupDraft(it.drafts)(matchId, edited) }

            val restored = session { RestoreLineupDraft(it.drafts)(matchId, entry) }
            assertEquals(edited, restored.home, "something done on the screen did not come back from the database")
        }

    @Test
    fun aDraftIsReplacedWholeAndTheOtherTeamsIsLeftAlone() =
        runTest {
            val entry = built()
            val home = entry.home.halfDone()
            val away =
                entry.away.withPresence(
                    entry.away.members
                        .first()
                        .player.id,
                    true,
                )
            session {
                SaveLineupDraft(it.drafts)(matchId, home)
                SaveLineupDraft(it.drafts)(matchId, away)
            }

            // The home team's marks are taken back: a smaller draft, not a merge with the old.
            val smaller =
                entry.home.withPresence(
                    entry.home.members
                        .first()
                        .player.id,
                    true,
                )
            session { SaveLineupDraft(it.drafts)(matchId, smaller) }

            val restored = session { RestoreLineupDraft(it.drafts)(matchId, entry) }
            assertEquals(smaller, restored.home)
            assertEquals(away, restored.away)
        }

    @Test
    fun aDraftWithFewerMembersDoesNotLeaveTheOldOnesBehind() =
        runTest {
            val entry = built()
            session { SaveLineupDraft(it.drafts)(matchId, entry.home.halfDone()) }
            val drafted = session { it.drafts.forMatch(matchId) }.single()
            assertEquals(entry.home.members.size, drafted.members.size)

            session { it.drafts.save(drafted.copy(members = drafted.members.take(1))) }

            assertEquals(1, session { it.drafts.forMatch(matchId) }.single().members.size, "an old member row survived")
        }

    @Test
    fun aDraftBelongsToItsMatch() =
        runTest {
            val entry = built()
            session { SaveLineupDraft(it.drafts)(matchId, entry.home.halfDone()) }

            assertEquals(emptyList(), session { it.drafts.forMatch(MatchId("another")) })
        }

    // ---------------------------------------------------------------------
    // It is never a lineup
    // ---------------------------------------------------------------------

    @Test
    fun aDraftIsNotALineupToAnythingThatReadsTheReport() =
        runTest {
            val entry = built()
            val problemsBefore = match.reportProblems()
            session { it.matches.save(match) }

            session { SaveLineupDraft(it.drafts)(matchId, entry.home.halfDone()) }

            val loaded = session { assertNotNull(it.matches.load(matchId)) }
            assertEquals(match, loaded, "the report changed because a draft was written")
            assertNull(loaded.homeLineup)
            assertNull(loaded.awayLineup)
            assertEquals(problemsBefore, loaded.reportProblems())
            assertTrue(ReportProblem.MissingLineup(TeamSide.HOME) in loaded.reportProblems())
            assertEquals(
                match.homeLineup,
                session {
                    it.matches
                        .findByStatus(match.status)
                        .single()
                        .homeLineup
                },
            )
        }

    @Test
    fun savingTheReportLeavesTheDraftBe() =
        runTest {
            val entry = built()
            val edited = entry.home.halfDone()
            session {
                it.matches.save(match)
                SaveLineupDraft(it.drafts)(matchId, edited)
            }

            session { it.matches.save(match.copy(kickoffAt = Instant.parse("2026-08-31T19:00:00Z"))) }

            val restored = session { RestoreLineupDraft(it.drafts)(matchId, entry) }
            assertEquals(edited, restored.home, "writing the report took the draft with it")
        }

    @Test
    fun aLineupThatIsWholeIsWrittenAsBeforeAndTheDraftBesideItAgrees() =
        runTest {
            val entry = built()
            val whole =
                entry.home.halfDone().let { team ->
                    team
                        .withMember(team.members[2].player.id) {
                            it.withCardNumberTyped("59003")
                        }.withPresence(team.members[2].player.id, true)
                }
            session {
                it.matches.save(match)
                SaveLineupDraft(it.drafts)(matchId, whole)
                SaveLineup(it.matches)(match, whole)
            }

            val loaded = session { assertNotNull(it.matches.load(matchId)) }
            val restored = session { RestoreLineupDraft(it.drafts)(matchId, entry) }
            assertEquals(whole.toLineup(), loaded.homeLineup)
            assertEquals(whole, restored.home)
        }

    @Test
    fun deletingTheMatchDeletesItsDraftsAndNobodyElses() =
        runTest {
            val entry = built()
            val other = MatchId("m2")
            session {
                it.matches.save(match)
                it.matches.save(Match(other, Fixtures.fixtureId, Fixtures.groupId))
                SaveLineupDraft(it.drafts)(matchId, entry.home.halfDone())
                SaveLineupDraft(it.drafts)(
                    matchId,
                    entry.away.withPresence(
                        entry.away.members
                            .first()
                            .player.id,
                        true,
                    ),
                )
                SaveLineupDraft(it.drafts)(other, entry.home.halfDone())
            }

            session { it.matches.delete(matchId) }

            assertEquals(emptyList(), session { it.drafts.forMatch(matchId) }, "a draft outlived its match")
            assertEquals(1, session { it.drafts.forMatch(other) }.size)
            session {
                // And no rows were left behind in the members' table either.
                assertEquals(
                    0L,
                    it.database.matchRecordQueries
                        .selectLineupDraftMembers(matchId.value)
                        .executeAsList()
                        .size
                        .toLong(),
                )
            }
        }

    // ---------------------------------------------------------------------
    // It never stops the screen opening
    // ---------------------------------------------------------------------

    @Test
    fun aRowThisVersionCannotReadIsSkippedAndTheRestOfTheDraftIsStillThere() =
        runTest {
            val entry = built()
            val edited = entry.home.halfDone()
            session { SaveLineupDraft(it.drafts)(matchId, edited) }

            session {
                // A side that does not exist, and a time that is not one.
                it.database.matchRecordQueries.upsertLineupDraft(
                    matchId.value,
                    "MIDDLE",
                    "k",
                    null,
                    null,
                    null,
                    null,
                    0L,
                )
                it.database.matchRecordQueries.upsertLineupDraft(
                    matchId.value,
                    "AWAY",
                    entry.away.kitId.value,
                    null,
                    null,
                    "yesterday-ish",
                    "Nobody",
                    0L,
                )
            }

            val drafts = session { it.drafts.forMatch(matchId) }
            assertEquals(setOf(TeamSide.HOME, TeamSide.AWAY), drafts.map { it.side }.toSet())
            assertEquals(edited, session { RestoreLineupDraft(it.drafts)(matchId, entry) }.home)
            assertNull(drafts.single { it.side == TeamSide.AWAY }.confirmation)
        }
}
