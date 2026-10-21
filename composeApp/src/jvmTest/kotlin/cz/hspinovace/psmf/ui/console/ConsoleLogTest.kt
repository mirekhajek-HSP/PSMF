package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.GoalEvent
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LoggedWhistle
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * The log under the console's lists shows the whistles (0.3.1, Gate 4): the kickoff,
 * the end of the half, the start of the next and the final whistle, each in its
 * place among the goals and cards, in the words Undo's question uses. The order
 * itself is pinned in `ConsoleLogWhistlesTest`; this is that it reaches the
 * screen, and reads the same in Ukrainian.
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleLogTest {
    private val novak = AppearanceId("a-novak")

    private fun state(): ConsoleUiState {
        val breaks = listOf(PeriodBreak(endedAt = KICKOFF + 31.minutes, nextStartedAt = KICKOFF + 33.minutes))
        val row =
            ConsoleRow(novak, JerseyNumber(9), PlayerName(PersonName.of("Novák"), PersonName.of("Jan")), false, 0)
        return ConsoleUiState(
            loading = false,
            entry =
                ConsoleEntry(
                    home = ConsoleTeam(TeamSide.HOME, UiTestData.homeTeam.name, listOf(row)),
                    away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
                    score = Score(2, 0),
                    kickoffAt = KICKOFF,
                    status = MatchStatus.FINISHED,
                    // Newest first, as the entry has them.
                    log =
                        listOf(
                            GoalEvent(Minute.Played(35), TeamSide.HOME, novak, Score(2, 0), sequence = 5),
                            GoalEvent(Minute.Played(10), TeamSide.HOME, novak, Score(1, 0), sequence = 2),
                        ),
                    powerPlays = emptyList(),
                    periodBreaks = breaks,
                    whistles =
                        listOf(
                            LoggedWhistle(Whistle.KICKOFF, 1, Minute.Played(0), 1),
                            LoggedWhistle(Whistle.END_OF_PERIOD, 1, Minute.HalfTime, 3),
                            LoggedWhistle(Whistle.START_OF_PERIOD, 2, Minute.Played(30), 4),
                            LoggedWhistle(Whistle.FINAL, 2, Minute.AfterFinalWhistle, 6),
                        ),
                ),
        )
    }

    @Test
    fun theWhistlesAreInTheLogInTheirPlaceInCzech() = theLogReadsNewestFirst("cs", CZECH_WHISTLES)

    @Test
    fun theWhistlesAreInTheLogInTheirPlaceInUkrainian() = theLogReadsNewestFirst("uk", UKRAINIAN_WHISTLES)

    private fun theLogReadsNewestFirst(
        language: String,
        whistles: List<String>,
    ) = runComposeUiTest {
        withLanguage(language) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = 1f)) {
                    Box(modifier = Modifier.requiredSize(width = 360.dp, height = 2000.dp)) {
                        PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF + 65.minutes, onEvent = {}) }
                    }
                }
            }

            // Top to bottom: the final whistle, the late goal, the second half, the end of the first,
            // the early goal, the kickoff. (`whistles` is newest first, in that order.)
            val lines =
                listOf(
                    whistles[0],
                    "35´ · Novák Jan · 2:0",
                    whistles[1],
                    whistles[2],
                    "10´ · Novák Jan · 1:0",
                    whistles[3],
                )
            val tops = lines.map { onNodeWithText(it).getBoundsInRoot().top }

            assertEquals(tops.sorted(), tops, "the log is not newest first, or a whistle is not in its place: $lines")
            assertEquals(tops.distinct(), tops, "two lines are in the same place: $lines")
        }
    }

    private companion object {
        // Newest first: the final whistle, the start of the second half, the end of the first, the kickoff.
        val CZECH_WHISTLES =
            listOf("60´+ · Konec utkání", "30´ · Začátek 2. poločasu", "30´+ · Konec 1. poločasu", "0´ · Výkop")
        val UKRAINIAN_WHISTLES =
            listOf(
                "60´+ · Кінець матчу",
                "30´ · Початок 2-го тайму",
                "30´+ · Кінець 1-го тайму",
                "0´ · Стартовий свисток",
            )
    }
}
