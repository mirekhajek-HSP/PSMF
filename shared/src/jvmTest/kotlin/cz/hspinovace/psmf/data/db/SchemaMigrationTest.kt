package cz.hspinovace.psmf.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.CompleteReport
import kotlinx.coroutines.test.runTest
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
     * Writes to a database without touching its version: the driver is
     * handed no schema, so it neither creates nor migrates. It is the only
     * way to produce rows that genuinely predate the migration.
     */
    private suspend fun beforeTheUpdate(
        file: File,
        block: suspend (MatchRepository) -> Unit,
    ) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            block(SqlDelightMatchRepository(PsmfDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    /** What the app itself does on the launch after an update. */
    private suspend fun <T> afterTheUpdate(
        file: File,
        block: suspend (MatchRepository) -> T,
    ): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
        try {
            return block(SqlDelightMatchRepository(PsmfDatabase(driver)))
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
