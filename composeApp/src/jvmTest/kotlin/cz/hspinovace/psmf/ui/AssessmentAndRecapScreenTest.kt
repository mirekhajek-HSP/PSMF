package cz.hspinovace.psmf.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.Confirmation
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.ZouAssessment
import cz.hspinovace.psmf.export.ZouCards
import cz.hspinovace.psmf.export.ZouHeader
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouResult
import cz.hspinovace.psmf.export.ZouTeamAssessment
import cz.hspinovace.psmf.ui.assessment.AssessmentEvent
import cz.hspinovace.psmf.ui.assessment.AssessmentScreen
import cz.hspinovace.psmf.ui.assessment.AssessmentUiState
import cz.hspinovace.psmf.ui.recap.RecapChange
import cz.hspinovace.psmf.ui.recap.RecapEvent
import cz.hspinovace.psmf.ui.recap.RecapScreen
import cz.hspinovace.psmf.ui.recap.RecapUiState
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.usecase.AssessmentDraft
import cz.hspinovace.psmf.usecase.TeamAssessmentDraft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Screen 5.
 *
 * `Č` and `B` feed into fines, so the thing worth testing is that neither
 * answer is given until the referee gives it.
 */
@OptIn(ExperimentalTestApi::class)
class AssessmentScreenTest {
    private fun state(draft: AssessmentDraft = AssessmentDraft()) =
        AssessmentUiState(
            loading = false,
            draft = draft,
            homeTeam = UiTestData.homeTeam.name,
            awayTeam = UiTestData.awayTeam.name,
        )

    @Test
    fun bothTeamsGetTheirOwnBlock() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText(UiTestData.homeTeam.name).assertIsDisplayed()
            onNodeWithText(UiTestData.awayTeam.name).assertIsDisplayed()
        }

    @Test
    fun theFineBearingRatingsStartWithNeitherAnswerChosen() =
        runComposeUiTest {
            val events = mutableListOf<AssessmentEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(), onEvent = events::add) } }
            }

            // The screen says why, because a blank that quietly means "yes"
            // would waive a fine and nobody would see it happen.
            onAllNodes(hasText("prázdná odpověď se nepočítá jako „ano“", substring = true))
                .onFirst()
                .assertIsDisplayed()
            assertTrue(events.isEmpty())
        }

    @Test
    fun answeringOneRatingReportsIt() =
        runComposeUiTest {
            val events = mutableListOf<AssessmentEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(), onEvent = events::add) } }
            }

            // Four Ano chips on the screen, two per team. The first belongs
            // to the home side's Č rating.
            onAllNodes(hasText("Ano")).onFirst().performClick()

            assertTrue(
                events.any {
                    it is AssessmentEvent.TeamEdited &&
                        it.side == TeamSide.HOME &&
                        it.draft.shirtsProperlyNumbered == true
                },
            )
        }

    @Test
    fun theCommentaryIsMarkedMandatoryAndSaysItStaysEditable() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(), onEvent = {}) } }
            }

            val note =
                "Měl by obsahovat všechny důležité okamžiky utkání. " +
                    "Zůstává upravitelný až do odeslání."
            onNode(hasScrollAction()).performScrollToNode(hasText(note))

            onNodeWithText("Povinný komentář").assertIsDisplayed()
            onNodeWithText(note).assertIsDisplayed()
        }

    @Test
    fun eachTeamHasANoteForAComplaintOrAProtestAndSaysItIsOptional() =
        runComposeUiTest {
            val events = mutableListOf<AssessmentEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(), onEvent = events::add) } }

                // One per team, below that team's ratings.
                onAllNodesWithText("Poznámka týmu").assertCountEquals(2)
                onAllNodes(hasText("Nepovinné.", substring = true)).onFirst().assertIsDisplayed()

                onAllNodesWithText("Poznámka týmu").onFirst().performTextInput("Protest proti 2. gólu.")
            }

            // Typed by the referee, into the home team's own block.
            assertTrue(
                events.any {
                    it is AssessmentEvent.TeamEdited &&
                        it.side == TeamSide.HOME &&
                        it.draft.note == "Protest proti 2. gólu."
                },
            )
        }

    @Test
    fun aNoteAlreadyWrittenIsShownBackInItsOwnTeamsBlock() =
        runComposeUiTest {
            val draft =
                AssessmentDraft(
                    home = TeamAssessmentDraft(note = "Domácí: protest."),
                    away = TeamAssessmentDraft(note = "Hosté: stížnost."),
                )
            withLanguage("cs") {
                setContent { PsmfTheme { AssessmentScreen(state = state(draft), onEvent = {}) } }

                onNodeWithText("Domácí: protest.").assertIsDisplayed()
                onNodeWithText("Hosté: stížnost.").assertExists()
            }
        }
}

/**
 * Screen 6.
 *
 * The point of this screen is that it is the document: whatever is not on
 * it is not being checked (analysis section 5.5). And that nothing on it is
 * typed (prompt 13, part 4): the result is the goals', and the three
 * signatures are taps under names the screen already shows.
 */
@OptIn(ExperimentalTestApi::class)
class RecapScreenTest {
    private fun report(cards: ZouCards) =
        ZouReport(
            header =
                ZouHeader(
                    pitch = "ZAKOS",
                    date = LocalDate(2026, 8, 31),
                    time = LocalTime(19, 0),
                    league = "6K",
                    homeTeam = "Kominíci",
                    awayTeam = "Sp. Sumýš",
                    referee = "Jiri Vlk",
                    refereeLicensedHire = false,
                    assistant = null,
                    assistantLicensedHire = false,
                    delegatingTeam = "Celtic THK",
                ),
            lineups = emptyList(),
            goals = emptyList(),
            cards = cards,
            result = ZouResult(halfTime = "1:1", fullTime = "2:1", winner = "Kominíci"),
            assessment =
                ZouAssessment(
                    home = ZouTeamAssessment(null, 0, null, null),
                    away = ZouTeamAssessment(null, 0, null, null),
                    commentary = "",
                ),
            confirmations = emptyList(),
        )

    private val atTheRecap = Instant.parse("2026-08-31T20:05:00Z")

    private val everyoneKnown =
        mapOf<ConfirmingParty, String?>(
            ConfirmingParty.HOME_CAPTAIN to "Houžev Karel",
            ConfirmingParty.AWAY_CAPTAIN to "Lepiš Adam",
            ConfirmingParty.REFEREE to "Jiri Vlk",
        )

    private fun given(party: ConfirmingParty) =
        Confirmation(party, atTheRecap, PersonName.of(everyoneKnown.getValue(party)!!))

    private fun state(
        cardsUnaccountedFor: Boolean = false,
        noCardsAffirmed: Boolean = false,
        confirmed: Set<ConfirmingParty> = emptySet(),
        signsAs: Map<ConfirmingParty, String?> = everyoneKnown,
        withdrawing: RecapChange? = null,
    ) = RecapUiState(
        loading = false,
        report = report(ZouCards(!cardsUnaccountedFor, emptyList(), emptyList())),
        signsAs = signsAs,
        confirmed = confirmed.associateWith { given(it) },
        cardsUnaccountedFor = cardsUnaccountedFor,
        noCardsAffirmed = noCardsAffirmed,
        withdrawing = withdrawing,
    )

    @Test
    fun theScreenShowsTheReportItselfRatherThanASummary() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Kapitáni potvrzují přesně tohle. Co tu není, nikdo nekontroluje.")
                .assertIsDisplayed()
            // The document, with the form's own field labels in it.
            onNodeWithText("Hřiště: ZAKOS", substring = true).assertIsDisplayed()
        }

    @Test
    fun theHalfTimeAndFinalScoresAreShownFromTheGoals() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Poločas").assertIsDisplayed()
            onNodeWithText("1:1").assertIsDisplayed()
            onNodeWithText("Konečný výsledek").assertIsDisplayed()
            onNodeWithText("2:1").assertIsDisplayed()
            // And a line says where a goal is corrected.
            onNodeWithText("Oprava gólu se dělá na obrazovce Zápis.", substring = true).assertIsDisplayed()
        }

    @Test
    fun theResultCannotBeEdited() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = {}) } }
            }

            // The result is shown, and there is nothing on the screen to type
            // it into: it comes from the goals.
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }

    @Test
    fun anUnaccountedCardsBlockOffersTheAffirmationAndExplainsIt() =
        runComposeUiTest {
            val events = mutableListOf<RecapEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        RecapScreen(state = state(cardsUnaccountedFor = true), onEvent = events::add)
                    }
                }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Žádné karty nebyly uděleny"))
            onNodeWithText("Na papíře se políčka proškrtávají.", substring = true).assertIsDisplayed()
            onNodeWithText("Žádné karty nebyly uděleny").performClick()

            assertTrue(events.contains(RecapEvent.NoCardsAffirmed))
        }

    @Test
    fun onceAffirmedTheAffirmationIsShownRatherThanOfferedAgain() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(noCardsAffirmed = true), onEvent = {}) } }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzeno: bez karet"))
            onNodeWithText("Potvrzeno: bez karet").assertIsDisplayed()
            onNodeWithText("Žádné karty nebyly uděleny").assertDoesNotExist()
        }

    @Test
    fun theNoCardsAffirmationCanBeWithdrawn() =
        runComposeUiTest {
            val events = mutableListOf<RecapEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme { RecapScreen(state = state(noCardsAffirmed = true), onEvent = events::add) }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Vzít zpět"))
                onNodeWithText("Vzít zpět").performClick()
            }

            assertTrue(events.contains(RecapEvent.NoCardsWithdrawn))
        }

    @Test
    fun eachPartyHasOneTapShowingTheNameItWillSignUnder() =
        runComposeUiTest {
            val events = mutableListOf<RecapEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = events::add) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Jiri Vlk"))
                // The names are the lineup's captains and the header's referee,
                // and there is nothing to type.
                onNodeWithText("Potvrzuje Houžev Karel").assertIsDisplayed()
                onNodeWithText("Potvrzuje Lepiš Adam").assertIsDisplayed()
                onNodeWithText("Potvrzuje Jiri Vlk").performClick()
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
            }

            assertTrue(events.contains(RecapEvent.ConfirmationGiven(ConfirmingParty.REFEREE)))
        }

    @Test
    fun thereIsNoDeputySwitchAndNoNameToType() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Jiri Vlk"))
                onNodeWithText("Zástupce kapitána").assertDoesNotExist()
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
            }
        }

    @Test
    fun aGivenSignatureReadsItsNameAndItsTimeInPragueTime() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        RecapScreen(state = state(confirmed = setOf(ConfirmingParty.HOME_CAPTAIN)), onEvent = {})
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Lepiš Adam"))
                onNodeWithText("Kapitán domácích: Houžev Karel — potvrzeno 31. 8. 2026 22:05").assertIsDisplayed()
                // The one who has not signed still has a button.
                onNodeWithText("Potvrzuje Lepiš Adam").assertIsDisplayed()
            }
        }

    @Test
    fun aTeamWithNoCaptainMarkedSaysSoAndOffersNothingToTypeInstead() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        RecapScreen(
                            state = state(signsAs = everyoneKnown + (ConfirmingParty.HOME_CAPTAIN to null)),
                            onEvent = {},
                        )
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrzuje Jiri Vlk"))
                onNodeWithText("Kapitán není označen v sestavě. Vraťte se do sestav (Zpět) a označte ho.")
                    .assertIsDisplayed()
                onAllNodes(hasSetTextAction()).assertCountEquals(0)
            }
        }

    @Test
    fun aSignedReportAsksBeforeATakeBackAndSaysWhatItCosts() =
        runComposeUiTest {
            val events = mutableListOf<RecapEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        RecapScreen(
                            state =
                                state(
                                    noCardsAffirmed = true,
                                    confirmed = ConfirmingParty.entries.toSet(),
                                    withdrawing = RecapChange.WITHDRAW_NO_CARDS,
                                ),
                            onEvent = events::add,
                        )
                    }
                }

                onNodeWithText("Zrušit podpisy?").assertIsDisplayed()
                onNodeWithText("Zpráva je podepsaná. Tato změna zruší podpisy", substring = true).assertIsDisplayed()
                onNodeWithText("Ano, zrušit podpisy").performClick()
                onNodeWithText("Ponechat podpisy").performClick()
            }

            assertEquals(listOf(RecapEvent.WithdrawalConfirmed, RecapEvent.WithdrawalDismissed), events)
        }
}
