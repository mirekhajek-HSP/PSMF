package cz.hspinovace.psmf.ui.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cz.hspinovace.psmf.data.settings.SettingsRepository
import org.koin.compose.koinInject

/**
 * iOS needs no Activity-like handle, unlike Android: [IosReportSaver] finds
 * the view controller to present from when it needs one. It is still built
 * here rather than in Koin so both platforms share one call site.
 */
@Composable
actual fun rememberReportSaver(): ReportSaver {
    val settings: SettingsRepository = koinInject()
    return remember(settings) { IosReportSaver(settings) }
}
