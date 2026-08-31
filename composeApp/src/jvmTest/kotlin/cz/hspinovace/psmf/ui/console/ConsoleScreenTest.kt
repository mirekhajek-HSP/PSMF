package cz.hspinovace.psmf.ui.console

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.PeriodBreak
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.PowerPlay
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.CardColour
import cz.hspinovace.psmf.usecase.CardDraft
import cz.hspinovace.psmf.usecase.CardProblem
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import cz.hspinovace.psmf.usecase.MinuteDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * Screen 4's flows: logging a goal, logging a card, and a dismissal
 * starting a power play — plus the rules that must not slip, namely that
 * nothing here can pause the clock and a sent-off player keeps their row.
 */
@OptIn(ExperimentalTestApi::class)
class ConsoleScreenTest {
    private val novak = row("a-novak", 9, "Novák", "Jan")
    private val poupe = row("a-poupe", 11, "Poupě", "Petr")

    private fun row(
        id: String,
        number: Int,
        surname: String,
        first: String,
        dismissed: Boolean = false,
        yellows: Int = 0,
    ) = ConsoleRow(
        appearanceId = AppearanceId(id),
        jerseyNumber = JerseyNumber(number),
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        dismissed = dismissed,
        yellowsInThisMatch = yellows,
    )

    private fun entry(
        rows: List<ConsoleRow> = listOf(novak, poupe),
        kickoffAt: Instant? = KICKOFF,
        score: Score = Score.GOALLESS,
        powerPlays: List<PowerPlay> = emptyList(),
        status: MatchStatus = MatchStatus.IN_PROGRESS,
        periodBreaks: List<PeriodBreak> = emptyList(),
    ) = ConsoleEntry(
        home = ConsoleTeam(TeamSide.HOME, UiTestData.homeTeam.name, rows),
        away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
        score = score,
        kickoffAt = kickoffAt,
        status = status,
        log = emptyList(),
        powerPlays = powerPlays,
        periodBreaks = periodBreaks,
    )

    private fun state(
        entry: ConsoleEntry = entry(),
        card: CardDraft? = null,
        cardProblems: List<CardProblem> = emptyList(),
    ) = ConsoleUiState(loading = false, entry = entry, card = card, cardProblems = cardProblems)

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    @Test
    fun theMinuteIsDrawnFromTheInstantPassedIn() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF + 25.minutes, onEvent = {}) }
                }
            }

            onNodeWithText("25´").assertIsDisplayed()
        }

    @Test
    fun thereIsNothingOnTheScreenThatCanPauseTheClock() =
        runComposeUiTest {
            // The clock runs continuously and the referee adds time. golblok
            // pauses; that behaviour must not come across, and the absence
            // of the control is the only way to be sure it has not. Checked
            // again below with a half-time button on screen, specifically so
            // that button cannot be mistaken later for permission to add a
            // pause: ending a period is not one.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF + 34.minutes, onEvent = {}) }
                }
            }

            onNodeWithText("Pauza").assertDoesNotExist()
            onNodeWithText("Zastavit").assertDoesNotExist()
            onNodeWithText("Pozastavit").assertDoesNotExist()
            // Nor anything golblok has that the ZoU does not.
            onNodeWithText("Střídání").assertDoesNotExist()
            onNodeWithText("Asistence").assertDoesNotExist()
        }

    @Test
    fun endingAHalfIsNotAPauseEvenWithItsButtonOnScreen() =
        runComposeUiTest {
            // The same absences as above, re-checked in the one state where
            // a reader might mistake "Ukončit poločas" for a pause control.
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF + 31.minutes, onEvent = {}) }
                }
            }

            onNodeWithText("Ukončit poločas").assertIsDisplayed()
            onNodeWithText("Pauza").assertDoesNotExist()
            onNodeWithText("Zastavit").assertDoesNotExist()
            onNodeWithText("Pozastavit").assertDoesNotExist()
        }

    // ------------------------------------------------------------------
    // The half-time break
    // ------------------------------------------------------------------

    @Test
    fun endingTheFirstPeriodIsOfferedWhileItIsRunningAndFiresTheEvent() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF + 20.minutes, onEvent = events::add) }
                }
            }

            onNodeWithText("Ukončit poločas").performClick()

            assertTrue(events.contains(ConsoleEvent.EndPeriodPressed))
        }

    @Test
    fun startingTheSecondPeriodIsOfferedDuringTheIntervalAndFiresTheEvent() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            val onBreak = entry(periodBreaks = listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes)))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(state = state(onBreak), now = KICKOFF + 33.minutes, onEvent = events::add)
                    }
                }
            }

            // The minute holds at the break rather than climbing through it.
            onNodeWithText("30´+").assertIsDisplayed()
            onNodeWithText("Zahájit 2. poločas").performClick()

            assertTrue(events.contains(ConsoleEvent.StartNextPeriodPressed))
        }

    @Test
    fun onceTheSecondPeriodIsRunningTheOnlyWayForwardIsToEndTheMatch() =
        runComposeUiTest {
            // HL's 2 x 30 has one break. Nothing more to offer here --
            // "Ukončit utkání" is what ends the match, unaffected by this.
            val secondHalf =
                entry(
                    periodBreaks =
                        listOf(PeriodBreak(endedAt = KICKOFF + 30.minutes, nextStartedAt = KICKOFF + 32.minutes)),
                )
            withLanguage("cs") {
                setContent {
                    PsmfTheme { ConsoleScreen(state = state(secondHalf), now = KICKOFF + 50.minutes, onEvent = {}) }
                }
            }

            onNodeWithText("Ukončit poločas").assertDoesNotExist()
            onNodeWithText("Zahájit", substring = true).assertDoesNotExist()
            onNodeWithText("Ukončit utkání").assertIsDisplayed()
        }

    @Test
    fun beforeKickoffTheScreenSaysSoAndOffersTheWhistle() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state = state(entry(kickoffAt = null)),
                            now = KICKOFF,
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNodeWithText("Před výkopem").assertIsDisplayed()
            onNodeWithText("Zahájit utkání").performClick()
            assertTrue(events.contains(ConsoleEvent.StartPressed))
        }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    @Test
    fun tappingGoalOnAPlayerLogsItAgainstThem() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(state = state(entry(rows = listOf(novak))), now = KICKOFF, onEvent = events::add)
                    }
                }
            }

            // Icon-only: this button carries "Gol" as contentDescription,
            // not as visible Text -- onNodeWithText does not find it.
            onNodeWithContentDescription("Gól").performClick()

            assertEquals<List<ConsoleEvent>>(listOf(ConsoleEvent.GoalScoredBy(novak.appearanceId)), events)
        }

    @Test
    fun aGoalCanBeLoggedWithNoScorer() =
        runComposeUiTest {
            // `13´ — 2:1` in the worked example.
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF, onEvent = events::add) } }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Gól bez střelce"))
            onNodeWithText("Gól bez střelce").performClick()

            assertEquals<List<ConsoleEvent>>(listOf(ConsoleEvent.GoalWithNoScorer(TeamSide.HOME)), events)
        }

    @Test
    fun theScoreboardShowsTheRunningScore() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(state = state(entry(score = Score(2, 1))), now = KICKOFF, onEvent = {})
                    }
                }
            }

            onNodeWithText("2:1").assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    @Test
    fun tappingCardOpensTheFormForThatPlayer() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(state = state(entry(rows = listOf(novak))), now = KICKOFF, onEvent = events::add)
                    }
                }
            }

            // Icon-only, same as the goal button above.
            onNodeWithContentDescription("Karta").performClick()

            assertEquals<List<ConsoleEvent>>(
                listOf(ConsoleEvent.CardOpened(novak.appearanceId, TeamSide.HOME)),
                events,
            )
        }

    @Test
    fun everyCardAsksForAReasonAndSaysSoWhenItIsMissing() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state =
                                state(
                                    card = CardDraft(TeamSide.HOME, novak.appearanceId, minute = MinuteDraft("20")),
                                    cardProblems = listOf(CardProblem.NO_REASON),
                                ),
                            now = KICKOFF,
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Zadejte důvod.").assertIsDisplayed()
            // The form's own warning about vague red-card reasons.
            onNodeWithText("zmaření vyložené šance", substring = true).assertIsDisplayed()
        }

    @Test
    fun aRedCardMustSayWhetherItWasStraightOrASecondYellow() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state =
                                state(
                                    card =
                                        CardDraft(
                                            TeamSide.HOME,
                                            novak.appearanceId,
                                            colour = CardColour.RED,
                                            minute = MinuteDraft("40"),
                                        ),
                                    cardProblems = listOf(CardProblem.NO_DISMISSAL_KIND),
                                ),
                            now = KICKOFF,
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Přímá ČK").assertIsDisplayed()
            // The literal string the form uses.
            onNodeWithText("2. ŽK").assertIsDisplayed()
            onNodeWithText("Vyberte, zda jde o přímou ČK, nebo o 2. ŽK.").assertIsDisplayed()
        }

    @Test
    fun theTwoMinutesNoIntegerHoldsAreOfferedAsChoices() =
        runComposeUiTest {
            // `30´+` and `60´+` are ordinary values on this form.
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state = state(card = CardDraft(TeamSide.HOME, novak.appearanceId)),
                            now = KICKOFF,
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNodeWithText("30´+ (poločas)").assertIsDisplayed()
            onNodeWithText("60´+ (po skončení)").performClick()

            assertTrue(
                events.any {
                    it is ConsoleEvent.CardEdited &&
                        it.draft.minute.toMinute() == Minute.AfterFinalWhistle
                },
            )
        }

    @Test
    fun aSecondYellowIsFlaggedBeforeItIsIssuedRatherThanAfter() =
        runComposeUiTest {
            val booked = row("a-baca", 13, "Bača", "Tomáš", yellows = 1)
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state =
                                state(
                                    entry = entry(rows = listOf(booked)),
                                    card = CardDraft(TeamSide.HOME, booked.appearanceId, minute = MinuteDraft("49")),
                                ),
                            now = KICKOFF,
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Druhá znamená vyloučení", substring = true).assertIsDisplayed()
        }

    @Test
    fun aCardCanBeShownToSomebodyWithNoJerseyNumber() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF, onEvent = events::add) } }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Karta jiné osobě"))
            onNodeWithText("Karta jiné osobě").performClick()

            assertEquals<List<ConsoleEvent>>(
                listOf(ConsoleEvent.CardOpened(null, TeamSide.HOME)),
                events,
            )
        }

    // ------------------------------------------------------------------
    // Dismissal and the power play
    // ------------------------------------------------------------------

    @Test
    fun aSentOffPlayerKeepsTheirRowAndLosesTheirButtons() =
        runComposeUiTest {
            val sentOff = row("a-baca", 13, "Bača", "Tomáš", dismissed = true)
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(state = state(entry(rows = listOf(sentOff))), now = KICKOFF, onEvent = {})
                    }
                }
            }

            onNodeWithText("Bača Tomáš").assertIsDisplayed()
            onNodeWithText("Vyloučen").assertIsDisplayed()
            onNodeWithContentDescription("Gól").assertDoesNotExist()
            onNodeWithContentDescription("Karta").assertDoesNotExist()
        }

    @Test
    fun aRunningPowerPlayIsShownWithTheTimeLeftOnIt() =
        runComposeUiTest {
            val powerPlay =
                PowerPlay(
                    shortHandedSide = TeamSide.AWAY,
                    startedAt = KICKOFF + 40.minutes,
                    dismissedAtMinute = Minute.Played(40),
                )
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state = state(entry(powerPlays = listOf(powerPlay))),
                            now = KICKOFF + 44.minutes,
                            onEvent = {},
                        )
                    }
                }
            }

            // Six minutes left of the ten, and it names the side that is short.
            onNodeWithText("Oslabení: ${UiTestData.awayTeam.name}, zbývá 6:00").assertIsDisplayed()
        }

    @Test
    fun aPowerPlayDisappearsWhenItsTenMinutesAreUp() =
        runComposeUiTest {
            // Not shortened by a goal, not extended by anything: ten minutes.
            val powerPlay =
                PowerPlay(
                    shortHandedSide = TeamSide.AWAY,
                    startedAt = KICKOFF + 40.minutes,
                    dismissedAtMinute = Minute.Played(40),
                )
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        ConsoleScreen(
                            state = state(entry(powerPlays = listOf(powerPlay))),
                            now = KICKOFF + 51.minutes,
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("Oslabení", substring = true).assertDoesNotExist()
        }

    @Test
    fun theLastThingRecordedCanBeTakenBack() =
        runComposeUiTest {
            val events = mutableListOf<ConsoleEvent>()
            withLanguage("cs") {
                setContent { PsmfTheme { ConsoleScreen(state = state(), now = KICKOFF, onEvent = events::add) } }
            }

            onNodeWithText("Vzít zpět").performClick()

            assertTrue(events.contains(ConsoleEvent.UndoPressed))
        }
}
