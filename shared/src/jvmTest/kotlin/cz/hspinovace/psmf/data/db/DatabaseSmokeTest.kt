package cz.hspinovace.psmf.data.db

import cz.hspinovace.psmf.db.PsmfDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the SQLDelight wiring end to end on the fast target: the plugin
 * generated a database, the driver opens it, the schema was created and a
 * round trip works.
 *
 * It used to round-trip through `schema_meta`, a version number written by
 * hand. That table is gone — SQLite's own `user_version` is what SQLDelight
 * reads, and two versioning mechanisms in one database is one too many. See
 * `SchemaMigrationTest` for the version behaviour and `1.sqm` for the
 * removal.
 */
class DatabaseSmokeTest {
    @Test
    fun schemaIsCreatedAndSurvivesARoundTrip() {
        val driver = DatabaseDriverFactory().create()
        val database = PsmfDatabase(driver)

        // `value_`, not `value`: SQLDelight escapes a column whose name is a
        // Kotlin soft keyword rather than failing to generate.
        database.matchRecordQueries.upsertPreference(key = "language", value_ = "cs")

        assertEquals(
            "cs",
            database.matchRecordQueries.selectPreference("language").executeAsOne(),
        )

        driver.close()
    }

    @Test
    fun theSchemaVersionIsTheOneTheMigrationsAddUpTo() {
        // Not a tautology: the version is derived from the number of .sqm
        // files, so this fails the moment a migration is added without the
        // recorded .db file and the test that go with it.
        assertEquals(2L, PsmfDatabase.Schema.version)
    }
}
