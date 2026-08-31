package cz.hspinovace.psmf.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * iOS: `NSUserDefaults` `AppleLanguages`, which is the documented hook and
 * the only one that does not need the app relaunched.
 *
 * **Never compiled.** iOS targets cannot be built on Linux and this project
 * has no Mac in the loop yet, so this is the one part of the picker that has
 * not run. It is the JetBrains sample with the null branch removed.
 */
internal actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"

    private val Local =
        staticCompositionLocalOf {
            NSLocale.currentLocale.languageCode
        }

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String): ProvidedValue<*> {
        NSUserDefaults.standardUserDefaults.setObject(listOf(value), LANGUAGES_KEY)
        return Local provides value
    }
}
