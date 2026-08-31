package cz.hspinovace.psmf.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Android: the activity's own `Configuration`, plus the JVM default that
 * Compose resources read.
 *
 * `updateConfiguration` is deprecated and is what the JetBrains sample
 * uses; the modern replacements all recreate the activity, which is exactly
 * what must not happen while a match is being recorded.
 */
internal actual object LocalAppLocale {
    private val Local = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String): ProvidedValue<*> {
        val locale = Locale.forLanguageTag(value)
        Locale.setDefault(locale)

        val configuration = LocalConfiguration.current
        configuration.setLocale(locale)
        val resources = LocalContext.current.resources

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        return Local provides value
    }
}
