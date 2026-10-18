package cz.hspinovace.psmf.data.db

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.data.player.SqlDelightAddedPlayerRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Lineup
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.domain.nextUndoStep
import cz.hspinovace.psmf.domain.reportProblems
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.CompleteReport
import cz.hspinovace.psmf.export.NoAddedPlayers
import cz.hspinovace.psmf.export.ZouLabels
import cz.hspinovace.psmf.export.ZouText
import cz.hspinovace.psmf.export.ZouWords
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.FakeRememberedCardNumberRepository
import cz.hspinovace.psmf.usecase.FakeRememberedDateOfBirthRepository
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.UndoLastEvent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * **An update must not be an uninstall.**
 *
 * Until 2026-08-31 there were no migrations, and the demo report said a
 * clean install was required. An uninstall takes the match database with
 * it, so on a phone that is being installed to repeatedly that instruction
 * is a standing offer to destroy a report the referee has not yet sent.
 *
 * These tests start from the *recorded bytes* of a released schema --
 * `databases/1.db`, `databases/2.db` -- rather than from a fresh database
 * with the old CREATE statements replayed into it. A migration test that
 * starts from an empty database proves only that the SQL parses.
 *
 * `verifyCommonMainPsmfDatabaseMigration` covers the other half, and covers
 * it better than a test could: it compares the *shape* of every migrated
 * schema against the `.sq` files. What it cannot do is notice that the
 * shape survived and the rows did not.
 *
 * **Only one assertion in the module names the version number**, and it is
 * in `DatabaseSmokeTest`. Everything here asks
 * `PsmfDatabase.Schema.version`, so adding a migration does not break four
 * tests that were not about the number.
 */
class SchemaMigrationTest {
    private val temporaryFiles = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temporaryFiles.forEach { it.delete() }
    }

    private val current: Int = PsmfDatabase.Schema.version.toInt()

    private val schemaDirectory: File =
        File(
            System.getProperty(SCHEMA_DIRECTORY_PROPERTY)
                ?: error("$SCHEMA_DIRECTORY_PROPERTY is unset -- see the Test wiring in shared/build.gradle.kts"),
        )

    /**
     * A writable copy of a released schema, stamped as a database created
     * by that release would have been.
     *
     * The stamp is not decoration. `generateCommonMainPsmfDatabaseSchema`
     * leaves `user_version` at 0, whereas every database on a device is
     * stamped by the driver that created it -- so without this the driver
     * reads 0, concludes the database is new, and tries to create tables
     * that are already there.
     */
    private fun databaseAtVersion(version: Int): File {
        val recorded = File(schemaDirectory, "$version.db")
        assertTrue(
            recorded.isFile,
            "$recorded is missing. Run :shared:generateCommonMainPsmfDatabaseSchema " +
                "BEFORE changing a .sq file, never after -- see the README beside it.",
        )
        val working = File.createTempFile("psmf-v$version-", ".db")
        temporaryFiles += working
        recorded.copyTo(working, overwrite = true)
        execute(working, "PRAGMA user_version = $version")
        return working
    }

    /**
     * Writes rows into an old database without touching its version, the
     * way the release that created it would have.
     *
     * Today's code cannot write into an old schema directly once a migration
     * adds a column to a table it writes -- 4.sqm added `sequence` to three
     * -- so [block] writes into a scratch database at the current version,
     * and the rows are then copied across with **only the columns the old
     * table has**. A column the old schema lacks is exactly a value the old
     * release could not have stored, so leaving it behind is what makes the
     * rows genuinely predate the migration. Nothing is migrated here; the
     * old file keeps its version.
     */
    private suspend fun beforeTheUpdate(
        file: File,
        block: suspend (MatchRepository) -> Unit,
    ) = beforeTheUpdateIn(file) { block(SqlDelightMatchRepository(it)) }

    /** [beforeTheUpdate] for a test that has to write to more than the reports. */
    private suspend fun beforeTheUpdateIn(
        file: File,
        block: suspend (PsmfDatabase) -> Unit,
    ) {
        val scratch = File.createTempFile("psmf-scratch-", ".db").also { it.delete() }
        temporaryFiles += scratch
        val driver = DatabaseDriverFactory("jdbc:sqlite:${scratch.absolutePath}").create()
        try {
            block(PsmfDatabase(driver))
        } finally {
            driver.close()
        }
        connect(file).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("ATTACH DATABASE '${scratch.absolutePath}' AS fresh")
                tableNames(file).forEach { table -> copyOldColumns(connection, table) }
                statement.execute("DETACH DATABASE fresh")
            }
        }
    }

    /** One table's rows from `fresh` into `main`, in the columns `main` has. */
    private fun copyOldColumns(
        connection: Connection,
        table: String,
    ) {
        val old = columnNames(connection, "main", table)
        val columns = old.intersect(columnNames(connection, "fresh", table))
        if (columns.isEmpty()) return
        val list = columns.joinToString(", ")
        connection.createStatement().use {
            it.execute(
                "INSERT INTO main.$table ($list) SELECT $list FROM fresh.$table",
            )
        }
    }

    private fun columnNames(
        connection: Connection,
        schema: String,
        table: String,
    ): Set<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA $schema.table_info($table)").use {
                buildSet {
                    while (it.next()) add(it.getString("name"))
                }
            }
        }

    /** What the app itself does on the launch after an update. */
    private suspend fun <T> afterTheUpdate(
        file: File,
        block: suspend (MatchRepository) -> T,
    ): T = afterTheUpdateIn(file) { block(SqlDelightMatchRepository(it)) }

    /** [afterTheUpdate] for a test that has to read more than the reports. */
    private suspend fun <T> afterTheUpdateIn(
        file: File,
        block: suspend (PsmfDatabase) -> T,
    ): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
        try {
            return block(PsmfDatabase(driver))
        } finally {
            driver.close()
        }
    }

    @Test
    fun aFinishedReportWrittenByTheFirstReleaseIsIntactAfterEveryMigration() =
        runTest {
            // Version 1 is the schema the demo shipped. A phone that has
            // not been updated since is still on it, and has to arrive at
            // the current version through every `.sqm` in order.
            val database = databaseAtVersion(1)
            val report = finishedReport()

            beforeTheUpdate(database) { it.save(report) }

            // Before: the schema the demo shipped, decoy version table and all.
            assertEquals(1, userVersion(database))
            assertContains(tableNames(database), "schema_meta")

            val restored = afterTheUpdate(database) { it.load(report.id) }

            assertEquals(current, userVersion(database), "the migration did not run")
            assertFalse("schema_meta" in tableNames(database), "1.sqm did not take effect")
            assertNotNull(restored, "the report did not survive the migration")
            assertEquals(report, restored)
        }

    @Test
    fun aReportWrittenBeforeTheTeamsTabIsIntactAfterItsMigration() =
        runTest {
            // The step the Týmy tab added, on its own. The test above walks
            // the whole chain and would pass with 2.sqm doing nothing to the
            // rows *and* nothing at all; this one starts on the version
            // immediately before it, so only 2.sqm can be responsible.
            val database = databaseAtVersion(2)
            val report = finishedReport()

            beforeTheUpdate(database) { it.save(report) }

            assertEquals(2, userVersion(database))
            assertFalse("followed_team" in tableNames(database), "2.db already had the new tables")

            val restored = afterTheUpdate(database) { it.load(report.id) }

            assertEquals(current, userVersion(database), "2.sqm did not run")
            assertContains(tableNames(database), "followed_team")
            assertContains(tableNames(database), "jersey_override")
            assertEquals(report, assertNotNull(restored, "the report did not survive 2.sqm"))
        }

    @Test
    fun aReportWrittenBeforeThereWereBreaksIsIntactAfterItsMigration() =
        runTest {
            // The step Phase 1 added, on its own -- starting immediately
            // before it, the same way the Týmy tab's own migration test
            // does, so only 3.sqm can be responsible for the result. A v3
            // report cannot itself have a period break -- the feature did
            // not exist yet -- the same way a v2 report could not already
            // follow a team; what this proves is that the *table* arrives
            // empty and the report already there is untouched.
            val database = databaseAtVersion(3)
            val report = finishedReport()

            beforeTheUpdate(database) { it.save(report) }

            assertEquals(3, userVersion(database))
            assertFalse("period_break_record" in tableNames(database), "3.db already had the new table")

            val restored = afterTheUpdate(database) { it.load(report.id) }

            assertEquals(current, userVersion(database), "3.sqm did not run")
            assertContains(tableNames(database), "period_break_record")
            val nonNullRestored = assertNotNull(restored, "the report did not survive 3.sqm")
            assertEquals(report, nonNullRestored)
            assertEquals(emptyList(), nonNullRestored.periodBreaks)
        }

    @Test
    fun aMatchFromZeroPointOneKeepsEveryEventAndUndoesItsOldOnesAsItAlwaysDid() =
        runTest {
            // The step 0.2.0 adds, on its own: version 4 is what 0.1.0 (1)
            // shipped with. Its rows carry no recording order, and 4.sqm
            // gives them none -- NULL is the truth. They read back as
            // recorded before 0.2.0, and Undo takes them by the timeline,
            // exactly as 0.1.0 did, until something new is recorded.
            val database = databaseAtVersion(4)
            val inProgress =
                finishedReport().copy(
                    status = MatchStatus.IN_PROGRESS,
                    result = null,
                    confirmations = emptyList(),
                )

            beforeTheUpdate(database) { it.save(inProgress) }

            assertEquals(4, userVersion(database))
            assertFalse("sequence" in columns(database, "card_record"), "4.db already had the new column")

            val restored = assertNotNull(afterTheUpdate(database) { it.load(inProgress.id) })

            assertEquals(current, userVersion(database), "4.sqm did not run")
            assertContains(columns(database, "card_record"), "sequence")
            assertEquals(inProgress, restored)
            assertTrue((restored.goals + restored.cardEvents).all { it.sequence == null })
            assertTrue(restored.powerPlays.all { it.sequence == null })

            // The last of the old events by the timeline is the 49´ red.
            val undone = afterTheUpdate(database) { UndoLastEvent(it)(restored) }
            assertEquals(listOf(Minute.Played(20), Minute.HalfTime), undone.cardEvents.map { it.minute })
            assertTrue(undone.powerPlays.isEmpty(), "the red's power play outlived it")

            // Something recorded after the update is newer than all of them.
            val withANewGoal =
                afterTheUpdate(database) { LogGoal(it)(restored, TeamSide.HOME, null, Minute.Played(55)) }
            assertEquals(1, withANewGoal.goals.last().sequence)
            val newestFirst = afterTheUpdate(database) { UndoLastEvent(it)(withANewGoal) }
            assertEquals(restored, newestFirst)
        }

    @Test
    fun aReportWrittenBeforeDatesOfBirthWereRememberedIsIntactAfterItsMigration() =
        runTest {
            // The step Part 2 of prompts/10 adds, on its own: 5.sqm creates
            // the table and nothing else. Nothing before version 6 could have
            // written into it, so it arrives empty.
            val database = databaseAtVersion(5)
            val report = finishedReport()

            beforeTheUpdate(database) { it.save(report) }

            assertEquals(5, userVersion(database))
            assertFalse("remembered_date_of_birth" in tableNames(database), "5.db already had the new table")

            val restored = afterTheUpdate(database) { it.load(report.id) }

            assertEquals(current, userVersion(database), "5.sqm did not run")
            assertContains(tableNames(database), "remembered_date_of_birth")
            assertEquals(report, assertNotNull(restored, "the report did not survive 5.sqm"))
        }

    /**
     * **0.2.1 to 0.3.0 on a referee's phone, with three matches on it.**
     *
     * Version 6 is what 0.2.1 (3) shipped. A lineup knows its captain and
     * goalkeeper from version 7 on, the captain's confirmation under it has
     * a place of its own, and a pitch-added player may have no date of birth.
     * One match is under way, one finished and not signed, one signed; all
     * three have to open afterwards, and each has to be what it was.
     */
    @Test
    fun threeMatchesWrittenByTheLastReleaseAllOpenAndMeanWhatTheyMeant() =
        runTest {
            val database = databaseAtVersion(6)
            val inProgress = oldMatch("m-play", MatchStatus.IN_PROGRESS, listOf(lineupTapHome, lineupTapAwayByADeputy))
            val unsigned = oldMatch("m-done", MatchStatus.FINISHED, listOf(lineupTapHome, signedAwayAtTheRecap))
            val signed =
                oldMatch(
                    "m-signed",
                    MatchStatus.CONFIRMED,
                    listOf(lineupTapHome, signedAwayAtTheRecap, signedByTheReferee),
                )
            val addedAtThePitch = pitchAddedPlayer()

            beforeTheUpdateIn(database) { db ->
                val repository = SqlDelightMatchRepository(db)
                listOf(inProgress, unsigned, signed).forEach { repository.save(it) }
                SqlDelightAddedPlayerRepository(db).add(inProgress.id, addedAtThePitch)
            }

            assertEquals(6, userVersion(database))
            assertFalse("captain_appearance_id" in columns(database, "lineup_record"), "6.db already had the columns")

            val restored =
                afterTheUpdate(database) { repository ->
                    listOf(
                        inProgress,
                        unsigned,
                        signed,
                    ).map { assertNotNull(repository.load(it.id), "${it.id} is gone") }
                }
            val (restoredPlay, restoredDone, restoredSigned) = restored

            assertEquals(current, userVersion(database), "6.sqm did not run")

            // THE LINEUPS: every player is still there and still present, and
            // nobody was given a captain or a goalkeeper they never had.
            listOf(restoredPlay, restoredDone, restoredSigned).forEach { match ->
                listOfNotNull(match.homeLineup, match.awayLineup).forEach { lineup ->
                    assertNull(lineup.captain, "an upgrade invented a captain")
                    assertNull(lineup.goalkeeper, "an upgrade invented a goalkeeper")
                }
            }
            assertEquals(inProgress.homeLineup?.appearances, restoredPlay.homeLineup?.appearances)
            assertEquals(inProgress.awayLineup?.appearances, restoredPlay.awayLineup?.appearances)
            val entry = presenceOf(restoredPlay)
            assertEquals(
                inProgress.homeLineup
                    ?.appearances
                    ?.map { it.playerId }
                    ?.toSet(),
                entry.home.present
                    .map { it.player.id }
                    .toSet(),
                "marking who came silently removed somebody from the lineup",
            )

            // IN PROGRESS, the captains tapped before the whistle: those taps
            // were the lineup's, so they moved to it, with their times, their
            // names and the deputy -- and the recap asks both captains again.
            assertEquals(
                LineupConfirmation(Instant.parse("2026-08-31T18:50:00Z"), PersonName.of("Novak")),
                restoredPlay.homeLineup?.confirmation,
            )
            assertEquals(
                LineupConfirmation(Instant.parse("2026-08-31T18:52:00Z"), PersonName.of("Lepis"), asDeputy = true),
                restoredPlay.awayLineup?.confirmation,
            )
            assertEquals(emptyList(), restoredPlay.confirmations)

            // FINISHED, not signed: the home captain's early tap moved, and
            // the away captain's, which came after the whistle at the recap,
            // is exactly where it was.
            assertNotNull(restoredDone.homeLineup?.confirmation)
            assertNull(restoredDone.awayLineup?.confirmation)
            assertEquals(listOf(signedAwayAtTheRecap), restoredDone.confirmations)

            // CONFIRMED: nothing moved. It was signed under the old rules and
            // its export says what it said; taking a signature away from a
            // finished report would be rewriting it. Equal in every field.
            assertEquals(signed, restoredSigned)
            assertEquals(signed.reportProblems(), restoredSigned.reportProblems())
            assertEquals(MatchStatus.CONFIRMED, restoredSigned.status)

            // THE WHISTLES: 0.2.1 kept no place in the recording order for
            // them and no instant for the final whistle, and none is invented.
            listOf(restoredPlay, restoredDone, restoredSigned).forEach { match ->
                assertNull(match.kickoffSequence, "an upgrade numbered a whistle it never saw pressed")
                assertNull(match.finishedAt)
                assertNull(match.finishedSequence)
            }
            // A whistle pressed under 0.2.1 can still be taken back -- after
            // everything recorded since -- except on the signed report, whose
            // clock is not changed under a signature.
            assertEquals(UndoStep.TakesBackWhistle(Whistle.KICKOFF, 1), restoredPlay.nextUndoStep())
            assertEquals(UndoStep.TakesBackWhistle(Whistle.FINAL, 1), restoredDone.nextUndoStep())
            assertNull(restoredSigned.nextUndoStep())

            // The player who was added at the pitch, with the date of birth
            // the old table insisted on, is still there with it.
            val players =
                afterTheUpdateIn(database) { SqlDelightAddedPlayerRepository(it).forMatch(inProgress.id) }
            assertEquals(listOf(addedAtThePitch), players)
        }

    @Test
    fun aReportWrittenBeforeCaptainsIsExportedWithOnlyNewFieldsEmpty() =
        runTest {
            val database = databaseAtVersion(6)
            val signed =
                oldMatch(
                    "m-signed",
                    MatchStatus.CONFIRMED,
                    listOf(lineupTapHome, signedAwayAtTheRecap, signedByTheReferee),
                )
            beforeTheUpdate(database) { it.save(signed) }

            val restored = assertNotNull(afterTheUpdate(database) { it.load(signed.id) })

            val build = BuildZouReport(CompleteReport.league(), NoAddedPlayers())
            val before = assertNotNull(build(signed))
            val after = assertNotNull(build(restored))
            assertEquals(before, after)
            // The new fields exist, and are empty: no captain, no goalkeeper,
            // no confirmation under either lineup.
            after.lineups.forEach { lineup ->
                assertNull(lineup.confirmation)
                assertTrue(lineup.rows.none { it.captain || it.goalkeeper })
            }
            assertContains(ZouText.format(after), "${ZouLabels.Lineup.CAPTAIN_BEFORE_KICKOFF}: ${ZouWords.NOT_GIVEN}")
        }

    private val lineupTapHome =
        Confirmation(ConfirmingParty.HOME_CAPTAIN, Instant.parse("2026-08-31T18:50:00Z"), PersonName.of("Novak"), false)

    private val lineupTapAwayByADeputy =
        Confirmation(ConfirmingParty.AWAY_CAPTAIN, Instant.parse("2026-08-31T18:52:00Z"), PersonName.of("Lepis"), true)

    private val signedAwayAtTheRecap =
        Confirmation(ConfirmingParty.AWAY_CAPTAIN, Instant.parse("2026-08-31T20:55:00Z"), PersonName.of("Lepis"), false)

    private val signedByTheReferee =
        Confirmation(ConfirmingParty.REFEREE, Instant.parse("2026-08-31T21:00:00Z"), PersonName.of("Jiri Vlk"), false)

    /**
     * A match as 0.2.1 wrote it: the shared lineups, kickoff at 19:00, no
     * goals or cards (their ids would have to be unique per match as well),
     * and the confirmations it was given. Appearance ids are made unique,
     * because `appearance_record` is keyed on the id alone.
     */
    private fun oldMatch(
        id: String,
        status: MatchStatus,
        confirmations: List<Confirmation>,
    ): Match {
        fun Lineup.unique() = copy(appearances = appearances.map { it.copy(id = AppearanceId("$id-${it.id.value}")) })
        return Match(
            id = MatchId(id),
            fixtureId = Fixtures.fixtureId,
            groupId = Fixtures.groupId,
            status = status,
            officials = Fixtures.officials,
            homeLineup = Fixtures.homeLineup.unique(),
            awayLineup = Fixtures.awayLineup.unique(),
            kickoffAt = Instant.parse("2026-08-31T19:00:00Z"),
            confirmations = confirmations,
        )
    }

    /** Who 0.2.1 could write: a name and the date of birth, which its table insisted on. */
    private fun pitchAddedPlayer(): Player =
        Player.addedAtThePitch(
            id = PlayerId("added-1"),
            ref = "pitch-added-1",
            teamId = Fixtures.homeTeamId,
            name = PlayerName(PersonName.of("Hlok"), PersonName.of("Petr")),
            dateOfBirth = LocalDate(1999, 1, 21),
        )

    /** What the lineup screen would build from the match as it now reads. */
    private suspend fun presenceOf(match: Match): LineupEntry =
        assertNotNull(
            BuildLineupEntry(
                CompleteReport.league(),
                NoAddedPlayers(),
                FakeRememberedDateOfBirthRepository(),
                FakeRememberedCardNumberRepository(),
            ) { "id" }(match),
        )

    @Test
    fun theSnapshottedColumnsSurviveAndAreNotRederived() =
        runTest {
            // These are stored rather than derived precisely so that a later
            // change to reference data cannot rewrite an old report. A
            // migration is a later change to the database itself, which puts
            // it in the same class of risk.
            val database = databaseAtVersion(1)
            val report = finishedReport()
            beforeTheUpdate(database) { it.save(report) }

            val restored = assertNotNull(afterTheUpdate(database) { it.load(report.id) })

            assertEquals(
                report.homeLineup?.appearances?.map { it.reportedIdentification },
                restored.homeLineup?.appearances?.map { it.reportedIdentification },
            )
            assertEquals(report.homeLineup?.kitLabel, restored.homeLineup?.kitLabel)
            assertEquals(report.awayLineup?.kitLabel, restored.awayLineup?.kitLabel)
        }

    @Test
    fun migratingHappensOnceAndTheLaunchAfterThatChangesNothing() =
        runTest {
            val database = databaseAtVersion(1)
            val report = finishedReport()
            beforeTheUpdate(database) { it.save(report) }

            val firstLaunch = afterTheUpdate(database) { it.load(report.id) }
            val secondLaunch = afterTheUpdate(database) { it.load(report.id) }

            assertEquals(firstLaunch, secondLaunch)
            assertEquals(current, userVersion(database))
        }

    @Test
    fun aFreshInstallGetsTheCurrentSchemaWithoutRunningAnyMigration() =
        runTest {
            // The other side of the same coin: a phone that has never had
            // the app must not be handed version 1 and then migrated. It
            // gets the .sq files, stamped at the current version.
            val database = File.createTempFile("psmf-new-", ".db").also { it.delete() }
            temporaryFiles += database

            afterTheUpdate(database) { it.summaries() }

            assertEquals(current, userVersion(database))
            assertFalse("schema_meta" in tableNames(database))
            assertContains(tableNames(database), "match_record")
            // Present from the CREATE statements, not from a migration.
            assertContains(tableNames(database), "followed_team")
            assertContains(tableNames(database), "jersey_override")
            assertContains(tableNames(database), "remembered_date_of_birth")
            assertContains(tableNames(database), "remembered_card_number")
            assertContains(columns(database, "match_record"), "kickoff_sequence")
            assertContains(columns(database, "match_record"), "finished_at")
            assertContains(columns(database, "period_break_record"), "next_started_sequence")
        }

    /**
     * The report every formatter test is checked against, plus the power
     * play its own dismissal implies -- otherwise the one table holding a
     * running timer is the one table the migration is not tested on.
     */
    private fun finishedReport(): Match =
        CompleteReport.match.copy(
            powerPlays =
                listOf(
                    PowerPlay(
                        shortHandedSide = TeamSide.AWAY,
                        startedAt = Instant.parse("2026-08-31T19:49:00Z"),
                        dismissedAtMinute = Minute.Played(49),
                    ),
                ),
        )

    // Read SQLite directly rather than through SQLDelight: the point is to
    // see what is on disk, not what the generated code believes is there.

    private fun connect(file: File): Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    private fun execute(
        file: File,
        sql: String,
    ) = connect(file).use { connection -> connection.createStatement().use { it.execute(sql) } }

    private fun userVersion(file: File): Int =
        connect(file).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use {
                    it.next()
                    it.getInt(1)
                }
            }
        }

    private fun columns(
        file: File,
        table: String,
    ): Set<String> = connect(file).use { columnNames(it, "main", table) }

    private fun tableNames(file: File): Set<String> =
        connect(file).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use {
                    buildSet {
                        while (it.next()) add(it.getString(1))
                    }
                }
            }
        }

    private companion object {
        const val SCHEMA_DIRECTORY_PROPERTY = "psmf.schemaDirectory"
    }
}
