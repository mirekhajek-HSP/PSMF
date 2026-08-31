package cz.hspinovace.psmf.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * The JVM target hosts the UI tests and is not shipped, so the whole of the
 * platform's part here is the default locale that Compose resources read.
 */
internal actual object LocalAppLocale {
    private val Local = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String): ProvidedValue<*> {
        Locale.setDefault(Locale.forLanguageTag(value))
        return Local provides value
    }
}
