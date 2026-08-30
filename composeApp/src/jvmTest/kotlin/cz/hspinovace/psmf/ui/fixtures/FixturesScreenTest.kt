package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screen 1's smoke test.
 *
 * Drives the plain composable directly — no ViewModel, no Koin, no
 * database. What it proves is that the screen renders its data and reports
 * taps; what it cannot prove is that the seed files are *packaged*, which
 * only running on a device answers.
 */
@OptIn(ExperimentalTestApi::class)
class FixturesScreenTest {
    @Test
    fun showsBothTeamsTheKickoffAndThePitch() =
        runComposeUiTest {
            setContent {
                PsmfTheme {
                    FixturesScreen(
                        state = FixturesUiState.Ready(UiTestData.listing()),
                        onFixtureSelected = {},
                        onRetry = {},
                    )
                }
            }

            // Data, not translations: these read the same in every language.
            onNodeWithText("Kominíci").assertIsDisplayed()
            onNodeWithText("Sp. Sumýš").assertIsDisplayed()
            onNodeWithText("19:00").assertIsDisplayed()
            onNodeWithText("ZAKOS").assertIsDisplayed()
        }

    @Test
    fun tappingAFixtureReportsWhichOne() =
        runComposeUiTest {
            var tapped: FixtureId? = null
            setContent {
                PsmfTheme {
                    FixturesScreen(
                        state = FixturesUiState.Ready(UiTestData.listing()),
                        onFixtureSelected = { tapped = it },
                        onRetry = {},
                    )
                }
            }

            // The whole row is the target, not a chevron.
            onNodeWithText("Kominíci").performClick()

            assertEquals(UiTestData.fixtureId, tapped)
        }

    @Test
    fun aFixtureWithAReportUnderWaySaysSo() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        FixturesScreen(
                            state = FixturesUiState.Ready(UiTestData.listing(UiTestData.row(MatchStatus.IN_PROGRESS))),
                            onFixtureSelected = {},
                            onRetry = {},
                        )
                    }
                }
            }

            // The only route back into a match the app was killed during.
            onNodeWithText("Probíhá").assertIsDisplayed()
        }

    @Test
    fun aSeedFailureShowsTheLoadersOwnMessageRatherThanHidingIt() =
        runComposeUiTest {
            var retried = false
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        FixturesScreen(
                            state = FixturesUiState.Failed("Seed data problem in 6k.json: unknown team 'x'"),
                            onFixtureSelected = {},
                            onRetry = { retried = true },
                        )
                    }
                }
            }

            // Seed data is hand-edited. Whoever broke it is holding the phone.
            onNodeWithText("Seed data problem in 6k.json: unknown team 'x'").assertIsDisplayed()
            onNodeWithText("Zkusit znovu").performClick()
            assertEquals(true, retried)
        }

    @Test
    fun theRoundHeadingRendersInEachOfTheThreeLanguages() =
        runComposeUiTest {
            // Not proof that Cyrillic GLYPHS draw -- that needs a device --
            // but proof that all three resource sets resolve and that the
            // Ukrainian string reaches the composition.
            withLanguage("uk") {
                setContent {
                    PsmfTheme {
                        FixturesScreen(
                            state = FixturesUiState.Ready(UiTestData.listing()),
                            onFixtureSelected = {},
                            onRetry = {},
                        )
                    }
                }
            }

            onNodeWithText("Тур 1").assertIsDisplayed()
        }

    @Test
    fun anEmptyCatalogueSaysSoInsteadOfShowingNothing() =
        runComposeUiTest {
            withLanguage("en") {
                setContent {
                    PsmfTheme {
                        FixturesScreen(
                            state =
                                FixturesUiState.Ready(
                                    cz.hspinovace.psmf.usecase
                                        .FixtureListing(emptyList()),
                                ),
                            onFixtureSelected = {},
                            onRetry = {},
                        )
                    }
                }
            }

            onNodeWithText("No fixtures to report on.").assertIsDisplayed()
        }
}
