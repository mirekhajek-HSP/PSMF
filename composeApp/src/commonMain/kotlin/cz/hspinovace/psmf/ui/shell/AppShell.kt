package cz.hspinovace.psmf.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.action_back
import cz.hspinovace.psmf.resources.tab_fixtures
import cz.hspinovace.psmf.resources.tab_report
import cz.hspinovace.psmf.resources.tab_report_in_progress
import cz.hspinovace.psmf.resources.tab_settings
import cz.hspinovace.psmf.resources.tab_teams
import cz.hspinovace.psmf.ui.navigation.NavigationState
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.theme.PsmfBrand
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every screen sits in: a title bar, the four tabs, and the
 * current screen between them.
 *
 * Takes state and callbacks and nothing else, so a test can drive the whole
 * shell without a dependency graph. `App` is where this meets Koin.
 */
@Composable
fun AppShell(
    navigation: NavigationState,
    title: String,
    reportInProgress: Boolean,
    onSelectTab: (Tab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ShellTopBar(
                title = title,
                // Back is offered for a step *within* a tab. Leaving a tab
                // is what the tab bar and the system gesture are for.
                canGoBack = navigation.canGoBackWithinTab,
                onBack = onBack,
            )
        },
        bottomBar = {
            PsmfNavigationBar(
                selected = navigation.tab,
                reportInProgress = reportInProgress,
                onSelect = onSelectTab,
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canGoBack) {
                // A word rather than a chevron. It reads at arm's length in
                // poor light, translates, and costs no icon dependency.
                //
                // The colour is spelled out because `TextButton` takes its
                // content colour from `primary`, which is ink -- invisible
                // on the ink bar. Nothing else in the app sits on a dark
                // ground, so this is the only place that has to say so.
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = PsmfBrand.Surface),
                ) {
                    Text(stringResource(Res.string.action_back))
                }
            }
        },
        // The dark bar from psmf.cz. White on #2B2B2B is about 12.6:1,
        // which is the best contrast anywhere in the app -- worth having on
        // the strip that says which step of the report this is.
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = PsmfBrand.Ink,
                titleContentColor = PsmfBrand.Surface,
                navigationIconContentColor = PsmfBrand.Surface,
                actionIconContentColor = PsmfBrand.Surface,
            ),
    )
}

/**
 * Four tabs, labelled with words rather than glyphs.
 *
 * **Not Material's `NavigationBar`**, and not for taste. That widget is a
 * fixed 80dp tall with a single-line label in a quarter of the screen
 * width, and two of the constraints here break it: Ukrainian labels are
 * half again as long as the Czech ones (`Налаштування` against
 * `Nastavení`), and the system font scale has to be respected because the
 * referee population skews older. Between them, a stock bar clips its own
 * labels at 130%. This one wraps to a second line and grows instead.
 *
 * Words also match a decision already taken for the back button: they read
 * at arm's length in poor light, they translate, and they need no icon
 * dependency — the project has none, and pulling in the Material icon set
 * for four glyphs would add a version to the matrix
 * `docs/BUILD_MATRIX.md` works to keep at one.
 */
@Composable
fun PsmfNavigationBar(
    selected: Tab,
    reportInProgress: Boolean,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                verticalAlignment = Alignment.Top,
            ) {
                Tab.entries.forEach { tab ->
                    TabItem(
                        label = tab.label(),
                        selected = tab == selected,
                        badgeDescription =
                            stringResource(Res.string.tab_report_in_progress)
                                .takeIf { tab == Tab.REPORT && reportInProgress },
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    badgeDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
            modifier
                // A minimum, never a fixed height: the label may be two
                // lines of Ukrainian at 130% font scale.
                .heightIn(min = PsmfDimens.minTouchTarget)
                .clickable(onClick = onClick)
                // Without this the longest label -- Ukrainian
                // "Налаштування" -- runs to the physical edge of the screen.
                .padding(horizontal = PsmfDimens.labelGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The selected indicator.
        Box(
            modifier =
                Modifier
                    .padding(bottom = PsmfDimens.labelGap)
                    .size(width = INDICATOR_WIDTH, height = INDICATOR_HEIGHT)
                    .background(
                        // The brand, and a fill: never something to read off.
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(INDICATOR_HEIGHT / 2),
                    ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                // 12sp Medium: Material's own bottom-bar size, and the size at
                // which "Налаштування" fits a quarter of a phone on one line.
                // 14sp bold was the first choice and it broke the word after
                // eleven of twelve letters, leaving an orphaned "я" on the
                // second line. The 56dp target below is what makes this
                // tappable in gloves; the label does not have to carry that.
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badgeDescription != null) {
                Spacer(Modifier.width(PsmfDimens.labelGap))
                Box(
                    modifier =
                        Modifier
                            .size(BADGE_DIAMETER)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .semantics { contentDescription = badgeDescription },
                )
            }
        }
    }
}

@Composable
private fun Tab.label(): String =
    when (this) {
        Tab.FIXTURES -> stringResource(Res.string.tab_fixtures)
        Tab.REPORT -> stringResource(Res.string.tab_report)
        Tab.TEAMS -> stringResource(Res.string.tab_teams)
        Tab.SETTINGS -> stringResource(Res.string.tab_settings)
    }

private val INDICATOR_WIDTH = 28.dp
private val INDICATOR_HEIGHT = 3.dp
private val BADGE_DIAMETER = 10.dp
