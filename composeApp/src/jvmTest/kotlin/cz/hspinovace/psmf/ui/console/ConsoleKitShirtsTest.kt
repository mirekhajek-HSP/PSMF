package cz.hspinovace.psmf.ui.console

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.common.KitColours
import cz.hspinovace.psmf.ui.common.ShirtImage
import cz.hspinovace.psmf.ui.common.isNear
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleTeam
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * Each team's kit colours under its name on the scoreboard and on its tab (0.3.1, Gate 5), so
 * that the referee need not remember which colour is which team.
 *
 * **The kit worn in this match** is what the console is given (`ConsoleKitTest` proves it is
 * the lineup's and not the team's first); what is pinned here is that it reaches all four
 * places, each side's own, in its own colours, under the name and on the tab.
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleKitShirtsTest {
    private val homeKit = Kit(KitId("k-home-2"), "bílo-modrá", listOf("bílá", "modrá"))
    private val awayKit = Kit(KitId("k-away-1"), "černo-bílá", listOf("černá", "bílá"))
    private val unknownKit = Kit(KitId("k-unknown"), "tyrkysová", listOf("tyrkysová"))

    private fun state(
        home: Kit?,
        away: Kit?,
    ) = ConsoleUiState(
        loading = false,
        entry =
            ConsoleEntry(
                home = ConsoleTeam(TeamSide.HOME, "Kominíci", emptyList(), home),
                away = ConsoleTeam(TeamSide.AWAY, "Sp. Sumýš", emptyList(), away),
                score = Score(0, 0),
                kickoffAt = KICKOFF,
                status = MatchStatus.IN_PROGRESS,
                log = emptyList(),
                powerPlays = emptyList(),
            ),
    )

    @Test
    fun theScoreboardAndTheTabShowTheKitEachSideWearsInItsColours() = theShirtsAreThere("cs")

    @Test
    fun theyAreThereInUkrainianToo() = theShirtsAreThere("uk")

    private fun theShirtsAreThere(language: String) =
        runDesktopComposeUiTest(width = WIDTH_PX, height = HEIGHT_PX) {
            withLanguage(language) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme(darkTheme = false) {
                            ConsoleScreen(state(homeKit, awayKit), KICKOFF + 20.minutes, onEvent = {})
                        }
                    }
                }

                // Document order: the scoreboard's first, the tab's second.
                val home = onAllNodes(hasContentDescription(homeKit.label), useUnmergedTree = true)
                val away = onAllNodes(hasContentDescription(awayKit.label), useUnmergedTree = true)
                home.assertCountEquals(2)
                away.assertCountEquals(2)
                val names = { name: String -> onAllNodesWithText(name, useUnmergedTree = true) }

                listOf(home to homeKit, away to awayKit).forEach { (shirts, kit) ->
                    listOf("scoreboard", "tab").forEachIndexed { place, where ->
                        val drawn = ShirtImage(shirts[place].captureToImage().toPixelMap()).bands(2)
                        val wanted = kit.colours.map { checkNotNull(KitColours.of(it)) }
                        assertTrue(
                            drawn.zip(wanted).all { (seen, want) -> seen.isNear(want) },
                            "${kit.label} on the $where: drawn $drawn, wanted $wanted",
                        )
                    }
                }

                // Under the name on the scoreboard, at the edge the name is aligned to.
                val homeName = names("Kominíci")[0].getBoundsInRoot()
                val awayName = names("Sp. Sumýš")[0].getBoundsInRoot()
                val homeShirt = home[0].getBoundsInRoot()
                val awayShirt = away[0].getBoundsInRoot()
                assertTrue(homeShirt.top >= homeName.bottom, "the home shirt is not under its name")
                assertTrue(awayShirt.top >= awayName.bottom, "the away shirt is not under its name")
                assertTrue(homeShirt.left.near(homeName.left), "the home shirt is not at the start of its name")
                assertTrue(awayShirt.right.near(awayName.right), "the away shirt is not at the end of its name")
                assertTrue(homeShirt.right < awayShirt.left, "the shirts are in the wrong order")

                // On each tab, before the name, in the tab's own half.
                val homeTab = home[1].getBoundsInRoot()
                val awayTab = away[1].getBoundsInRoot()
                assertTrue(homeTab.right <= names("Kominíci")[1].getBoundsInRoot().left, "the home tab's shirt is late")
                assertTrue(awayTab.right <= names("Sp. Sumýš")[1].getBoundsInRoot().left, "the away shirt is late")
                assertTrue(homeTab.left < awayTab.left, "the tabs are the wrong way round")
            }
        }

    @Test
    fun aKitWithNoKnownColourDrawsNothingAndTheScreenIsAsItWas() =
        runDesktopComposeUiTest(width = WIDTH_PX, height = HEIGHT_PX) {
            withLanguage("cs") {
                var shown by mutableStateOf(state(home = null, away = null))
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme(darkTheme = false) { ConsoleScreen(shown, KICKOFF + 20.minutes, onEvent = {}) }
                    }
                }

                fun tabsTop(): Dp = onAllNodesWithText("Kominíci", useUnmergedTree = true)[1].getBoundsInRoot().top

                val withoutAKit = tabsTop()

                shown = state(unknownKit, unknownKit)
                waitForIdle()
                onAllNodes(hasContentDescription(unknownKit.label), useUnmergedTree = true).assertCountEquals(0)
                assertEquals(withoutAKit, tabsTop(), "a kit with no colour changed the layout")

                shown = state(homeKit, awayKit)
                waitForIdle()
                assertTrue(tabsTop() > withoutAKit, "the scoreboard did not make room for the shirts")
            }
        }

    private fun Dp.near(other: Dp) = abs((this - other).value) <= TOLERANCE_DP

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 1400
        const val TOLERANCE_DP = 1.0f
    }
}
