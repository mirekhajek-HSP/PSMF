package cz.hspinovace.psmf.data.db

import cz.hspinovace.psmf.db.PsmfDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the SQLDelight wiring end to end on the fast target: the plugin
 * generated a database, the driver opens it, the schema was created and a
 * round trip works.
 *
 * The domain schema arrives in Phase 2; this only guards the plumbing.
 */
class DatabaseSmokeTest {

    @Test
    fun schemaIsCreatedAndSurvivesARoundTrip() {
        val driver = DatabaseDriverFactory().create()
        val database = PsmfDatabase(driver)

        database.schemaMetaQueries.upsertVersion(
            version = 1,
            applied_at = "2026-08-29T00:00:00Z",
        )

        assertEquals(1L, database.schemaMetaQueries.selectVersion().executeAsOne())

        driver.close()
    }
}
