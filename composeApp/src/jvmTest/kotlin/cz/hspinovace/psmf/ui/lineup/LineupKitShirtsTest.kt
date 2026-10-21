package cz.hspinovace.psmf.ui.lineup

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
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.common.KitColours
import cz.hspinovace.psmf.ui.common.ShirtImage
import cz.hspinovace.psmf.ui.common.isNear
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The kit's colours on each team's tab in *Sestavy* (0.3.1, Gate 5): the kit the referee has
 * picked for that team, so choosing the other chip changes the shirt.
 *
 * Both teams here own two kits, and the tests have them wear the second, so that a tab that
 * showed a team's first kit, or the other team's, could not pass.
 */
@OptIn(ExperimentalTestApi::class)
class LineupKitShirtsTest {
    private val homeFirst = Kit(KitId("h1"), "modrá", listOf("modrá"))
    private val homeSecond = Kit(KitId("h2"), "bílo-modrá", listOf("bílá", "modrá"))
    private val awayFirst = Kit(KitId("a1"), "černá", listOf("černá"))
    private val awaySecond = Kit(KitId("a2"), "černo-bílá", listOf("černá", "bílá"))
    private val unknown = Kit(KitId("u"), "tyrkysová", listOf("tyrkysová"))

    private val home =
        Team(UiTestData.homeTeamId, "kominici", UiTestData.groupId, "Kominíci", listOf(homeFirst, homeSecond, unknown))
    private val away =
        Team(UiTestData.awayTeamId, "sp-sumys", UiTestData.groupId, "Sp. Sumýš", listOf(awayFirst, awaySecond))

    private fun state(
        homeWears: Kit,
        awayWears: Kit,
    ) = LineupUiState(
        loading = false,
        entry =
            LineupEntry(
                TeamLineupEntry(TeamSide.HOME, home, emptyList(), homeWears.id),
                TeamLineupEntry(TeamSide.AWAY, away, emptyList(), awayWears.id),
            ),
    )

    @Test
    fun eachTabShowsTheKitItsTeamWearsInItsColoursBeforeItsName() =
        runDesktopComposeUiTest(width = WIDTH_PX, height = HEIGHT_PX) {
            withLanguage("cs") {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme(darkTheme = false) {
                            LineupScreen(state(homeSecond, awaySecond), onEvent = {})
                        }
                    }
                }

                // Not the first kits, and nothing else on the screen is described as a kit.
                onAllNodes(hasContentDescription(homeFirst.label), useUnmergedTree = true).assertCountEquals(0)
                onAllNodes(hasContentDescription(awayFirst.label), useUnmergedTree = true).assertCountEquals(0)
                val homeShirt = onAllNodes(hasContentDescription(homeSecond.label), useUnmergedTree = true)
                val awayShirt = onAllNodes(hasContentDescription(awaySecond.label), useUnmergedTree = true)
                homeShirt.assertCountEquals(1)
                awayShirt.assertCountEquals(1)

                listOf(homeShirt[0] to homeSecond, awayShirt[0] to awaySecond).forEach { (shirt, kit) ->
                    val drawn = ShirtImage(shirt.captureToImage().toPixelMap()).bands(2)
                    val wanted = kit.colours.map { checkNotNull(KitColours.of(it)) }
                    val right = drawn.zip(wanted).all { (seen, want) -> seen.isNear(want) }
                    assertTrue(right, "${kit.label}: drawn $drawn, wanted $wanted")
                }

                val homeName = onAllNodesWithText("Kominíci", useUnmergedTree = true)[0].getBoundsInRoot()
                val awayName = onAllNodesWithText("Sp. Sumýš", useUnmergedTree = true)[0].getBoundsInRoot()
                val homeBounds = homeShirt[0].getBoundsInRoot()
                val awayBounds = awayShirt[0].getBoundsInRoot()
                assertTrue(homeBounds.right <= homeName.left, "the home shirt is not before its name")
                assertTrue(awayBounds.right <= awayName.left, "the away shirt is not before its name")
                assertTrue(homeBounds.left < awayBounds.left, "tabs reversed")
            }
        }

    @Test
    fun pickingTheOtherKitChangesTheShirt() =
        runDesktopComposeUiTest(width = WIDTH_PX, height = HEIGHT_PX) {
            withLanguage("cs") {
                var shown by mutableStateOf(state(homeFirst, awayFirst))
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme(darkTheme = false) { LineupScreen(shown, onEvent = {}) }
                    }
                }
                onAllNodes(hasContentDescription(homeFirst.label), useUnmergedTree = true).assertCountEquals(1)
                onAllNodes(hasContentDescription(homeSecond.label), useUnmergedTree = true).assertCountEquals(0)

                shown = state(homeSecond, awayFirst)
                waitForIdle()

                onAllNodes(hasContentDescription(homeFirst.label), useUnmergedTree = true).assertCountEquals(0)
                onAllNodes(hasContentDescription(homeSecond.label), useUnmergedTree = true).assertCountEquals(1)
                onAllNodes(hasContentDescription(awayFirst.label), useUnmergedTree = true).assertCountEquals(1)
            }
        }

    @Test
    fun aKitWithNoKnownColourLeavesItsTabAsItWas() =
        runDesktopComposeUiTest(width = WIDTH_PX, height = HEIGHT_PX) {
            withLanguage("cs") {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                        PsmfTheme(darkTheme = false) { LineupScreen(state(unknown, awaySecond), onEvent = {}) }
                    }
                }

                onAllNodes(hasContentDescription(unknown.label), useUnmergedTree = true).assertCountEquals(0)
                onAllNodes(hasContentDescription(awaySecond.label), useUnmergedTree = true).assertCountEquals(1)
            }
        }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 1400
    }
}
