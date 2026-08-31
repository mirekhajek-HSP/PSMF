package cz.hspinovace.psmf.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mapping a device locale onto the three languages the app has.
 *
 * On first run the device decides, so this runs before the referee has ever
 * touched the picker — and it has to cope with tags the app has no
 * translation for.
 */
class AppLanguageTest {
    @Test
    fun theThreeLanguagesAreRecognisedByTheirTags() {
        assertEquals(AppLanguage.CZECH, AppLanguage.forTag("cs"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forTag("en"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.forTag("uk"))
    }

    @Test
    fun aRegionOrScriptOnTheTagDoesNotHideTheLanguage() {
        // Android hands over cs-CZ, en-GB, uk-UA; the JVM writes uk_UA.
        assertEquals(AppLanguage.CZECH, AppLanguage.forTag("cs-CZ"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forTag("en-GB"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.forTag("uk_UA"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forTag("EN"))
    }

    @Test
    fun aLanguageTheAppDoesNotHaveIsNotGuessedAt() {
        // Null rather than a near miss: the caller falls back to Czech, the
        // league's language, rather than to whatever sorts first.
        assertNull(AppLanguage.forTag("de"))
        assertNull(AppLanguage.forTag("sk"))
        assertNull(AppLanguage.forTag(""))
    }

    @Test
    fun everyLanguageNamesItselfInItsOwnScript() {
        // What a picker has to show: a Ukrainian captain finding Ukrainian
        // in a list they cannot otherwise read.
        assertEquals("Čeština", AppLanguage.CZECH.autonym)
        assertEquals("English", AppLanguage.ENGLISH.autonym)
        assertEquals("Українська", AppLanguage.UKRAINIAN.autonym)
    }
}
