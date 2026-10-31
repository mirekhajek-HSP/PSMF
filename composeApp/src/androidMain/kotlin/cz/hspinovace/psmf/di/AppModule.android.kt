package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.ui.export.AndroidReportClipboard
import cz.hspinovace.psmf.ui.export.AndroidReportSender
import cz.hspinovace.psmf.ui.export.ReportClipboard
import cz.hspinovace.psmf.ui.export.ReportSender
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory(androidContext()) }
        // Writes the three files and opens a mail draft with them attached.
        single<ReportSender> { AndroidReportSender(androidContext()) }
        // The report's text on the clipboard, marked sensitive from Android 13.
        single<ReportClipboard> { AndroidReportClipboard(androidContext()) }
    }
