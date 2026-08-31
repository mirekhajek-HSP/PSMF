package cz.hspinovace.psmf.ui.export

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The composable's own `Context` is the hosting Activity on Android --
 * unlike the Application context Koin injects everywhere else in this app
 * -- because [AndroidReportSaver] needs `activityResultRegistry`, which
 * only an Activity has.
 */
@Composable
actual fun rememberReportSaver(): ReportSaver {
    val activity = LocalContext.current as ComponentActivity
    return remember(activity) { AndroidReportSaver(activity) }
}
