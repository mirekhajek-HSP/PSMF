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
import cz.hspinovace.psmf.domain.ClockPhase
import cz.hspinovace.psmf.domain.ClockReading
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.console_before_kickoff
import cz.hspinovace.psmf.resources.console_clock_added_time
import cz.hspinovace.psmf.resources.console_clock_finished
import cz.hspinovace.psmf.resources.console_clock_interval
import cz.hspinovace.psmf.resources.console_clock_written
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

        ClockLines(entry.readingAt(now))

        entry.powerPlaysRunningAt(now).forEach { powerPlay ->
            val team = entry.side(powerPlay.shortHandedSide).teamName
            Text(
                // Ten minutes of play: it holds through the interval, and
                // nothing is listed here once the final whistle has gone.
                text =
                    stringResource(
                        Res.string.console_power_play,
                        team,
                        entry.remainingAt(powerPlay, now).asCountdown(),
                    ),
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
 * The clock, in two lines that are not the same thing and both stay.
 *
 * **Above: play so far, `mm:ss`.** The first half counts on past `30:00` in
 * its added time, holds through the interval, and the second half starts at
 * `30:00` whatever the first added -- which is why it can be less than what
 * the interval showed. After the final whistle it is frozen at the whistle.
 *
 * **Below: the minute the next thing recorded gets**, as the form writes it:
 * `12´` for 12:40, `30´+` for the first half's added time and the interval,
 * `60´+` once the match is over. It was the only thing shown before, and a
 * referee about to tap a goal in added time needs to read it off.
 *
 * What the report and the log keep is still the form's minutes; the seconds
 * are for the referee, because added time is added by looking at them.
 */
@Composable
private fun ClockLines(reading: ClockReading) {
    val written = reading.written?.written.orEmpty()
    reading.shown?.let {
        Text(
            text = it.asMinutesAndSeconds(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    Text(
        text =
            when (reading.phase) {
                ClockPhase.BEFORE_KICKOFF -> stringResource(Res.string.console_before_kickoff)
                ClockPhase.PLAYING -> stringResource(Res.string.console_clock_written, written)
                ClockPhase.FIRST_HALF_ADDED_TIME -> stringResource(Res.string.console_clock_added_time, written)
                ClockPhase.INTERVAL -> stringResource(Res.string.console_clock_interval, written)
                ClockPhase.FINISHED -> stringResource(Res.string.console_clock_finished, written)
            },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** `12:40`, with the minutes as long as they are: a second half in added time reads `62:05`. */
private fun Duration.asMinutesAndSeconds(): String {
    val total = inWholeSeconds
    val minutes = (total / SECONDS_IN_A_MINUTE).toString().padStart(SECONDS_PAD, '0')
    return "$minutes:${(total % SECONDS_IN_A_MINUTE).toString().padStart(SECONDS_PAD, '0')}"
}

/**
 * The power play, in minutes and seconds, because ten minutes is short
 * enough that the seconds matter to the side playing short.
 */
private fun Duration.asCountdown(): String {
    val total = inWholeSeconds
    return "${total / SECONDS_IN_A_MINUTE}:${(total % SECONDS_IN_A_MINUTE).toString().padStart(SECONDS_PAD, '0')}"
}

private const val SECONDS_IN_A_MINUTE = 60
