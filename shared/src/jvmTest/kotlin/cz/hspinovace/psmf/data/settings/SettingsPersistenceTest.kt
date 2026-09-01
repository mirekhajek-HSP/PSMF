package cz.hspinovace.psmf.data.settings

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.db.PsmfDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The picker has to outlive the app, or every launch asks the referee again.
 *
 * Opens a real database file, closes the driver — as far as SQLite is
 * concerned the app has died — and opens a fresh one against the same bytes,
 * the same way the crash-recovery tests do.
 */
class SettingsPersistenceTest {
    private val databaseFile: File = File.createTempFile("psmf-settings-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        databaseFile.delete()
    }

    private suspend fun <T> session(block: suspend (SettingsRepository) -> T): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${databaseFile.absolutePath}").create()
        try {
            return block(SqlDelightSettingsRepository(PsmfDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    @Test
    fun nothingIsStoredUntilTheRefereePicks() =
        runTest {
            // Null, not Czech. On first run the device decides; storing a
            // resolved default would freeze whatever language the phone
            // happened to be in the first time the app opened.
            assertNull(session { it.load() }.language)
        }

    @Test
    fun aPickedLanguageSurvivesTheAppBeingKilled() =
        runTest {
            session { it.setLanguage(AppLanguage.UKRAINIAN) }
            assertEquals(AppLanguage.UKRAINIAN, session { it.load() }.language)
        }

    @Test
    fun theLanguageAndTheThemeDoNotOverwriteEachOther() =
        runTest {
            // Both live in preference_record, keyed by name. A shared key
            // would make the last one written the only one stored.
            session {
                it.setLanguage(AppLanguage.ENGLISH)
                it.setTheme(ThemeChoice.DARK)
            }

            val stored = session { it.load() }
            assertEquals(AppLanguage.ENGLISH, stored.language)
            assertEquals(ThemeChoice.DARK, stored.theme)
        }

    @Test
    fun pickingAgainReplacesRatherThanAccumulates() =
        runTest {
            session {
                it.setLanguage(AppLanguage.ENGLISH)
                it.setLanguage(AppLanguage.CZECH)
            }
            assertEquals(AppLanguage.CZECH, session { it.load() }.language)
        }

    @Test
    fun nothingIsStoredForTheExportFolderUntilOneIsChosen() =
        runTest {
            // Null, not a folder nobody picked: AndroidReportSaver reads
            // this to decide whether to ask at all.
            assertNull(session { it.load() }.exportFolderUri)
        }

    @Test
    fun aChosenExportFolderSurvivesTheAppBeingKilled() =
        runTest {
            session { it.setExportFolderUri("content://tree/primary%3APSMF") }
            assertEquals("content://tree/primary%3APSMF", session { it.load() }.exportFolderUri)
        }

    @Test
    fun changingTheExportFolderReplacesRatherThanAccumulates() =
        runTest {
            session {
                it.setExportFolderUri("content://tree/primary%3AFirst")
                it.setExportFolderUri("content://tree/primary%3ASecond")
            }
            assertEquals("content://tree/primary%3ASecond", session { it.load() }.exportFolderUri)
        }
}
