package cz.hspinovace.psmf.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import cz.hspinovace.psmf.db.PsmfDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(
            schema = PsmfDatabase.Schema,
            name = DATABASE_FILE_NAME,
        )
}
