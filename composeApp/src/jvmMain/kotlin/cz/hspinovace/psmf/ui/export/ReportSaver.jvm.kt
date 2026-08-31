package cz.hspinovace.psmf.ui.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** The JVM target hosts Compose UI tests, not a document picker. */
@Composable
actual fun rememberReportSaver(): ReportSaver = remember { UnavailableReportSaver() }
