package cz.hspinovace.psmf

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_back
import cz.hspinovace.psmf.resources.fixtures_title
import cz.hspinovace.psmf.resources.header_title
import cz.hspinovace.psmf.resources.lineup_title
import cz.hspinovace.psmf.ui.fixtures.FixturesScreen
import cz.hspinovace.psmf.ui.fixtures.FixturesViewModel
import cz.hspinovace.psmf.ui.header.MatchHeaderScreen
import cz.hspinovace.psmf.ui.header.MatchHeaderViewModel
import cz.hspinovace.psmf.ui.lineup.LineupScreen
import cz.hspinovace.psmf.ui.lineup.LineupViewModel
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
 * compose`, a dependency this project does not otherwise have. For a
 * back stack of two sealed values that is a lot of machinery and a new
 * version to pin for no behaviour change, so the one build warning stands
 * until predictive back is actually wanted. The alternative — an
 * expect/actual over `androidx.activity.compose` on Android — is more code
 * for the same behaviour again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    PsmfTheme {
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
        ) { modifier ->
            when (current) {
                Destination.Fixtures -> {
                    FixturesRoute(
                        modifier = modifier,
                        onOpenMatch = { navigator.goTo(Destination.MatchHeader(it)) },
                    )
                }

                is Destination.MatchHeader -> {
                    MatchHeaderRoute(
                        matchId = current.matchId,
                        onContinue = { navigator.goTo(Destination.Lineup(it)) },
                        modifier = modifier,
                    )
                }

                is Destination.Lineup -> {
                    LineupRoute(matchId = current.matchId, modifier = modifier)
                }
            }
        }
    }
}

@Composable
private fun Destination.title(): String =
    when (this) {
        Destination.Fixtures -> stringResource(Res.string.fixtures_title)
        is Destination.MatchHeader -> stringResource(Res.string.header_title)
        is Destination.Lineup -> stringResource(Res.string.lineup_title)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
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
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun FixturesRoute(
    onOpenMatch: (MatchId) -> Unit,
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

    MatchHeaderScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun LineupRoute(
    matchId: MatchId,
    modifier: Modifier = Modifier,
) {
    val viewModel: LineupViewModel = koinViewModel(key = matchId.value) { parametersOf(matchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LineupScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
