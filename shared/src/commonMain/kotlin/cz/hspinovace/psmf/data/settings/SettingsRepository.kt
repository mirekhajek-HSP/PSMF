package cz.hspinovace.psmf.data.settings

import cz.hspinovace.psmf.db.PsmfDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Light, dark, or whatever the device is set to. */
enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The two things the referee may change.
 *
 * Everything else golblok exposes — half length, periods, players per
 * side, assists, substitutions — is **league data, not a setting**. A
 * referee changing the half length is a defect, so those are shown as
 * information on the settings screen and nowhere else.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
)

interface SettingsRepository {
    suspend fun load(): AppSettings

    suspend fun setTheme(theme: ThemeChoice)
}

/**
 * Stored in the app's own database rather than in a platform preference
 * store, so there is one persistence mechanism to reason about and no
 * per-platform code for two values.
 */
class SqlDelightSettingsRepository(
    private val database: PsmfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsRepository {
    private val queries get() = database.matchRecordQueries

    override suspend fun load(): AppSettings =
        withContext(dispatcher) {
            val stored = queries.selectPreference(THEME).executeAsOneOrNull()
            AppSettings(
                theme =
                    ThemeChoice.entries.firstOrNull { it.name == stored } ?: ThemeChoice.SYSTEM,
            )
        }

    override suspend fun setTheme(theme: ThemeChoice): Unit =
        withContext(dispatcher) {
            queries.upsertPreference(THEME, theme.name)
        }

    private companion object {
        const val THEME = "theme"
    }
}
