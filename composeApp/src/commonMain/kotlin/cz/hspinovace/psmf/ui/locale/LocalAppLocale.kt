package cz.hspinovace.psmf.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import cz.hspinovace.psmf.data.settings.AppLanguage

/**
 * The app's language, pushed down into the platform's own locale.
 *
 * **There is no common Compose Multiplatform API for this.** JetBrains
 * document an `expect`/`actual` CompositionLocal as the way to do it, and
 * this is that pattern rather than an invention:
 * <https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html>
 *
 * Android goes through `Configuration.setLocale`, iOS through
 * `NSUserDefaults` `AppleLanguages`, and the JVM — which exists here only
 * to host the UI tests — through `Locale.setDefault`.
 *
 * **Not `AppCompatDelegate.setApplicationLocales`.** That recreates the
 * activity, and the app must not restart mid-match.
 *
 * One deliberate difference from the documented sample: it takes a language
 * tag and never null. The sample uses null for "follow the device", but
 * "follow the device" is resolved to one of the three languages before it
 * reaches here, so the platform never has to remember and restore a
 * default it captured at an arbitrary moment.
 */
internal expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String): ProvidedValue<*>
}

/**
 * Runs [content] in [language].
 *
 * The `key` is not decoration. Compose resources resolve a string the first
 * time it is composed, so a new language only reaches the strings already
 * on screen if the subtree is rebuilt — which is what makes the picker take
 * effect with no restart. Everything that must survive it does: the report
 * is in the database, the back stacks are a Koin `single`, and the
 * ViewModels are in the ViewModelStore.
 */
@Composable
fun AppEnvironment(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppLocale provides language.tag) {
        key(language) { content() }
    }
}
