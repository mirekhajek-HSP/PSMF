package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.ui.export.ReportSender
import cz.hspinovace.psmf.ui.export.UnavailableReportSender
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory() }
        // Sending needs UIActivityViewController, which needs a Mac to
        // build and test. Declared unavailable rather than half-written.
        single<ReportSender> { UnavailableReportSender() }
    }
