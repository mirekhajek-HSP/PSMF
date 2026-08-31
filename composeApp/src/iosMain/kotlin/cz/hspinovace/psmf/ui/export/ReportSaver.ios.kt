package cz.hspinovace.psmf.ui.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Saving would use `UIDocumentPickerViewController` for a folder, the same
 * way [ReportSender]'s iOS half would use `UIActivityViewController` --
 * both need a Mac to build and test. **Never compiled**: see
 * `LocalAppLocale.ios.kt` for why that is stated rather than assumed.
 */
@Composable
actual fun rememberReportSaver(): ReportSaver = remember { UnavailableReportSaver() }
