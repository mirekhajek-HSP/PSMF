package cz.hspinovace.psmf.ui.lineup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the captain's section says while the team's block is not whole (prompt 17, Gate 3):
 * the problems, by the button, from the start -- a button that cannot be pressed yet has to
 * say why before it is pressed, not only after.
 *
 * The check itself, and that the tap records nothing, is [CaptainsTapChecksTheTeamTest].
 */
@OptIn(ExperimentalTestApi::class)
class CaptainSectionProblemsTest : LineupScreenTestBase() {
    @Test
    fun aCaptainAndNoGoalkeeperListsTheMissingGoalkeeperByTheButton() =
        runComposeUiTest {
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(members, captain = novak.id), onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Potvrzuje Novák Jan").assertIsDisplayed()
                onNodeWithText("Potvrdit jde, až bude sestava úplná:").assertIsDisplayed()
                onNodeWithText("Kominíci: není vybraný brankář.").assertIsDisplayed()
            }
        }

    @Test
    fun aRefusedTapTurnsTheListToItsOwnHeading() =
        runComposeUiTest {
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            val refused = state(members, captain = novak.id).copy(confirmRefused = setOf(TeamSide.HOME))
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = refused, onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Potvrdit zatím nejde:").assertIsDisplayed()
                onNodeWithText("Kominíci: není vybraný brankář.").assertIsDisplayed()
                onNodeWithText("Potvrdit jde, až bude sestava úplná:").assertDoesNotExist()
            }
        }

    @Test
    fun aPresentPlayerWithNothingInCisloRpIsNamedByTheButton() =
        runComposeUiTest {
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(members, captain = novak.id, goalkeeper = novak.id), onEvent = {})
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Poupě Petr: chybí údaj do sloupce Číslo RP", substring = true).assertIsDisplayed()
            }
        }

    @Test
    fun aSharedShirtNumberIsNamedByTheButton() =
        runComposeUiTest {
            val members =
                listOf(
                    member(novak, present = true, card = "59001"),
                    member(poupe, present = true, card = "59002").copy(jerseyNumber = novak.defaultJerseyNumber),
                )
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(members, captain = novak.id, goalkeeper = poupe.id), onEvent = {})
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Dres č. 9 nosí víc hráčů.").assertIsDisplayed()
            }
        }

    @Test
    fun aWholeTeamHasNothingListedByTheButton() =
        runComposeUiTest {
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(members, captain = novak.id, goalkeeper = poupe.id), onEvent = {})
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Potvrdit jde, až bude sestava úplná:").assertDoesNotExist()
                onNodeWithText("Potvrdit zatím nejde:").assertDoesNotExist()
            }
        }

    @Test
    fun theOtherTeamsProblemsAreNotListedThere() =
        runComposeUiTest {
            // Home is missing its goalkeeper; away, shown on the other tab, has nobody present
            // and so a problem of its own. Only home's is the captain's business here.
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(members, captain = novak.id), onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Kominíci: není vybraný brankář.").assertIsDisplayed()
                // (The tab still carries the team's name: it is the line that must not be there.)
                onNodeWithText("Sp. Sumýš: není označen nikdo přítomný.").assertDoesNotExist()
            }
        }

    @Test
    fun theTapIsAlwaysPassedOnSoTheScreenCanSayWhyItWasRefused() =
        runComposeUiTest {
            // The button is never a dead, grey control: it is pressed, and the view model answers.
            val events = mutableListOf<LineupEvent>()
            val members = listOf(member(novak, present = true, card = "59001"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(members, captain = novak.id), onEvent = events::add) }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Novák Jan"))
                onNodeWithText("Potvrzuje Novák Jan").performClick()
            }

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.CaptainConfirms), events)
        }
}
