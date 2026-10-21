package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.Dismissal
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.LoggedWhistle
import cz.hspinovace.psmf.domain.MatchEvent
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.isPastTheFinalWhistle
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.card_dismissal_second
import cz.hspinovace.psmf.resources.console_card
import cz.hspinovace.psmf.resources.console_card_other
import cz.hspinovace.psmf.resources.console_continue
import cz.hspinovace.psmf.resources.console_dismissed
import cz.hspinovace.psmf.resources.console_end_period
import cz.hspinovace.psmf.resources.console_finish
import cz.hspinovace.psmf.resources.console_goal
import cz.hspinovace.psmf.resources.console_goal_no_scorer
import cz.hspinovace.psmf.resources.console_goal_with_minute
import cz.hspinovace.psmf.resources.console_loading
import cz.hspinovace.psmf.resources.console_log_empty
import cz.hspinovace.psmf.resources.console_log_title
import cz.hspinovace.psmf.resources.console_marker_red
import cz.hspinovace.psmf.resources.console_marker_yellow
import cz.hspinovace.psmf.resources.console_not_started
import cz.hspinovace.psmf.resources.console_start
import cz.hspinovace.psmf.resources.console_start_next_period
import cz.hspinovace.psmf.resources.console_undo
import cz.hspinovace.psmf.resources.console_yellows
import cz.hspinovace.psmf.ui.common.EvenActionContentPadding
import cz.hspinovace.psmf.ui.common.EvenActionRow
import cz.hspinovace.psmf.ui.common.TeamTabLabel
import cz.hspinovace.psmf.ui.common.WithdrawSignaturesDialog
import cz.hspinovace.psmf.ui.common.brandFilledColors
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import cz.hspinovace.psmf.usecase.LogLine
import cz.hspinovace.psmf.usecase.PeriodAction
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
    state.goal?.let { GoalSheet(draft = it, state = state, now = now, onEvent = onEvent) }
    state.takeBack?.let { TakeBackDialog(step = it, signed = state.signed, onEvent = onEvent) }
    // Asked before a goal, a card or a whistle is saved on a signed report:
    // it takes the signatures back.
    if (state.withdrawing != null) {
        WithdrawSignaturesDialog(
            onConfirm = { onEvent(ConsoleEvent.WithdrawalConfirmed) },
            onDismiss = { onEvent(ConsoleEvent.WithdrawalDismissed) },
        )
    }
}

/**
 * What moves the match on, and Undo (0.3.1; the testers found the buttons
 * ragged: *"to the right, below or beside each other"*).
 *
 * **Two lines, the same in every state.** The first is the whistles: the end
 * of the half, the start of the next, the end of the match, or the one that
 * carries on to the assessment. One or two buttons in a row as wide as the
 * lists below, the same width and height where there are two ([EvenActionRow]).
 * The second is Undo, **apart** (a gap wider than the one between whistles)
 * and **quieter** (text, no outline, no fill), at the start edge, **only as
 * wide as its word**: a full-width Undo is a target a stray thumb takes a goal
 * back with. It is at the left, where a right thumb reaches least, which is
 * where a control for mistakes belongs.
 */
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
        EvenActionRow { each ->
            // Not a pause: a half-time exists, but nothing here stops the
            // clock inside a period. See PeriodAction and ConsoleEntry.minuteAt.
            when (entry.periodAction) {
                PeriodAction.END_PERIOD -> {
                    WhistleButton(
                        text = stringResource(Res.string.console_end_period),
                        onClick = { onEvent(ConsoleEvent.EndPeriodPressed) },
                        modifier = each,
                    )
                }

                PeriodAction.START_NEXT_PERIOD -> {
                    WhistleButton(
                        text =
                            stringResource(
                                Res.string.console_start_next_period,
                                (entry.periodBreaks.size + 1).toString(),
                            ),
                        onClick = { onEvent(ConsoleEvent.StartNextPeriodPressed) },
                        modifier = each,
                    )
                }

                PeriodAction.NONE -> {}
            }
            when {
                !entry.started -> {
                    WhistleButton(
                        text = stringResource(Res.string.console_start),
                        onClick = { onEvent(ConsoleEvent.StartPressed) },
                        modifier = each,
                        filled = true,
                    )
                }

                entry.status.isPastTheFinalWhistle -> {
                    WhistleButton(
                        text = stringResource(Res.string.console_continue),
                        onClick = { onEvent(ConsoleEvent.ContinuePressed) },
                        modifier = each,
                        filled = true,
                    )
                }

                else -> {
                    WhistleButton(
                        text = stringResource(Res.string.console_finish),
                        onClick = { onEvent(ConsoleEvent.FinishPressed) },
                        modifier = each,
                    )
                }
            }
        }
        // Undo, never edit. Amending a finished report is screen 9 and
        // is out of the demo.
        TextButton(
            onClick = { onEvent(ConsoleEvent.UndoPressed) },
            modifier =
                Modifier
                    .padding(top = PsmfDimens.itemSpacing - PsmfDimens.labelGap)
                    .heightIn(min = PsmfDimens.minTouchTarget),
            // The word starts where the whistles' edge does, and is in the secondary text colour.
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            contentPadding = UndoPadding,
        ) {
            Text(stringResource(Res.string.console_undo))
        }
    }
}

/** Nothing before the word, so it starts at the edge the whistles do; a little after it, to tap. */
private val UndoPadding =
    PaddingValues(
        start = 0.dp,
        top = PsmfDimens.labelGap * 2,
        end = PsmfDimens.itemSpacing * 2,
        bottom = PsmfDimens.labelGap * 2,
    )

/** One of the whistles: outlined, or filled in the brand's yellow for the one that carries on. */
@Composable
private fun WhistleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    filled: Boolean = false,
) {
    val label = @Composable { Text(text, textAlign = TextAlign.Center) }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = brandFilledColors(),
            contentPadding = EvenActionContentPadding,
            content = { label() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = EvenActionContentPadding,
            content = { label() },
        )
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
                text = { TeamTabLabel(team.teamName, team.kit) },
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

        item(key = "goal-with-minute") {
            // A goal the referee forgot, or any goal once the match is over:
            // the one-tap goal writes the clock's minute, and this is for
            // every other.
            OutlinedButton(
                onClick = { onEvent(ConsoleEvent.GoalWithMinuteOpened(team.side)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.console_goal_with_minute))
            }
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

        val log = state.entry?.logLines.orEmpty()
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
                val goalDescription = stringResource(Res.string.console_goal)
                IconButton(
                    onClick = { onEvent(ConsoleEvent.GoalScoredBy(row.appearanceId)) },
                    modifier =
                        Modifier
                            .size(PsmfDimens.minTouchTarget)
                            .semantics { contentDescription = goalDescription },
                ) {
                    Icon(Icons.Filled.SportsSoccer, contentDescription = null)
                }
                val cardDescription = stringResource(Res.string.console_card)
                IconButton(
                    onClick = { onEvent(ConsoleEvent.CardOpened(row.appearanceId, side)) },
                    modifier =
                        Modifier
                            .size(PsmfDimens.minTouchTarget)
                            .semantics { contentDescription = cardDescription },
                ) {
                    CardGlyph()
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * The card-logging button's glyph. Not a Material icon: golblok's own
 * equivalent reuses [Icons.Default.Stop][androidx.compose.material.icons.filled.Stop],
 * which sits one thought away from the Pause/Stop/PlayArrow family this
 * screen deliberately has none of (see the no-pause-control test).
 * A small yellow-to-red card shape needs no borrowed meaning and, unlike
 * a football, does not commit to a colour before the sheet asks for one.
 */
@Composable
private fun CardGlyph() {
    Box(
        modifier =
            Modifier
                .size(width = 18.dp, height = 26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(listOf(CardGlyphYellow, CardGlyphRed))),
    )
}

private val CardGlyphYellow = Color(0xFFFFC107)
private val CardGlyphRed = Color(0xFFD32F2F)

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
    line: LogLine,
) {
    when (line) {
        is LogLine.Recorded -> EventLogRow(state, line.event)
        is LogLine.Whistled -> WhistleLogRow(line.whistle)
    }
}

/**
 * A whistle in the log: its minute as the form writes it, and its name as Undo's
 * question gives it. In the quieter colour, so the goals and cards, which are what
 * the referee looks the log up for, stay what the eye finds.
 */
@Composable
private fun WhistleLogRow(whistle: LoggedWhistle) {
    Text(
        text = "${whistle.minute.written} · ${whistleName(whistle.whistle, whistle.period)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = PsmfDimens.labelGap),
    )
}

@Composable
private fun EventLogRow(
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
    val secondYellow = stringResource(Res.string.card_dismissal_second)
    val detail =
        when (event) {
            is GoalEvent -> {
                event.scoreAfter.asWrittenOnReport
            }

            // The kind from the stored field, as the report writes it.
            is RedCard -> {
                if (event.dismissal ==
                    Dismissal.SECOND_YELLOW
                ) {
                    "$secondYellow · ${event.reason}"
                } else {
                    event.reason.text
                }
            }

            is CardEvent -> {
                event.reason.text
            }
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
