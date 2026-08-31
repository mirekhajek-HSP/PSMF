package cz.hspinovace.psmf

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.hspinovace.psmf.data.settings.ThemeChoice
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_back
import cz.hspinovace.psmf.resources.action_settings
import cz.hspinovace.psmf.resources.assessment_title
import cz.hspinovace.psmf.resources.console_title
import cz.hspinovace.psmf.resources.export_title
import cz.hspinovace.psmf.resources.fixtures_title
import cz.hspinovace.psmf.resources.header_title
import cz.hspinovace.psmf.resources.lineup_title
import cz.hspinovace.psmf.resources.recap_title
import cz.hspinovace.psmf.resources.settings_title
import cz.hspinovace.psmf.ui.assessment.AssessmentScreen
import cz.hspinovace.psmf.ui.assessment.AssessmentViewModel
import cz.hspinovace.psmf.ui.console.ConsoleScreen
import cz.hspinovace.psmf.ui.console.ConsoleViewModel
import cz.hspinovace.psmf.ui.export.ExportScreen
import cz.hspinovace.psmf.ui.export.ExportViewModel
import cz.hspinovace.psmf.ui.fixtures.FixturesScreen
import cz.hspinovace.psmf.ui.fixtures.FixturesViewModel
import cz.hspinovace.psmf.ui.fixtures.OpenMatch
import cz.hspinovace.psmf.ui.header.MatchHeaderScreen
import cz.hspinovace.psmf.ui.header.MatchHeaderViewModel
import cz.hspinovace.psmf.ui.lineup.LineupScreen
import cz.hspinovace.psmf.ui.lineup.LineupViewModel
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.recap.RecapScreen
import cz.hspinovace.psmf.ui.recap.RecapViewModel
import cz.hspinovace.psmf.ui.settings.SettingsScreen
import cz.hspinovace.psmf.ui.settings.SettingsViewModel
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.usecase.ResumePoint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

/**
 * The whole app: a wizard over one back stack.
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

    PsmfTheme(darkTheme = settingsState.theme.isDark()) {
        val navigator: AppNavigator = koinInject()
        val backStack by navigator.backStack.collectAsStateWithLifecycle()
        val current = backStack.last()

        // At the root, let the platform have the gesture -- on Android that
        // means back leaves the app rather than doing nothing.
        BackHandler(enabled = backStack.size > 1) { navigator.back() }

        AppScaffold(
            title = current.title(),
            canGoBack = backStack.size > 1,
            onBack = { navigator.back() },
            onSettings =
                if (current == Destination.Fixtures) {
                    { navigator.goTo(Destination.Settings) }
                } else {
                    null
                },
        ) { modifier ->
            Route(current, navigator, settings, modifier)
        }
    }
}

@Composable
private fun Route(
    current: Destination,
    navigator: AppNavigator,
    settings: SettingsViewModel,
    modifier: Modifier,
) {
    when (current) {
        Destination.Fixtures -> {
            FixturesRoute(modifier = modifier, onOpenMatch = { navigator.goTo(it.destination()) })
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

        Destination.Settings -> {
            val state by settings.state.collectAsStateWithLifecycle()
            SettingsScreen(state = state, onEvent = settings::onEvent, modifier = modifier)
        }
    }
}

@Composable
private fun Destination.title(): String =
    when (this) {
        Destination.Fixtures -> stringResource(Res.string.fixtures_title)
        is Destination.MatchHeader -> stringResource(Res.string.header_title)
        is Destination.Lineup -> stringResource(Res.string.lineup_title)
        is Destination.Console -> stringResource(Res.string.console_title)
        is Destination.Assessment -> stringResource(Res.string.assessment_title)
        is Destination.Recap -> stringResource(Res.string.recap_title)
        is Destination.Export -> stringResource(Res.string.export_title)
        Destination.Settings -> stringResource(Res.string.settings_title)
    }

@Composable
private fun ThemeChoice.isDark(): Boolean =
    when (this) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSettings: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (canGoBack) {
                        // A word rather than a chevron. It reads at arm's
                        // length in poor light, translates, and costs no
                        // icon dependency.
                        TextButton(onClick = onBack) {
                            Text(stringResource(Res.string.action_back))
                        }
                    }
                },
                actions = {
                    if (onSettings != null) {
                        TextButton(onClick = onSettings) {
                            Text(stringResource(Res.string.action_settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
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
        modifier = modifier,
    )
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
 * being killed — loses nothing, which is the whole reason the clock is
 * derived rather than ticked.
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

    ExportScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

private val CLOCK_REFRESH = 1.seconds
