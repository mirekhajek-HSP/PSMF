package cz.hspinovace.psmf

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.hspinovace.psmf.data.settings.AppLanguage
import cz.hspinovace.psmf.data.settings.ThemeChoice
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.assessment_title
import cz.hspinovace.psmf.resources.console_title
import cz.hspinovace.psmf.resources.export_title
import cz.hspinovace.psmf.resources.fixtures_title
import cz.hspinovace.psmf.resources.header_title
import cz.hspinovace.psmf.resources.lineup_title
import cz.hspinovace.psmf.resources.no_report_title
import cz.hspinovace.psmf.resources.recap_title
import cz.hspinovace.psmf.resources.settings_title
import cz.hspinovace.psmf.resources.teams_title
import cz.hspinovace.psmf.ui.assessment.AssessmentScreen
import cz.hspinovace.psmf.ui.assessment.AssessmentViewModel
import cz.hspinovace.psmf.ui.console.ConsoleScreen
import cz.hspinovace.psmf.ui.console.ConsoleViewModel
import cz.hspinovace.psmf.ui.export.ExportScreen
import cz.hspinovace.psmf.ui.export.ExportViewModel
import cz.hspinovace.psmf.ui.export.rememberReportSaver
import cz.hspinovace.psmf.ui.fixtures.FixturesScreen
import cz.hspinovace.psmf.ui.fixtures.FixturesViewModel
import cz.hspinovace.psmf.ui.fixtures.OpenMatch
import cz.hspinovace.psmf.ui.header.MatchHeaderScreen
import cz.hspinovace.psmf.ui.header.MatchHeaderViewModel
import cz.hspinovace.psmf.ui.lineup.LineupScreen
import cz.hspinovace.psmf.ui.lineup.LineupViewModel
import cz.hspinovace.psmf.ui.locale.AppEnvironment
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.recap.RecapScreen
import cz.hspinovace.psmf.ui.recap.RecapViewModel
import cz.hspinovace.psmf.ui.settings.SettingsScreen
import cz.hspinovace.psmf.ui.settings.SettingsViewModel
import cz.hspinovace.psmf.ui.shell.AppShell
import cz.hspinovace.psmf.ui.shell.NoReportScreen
import cz.hspinovace.psmf.ui.shell.ShellViewModel
import cz.hspinovace.psmf.ui.teams.TeamRosterScreen
import cz.hspinovace.psmf.ui.teams.TeamRosterViewModel
import cz.hspinovace.psmf.ui.teams.TeamsScreen
import cz.hspinovace.psmf.ui.teams.TeamsViewModel
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.usecase.ResumePoint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

/**
 * The whole app: four tabs, each with its own back stack, and the report a
 * linear wizard inside one of them.
 *
 * The report stays linear on purpose. A ZoU has a required order and a
 * referee outdoors should not be choosing where to go next; the tabs exist
 * so that everything *else* is reachable without abandoning the report.
 *
 * Screens are wired here and nowhere else. Each `*Screen` composable takes
 * immutable state and callbacks and knows nothing about Koin, so it can be
 * driven from a test without a dependency graph; the `*Route` wrappers
 * below are the only place the two meet.
 *
 * `BackHandler` is both experimental and deprecated upstream, which is an
 * awkward pair. Its replacement, `NavigationEventHandler`, is not a
 * drop-in: it takes a `NavigationEventState` that has to be built and
 * remembered, and it lives in `androidx.navigationevent:navigationevent-
 * compose`, a dependency this project does not otherwise have. For a back
 * stack of a few sealed values that is a lot of machinery and a new
 * version to pin for no behaviour change, so the one build warning stands
 * until predictive back is actually wanted.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val settings: SettingsViewModel = koinViewModel()
    val settingsState by settings.state.collectAsStateWithLifecycle()

    // Read on the first composition, before anything has been provided:
    // once a language is in force the platform locale reports that instead,
    // so asking later would be asking the app about itself.
    val deviceTag = Locale.current.language
    val deviceLanguage = remember { AppLanguage.forTag(deviceTag) ?: AppLanguage.CZECH }
    val language = settingsState.language ?: deviceLanguage

    PsmfTheme(darkTheme = settingsState.theme.isDark()) {
        // A ground to draw on for the frame or two before the stored
        // settings arrive. Drawing the app first and correcting it after
        // would show the wrong language and then the right one.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (settingsState.loaded) {
                AppEnvironment(language) {
                    Shell(language, settings)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shell(
    language: AppLanguage,
    settings: SettingsViewModel,
) {
    val navigator: AppNavigator = koinInject()
    val navigation by navigator.state.collectAsStateWithLifecycle()

    val shell: ShellViewModel = koinViewModel()
    val shellState by shell.state.collectAsStateWithLifecycle()

    // The badge comes from the database and the back stacks do not, so
    // on the first launch after a kill the two disagree: a match is in
    // progress but the report tab is empty. Seeding it here is what
    // makes tapping the badge land on the console rather than on "no
    // report open". It does not change tab -- the app still opens on
    // the fixture list, which is where the referee should land.
    val inProgress = shellState.inProgress
    LaunchedEffect(inProgress) {
        inProgress?.let { navigator.adoptReport(Destination.Console(it)) }
    }

    // Back is handled here whenever there is anywhere to go: within the
    // tab, or out of a tab to the fixture list. Only at the root of the
    // fixture list does the platform get the gesture, which on Android
    // means leaving the app.
    BackHandler(enabled = navigation.canGoBackWithinTab || navigation.tab != Tab.FIXTURES) {
        navigator.back()
    }

    AppShell(
        navigation = navigation,
        title = navigation.current.title(),
        reportInProgress = inProgress != null,
        onSelectTab = navigator::select,
        onBack = { navigator.back() },
    ) { modifier ->
        Route(navigation.current, navigator, settings, language, modifier)
    }
}

@Composable
private fun Route(
    current: Destination,
    navigator: AppNavigator,
    settings: SettingsViewModel,
    language: AppLanguage,
    modifier: Modifier,
) {
    when (current) {
        Destination.Fixtures -> {
            FixturesRoute(modifier = modifier, onOpenMatch = { navigator.openReport(it.destination()) })
        }

        Destination.NoReport -> {
            NoReportScreen(onPickFixture = { navigator.select(Tab.FIXTURES) }, modifier = modifier)
        }

        is Destination.MatchHeader -> {
            MatchHeaderRoute(
                matchId = current.matchId,
                onContinue = { navigator.goTo(Destination.Lineup(it)) },
                modifier = modifier,
            )
        }

        is Destination.Lineup -> {
            LineupRoute(
                matchId = current.matchId,
                onContinue = { navigator.goTo(Destination.Console(it)) },
                modifier = modifier,
            )
        }

        is Destination.Console -> {
            ConsoleRoute(
                matchId = current.matchId,
                onContinue = { navigator.goTo(Destination.Assessment(it)) },
                modifier = modifier,
            )
        }

        is Destination.Assessment -> {
            AssessmentRoute(
                matchId = current.matchId,
                onContinue = { navigator.goTo(Destination.Recap(it)) },
                modifier = modifier,
            )
        }

        is Destination.Recap -> {
            RecapRoute(
                matchId = current.matchId,
                onContinue = { navigator.goTo(Destination.Export(it)) },
                modifier = modifier,
            )
        }

        is Destination.Export -> {
            ExportRoute(matchId = current.matchId, modifier = modifier)
        }

        Destination.Teams -> {
            TeamsRoute(
                onOpenTeam = { navigator.goTo(Destination.TeamRoster(it)) },
                modifier = modifier,
            )
        }

        is Destination.TeamRoster -> {
            TeamRosterRoute(teamId = current.teamId, modifier = modifier)
        }

        Destination.Settings -> {
            val state by settings.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                language = language,
                onEvent = settings::onEvent,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Destination.title(): String =
    when (this) {
        Destination.Fixtures -> stringResource(Res.string.fixtures_title)
        Destination.NoReport -> stringResource(Res.string.no_report_title)
        is Destination.MatchHeader -> stringResource(Res.string.header_title)
        is Destination.Lineup -> stringResource(Res.string.lineup_title)
        is Destination.Console -> stringResource(Res.string.console_title)
        is Destination.Assessment -> stringResource(Res.string.assessment_title)
        is Destination.Recap -> stringResource(Res.string.recap_title)
        is Destination.Export -> stringResource(Res.string.export_title)
        Destination.Teams -> stringResource(Res.string.teams_title)
        is Destination.TeamRoster -> stringResource(Res.string.teams_title)
        Destination.Settings -> stringResource(Res.string.settings_title)
    }

@Composable
private fun ThemeChoice.isDark(): Boolean =
    when (this) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

/**
 * Tapping a fixture lands where the work is: a match already under way
 * opens on the console, and a finished one on the recap, rather than on a
 * header filled in an hour ago.
 */
private fun OpenMatch.destination(): Destination =
    when (resumePoint) {
        ResumePoint.HEADER -> Destination.MatchHeader(matchId)
        ResumePoint.CONSOLE -> Destination.Console(matchId)
        ResumePoint.RECAP -> Destination.Recap(matchId)
    }

@Composable
private fun FixturesRoute(
    onOpenMatch: (OpenMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FixturesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openMatch by viewModel.openMatch.collectAsStateWithLifecycle()

    LaunchedEffect(openMatch) {
        openMatch?.let {
            onOpenMatch(it)
            viewModel.matchOpened()
        }
    }

    FixturesScreen(
        state = state,
        onFixtureSelected = viewModel::onFixtureSelected,
        onRetry = viewModel::load,
        onFilterChanged = viewModel::onFilterChanged,
        modifier = modifier,
    )
}

/**
 * The Týmy tab.
 *
 * Reloaded on every entry rather than observed, because the only things
 * that change it are edits made on this screen and on the roster below it,
 * and both come back through here.
 */
@Composable
private fun TeamsRoute(
    onOpenTeam: (TeamId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TeamsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Following a team on the roster screen has to show up here when the
    // referee comes back, and the directory was loaded before they left.
    LaunchedEffect(Unit) { viewModel.reload() }

    TeamsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTeam = onOpenTeam,
        modifier = modifier,
    )
}

@Composable
private fun TeamRosterRoute(
    teamId: TeamId,
    modifier: Modifier = Modifier,
) {
    // Keyed by the team, so opening a second roster does not inherit the
    // first one's half-typed number.
    val viewModel: TeamRosterViewModel =
        koinViewModel(key = teamId.value) { parametersOf(teamId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    TeamRosterScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun MatchHeaderRoute(
    matchId: MatchId,
    onContinue: (MatchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed by the match, so opening a different report does not inherit
    // the previous one's half-typed names.
    val viewModel: MatchHeaderViewModel =
        koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.complete) {
        if (state.complete) {
            onContinue(matchId)
            // Cleared, or coming back with Back would immediately go
            // forwards again.
            viewModel.continued()
        }
    }

    MatchHeaderScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun LineupRoute(
    matchId: MatchId,
    onContinue: (MatchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LineupViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.readyToContinue) {
        if (state.readyToContinue) {
            onContinue(matchId)
            viewModel.continued()
        }
    }

    LineupScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * The one place a clock reading is produced.
 *
 * A one-second re-read of the system clock, **not** a timer that counts:
 * the elapsed time is `now - kickoffAt`, so this loop only decides how
 * often the screen is redrawn. Stopping it — going to the background,
 * being killed, or the referee switching to another tab — loses nothing,
 * which is the whole reason the clock is derived rather than ticked. On
 * return the loop starts again and reads the same wall clock it would have
 * read had it never stopped.
 */
@Composable
private fun ConsoleRoute(
    matchId: MatchId,
    onContinue: (MatchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConsoleViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(viewModel.now()) }
    LaunchedEffect(state.entry?.kickoffAt) {
        while (true) {
            now = viewModel.now()
            delay(CLOCK_REFRESH)
        }
    }

    LaunchedEffect(state.readyToContinue) {
        if (state.readyToContinue) {
            onContinue(matchId)
            viewModel.continued()
        }
    }

    ConsoleScreen(state = state, now = now, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun AssessmentRoute(
    matchId: MatchId,
    onContinue: (MatchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AssessmentViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.readyToContinue) {
        if (state.readyToContinue) {
            onContinue(matchId)
            viewModel.continued()
        }
    }

    AssessmentScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun RecapRoute(
    matchId: MatchId,
    onContinue: (MatchId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecapViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.readyToContinue) {
        if (state.readyToContinue) {
            onContinue(matchId)
            viewModel.continued()
        }
    }

    RecapScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun ExportRoute(
    matchId: MatchId,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExportViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The save itself needs a platform document picker, which the
    // ViewModel has no business knowing about -- see `ReportSaver`. It
    // only asks; this is where the asking becomes a real save.
    val saver = rememberReportSaver()
    val savePending by viewModel.savePending.collectAsStateWithLifecycle()
    LaunchedEffect(savePending) {
        savePending?.let { documents -> viewModel.saveHandled(saver.save(documents)) }
    }

    ExportScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

private val CLOCK_REFRESH = 1.seconds
