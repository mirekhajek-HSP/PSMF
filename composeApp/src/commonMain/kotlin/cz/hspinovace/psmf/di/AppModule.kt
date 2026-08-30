package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.SeedLeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.data.seed.ComposeResourceSeedFileReader
import cz.hspinovace.psmf.data.seed.SeedFileReader
import cz.hspinovace.psmf.data.seed.SeedLeagueCatalog
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.ui.fixtures.FixturesViewModel
import cz.hspinovace.psmf.ui.header.MatchHeaderViewModel
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.SaveMatchHeader
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Bindings that are the same on every platform.
 *
 * Koin rather than Hilt: Hilt is Android-only and cannot compile into
 * shared code. See docs/TECH_STACK.md section 2.
 */
@OptIn(ExperimentalUuidApi::class)
val appModule: Module =
    module {
        // Lazy, so nothing touches the disk until something actually asks
        // for the database.
        single { PsmfDatabase(get<DatabaseDriverFactory>().create()) }
        single<MatchRepository> { SqlDelightMatchRepository(get()) }

        // Seed data. The reader lives in this module because Compose
        // resources are generated here and `shared` cannot see them.
        single<SeedFileReader> { ComposeResourceSeedFileReader() }
        single { SeedLeagueCatalog(get()) }
        single<LeagueRepository> { SeedLeagueRepository(get()) }

        // Ids for things the app creates. Behind an interface so a test can
        // hand out predictable ones; a UUID here for the same reason seed
        // entities carry them -- an id that encodes anything is an id that
        // can become a lie.
        single<NewId> { NewId { Uuid.random().toString() } }

        // The back stack. A single, so it survives a configuration change;
        // deliberately not persisted, because after a process death the
        // fixture list is where the referee should land anyway.
        single { AppNavigator() }

        factoryOfUseCases()

        viewModel { FixturesViewModel(get(), get()) }
        viewModel { (matchId: MatchId) -> MatchHeaderViewModel(matchId, get(), get(), get()) }
    }

/**
 * Use cases are factories, not singles: they hold no state, and a new one
 * per injection costs an allocation.
 */
private fun Module.factoryOfUseCases() {
    factory { ListFixtures(get(), get()) }
    factory { StartOrResumeMatch(get(), get(), get()) }
    factory { SaveMatchHeader(get()) }
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
