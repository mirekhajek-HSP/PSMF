package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.ui.export.ReportSender
import cz.hspinovace.psmf.ui.export.UnavailableReportSender
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The JVM target exists so Compose UI tests have a host to run on, not so
 * the app can be shipped for desktop. The driver therefore defaults to an
 * in-memory database: a test that leaves a file behind is a test that
 * passes for the wrong reason the second time it runs.
 */
actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory() }
        single<ReportSender> { UnavailableReportSender() }
    }
