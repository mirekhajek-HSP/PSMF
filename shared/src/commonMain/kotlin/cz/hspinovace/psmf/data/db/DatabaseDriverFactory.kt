package cz.hspinovace.psmf.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform's SQLite driver.
 *
 * The actual implementations differ in what they need to be constructed
 * with -- Android needs a [android.content.Context], iOS and the JVM do
 * not -- which is exactly why this is an `expect class` rather than an
 * interface with a common factory. No platform type reaches `commonMain`.
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

/** Filename used by every platform that persists to disk. */
const val DATABASE_FILE_NAME: String = "psmf.db"
