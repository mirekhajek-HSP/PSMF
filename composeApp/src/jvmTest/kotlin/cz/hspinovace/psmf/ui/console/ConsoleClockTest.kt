package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleTeam
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * **What the screen shows at each boundary** (prompt 13, part 3).
 *
 * Two lines, which are not the same thing: *play so far* as `mm:ss`, and
 * *the minute the next event would be written with*, as the form has it. The
 * second stays on screen at every boundary -- above all in the first half's
 * added time and in the interval, where it is `30´+` while the clock reads
 * 31:12 or holds there. The log and the report keep the form's minutes.
 *
 * The rule for which minute a moment maps to is `ClockReadingTest`'s.
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleClockTest {
    private val firstHalfOver = PeriodBreak(endedAt = KICKOFF + 31.minutes + 12.seconds)
    private val secondHalfStarted = firstHalfOver.copy(nextStartedAt = KICKOFF + 45.minutes)

    private fun entry(
        kickoffAt: Instant? = KICKOFF,
        status: MatchStatus = MatchStatus.IN_PROGRESS,
        periodBreaks: List<PeriodBreak> = emptyList(),
        finishedAt: Instant? = null,
    ) = ConsoleEntry(
        home = ConsoleTeam(TeamSide.HOME, UiTestData.homeTeam.name, emptyList()),
        away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
        score = Score.GOALLESS,
        kickoffAt = kickoffAt,
        status = status,
        log = emptyList(),
        powerPlays = emptyList(),
        periodBreaks = periodBreaks,
        finishedAt = finishedAt,
    )

    /** Draws the console at [now] and checks that each of [texts] is on it. */
    private fun drawnAt(
        entry: ConsoleEntry,
        now: Instant,
        vararg texts: String,
    ) = runComposeUiTest {
        withLanguage("cs") {
            setContent {
                PsmfTheme {
                    ConsoleScreen(
                        state = ConsoleUiState(loading = false, entry = entry),
                        now = now,
                        onEvent = {},
                    )
                }
            }
            texts.forEach { onNodeWithText(it).assertIsDisplayed() }
        }
    }

    @Test
    fun theThreeControlsOfTheFirstHalfStayReadableOnANarrowPhone() =
        runComposeUiTest {
            // Found by drawing the console at 360 dp: *Undo*, *End the half* and
            // *End the match* did not fit on one row, and the last was squeezed
            // into a strip a letter wide. The row wraps instead.
            withLanguage("cs") {
                setContent {
                    Box(modifier = Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                        PsmfTheme {
                            ConsoleScreen(
                                ConsoleUiState(loading = false, entry = entry()),
                                now = KICKOFF + 12.minutes,
                                onEvent = {},
                            )
                        }
                    }
                }

                listOf("Ukončit poločas", "Ukončit utkání", "Vzít zpět").forEach { label ->
                    val bounds = onNodeWithText(label).getBoundsInRoot()
                    assertTrue(bounds.width >= 80.dp, "$label is ${bounds.width} wide: squeezed")
                    assertTrue(bounds.height <= 60.dp, "$label is ${bounds.height} tall: wrapped letter by letter")
                }
            }
        }

    @Test
    fun beforeKickoffThereIsNoTimeOnTheScreenAtAll() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(kickoffAt = null, status = MatchStatus.SETUP),
                            ),
                            now = KICKOFF,
                            onEvent = {},
                        )
                    }
                }
                onNodeWithText("Před výkopem").assertIsDisplayed()
                // Only the score has a colon on it.
                onAllNodes(hasText(":", substring = true)).assertCountEquals(1)
            }
        }

    @Test
    fun whileTheFirstHalfRunsItReadsMinutesAndSecondsAndTheMinuteBeingWritten() =
        drawnAt(entry(), KICKOFF + 12.minutes + 40.seconds, "12:40", "Zápis: 12´")

    @Test
    fun atThirtyMinutesTheFirstHalfIsInAddedTimeAndTheMinuteWrittenIsThirtyPlus() =
        drawnAt(entry(), KICKOFF + 30.minutes, "30:00", "Nastavení 1. poločasu · zápis 30´+")

    @Test
    fun inTheFirstHalfsAddedTimeTheClockGoesOnAndTheMinuteStaysThirtyPlus() =
        drawnAt(entry(), KICKOFF + 31.minutes + 12.seconds, "31:12", "Nastavení 1. poločasu · zápis 30´+")

    @Test
    fun inTheIntervalTheClockHoldsWhereTheHalfEndedAndTheMinuteIsStillThirtyPlus() =
        drawnAt(
            entry(periodBreaks = listOf(firstHalfOver)),
            KICKOFF + 40.minutes,
            "31:12",
            "Poločas · zápis 30´+",
        )

    @Test
    fun theSecondHalfStartsAtThirtyColonZeroZeroWhateverTheFirstAdded() =
        drawnAt(
            entry(periodBreaks = listOf(secondHalfStarted)),
            KICKOFF + 45.minutes,
            "30:00",
            "Zápis: 30´",
        )

    @Test
    fun midSecondHalfItReadsOnFromThirty() =
        drawnAt(
            entry(periodBreaks = listOf(secondHalfStarted)),
            KICKOFF + 45.minutes + 17.minutes + 5.seconds,
            "47:05",
            "Zápis: 47´",
        )

    @Test
    fun theSecondHalfsAddedTimeReadsPastSixtyAndIsWrittenAsAPlayedMinute() =
        drawnAt(
            entry(periodBreaks = listOf(secondHalfStarted)),
            KICKOFF + 45.minutes + 31.minutes + 5.seconds,
            "61:05",
            "Zápis: 61´",
        )

    @Test
    fun afterTheFinalWhistleTheClockIsFrozenAtTheWhistleAndTheMinuteIsSixtyPlus() =
        drawnAt(
            entry(
                status = MatchStatus.FINISHED,
                periodBreaks = listOf(secondHalfStarted),
                finishedAt = KICKOFF + 45.minutes + 31.minutes + 7.seconds,
            ),
            // Written an hour later: it still says where the whistle was.
            KICKOFF + 3.minutes * 50,
            "61:07",
            "Konec utkání · zápis 60´+",
        )

    @Test
    fun aMatchFinishedBeforeTheWhistleWasRecordedShowsNoTimeAfterItJustTheMinute() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(status = MatchStatus.FINISHED, periodBreaks = listOf(secondHalfStarted)),
                            ),
                            now = KICKOFF + 100.minutes,
                            onEvent = {},
                        )
                    }
                }
                onNodeWithText("Konec utkání · zápis 60´+").assertIsDisplayed()
                onAllNodes(hasText(":", substring = true)).assertCountEquals(1)
            }
        }
}
