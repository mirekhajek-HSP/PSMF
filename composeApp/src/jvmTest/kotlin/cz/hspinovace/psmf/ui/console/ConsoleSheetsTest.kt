package cz.hspinovace.psmf.ui.console

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.domain.UndoStep
import cz.hspinovace.psmf.domain.Whistle
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import cz.hspinovace.psmf.usecase.GoalDraft
import cz.hspinovace.psmf.usecase.GoalProblem
import cz.hspinovace.psmf.usecase.MinuteDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * The two things the console can now ask: a goal with a minute of its own,
 * and whether to take a whistle back (prompt 13, part 3).
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleSheetsTest {
    private val novak = row("a-novak", 9, "Novák", dismissed = false)
    private val poupe = row("a-poupe", 11, "Poupě", dismissed = false)
    private val sentOff = row("a-vlk", 4, "Vlk", dismissed = true)

    private fun row(
        id: String,
        number: Int,
        surname: String,
        dismissed: Boolean,
    ) = ConsoleRow(
        appearanceId = AppearanceId(id),
        jerseyNumber = JerseyNumber(number),
        name = PlayerName(PersonName.of(surname), PersonName.of("Jan")),
        dismissed = dismissed,
        yellowsInThisMatch = 0,
    )

    private fun entry(
        status: MatchStatus = MatchStatus.IN_PROGRESS,
        periodBreaks: List<PeriodBreak> = emptyList(),
    ) = ConsoleEntry(
        home = ConsoleTeam(TeamSide.HOME, UiTestData.homeTeam.name, listOf(novak, poupe, sentOff)),
        away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
        score = Score.GOALLESS,
        kickoffAt = KICKOFF,
        status = status,
        log = emptyList(),
        powerPlays = emptyList(),
        periodBreaks = periodBreaks,
    )

    // ------------------------------------------------------------------
    // The goal sheet
    // ------------------------------------------------------------------

    @Test
    fun aGoalWithAnotherMinuteIsOpenedFromTheTeamOnScreen() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(loading = false, entry = entry()),
                            KICKOFF + 20.minutes,
                            events::add,
                        )
                    }
                }
                onNode(hasScrollAction()).performScrollToNode(hasText("Gól s jinou minutou"))
                onNodeWithText("Gól s jinou minutou").performClick()
            }

            assertEquals<List<ConsoleEvent>>(listOf(ConsoleEvent.GoalWithMinuteOpened(TeamSide.HOME)), events)
        }

    @Test
    fun theSheetOffersNumberedMinutesAndThirtyPlusButNeverSixtyPlus() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(loading = false, entry = entry(), goal = GoalDraft(TeamSide.HOME)),
                            KICKOFF + 25.minutes,
                            onEvent = {},
                        )
                    }
                }

                onNodeWithText("Zapsat gól").assertIsDisplayed()
                onNodeWithText("30´+ (poločas)").assertIsDisplayed()
                // No goal has one.
                onNodeWithText("60´+ (po skončení)").assertDoesNotExist()
                // And it says how late the clock allows.
                onNodeWithText("Nejpozdější minuta teď: 25´.").assertIsDisplayed()
            }
        }

    @Test
    fun theScorerIsChosenFromThoseOnThePitchAndNotFromTheSentOff() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(),
                                goal = GoalDraft(TeamSide.HOME, scorer = novak.appearanceId),
                            ),
                            KICKOFF + 25.minutes,
                            onEvent = {},
                        )
                    }
                }

                // The scorer on the draft is what the select shows.
                onNodeWithText("Novák Jan · č. 9").assertIsDisplayed()
                onNodeWithText("Střelec").assertIsDisplayed()
            }
        }

    @Test
    fun aMinuteLaterThanTheClockIsRefusedInWordsWithTheLatestOne() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(),
                                goal = GoalDraft(TeamSide.HOME, minute = MinuteDraft("26")),
                                goalProblems = listOf(GoalProblem.LATER_THAN_THE_CLOCK),
                            ),
                            KICKOFF + 25.minutes,
                            onEvent = {},
                        )
                    }
                }

                onNodeWithText("Tato minuta ještě nebyla odehrána (nejpozději 25´).").assertIsDisplayed()
            }
        }

    @Test
    fun afterTheFinalWhistleTheSheetSaysTheMinuteIsTheRefereesToWrite() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(status = MatchStatus.FINISHED),
                                goal = GoalDraft(TeamSide.HOME, scorer = novak.appearanceId),
                            ),
                            KICKOFF + 90.minutes,
                            onEvent = {},
                        )
                    }
                }

                onNodeWithText("Utkání skončilo: gól zapište s minutou, ve které padl.").assertIsDisplayed()
            }
        }

    @Test
    fun savingAndCancellingTheSheetFireTheirEvents() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(loading = false, entry = entry(), goal = GoalDraft(TeamSide.HOME)),
                            KICKOFF + 25.minutes,
                            events::add,
                        )
                    }
                }
                onNodeWithText("Zapsat").performClick()
                onNodeWithText("Zrušit").performClick()
            }

            assertEquals(listOf(ConsoleEvent.GoalSubmitted, ConsoleEvent.GoalDismissed), events)
        }

    // ------------------------------------------------------------------
    // Taking a whistle back
    // ------------------------------------------------------------------

    @Test
    fun theQuestionNamesTheWhistleItWouldTakeBack() {
        val named =
            mapOf(
                (Whistle.KICKOFF to 1) to "Výkop se vezme zpět.",
                (Whistle.END_OF_PERIOD to 1) to "Konec 1. poločasu se vezme zpět.",
                (Whistle.START_OF_PERIOD to 2) to "Začátek 2. poločasu se vezme zpět.",
                (Whistle.FINAL to 2) to "Konec utkání se vezme zpět.",
            )
        named.forEach { (key, expected) ->
            runComposeUiTest {
                withLanguage("cs") {
                    setContent {
                        PsmfTheme {
                            ConsoleScreen(
                                ConsoleUiState(
                                    loading = false,
                                    entry = entry(),
                                    takeBack = UndoStep.TakesBackWhistle(key.first, key.second),
                                ),
                                KICKOFF + 33.minutes,
                                onEvent = {},
                            )
                        }
                    }
                    onNodeWithText(expected, substring = true).assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun theRefereeCanSayYesOrNo() {
        val events = mutableListOf<ConsoleEvent>()
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(),
                                takeBack = UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, 1),
                            ),
                            KICKOFF + 33.minutes,
                            events::add,
                        )
                    }
                }
                onNodeWithText("Ano, vzít zpět").performClick()
                onNodeWithText("Zrušit").performClick()
            }
        }

        assertEquals(listOf(ConsoleEvent.TakeBackConfirmed, ConsoleEvent.TakeBackDismissed), events)
    }

    @Test
    fun thereIsStillNothingOnTheScreenThatCanPauseTheClockWithTheQuestionOpen() =
        runComposeUiTest {
            // The question is about taking back an instant, which is not a
            // pause: it offers nothing that stops, holds or adjusts the clock.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            ConsoleUiState(
                                loading = false,
                                entry = entry(),
                                takeBack = UndoStep.TakesBackWhistle(Whistle.END_OF_PERIOD, 1),
                            ),
                            KICKOFF + 33.minutes,
                            onEvent = {},
                        )
                    }
                }

                listOf("Pauza", "Zastavit", "Pozastavit", "Upravit čas", "Upravit hodiny").forEach {
                    onNodeWithText(it).assertDoesNotExist()
                }
            }
        }
}
