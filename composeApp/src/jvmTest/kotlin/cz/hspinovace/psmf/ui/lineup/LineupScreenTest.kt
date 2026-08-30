package cz.hspinovace.psmf.ui.lineup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
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
import kotlin.test.assertTrue

/**
 * Screen 3's flows, driven through the plain composable.
 *
 * The two Gate 2 asks — marking absentees and adding a player — plus the
 * rules that must not be got wrong: no RP field anywhere, and a suspension
 * badge that warns without ever clearing anyone.
 */
@OptIn(ExperimentalTestApi::class)
class LineupScreenTest {
    private fun player(
        ref: String,
        surname: String,
        first: String,
        number: Int,
        yellows: Int? = null,
        born: LocalDate = LocalDate(1990, 6, 15),
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = UiTestData.homeTeamId,
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        rpNumber = null,
        dateOfBirth = born,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber(number),
        discipline = yellows?.let { DisciplinaryRecord(it, LocalDate(2026, 8, 24)) },
    )

    private val novak = player("novak", "Novák", "Jan", 9)
    private val poupe = player("poupe", "Poupě", "Petr", 11, born = LocalDate(1988, 2, 3))

    /** Two yellows: on an even total, so the advisory badge shows. */
    private val kriz = player("kriz", "Kříž", "Ondřej", 18, yellows = 2, born = LocalDate(2004, 8, 20))

    /** Three yellows: odd, so no badge — which is NOT clearance. */
    private val odd = player("odd", "Růžička", "Radek", 27, yellows = 3, born = LocalDate(1992, 5, 18))

    private fun member(
        p: Player,
        absent: Boolean = false,
    ) = SquadMemberEntry(p, AppearanceId("app-${p.ref}"), absent = absent)

    private fun homeTeam(members: List<SquadMemberEntry>) =
        TeamLineupEntry(
            side = TeamSide.HOME,
            team = UiTestData.homeTeam,
            members = members,
            kitId = UiTestData.homeTeam.primaryKit.id,
        )

    private fun state(
        members: List<SquadMemberEntry> = listOf(member(novak), member(poupe), member(kriz)),
        newPlayer: NewPlayerRequest? = null,
        rejected: Boolean = false,
    ): LineupUiState {
        val home = homeTeam(members)
        val away =
            TeamLineupEntry(
                side = TeamSide.AWAY,
                team = UiTestData.awayTeam,
                members = emptyList(),
                kitId = UiTestData.awayTeam.primaryKit.id,
            )
        return LineupUiState(
            loading = false,
            entry = LineupEntry(home, away),
            newPlayer = newPlayer,
            newPlayerRejected = rejected,
        )
    }

    // ------------------------------------------------------------------
    // Marking absentees
    // ------------------------------------------------------------------

    @Test
    fun theSquadStartsPresentAndSaysHowMany() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Přítomno 3 z 3").assertIsDisplayed()
            onNodeWithText("Novák Jan").assertIsDisplayed()
        }

    @Test
    fun tappingANameMarksThatPlayerAbsent() =
        runComposeUiTest {
            // THE INVERSION. Three to five taps, not ten names.
            val events = mutableListOf<LineupEvent>()
            setContent { PsmfTheme { LineupScreen(state = state(), onEvent = events::add) } }

            onNodeWithText("Poupě Petr").performClick()

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.AbsenceToggled(poupe.id)), events)
        }

    @Test
    fun anAbsentPlayerIsStruckThroughAndCounted() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(listOf(member(novak), member(poupe, absent = true), member(kriz))),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Přítomno 2 z 3").assertIsDisplayed()
            onNodeWithText("Nepřítomen").assertIsDisplayed()
        }

    @Test
    fun anAbsentPlayerHasNoJerseyNumberToEdit() =
        runComposeUiTest {
            // The number belongs to the appearance, and an absent player has
            // no appearance. Leaving the field there would invite a clash
            // that cannot exist.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(listOf(member(novak, absent = true))), onEvent = {})
                    }
                }
            }

            onNodeWithText("Dres").assertDoesNotExist()
        }

    @Test
    fun editingAJerseyNumberReportsIt() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = events::add) } }
            }

            onNodeWithText("9").performTextInput("7")

            assertTrue(events.any { it is LineupEvent.JerseyNumberChanged })
        }

    // ------------------------------------------------------------------
    // Identification and eligibility
    // ------------------------------------------------------------------

    @Test
    fun noRpFieldIsOfferedAnywhereOnTheScreen() =
        runComposeUiTest {
            // The value is SHOWN, because it is what goes on the report.
            // What does not exist is anywhere to type one: RP numbers are
            // issued by PSMF.
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Číslo RP: 900615").assertIsDisplayed()
            // No card toggle either: every player here writes a date of
            // birth whatever happens, so it would change nothing.
            onNodeWithText("Bez průkazu").assertDoesNotExist()
        }

    @Test
    fun anEvenYellowTotalWarnsAndCarriesItsDate() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("2 ŽK k 24. 8. 2026 — může mít stop").assertIsDisplayed()
        }

    @Test
    fun theScreenNeverSaysAPlayerMayPlay() =
        runComposeUiTest {
            // THE HARD CONSTRAINT. A player on an odd total gets no badge,
            // and nothing anywhere reads as clearance. Fielding an
            // ineligible player is a technical forfeit, so a false all-clear
            // would be the app causing the harm.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(listOf(member(novak), member(odd))), onEvent = {}) }
                }
            }

            onNodeWithText("může mít stop", substring = true).assertDoesNotExist()
            onNode(hasScrollAction()).performScrollToNode(hasText("Aplikace nepotvrzuje", substring = true))
            onNodeWithText("Aplikace nepotvrzuje", substring = true).assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Adding a player
    // ------------------------------------------------------------------

    @Test
    fun theAddPlayerButtonOpensTheForm() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = events::add) } }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Přidat hráče mimo soupisku"))
            onNodeWithText("Přidat hráče mimo soupisku").performClick()

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.AddPlayerOpened), events)
        }

    @Test
    fun theAddPlayerFormAsksForThreeThingsAndOffersNoRpField() =
        runComposeUiTest {
            // Surname, first name, date of birth. Nothing else, and in
            // particular nowhere to type an RP number.
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
            onNodeWithText("Datum narození").assertIsDisplayed()
            onNodeWithText("Číslo RP se nezadává", substring = true).assertIsDisplayed()
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

            onNodeWithText("Zkontrolujte příjmení, jméno a datum narození.").assertIsDisplayed()
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
                    PsmfTheme { LineupScreen(state = state(listOf(member(novak), member(added))), onEvent = {}) }
                }
            }

            onNodeWithText("Dopsán na hřišti").assertIsDisplayed()
            onNodeWithText("Číslo RP: 990121").assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Kit and confirmation
    // ------------------------------------------------------------------

    @Test
    fun theKitDefaultsToThePrimaryAndTheLabelIsWhatIsShown() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            // The label verbatim from PSMF, never derived from the colours.
            onNodeWithText("modrá").assertIsDisplayed()
        }

    @Test
    fun theCaptainConfirmationIsATapAndAttributesTheClaimToTheCaptain() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = events::add) } }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Kapitáni potvrzují", substring = true))
            // The form's own words, and the CAPTAIN's claim rather than the
            // app's. The app says nothing about who may play.
            onNodeWithText("Kapitáni potvrzují, že všichni hráči startují oprávněně.").assertIsDisplayed()

            onNodeWithText("Kapitán potvrzuje sestavu").performClick()
            assertTrue(events.any { it is LineupEvent.CaptainConfirmationOpened })
        }
}
