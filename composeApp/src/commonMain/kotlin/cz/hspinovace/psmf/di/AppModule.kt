package cz.hspinovace.psmf.di

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.league.JerseyOverridingLeagueRepository
import cz.hspinovace.psmf.data.league.LeagueRepository
import cz.hspinovace.psmf.data.league.SeedLeagueRepository
import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.data.player.AddedPlayerRepository
import cz.hspinovace.psmf.data.player.SqlDelightAddedPlayerRepository
import cz.hspinovace.psmf.data.seed.ComposeResourceSeedFileReader
import cz.hspinovace.psmf.data.seed.SeedFileReader
import cz.hspinovace.psmf.data.seed.SeedLeagueCatalog
import cz.hspinovace.psmf.data.settings.SettingsRepository
import cz.hspinovace.psmf.data.settings.SqlDelightSettingsRepository
import cz.hspinovace.psmf.data.team.FollowedTeamRepository
import cz.hspinovace.psmf.data.team.JerseyOverrideRepository
import cz.hspinovace.psmf.data.team.SqlDelightFollowedTeamRepository
import cz.hspinovace.psmf.data.team.SqlDelightJerseyOverrideRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.export.BuildZouReport
import cz.hspinovace.psmf.export.ExportZou
import cz.hspinovace.psmf.ui.assessment.AssessmentViewModel
import cz.hspinovace.psmf.ui.console.ConsoleViewModel
import cz.hspinovace.psmf.ui.export.ExportViewModel
import cz.hspinovace.psmf.ui.fixtures.FixturesViewModel
import cz.hspinovace.psmf.ui.header.MatchHeaderViewModel
import cz.hspinovace.psmf.ui.lineup.LineupViewModel
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.recap.RecapViewModel
import cz.hspinovace.psmf.ui.settings.SettingsViewModel
import cz.hspinovace.psmf.ui.shell.ShellViewModel
import cz.hspinovace.psmf.ui.teams.TeamRosterViewModel
import cz.hspinovace.psmf.ui.teams.TeamsViewModel
import cz.hspinovace.psmf.usecase.AddPlayerAtThePitch
import cz.hspinovace.psmf.usecase.AddPlayerToLineup
import cz.hspinovace.psmf.usecase.AffirmNoCards
import cz.hspinovace.psmf.usecase.BrowseTeams
import cz.hspinovace.psmf.usecase.BuildConsoleEntry
import cz.hspinovace.psmf.usecase.BuildLineupEntry
import cz.hspinovace.psmf.usecase.ConfirmReport
import cz.hspinovace.psmf.usecase.FinishMatch
import cz.hspinovace.psmf.usecase.ListFixtures
import cz.hspinovace.psmf.usecase.LoadTeamRoster
import cz.hspinovace.psmf.usecase.LogCard
import cz.hspinovace.psmf.usecase.LogGoal
import cz.hspinovace.psmf.usecase.NewId
import cz.hspinovace.psmf.usecase.ObserveReportInProgress
import cz.hspinovace.psmf.usecase.RecordResult
import cz.hspinovace.psmf.usecase.SaveAssessment
import cz.hspinovace.psmf.usecase.SaveLineup
import cz.hspinovace.psmf.usecase.SaveMatchHeader
import cz.hspinovace.psmf.usecase.SetDefaultJerseyNumber
import cz.hspinovace.psmf.usecase.StartMatch
import cz.hspinovace.psmf.usecase.StartOrResumeMatch
import cz.hspinovace.psmf.usecase.ToggleFollowedTeam
import cz.hspinovace.psmf.usecase.UndoLastEvent
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
        single<AddedPlayerRepository> { SqlDelightAddedPlayerRepository(get()) }
        single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
        single<FollowedTeamRepository> { SqlDelightFollowedTeamRepository(get()) }
        single<JerseyOverrideRepository> { SqlDelightJerseyOverrideRepository(get()) }

        // Seed data. The reader lives in this module because Compose
        // resources are generated here and `shared` cannot see them.
        single<SeedFileReader> { ComposeResourceSeedFileReader() }
        single { SeedLeagueCatalog(get()) }
        // League data, with the referee's corrected jersey numbers over
        // it. This is the only file that knows both halves exist: the seed
        // repository never hears about the override table, and everything
        // upstream asks for `LeagueRepository` and gets the corrected view.
        single<LeagueRepository> {
            JerseyOverridingLeagueRepository(
                delegate = SeedLeagueRepository(get()),
                overrides = get(),
            )
        }

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
        viewModel { (matchId: MatchId) -> LineupViewModel(matchId, get(), get(), get(), get()) }
        viewModel { (matchId: MatchId) ->
            ConsoleViewModel(matchId, get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (matchId: MatchId) -> AssessmentViewModel(matchId, get(), get(), get()) }
        viewModel { (matchId: MatchId) -> RecapViewModel(matchId, get(), get(), get(), get(), get()) }
        viewModel { (matchId: MatchId) -> ExportViewModel(matchId, get(), get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
        viewModel { ShellViewModel(get()) }
        viewModel { TeamsViewModel(get(), get()) }
        viewModel { (teamId: TeamId) -> TeamRosterViewModel(teamId, get(), get(), get()) }
    }

/**
 * Use cases are factories, not singles: they hold no state, and a new one
 * per injection costs an allocation.
 */
private fun Module.factoryOfUseCases() {
    factory { ListFixtures(get(), get(), get()) }
    factory { ObserveReportInProgress(get()) }
    factory { BrowseTeams(get(), get()) }
    factory { LoadTeamRoster(get(), get(), get()) }
    factory { SetDefaultJerseyNumber(get()) }
    factory { ToggleFollowedTeam(get()) }
    factory { StartOrResumeMatch(get(), get(), get()) }
    factory { SaveMatchHeader(get()) }
    factory { BuildLineupEntry(get(), get(), get()) }
    factory { SaveLineup(get()) }
    factory { AddPlayerAtThePitch(get(), get()) }
    factory { AddPlayerToLineup(get(), get(), get()) }
    factory { BuildConsoleEntry(get(), get()) }
    factory { StartMatch(get()) }
    factory { FinishMatch(get()) }
    factory { LogGoal(get()) }
    factory { LogCard(get()) }
    factory { UndoLastEvent(get()) }
    factory { BuildZouReport(get(), get()) }
    factory { ExportZou() }
    factory { SaveAssessment(get()) }
    factory { RecordResult(get()) }
    factory { ConfirmReport(get()) }
    factory { AffirmNoCards(get()) }
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
