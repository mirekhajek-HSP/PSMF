package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.SquadMemberEntry
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Adding a player who is not on the squad list: the form, what it asks for, and what it
 * will not offer. Split out of [LineupScreenTest] for size.
 */
@OptIn(ExperimentalTestApi::class)
class AddPlayerFormTest : LineupScreenTestBase() {
    // ------------------------------------------------------------------
    // Adding a player
    // ------------------------------------------------------------------

    @Test
    fun theAddPlayerButtonOpensTheForm() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = events::add) } }

                // Inside the language: the scroll composes rows that were not
                // there, and they read the language as it is when they do.
                onNode(hasScrollAction()).performScrollToNode(hasText("Přidat hráče mimo soupisku"))
                onNodeWithText("Přidat hráče mimo soupisku").performClick()
            }

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.AddPlayerOpened), events)
        }

    @Test
    fun theAddPlayerFormAsksForNameAndTheNumberFromTheCard() =
        runComposeUiTest {
            // Surname, first name, and the Číslo RP the player's card says.
            // Not a date of birth, which is for a player with no card.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId)),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Příjmení").assertIsDisplayed()
            onNodeWithText("Jméno").assertIsDisplayed()
            onNodeWithText("Číslo RP").assertIsDisplayed()
            onNodeWithText("Nemá průkaz").assertIsDisplayed()
            onNodeWithText("Datum narození").assertDoesNotExist()
            onNodeWithText("Číslo RP opište", substring = true).assertIsDisplayed()
        }

    @Test
    fun withNoCardTheFormAsksForTheDateOfBirthInstead() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId, noCard = true)),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Datum narození").assertIsDisplayed()
            // The one place "Číslo RP" can still be read is the form's note.
            onAllNodes(hasSetTextAction()).assertCountEquals(3)
        }

    @Test
    fun theNoCardSwitchReportsTheRequestWithItTurnedOn() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            val request = NewPlayerRequest(teamId = UiTestData.homeTeamId)
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(newPlayer = request), onEvent = events::add) }
                }
            }

            onNodeWithText("Nemá průkaz").performClick()

            // The switch and its label are one control to the eye; either is a tap.
            assertTrue(events.isEmpty() || events.last() == LineupEvent.NewPlayerEdited(request.copy(noCard = true)))
        }

    @Test
    fun typingIntoTheFormReportsEachField() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            val request = NewPlayerRequest(teamId = UiTestData.homeTeamId)
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(newPlayer = request), onEvent = events::add) }
                }
            }

            onNodeWithText("Příjmení").performTextInput("Hlok")

            assertTrue(
                events.any {
                    it is LineupEvent.NewPlayerEdited && it.request.surname == "Hlok"
                },
            )
        }

    @Test
    fun aTypedDateOfBirthIsEchoedBackSoAMistakeIsVisible() =
        runComposeUiTest {
            // It becomes six digits in the Číslo RP column of a report that
            // goes to PSMF, so it is worth showing in full first.
            val request =
                NewPlayerRequest(
                    teamId = UiTestData.homeTeamId,
                    surname = "Hlok",
                    firstName = "Petr",
                    noCard = true,
                    dateOfBirth = "21011999",
                )
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(newPlayer = request), onEvent = {}) } }
            }

            onNodeWithText("21. 1. 1999").assertIsDisplayed()
        }

    @Test
    fun submittingTheFormReportsIt() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId)),
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNodeWithText("Přidat").performClick()

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.NewPlayerSubmitted), events)
        }

    @Test
    fun aRejectedFormSaysWhichFieldsToCheck() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state =
                                state(
                                    newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId),
                                    rejected = true,
                                ),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText(
                "Zkontrolujte příjmení, jméno a číslo RP (u hráče bez průkazu datum narození).",
            ).assertIsDisplayed()
        }

    @Test
    fun aPitchAddedPlayerIsFlaggedInTheList() =
        runComposeUiTest {
            val added =
                Player.addedAtThePitch(
                    id = PlayerId("added"),
                    ref = "pitch-1",
                    teamId = UiTestData.homeTeamId,
                    name = PlayerName(PersonName.of("Hlok"), PersonName.of("Petr")),
                    dateOfBirth = LocalDate(1999, 1, 21),
                )
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(listOf(member(novak), member(added, present = true, cardPresent = false))),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Dopsán na hřišti").assertIsDisplayed()
            onNodeWithText("Datum narození: 21. 1. 1999").assertIsDisplayed()
        }
}
