package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.MatchEvent
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.console_card
import cz.hspinovace.psmf.resources.console_card_other
import cz.hspinovace.psmf.resources.console_dismissed
import cz.hspinovace.psmf.resources.console_finish
import cz.hspinovace.psmf.resources.console_finished
import cz.hspinovace.psmf.resources.console_goal
import cz.hspinovace.psmf.resources.console_goal_no_scorer
import cz.hspinovace.psmf.resources.console_loading
import cz.hspinovace.psmf.resources.console_log_empty
import cz.hspinovace.psmf.resources.console_log_title
import cz.hspinovace.psmf.resources.console_marker_red
import cz.hspinovace.psmf.resources.console_marker_yellow
import cz.hspinovace.psmf.resources.console_not_started
import cz.hspinovace.psmf.resources.console_start
import cz.hspinovace.psmf.resources.console_undo
import cz.hspinovace.psmf.resources.console_yellows
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Screen 4, as a plain state-driven composable.
 *
 * [now] is a parameter rather than something read inside: the clock is a
 * subtraction from the stored kickoff instant, so passing the instant in
 * makes the whole screen renderable at any moment of the match without a
 * timer, in a test or in a preview.
 *
 * What is **not** here, deliberately: no pause, no stop, no substitutions
 * and no assists. The clock runs continuously and the referee adds time;
 * neither substitutions nor assists appear anywhere on the ZoU.
 */
@Composable
fun ConsoleScreen(
    state: ConsoleUiState,
    now: Instant,
    onEvent: (ConsoleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val entry = state.entry
        val team = state.selected
        if (state.loading || entry == null || team == null) {
            Text(
                text = stringResource(Res.string.console_loading),
                modifier = Modifier.fillMaxWidth().padding(PsmfDimens.screenPadding),
                textAlign = TextAlign.Center,
            )
            return@Surface
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Scoreboard(entry = entry, now = now)
            Controls(state, onEvent)
            SideTabs(state, onEvent)
            Body(state, team, onEvent, modifier = Modifier.weight(1f))
        }
    }

    state.card?.let { CardSheet(draft = it, state = state, onEvent = onEvent) }
}

@Composable
private fun Controls(
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val entry = state.entry ?: return
    Column(
        modifier = Modifier.padding(horizontal = PsmfDimens.screenPadding, vertical = PsmfDimens.labelGap),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
    ) {
        if (!entry.started) {
            Text(
                text = stringResource(Res.string.console_not_started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionRow {
            // Undo, never edit. Amending a finished report is screen 9 and
            // is out of the demo.
            TextButton(
                onClick = { onEvent(ConsoleEvent.UndoPressed) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.console_undo))
            }
            when {
                !entry.started -> {
                    Button(
                        onClick = { onEvent(ConsoleEvent.StartPressed) },
                        modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                    ) {
                        Text(stringResource(Res.string.console_start))
                    }
                }

                entry.status == MatchStatus.FINISHED -> {
                    Text(
                        text = stringResource(Res.string.console_finished),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> {
                    OutlinedButton(
                        onClick = { onEvent(ConsoleEvent.FinishPressed) },
                        modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                    ) {
                        Text(stringResource(Res.string.console_finish))
                    }
                }
            }
        }
    }
}

@Composable
private fun SideTabs(
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
) {
    val entry = state.entry ?: return
    TabRow(selectedTabIndex = if (state.selectedSide == TeamSide.HOME) 0 else 1) {
        listOf(entry.home, entry.away).forEach { team ->
            Tab(
                selected = state.selectedSide == team.side,
                onClick = { onEvent(ConsoleEvent.SideSelected(team.side)) },
                modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                text = { Text(team.teamName, maxLines = 2, textAlign = TextAlign.Center) },
            )
        }
    }
}

@Composable
private fun Body(
    state: ConsoleUiState,
    team: ConsoleTeam,
    onEvent: (ConsoleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
    ) {
        items(team.rows.size, key = { team.rows[it].appearanceId.value }) { index ->
            PlayerRow(row = team.rows[index], side = team.side, onEvent = onEvent)
        }

        item(key = "unattributed") {
            // `13´ — 2:1` in the worked example. One tap, because the
            // alternative is being unable to record it at all.
            OutlinedButton(
                onClick = { onEvent(ConsoleEvent.GoalWithNoScorer(team.side)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.console_goal_no_scorer))
            }
        }

        item(key = "other-person") {
            // A card shown to somebody with no jersey number: the worked
            // example cards a deputy captain.
            OutlinedButton(
                onClick = { onEvent(ConsoleEvent.CardOpened(appearanceId = null, side = team.side)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.console_card_other))
            }
        }

        item(key = "log-title") {
            Text(
                text = stringResource(Res.string.console_log_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = PsmfDimens.sectionSpacing),
            )
        }

        val log = state.entry?.log.orEmpty()
        if (log.isEmpty()) {
            item(key = "log-empty") {
                Text(
                    text = stringResource(Res.string.console_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(log.size) { index -> LogRow(state, log[index]) }
        }
    }
}

/**
 * One player, and the two things that can be logged against them.
 *
 * A sent-off player keeps their row and loses their buttons: hiding them
 * would lose the reason they are unavailable, and the referee needs to see
 * who is off while the power play beside it counts down.
 */
@Composable
private fun PlayerRow(
    row: ConsoleRow,
    side: TeamSide,
    onEvent: (ConsoleEvent) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.jerseyNumber?.value?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = PsmfDimens.labelGap),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name.asWrittenOnReport,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (row.dismissed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                RowStatus(row)
            }

            if (!row.dismissed) {
                TextButton(
                    onClick = { onEvent(ConsoleEvent.GoalScoredBy(row.appearanceId)) },
                    modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                ) {
                    Text(stringResource(Res.string.console_goal))
                }
                TextButton(
                    onClick = { onEvent(ConsoleEvent.CardOpened(row.appearanceId, side)) },
                    modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
                ) {
                    Text(stringResource(Res.string.console_card))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun RowStatus(row: ConsoleRow) {
    Row(horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        if (row.dismissed) {
            Text(
                text = stringResource(Res.string.console_dismissed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (row.yellowsInThisMatch > 0) {
            // A second one is a dismissal, written `2. ŽK`. The referee has
            // to know before they issue it, not after.
            Text(
                text = stringResource(Res.string.console_yellows, row.yellowsInThisMatch),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = PsmfDimens.labelGap),
            )
        }
    }
}

@Composable
private fun LogRow(
    state: ConsoleUiState,
    event: MatchEvent,
) {
    val marker =
        when (event) {
            is GoalEvent -> ""
            is RedCard -> stringResource(Res.string.console_marker_red)
            is CardEvent -> stringResource(Res.string.console_marker_yellow)
        }
    val who =
        when (event) {
            is GoalEvent -> {
                event.scorer?.let {
                    state.entry
                        ?.row(it)
                        ?.name
                        ?.asWrittenOnReport
                } ?: NO_SCORER
            }

            is CardEvent -> {
                event.subjectName(state)
            }
        }
    val detail =
        when (event) {
            is GoalEvent -> event.scoreAfter.asWrittenOnReport
            is CardEvent -> event.reason.text
        }

    Text(
        text = listOf(event.minute.written, marker, who, detail).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = PsmfDimens.labelGap),
    )
}

private fun CardEvent.subjectName(state: ConsoleUiState): String =
    when (val subject = this.subject) {
        is CardSubject.Player -> {
            state.entry
                ?.row(subject.appearance)
                ?.name
                ?.asWrittenOnReport
                .orEmpty()
        }

        is CardSubject.NamedPerson -> {
            subject.name.value
        }
    }

/** The dash the form uses in the `Střelec` column when nobody is named. */
private const val NO_SCORER = "—"
