package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.ui.export.IosReportSender
import cz.hspinovace.psmf.ui.export.ReportSender
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory() }
        // Mail composer when Apple Mail has an account, otherwise the share
        // sheet. See IosReportSender.
        single<ReportSender> { IosReportSender() }
    }
