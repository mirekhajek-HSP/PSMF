package cz.hspinovace.psmf.ui.export

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.data.settings.AppLanguage
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.domain.ReportProblem
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.export.ExportZou
import cz.hspinovace.psmf.export.ZouAppearance
import cz.hspinovace.psmf.export.ZouAssessment
import cz.hspinovace.psmf.export.ZouCard
import cz.hspinovace.psmf.export.ZouCards
import cz.hspinovace.psmf.export.ZouConfirmation
import cz.hspinovace.psmf.export.ZouFormat
import cz.hspinovace.psmf.export.ZouGoal
import cz.hspinovace.psmf.export.ZouHeader
import cz.hspinovace.psmf.export.ZouLineup
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouResult
import cz.hspinovace.psmf.export.ZouSide
import cz.hspinovace.psmf.export.ZouTeamAssessment
import cz.hspinovace.psmf.ui.locale.AppEnvironment
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Screen 7, and the rule that matters most about it.
 *
 * **The report is always Czech, whatever language the app is in.** These
 * tests set the app to English and to Ukrainian and check that the
 * document underneath has not moved.
 */
@OptIn(ExperimentalTestApi::class)
class ExportScreenTest {
    private val report =
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
                    assistant = "Roman Liska",
                    assistantLicensedHire = true,
                    delegatingTeam = "Celtic THK",
                ),
            lineups =
                listOf(
                    ZouLineup(
                        side = ZouSide.HOME,
                        teamName = "Kominíci",
                        kitLabel = "modrá",
                        rows = listOf(ZouAppearance(9, "900615", "Poupě Petr")),
                    ),
                ),
            goals = listOf(ZouGoal(ZouSide.HOME, "13´", null, null, "1:0")),
            cards =
                ZouCards(
                    accountedFor = true,
                    yellow = listOf(ZouCard(ZouSide.AWAY, "20´", 13, "Bača Tomáš", "podražení")),
                    red = emptyList(),
                ),
            result = ZouResult("1:0", "1:0", "Kominíci"),
            assessment =
                ZouAssessment(
                    home = ZouTeamAssessment(9, 0, true, true),
                    away = ZouTeamAssessment(13, 5, false, true),
                    commentary = "Nastřelená tyč ve 12. minutě.",
                ),
            confirmations =
                listOf(
                    ZouConfirmation("Podpis rozhodčího", "Jiri Vlk", Instant.parse("2026-08-31T20:05:00Z"), false),
                ),
        )

    /**
     * The page itself. There is a second scrollable on this screen -- the
     * preview scrolls sideways, because a CSV row is wider than a phone --
     * so a bare hasScrollAction() is ambiguous.
     */
    private fun ComposeUiTest.page() = onAllNodes(hasScrollAction()).onFirst()

    private fun state(
        problems: List<ReportProblem> = emptyList(),
        selected: ZouFormat = ZouFormat.TEXT,
        sendOutcome: SendOutcome? = null,
        saveOutcome: SaveOutcome? = null,
        recipientPrefilled: Boolean = true,
    ) = ExportUiState(
        loading = false,
        report = report,
        documents = ExportZou()(report),
        problems = problems,
        selected = selected,
        sendOutcome = sendOutcome,
        saveOutcome = saveOutcome,
        recipientPrefilled = recipientPrefilled,
    )

    // ------------------------------------------------------------------
    // The rule
    // ------------------------------------------------------------------

    @Test
    fun theReportStaysCzechWithTheAppInEnglish() =
        runComposeUiTest {
            withLanguage("en") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = {}) } }
            }

            // The interface is English…
            onNodeWithText("The report is complete and ready to send.").assertIsDisplayed()
            page().performScrollToNode(hasText("Send to PSMF"))
            onNodeWithText("Send to PSMF").assertIsDisplayed()
            // …and the document under it is not.
            onNodeWithText("Hřiště: ZAKOS", substring = true).assertIsDisplayed()
        }

    @Test
    fun theReportStaysCzechWithTheAppInUkrainian() =
        runComposeUiTest {
            withLanguage("uk") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = {}) } }
            }

            page().performScrollToNode(hasText("Надіслати до PSMF"))
            onNodeWithText("Надіслати до PSMF").assertIsDisplayed()
            onNodeWithText("Hřiště: ZAKOS", substring = true).assertIsDisplayed()
        }

    @Test
    fun theScreenSaysSoInWhateverLanguageTheRefereeIsReading() =
        runComposeUiTest {
            withLanguage("uk") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Протокол завжди чеською, незалежно від мови застосунку.").assertIsDisplayed()
        }

    @Test
    fun theReportStaysCzechWhenThePickerIsSetToUkrainian() =
        runComposeUiTest {
            // The two tests above prove it against the *device* language,
            // which is no longer what decides: the referee picks in the app.
            // This one goes through the picker's own mechanism, with the
            // host left in Czech so a failure cannot be a host artefact.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        AppEnvironment(AppLanguage.UKRAINIAN) {
                            ExportScreen(state = state(), onEvent = {})
                        }
                    }
                }

                // The interface followed the picker...
                onNodeWithText("Протокол завжди чеською, незалежно від мови застосунку.")
                    .assertIsDisplayed()
                // ...and the document did not.
                onNodeWithText("Hřiště: ZAKOS", substring = true).assertIsDisplayed()
                onNodeWithText("Barva dresů: modrá", substring = true).assertIsDisplayed()
            }
        }

    // ------------------------------------------------------------------
    // Readiness
    // ------------------------------------------------------------------

    @Test
    fun aMissingCommentaryBlocksSendingAndSaysSo() =
        runComposeUiTest {
            // The fine for an incomplete report lands on the delegating
            // team, so this is the one place refusing is worth doing.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ExportScreen(
                            state = state(problems = listOf(ReportProblem.MissingCommentary)),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Chybí povinný komentář.").assertIsDisplayed()
            onNodeWithText("Odeslat na PSMF").assertDoesNotExist()
        }

    @Test
    fun everyKindOfMissingThingIsNamedRatherThanCounted() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ExportScreen(
                            state =
                                state(
                                    problems =
                                        listOf(
                                            ReportProblem.MissingOfficials,
                                            ReportProblem.MissingLineup(TeamSide.AWAY),
                                            ReportProblem.CardsNotAccountedFor,
                                            ReportProblem.MissingConfirmation(ConfirmingParty.HOME_CAPTAIN),
                                        ),
                                ),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Chybí rozhodčí nebo delegující tým.").assertIsDisplayed()
            onNodeWithText("Chybí sestava (H).").assertIsDisplayed()
            onNodeWithText("Chybí potvrzení kapitána domácích.").assertIsDisplayed()
        }

    @Test
    fun aCompleteReportOffersTheSend() =
        runComposeUiTest {
            val events = mutableListOf<ExportEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = events::add) } }
            }

            page().performScrollToNode(hasText("Odeslat na PSMF"))
            onNodeWithText("Odeslat na PSMF").performClick()

            assertTrue(events.contains(ExportEvent.SendPressed))
        }

    // ------------------------------------------------------------------
    // The three formats
    // ------------------------------------------------------------------

    @Test
    fun allThreeFormatsAreOfferedAndNamed() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("TXT").assertIsDisplayed()
            onNodeWithText("CSV").assertIsDisplayed()
            onNodeWithText("JSON").assertIsDisplayed()
            onNodeWithText("zapis_6K_2026-08-31_Kominici_Sp--Sumys.txt").assertIsDisplayed()
        }

    @Test
    fun theSpreadsheetIsSemicolonSeparatedAndSaysSoOnScreen() =
        runComposeUiTest {
            // Visible in the preview, because a comma-separated file opens
            // as one column in a Czech Excel and the crew goes back to
            // retyping.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ExportScreen(state = state(selected = ZouFormat.CSV), onEvent = {}) }
                }
            }

            onNodeWithText("Hřiště;ZAKOS", substring = true).assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Saving to the device
    //
    // The save itself needs a platform document picker and cannot be
    // driven from a JVM test -- see `ReportSaver`. What is tested here is
    // the screen's half of it: the button exists beside send, is withheld
    // under the same rule send is, reports a press rather than acting on
    // its own, and shows the two outcomes rather than staying silent.
    // ------------------------------------------------------------------

    @Test
    fun theSaveButtonSitsBesideSendAndReportsAPress() =
        runComposeUiTest {
            val events = mutableListOf<ExportEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = events::add) } }
            }

            page().performScrollToNode(hasText("Uložit do zařízení"))
            onNodeWithText("Uložit do zařízení").assertIsDisplayed()
            onNodeWithText("Odeslat na PSMF").assertIsDisplayed()

            onNodeWithText("Uložit do zařízení").performClick()

            assertTrue(events.contains(ExportEvent.SavePressed))
            // And pressing it does not also send: the two are independent.
            assertTrue(!events.contains(ExportEvent.SendPressed))
        }

    @Test
    fun theSaveButtonIsWithheldUnderTheSameRuleAsSend() =
        runComposeUiTest {
            // The fine for an incomplete report lands on the delegating
            // team either way it would leave the phone.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ExportScreen(
                            state = state(problems = listOf(ReportProblem.MissingCommentary)),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Uložit do zařízení").assertDoesNotExist()
        }

    @Test
    fun aSuccessfulSaveIsConfirmedRatherThanAssumed() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(saveOutcome = SaveOutcome.Saved), onEvent = {}) } }
            }

            page().performScrollToNode(hasText("Zápis uložen", substring = true))
            onNodeWithText("Zápis uložen. K souborům se lze vrátit i bez aplikace.").assertIsDisplayed()
        }

    @Test
    fun aFailedSaveIsNamedRatherThanSilent() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ExportScreen(state = state(saveOutcome = SaveOutcome.Failed), onEvent = {}) }
                }
            }

            page().performScrollToNode(hasText("Uložení se nezdařilo", substring = true))
            onNodeWithText("Uložení se nezdařilo. Soubory nemusí být kompletní.").assertIsDisplayed()
        }

    @Test
    fun aCancelledSaveIsNamedAsCancelledNotAsAFailure() =
        runComposeUiTest {
            // Until 2026-09-29 the saver returned a bare Boolean and the
            // screen had to say "failed or cancelled". Backing out of the
            // folder picker is now its own outcome, with its own words.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ExportScreen(state = state(saveOutcome = SaveOutcome.Cancelled), onEvent = {}) }
                }
            }

            page().performScrollToNode(hasText("Uložení zrušeno", substring = true))
            onNodeWithText("Uložení zrušeno. Nic nebylo uloženo.").assertIsDisplayed()
            onNodeWithText("Uložení se nezdařilo", substring = true).assertDoesNotExist()
        }

    @Test
    fun sendingOpensADraftRatherThanClaimingToHaveSent() =
        runComposeUiTest {
            // The referee presses send in their own mail app: the last word
            // stays with the person whose name is on the report.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ExportScreen(state = state(sendOutcome = SendOutcome.DraftOpened), onEvent = {}) }
                }
            }

            page().performScrollToNode(hasText("Otevřen e-mail", substring = true))
            onNodeWithText("Otevřen e-mail na psmf@psmf.cz. Odeslání potvrďte v poštovní aplikaci.")
                .assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Every send outcome has its own words. iOS reports more of them than
    // Android can; each is still named on screen, never silent.
    // ------------------------------------------------------------------

    private fun ComposeUiTest.sendOutcomeReads(
        outcome: SendOutcome,
        expected: String,
    ) {
        withLanguage("cs") {
            setContent { PsmfTheme { ExportScreen(state = state(sendOutcome = outcome), onEvent = {}) } }
        }
        page().performScrollToNode(hasText(expected))
        onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun mailAcceptingItForSendingIsSaidAsHandedOverNotAsDelivered() =
        runComposeUiTest {
            sendOutcomeReads(SendOutcome.HandedToMail, "Předáno aplikaci Mail k odeslání na psmf@psmf.cz.")
        }

    @Test
    fun aDraftKeptInMailSaysItIsNotSent() =
        runComposeUiTest {
            sendOutcomeReads(SendOutcome.DraftSaved, "Uloženo jako koncept v aplikaci Mail. Zatím neodesláno.")
        }

    @Test
    fun aCancelledSendIsNamed() =
        runComposeUiTest {
            sendOutcomeReads(SendOutcome.Cancelled, "Odeslání zrušeno. Nic nebylo odesláno.")
        }

    @Test
    fun aFailedSendIsNamedAndIsNotTheNoMailAppMessage() =
        runComposeUiTest {
            sendOutcomeReads(SendOutcome.Failed, "Odeslání se nezdařilo. Nic nebylo odesláno.")
            onNodeWithText("Na tomto zařízení není poštovní aplikace", substring = true).assertDoesNotExist()
        }

    @Test
    fun noMailAppKeepsItsOwnMessage() =
        runComposeUiTest {
            sendOutcomeReads(SendOutcome.NoMailApp, "Na tomto zařízení není poštovní aplikace, která by to zvládla.")
        }

    @Test
    fun theShareSheetRouteIsNotPresentedAsConfirmedDelivery() =
        runComposeUiTest {
            // The share sheet cannot fill in a recipient. "Handed over" is
            // true; "sent to PSMF" would not be, so the screen asks the
            // referee to check where it went.
            sendOutcomeReads(
                SendOutcome.SharedWithoutRecipient,
                "Předáno vybrané aplikaci. Adresu nešlo vyplnit — zkontrolujte, že zápis šel na psmf@psmf.cz.",
            )
            onNodeWithText("Otevřen e-mail", substring = true).assertDoesNotExist()
        }

    @Test
    fun whenTheAddressCannotBePrefilledTheRefereeIsToldBeforeSending() =
        runComposeUiTest {
            withLanguage("en") {
                setContent { PsmfTheme { ExportScreen(state = state(recipientPrefilled = false), onEvent = {}) } }
            }

            val note =
                "There is no Mail account on this device, so the address cannot be filled in for you. " +
                    "After pressing Send, choose your mail app and paste psmf@psmf.cz as the recipient " +
                    "— the address is copied for you."
            page().performScrollToNode(hasText(note))
            onNodeWithText(note).assertIsDisplayed()
            // Before, not after: nothing has been pressed yet, and the
            // button it is about is right there.
            page().performScrollToNode(hasText("Send to PSMF"))
            onNodeWithText("Send to PSMF").assertIsDisplayed()
        }

    @Test
    fun whenTheAddressIsPrefilledThereIsNoWarningAboutIt() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { ExportScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("není účet v aplikaci Mail", substring = true).assertDoesNotExist()
        }
}
