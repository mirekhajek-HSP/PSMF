package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory() }
    }
