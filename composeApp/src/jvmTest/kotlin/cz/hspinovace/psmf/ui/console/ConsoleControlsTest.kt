package cz.hspinovace.psmf.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.breaksAfterAnOrdinal
import cz.hspinovace.psmf.ui.breaksInsideAWord
import cz.hspinovace.psmf.ui.textLayout
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * The console's controls, laid out on purpose (0.3.1, Gate 4; the testers' point 5:
 * "the buttons sit to the right, below or beside each other").
 *
 * Every state the match has is drawn at the two widths the phones have (360 and
 * 411 dp), in Czech and in Ukrainian, at the standard font and at 130 %, and the
 * same rules are asked of each:
 *
 * - the whistles (the buttons that move the match on) are in one row as wide as
 *   the list's own buttons below it; where two sit together they are the same
 *   width and the same height, and where one sits alone it has the whole width;
 * - no label is broken in the middle of a word;
 * - Undo is on a line of its own under them, apart, and in the same place in
 *   every state; and it is quieter: no outline, no fill.
 *
 * Measured once, in [layouts]: 56 renders, and the rules are asserted from that.
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleControlsTest {
    @Test
    fun whistlesThatSitTogetherAreTheSameSizeAndFillTheWidthOfTheList() {
        val problems = mutableListOf<String>()
        layouts.forEach { (combo, shown) ->
            val first = shown.whistles.first()
            val last = shown.whistles.last()
            if (!shown.list.width.near(combo.width - PsmfDimens.screenPadding * 2)) {
                problems += "$combo: the harness is not ${combo.width}: the list's button is ${shown.list.width}"
            }
            if (!first.left.near(shown.list.left) || !last.right.near(shown.list.right)) {
                val run = "${first.left}..${last.right}"
                val listRun = "${shown.list.left}..${shown.list.right}"
                problems += "$combo: the whistles run $run, the list $listRun"
            }
            if (shown.whistles.zipWithNext().any { (a, b) -> !a.width.near(b.width) }) {
                problems += "$combo: widths ${shown.whistles.map { it.width }}"
            }
            if (shown.whistles.zipWithNext().any { (a, b) -> !a.height.near(b.height) || !a.top.near(b.top) }) {
                problems += "$combo: heights ${shown.whistles.map { it.height }}, tops ${shown.whistles.map { it.top }}"
            }
            shown.whistles.zipWithNext().forEach { (a, b) ->
                if (!(b.left - a.right).near(PsmfDimens.itemSpacing)) problems += "$combo: gap ${b.left - a.right}"
            }
            if (shown.whistles.any { it.height < PsmfDimens.minTouchTarget - 1.dp }) {
                problems += "$combo: a whistle is only ${shown.whistles.minOf { it.height.value }} dp tall"
            }
        }
        assertNone(problems)
    }

    @Test
    fun noLabelIsBrokenInTheMiddleOfAWord() {
        val problems =
            layouts.mapNotNull { (combo, shown) ->
                shown.broken.takeIf { it.isNotEmpty() }?.let { "$combo: $it" }
            }
        assertNone(problems)
    }

    @Test
    fun theNumberOfAPeriodIsNeverLeftAloneAtTheEndOfALine() {
        // "Zahájit 2." over "poločas": a break between two words, and still the wrong one.
        val problems =
            layouts.mapNotNull { (combo, shown) ->
                shown.splitAfterAnOrdinal.takeIf { it.isNotEmpty() }?.let { "$combo: $it" }
            }
        assertNone(problems)
    }

    @Test
    fun undoHasALineOfItsOwnUnderTheWhistlesAndStaysWhereItIsInEveryState() {
        val problems = mutableListOf<String>()
        layouts.forEach { (combo, shown) ->
            val whistlesBottom = shown.whistles.maxOf { it.bottom }
            if (shown.undo.top < whistlesBottom + PsmfDimens.itemSpacing - 1.dp) {
                problems += "$combo: Undo starts at ${shown.undo.top}, the whistles end at $whistlesBottom"
            }
            if (shown.undo.height < PsmfDimens.minTouchTarget - 1.dp) {
                problems += "$combo: Undo is only ${shown.undo.height} tall"
            }
            if (!shown.undo.left.near(shown.list.left)) {
                problems += "$combo: Undo's left edge is ${shown.undo.left}, the list's ${shown.list.left}"
            }
            // The word itself starts where the whistles' edge is, not a padding in from it.
            if (!shown.undoWord.left.near(shown.list.left)) {
                problems +=
                    "$combo: Undo's word starts at ${shown.undoWord.left}, the list's edge is ${shown.list.left}"
            }
            // Only as wide as its word, not a target as wide as the list: a stray thumb on a
            // full-width Undo takes back a goal. (The Ukrainian word at 130 % is 62 % of 360 dp.)
            if (shown.undo.width > shown.list.width * UNDO_AT_MOST) {
                problems += "$combo: Undo is ${shown.undo.width} wide of ${shown.list.width}"
            }
        }
        assertNone(problems)
    }

    @Test
    fun undoHasNeitherOutlineNorFillWhereTheWhistlesHaveOne() =
        runDesktopComposeUiTest(width = PIXELS_AT_360, height = 1700) {
            withLanguage("cs") {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme { ConsoleScreen(state(Phase.FIRST_HALF), Phase.FIRST_HALF.now, {}) }
                    }
                }

                assertFalse(hasVisibleEdge("Vzít zpět"), "Undo is drawn like a whistle")
                assertTrue(hasVisibleEdge("Ukončit poločas"), "a whistle has no outline")
                assertTrue(hasVisibleEdge("Ukončit utkání"), "a whistle has no outline")
            }
        }

    @Test
    fun everyStateHasTheWhistlesItShouldAndNoOtherInEitherLanguage() {
        // Guards the table the tests above rely on: a label that is not on screen
        // would make them measure nothing.
        assertEquals(7 * 2 * 2 * 2, layouts.size)
        layouts.forEach { (combo, shown) ->
            assertEquals(combo.labels.size, shown.whistles.size, "$combo")
        }
    }

    // ------------------------------------------------------------------

    private fun ComposeUiTest.hasVisibleEdge(text: String): Boolean {
        val image = onNodeWithText(text).captureToImage().toPixelMap()
        val background = image[0, 0]
        val middle = image.height / 2
        return (0 until EDGE_PIXELS).any { image[it, middle] != background }
    }

    private fun assertNone(problems: List<String>) {
        assertTrue(
            problems.isEmpty(),
            "${problems.size} problems:\n" + problems.take(8).joinToString("\n") +
                if (problems.size > 8) "\n..." else "",
        )
    }

    private fun Dp.near(other: Dp) = abs((this - other).value) <= TOLERANCE_DP

    // ------------------------------------------------------------------

    private enum class Phase(
        val now: Instant,
        val kickedOff: Boolean,
        val status: MatchStatus,
        val periodBreaks: List<PeriodBreak>,
        val cs: List<String>,
        val uk: List<String>,
    ) {
        BEFORE_KICKOFF(
            KICKOFF,
            false,
            MatchStatus.SETUP,
            emptyList(),
            listOf("Zahájit utkání"),
            listOf("Розпочати матч"),
        ),
        FIRST_HALF(
            KICKOFF + 20.minutes,
            true,
            MatchStatus.IN_PROGRESS,
            emptyList(),
            listOf("Ukončit poločas", "Ukončit utkání"),
            listOf("Завершити тайм", "Завершити матч"),
        ),
        ADDED_TIME(
            KICKOFF + 31.minutes,
            true,
            MatchStatus.IN_PROGRESS,
            emptyList(),
            listOf("Ukončit poločas", "Ukončit utkání"),
            listOf("Завершити тайм", "Завершити матч"),
        ),
        INTERVAL(
            KICKOFF + 34.minutes,
            true,
            MatchStatus.IN_PROGRESS,
            listOf(PeriodBreak(endedAt = KICKOFF + 31.minutes)),
            listOf("Zahájit 2.\u00A0poločas", "Ukončit utkání"),
            listOf("Розпочати 2.\u00A0тайм", "Завершити матч"),
        ),
        SECOND_HALF(
            KICKOFF + 50.minutes,
            true,
            MatchStatus.IN_PROGRESS,
            listOf(PeriodBreak(endedAt = KICKOFF + 31.minutes, nextStartedAt = KICKOFF + 33.minutes)),
            listOf("Ukončit utkání"),
            listOf("Завершити матч"),
        ),
        AFTER_THE_FINAL_WHISTLE(
            KICKOFF + 65.minutes,
            true,
            MatchStatus.FINISHED,
            listOf(PeriodBreak(endedAt = KICKOFF + 31.minutes, nextStartedAt = KICKOFF + 33.minutes)),
            listOf("Pokračovat na hodnocení"),
            listOf("Далі до оцінювання"),
        ),
        CONFIRMED(
            KICKOFF + 65.minutes,
            true,
            MatchStatus.CONFIRMED,
            listOf(PeriodBreak(endedAt = KICKOFF + 31.minutes, nextStartedAt = KICKOFF + 33.minutes)),
            listOf("Pokračovat na hodnocení"),
            listOf("Далі до оцінювання"),
        ),
    }

    private data class Combo(
        val phase: Phase,
        val language: String,
        val width: Dp,
        val fontScale: Float,
    ) {
        val labels get() = if (language == "cs") phase.cs else phase.uk
        val undo get() = if (language == "cs") "Vzít zpět" else "Скасувати останнє"
        val listButton get() = if (language == "cs") "Gól s jinou minutou" else "Гол з іншою хвилиною"

        override fun toString() = "${phase.name} $language ${width.value.toInt()}dp x$fontScale"
    }

    private class Shown(
        val whistles: List<DpRect>,
        val undo: DpRect,
        val undoWord: DpRect,
        val list: DpRect,
        val broken: List<String>,
        val splitAfterAnOrdinal: List<String>,
    )

    private companion object {
        const val DENSITY = 2.625f
        const val PIXELS_AT_360 = 945
        const val TOLERANCE_DP = 1f
        const val EDGE_PIXELS = 3
        const val UNDO_AT_MOST = 0.75f

        val layouts: Map<Combo, Shown> by lazy {
            buildMap {
                for (phase in Phase.entries) {
                    for (language in listOf("cs", "uk")) {
                        for (width in listOf(360.dp, 411.dp)) {
                            for (fontScale in listOf(1f, 1.3f)) {
                                val combo = Combo(phase, language, width, fontScale)
                                put(combo, measure(combo))
                            }
                        }
                    }
                }
            }
        }

        fun measure(combo: Combo): Shown {
            lateinit var shown: Shown
            runComposeUiTest {
                withLanguage(combo.language) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(DENSITY, combo.fontScale)) {
                            // Tall enough to lay everything out: the host's window shows a part.
                            Box(modifier = Modifier.requiredSize(width = combo.width, height = 1600.dp)) {
                                PsmfTheme { ConsoleScreen(state(combo.phase), combo.phase.now, {}) }
                            }
                        }
                    }
                    shown =
                        Shown(
                            whistles = combo.labels.map { onNodeWithText(it).getBoundsInRoot() },
                            undo = onNodeWithText(combo.undo).getBoundsInRoot(),
                            undoWord = onNodeWithText(combo.undo, useUnmergedTree = true).getBoundsInRoot(),
                            list = onNodeWithText(combo.listButton).getBoundsInRoot(),
                            broken =
                                (combo.labels + combo.undo).filter {
                                    onNodeWithText(it).textLayout().breaksInsideAWord()
                                },
                            splitAfterAnOrdinal =
                                combo.labels.filter { onNodeWithText(it).textLayout().breaksAfterAnOrdinal() },
                        )
                }
            }
            return shown
        }

        fun state(phase: Phase): ConsoleUiState {
            val players =
                listOf(
                    ConsoleRow(
                        AppearanceId("a-novak"),
                        JerseyNumber(9),
                        PlayerName(PersonName.of("Novák"), PersonName.of("Jan")),
                        false,
                        0,
                    ),
                    ConsoleRow(
                        AppearanceId("a-poupe"),
                        JerseyNumber(11),
                        PlayerName(PersonName.of("Poupě"), PersonName.of("Petr")),
                        false,
                        0,
                    ),
                )
            return ConsoleUiState(
                loading = false,
                entry =
                    ConsoleEntry(
                        home = ConsoleTeam(TeamSide.HOME, UiTestData.homeTeam.name, players),
                        away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
                        score = Score.GOALLESS,
                        kickoffAt = if (phase.kickedOff) KICKOFF else null,
                        status = phase.status,
                        log = emptyList(),
                        powerPlays = emptyList(),
                        periodBreaks = phase.periodBreaks,
                    ),
            )
        }
    }
}
