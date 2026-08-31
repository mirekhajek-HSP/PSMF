package cz.hspinovace.psmf.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.ZouAssessment
import cz.hspinovace.psmf.export.ZouCards
import cz.hspinovace.psmf.export.ZouHeader
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouTeamAssessment
import cz.hspinovace.psmf.ui.assessment.AssessmentEvent
import cz.hspinovace.psmf.ui.assessment.AssessmentScreen
import cz.hspinovace.psmf.ui.assessment.AssessmentUiState
import cz.hspinovace.psmf.ui.recap.RecapEvent
import cz.hspinovace.psmf.ui.recap.RecapScreen
import cz.hspinovace.psmf.ui.recap.RecapUiState
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.usecase.AssessmentDraft
import cz.hspinovace.psmf.usecase.ResultDraft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertTrue

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
}

/**
 * Screen 6.
 *
 * The point of this screen is that it is the document: whatever is not on
 * it is not being checked (analysis section 5.5).
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
            result = null,
            assessment =
                ZouAssessment(
                    home = ZouTeamAssessment(null, 0, null, null),
                    away = ZouTeamAssessment(null, 0, null, null),
                    commentary = "",
                ),
            confirmations = emptyList(),
        )

    private fun state(
        cardsUnaccountedFor: Boolean = false,
        noCardsAffirmed: Boolean = false,
        confirmed: Set<ConfirmingParty> = emptySet(),
    ) = RecapUiState(
        loading = false,
        report = report(ZouCards(!cardsUnaccountedFor, emptyList(), emptyList())),
        result = ResultDraft("1", "1", "2", "1"),
        confirmed = confirmed,
        cardsUnaccountedFor = cardsUnaccountedFor,
        noCardsAffirmed = noCardsAffirmed,
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
    fun theHalfTimeAndFinalScoresAreBothAskedFor() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { RecapScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Poločas").assertIsDisplayed()
            onNodeWithText("Konečný výsledek").assertIsDisplayed()
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
    fun eachPartyConfirmsOnceAndThenSaysSo() =
        runComposeUiTest {
            // One captain per team, plus the referee.
            val events = mutableListOf<RecapEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        RecapScreen(
                            state = state(confirmed = setOf(ConfirmingParty.HOME_CAPTAIN)),
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Kapitán hostí"))
            onNodeWithText("Kapitán domácích — potvrzeno").assertIsDisplayed()
            onNodeWithText("Kapitán hostí").performClick()

            assertTrue(
                events.any {
                    it is RecapEvent.ConfirmationOpened && it.party == ConfirmingParty.AWAY_CAPTAIN
                },
            )
        }
}
