package cz.hspinovace.psmf.ui.export

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cz.hspinovace.psmf.data.settings.SettingsRepository
import org.koin.compose.koinInject

/**
 * The composable's own `Context` is the hosting Activity on Android --
 * unlike the Application context Koin injects everywhere else in this app
 * -- because [AndroidReportSaver] needs `activityResultRegistry`, which
 * only an Activity has. [SettingsRepository] is still ordinary Koin,
 * fetched with [koinInject] rather than threaded through as a parameter,
 * because it is the Application-scoped kind of dependency this file is the
 * exception for, not the rule.
 */
@Composable
actual fun rememberReportSaver(): ReportSaver {
    val activity = LocalContext.current as ComponentActivity
    val settings: SettingsRepository = koinInject()
    return remember(activity, settings) { AndroidReportSaver(activity, settings) }
}
