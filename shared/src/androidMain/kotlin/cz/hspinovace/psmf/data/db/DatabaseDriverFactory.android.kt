package cz.hspinovace.psmf.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import cz.hspinovace.psmf.db.PsmfDatabase

actual class DatabaseDriverFactory(
    private val context: Context,
) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = PsmfDatabase.Schema,
            context = context,
            name = DATABASE_FILE_NAME,
        )
}
