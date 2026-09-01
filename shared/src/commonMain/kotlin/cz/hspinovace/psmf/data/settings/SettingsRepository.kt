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
 * The language the app is read in.
 *
 * **Picked in the app, not inherited from the device**, and the reason is
 * not preference: the phone is read by more than one person. The captain
 * confirms the lineup on the referee's phone and both captains confirm the
 * recap, so a device-level language serves the phone's owner only — and a
 * Ukrainian captain confirming on a Czech referee's phone is precisely the
 * case three languages exist for.
 *
 * **Not the report's language.** The ZoU is always Czech whatever this
 * says; see `ZouLabels`, which is fixed strings rather than resources for
 * exactly that reason.
 *
 * [autonym] is each language's own name for itself, which is what a
 * language picker has to show: a Ukrainian captain has to find Ukrainian in
 * a list they cannot otherwise read. It is the same in every language, so
 * it is not a translated resource.
 */
enum class AppLanguage(
    val tag: String,
    val autonym: String,
) {
    CZECH("cs", "Čeština"),
    ENGLISH("en", "English"),
    UKRAINIAN("uk", "Українська"),
    ;

    companion object {
        /** The app's language for a device locale, where it has one. */
        fun forTag(tag: String): AppLanguage? {
            val language = tag.substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it.tag == language }
        }
    }
}

/**
 * The things the referee may change.
 *
 * Everything else golblok exposes — half length, periods, players per
 * side, assists, substitutions — is **league data, not a setting**. A
 * referee changing the half length is a defect, so those are shown as
 * information on the settings screen and nowhere else.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Null until the referee picks one, which is not the same as Czech: on
     * first run the device decides, and only a pick overrides it. Storing a
     * resolved default would silently freeze whatever language the phone
     * happened to be in the first time the app opened.
     */
    val language: AppLanguage? = null,
    /**
     * Where saved reports go, as an opaque platform URI string -- a SAF
     * tree URI on Android, meaningless on a platform with no such saver.
     * Null until the referee has been asked once. See `AndroidReportSaver`.
     */
    val exportFolderUri: String? = null,
)

interface SettingsRepository {
    suspend fun load(): AppSettings

    suspend fun setTheme(theme: ThemeChoice)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setExportFolderUri(uri: String)
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
            AppSettings(
                theme =
                    ThemeChoice.entries.firstOrNull {
                        it.name == queries.selectPreference(THEME).executeAsOneOrNull()
                    } ?: ThemeChoice.SYSTEM,
                language =
                    AppLanguage.entries.firstOrNull {
                        it.name == queries.selectPreference(LANGUAGE).executeAsOneOrNull()
                    },
                exportFolderUri = queries.selectPreference(EXPORT_FOLDER_URI).executeAsOneOrNull(),
            )
        }

    override suspend fun setTheme(theme: ThemeChoice): Unit =
        withContext(dispatcher) {
            queries.upsertPreference(THEME, theme.name)
        }

    override suspend fun setLanguage(language: AppLanguage): Unit =
        withContext(dispatcher) {
            queries.upsertPreference(LANGUAGE, language.name)
        }

    override suspend fun setExportFolderUri(uri: String): Unit =
        withContext(dispatcher) {
            queries.upsertPreference(EXPORT_FOLDER_URI, uri)
        }

    private companion object {
        const val THEME = "theme"
        const val LANGUAGE = "language"
        const val EXPORT_FOLDER_URI = "export_folder_uri"
    }
}
