package cz.hspinovace.psmf.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cz.hspinovace.psmf.db.PsmfDatabase

/**
 * JVM driver. Exists so the shared module has a fast, headless test target;
 * the app itself never runs on the JVM.
 *
 * [url] defaults to an in-memory database so tests do not touch the disk.
 */
actual class DatabaseDriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) {
    actual fun create(): SqlDriver =
        JdbcSqliteDriver(url).also { driver ->
            PsmfDatabase.Schema.create(driver)
        }
}
