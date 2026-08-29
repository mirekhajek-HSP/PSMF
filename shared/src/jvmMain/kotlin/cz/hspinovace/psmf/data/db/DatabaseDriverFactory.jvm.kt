package cz.hspinovace.psmf.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cz.hspinovace.psmf.db.PsmfDatabase
import java.util.Properties

/**
 * JVM driver. Exists so the shared module has a fast, headless test
 * target; the app itself never runs on the JVM.
 *
 * [url] defaults to an in-memory database so tests do not touch the disk.
 * Passing a `jdbc:sqlite:/path` URL gives a real file, which is what the
 * crash-recovery test needs: it has to close one driver and open another
 * against the same bytes.
 */
actual class DatabaseDriverFactory(
    private val url: String = JdbcSqliteDriver.IN_MEMORY,
) {
    /**
     * The schema is handed to the driver rather than created by hand, so
     * that opening an *existing* database is a no-op instead of failing on
     * tables that are already there.
     */
    actual fun create(): SqlDriver = JdbcSqliteDriver(url, Properties(), PsmfDatabase.Schema)
}
