package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.console_before_kickoff
import cz.hspinovace.psmf.resources.console_power_play
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.ConsoleEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Instant

private const val SECONDS_PAD = 2

/**
 * Score, minute, and any side currently playing a player short.
 *
 * **The minute is a subtraction, not a count.** Nothing ticks anywhere
 * behind this: [now] arrives from the caller, the kickoff instant is
 * stored, and the difference is the clock. It therefore cannot drift, is
 * not killed with the process, and does not need a background timer — iOS
 * cannot run one at all.
 */
@Composable
fun Scoreboard(
    entry: ConsoleEntry,
    now: Instant,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(PsmfDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamName(entry.home.teamName, TextAlign.Start, Modifier.weight(1f))
            Text(
                text = entry.score.asWrittenOnReport,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = PsmfDimens.itemSpacing),
            )
            TeamName(entry.away.teamName, TextAlign.End, Modifier.weight(1f))
        }

        Text(
            text = entry.clockReading(now),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        entry.powerPlaysRunningAt(now).forEach { powerPlay ->
            val team = entry.side(powerPlay.shortHandedSide).teamName
            Text(
                text = stringResource(Res.string.console_power_play, team, powerPlay.remainingAt(now).asCountdown()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(PsmfDimens.labelGap),
                        ).padding(PsmfDimens.labelGap),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TeamName(
    name: String,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        textAlign = align,
        modifier = modifier,
        maxLines = 2,
    )
}

/**
 * `34´` once the match is under way.
 *
 * Minutes rather than minutes and seconds: the form records minutes, and a
 * second-by-second display invites a precision the report does not have.
 */
@Composable
private fun ConsoleEntry.clockReading(now: Instant): String =
    minuteAt(now)?.written ?: stringResource(Res.string.console_before_kickoff)

/**
 * The power play, in minutes and seconds, because ten minutes is short
 * enough that the seconds matter to the side playing short.
 */
private fun Duration.asCountdown(): String {
    val total = inWholeSeconds
    return "${total / SECONDS_IN_A_MINUTE}:${(total % SECONDS_IN_A_MINUTE).toString().padStart(SECONDS_PAD, '0')}"
}

private const val SECONDS_IN_A_MINUTE = 60
