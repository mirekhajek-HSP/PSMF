package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.db.PsmfDatabase
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that are the same on every platform.
 *
 * Koin rather than Hilt: Hilt is Android-only and cannot compile into
 * shared code. See docs/TECH_STACK.md section 2.
 */
val appModule: Module = module {
    // Lazy, so nothing touches the disk until something actually asks for
    // the database. The domain schema and its repositories arrive in
    // Phase 2; this binding proves the graph resolves.
    single { PsmfDatabase(get<DatabaseDriverFactory>().create()) }
}

/**
 * Bindings that genuinely differ per platform. The Android driver needs a
 * Context and the iOS one does not, which is the whole reason this is not
 * a single common module.
 */
expect fun platformModule(): Module

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule())
    }
