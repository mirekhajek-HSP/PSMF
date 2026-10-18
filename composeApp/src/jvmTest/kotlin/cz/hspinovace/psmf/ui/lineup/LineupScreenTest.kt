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
 * Screen 3's flows, driven through the plain composable.
 *
 * The referee marks who came, names a captain and a goalkeeper, and the
 * captain taps once. Around that: the number from a player's card, a player
 * without one, someone who is not on the list at all -- and the rules that
 * must not be got wrong: no rodné číslo anywhere, no way to type over a
 * number PSMF's own data supplied, and a suspension badge that warns without
 * ever clearing anyone.
 */
@OptIn(ExperimentalTestApi::class)
class LineupScreenTest : LineupScreenTestBase() {
    // ------------------------------------------------------------------
    // Marking who came
    // ------------------------------------------------------------------

    @Test
    fun nobodyIsMarkedUntilTheyAreTappedAndTheScreenSaysHowMany() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            onNodeWithText("Přítomno 0 z 3").assertIsDisplayed()
            onNodeWithText("Novák Jan").assertIsDisplayed()
            // Nobody has come, so there is nothing to type for anybody.
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }

    @Test
    fun tappingANameMarksThatPlayerAndTappingAgainTakesItBack() =
        runComposeUiTest {
            // THE INVERSION. The same tap both ways: what it does depends on
            // what the row is now.
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(listOf(member(novak), member(poupe, present = true, card = "59002"))),
                            onEvent = events::add,
                        )
                    }
                }
            }

            onNodeWithText("Novák Jan").performClick()
            onNodeWithText("Poupě Petr").performClick()

            assertEquals<List<LineupEvent>>(
                listOf(LineupEvent.PresenceToggled(novak.id), LineupEvent.PresenceToggled(poupe.id)),
                events,
            )
        }

    @Test
    fun aMarkedRowIsOnAndAnUnmarkedOneIsOff() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(listOf(member(novak, present = true), member(poupe))), onEvent = {})
                    }
                }
            }

            onNode(hasText("Novák Jan")).assertIsOn()
            onNode(hasText("Poupě Petr")).assertIsOff()
        }

    @Test
    fun aMarkedRowOpensItsJerseyAndRpNumberFields() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(listOf(member(novak, present = true))), onEvent = {}) }
                }
            }

            // Dres and Číslo RP: and nothing else, not even a date of birth.
            onAllNodes(hasSetTextAction()).assertCountEquals(2)
            onNodeWithText("Číslo RP").assertIsDisplayed()
            onNodeWithText("Datum narození").assertDoesNotExist()
        }

    @Test
    fun markingARowDoesNotMoveTheRowsBelowIt() =
        runComposeUiTest {
            // The thumb is on the next name. It must still be there.
            var current by mutableStateOf(state(listOf(member(novak), member(poupe), member(kriz))))
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = current, onEvent = {}) } }

                val before = onNodeWithText("Kříž Ondřej").getBoundsInRoot()
                current = state(listOf(member(novak, present = true), member(poupe), member(kriz)))
                waitForIdle()
                val after = onNodeWithText("Kříž Ondřej").getBoundsInRoot()

                assertEquals(before.top, after.top, "marking the first row moved the third")
                // And the row that was marked is no taller than it was.
                val marked = onNodeWithText("Novák Jan").getBoundsInRoot()
                assertTrue(marked.height > 0.dp)
            }
        }

    @Test
    fun markingARowDoesNotMoveTheRowsBelowItAtALargeFontEither() =
        runComposeUiTest {
            // The referee population skews older and many run the system font
            // turned up. A row sized for one scale is wrong at the next, which
            // is why unmarked rows lay out the same controls rather than
            // reserving a height.
            var current by mutableStateOf(state(listOf(member(novak), member(poupe), member(kriz))))
            withLanguage("cs") {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = 1.5f)) {
                        // Tall enough to lay all three rows out: at this scale
                        // the host's window shows barely one.
                        Box(modifier = Modifier.requiredSize(width = 411.dp, height = 2400.dp)) {
                            PsmfTheme { LineupScreen(state = current, onEvent = {}) }
                        }
                    }
                }

                val before = onNodeWithText("Kříž Ondřej").getBoundsInRoot()
                current = state(listOf(member(novak, present = true), member(poupe), member(kriz)))
                waitForIdle()

                assertEquals(before.top, onNodeWithText("Kříž Ondřej").getBoundsInRoot().top)
            }
        }

    @Test
    fun sayingAPlayerHasNoCardDoesNotMoveTheRowsBelowIt() =
        runComposeUiTest {
            // The same thumb, one control over: "Nemá průkaz" turns the row to
            // the date of birth, and the name below must still be where it was.
            // On a narrow phone, where a hint that is a few characters too long
            // takes a second line and the row grows by it.
            var current by mutableStateOf(state(listOf(member(novak, present = true), member(poupe), member(kriz))))
            withLanguage("cs") {
                setContent {
                    Box(modifier = Modifier.requiredSize(width = 360.dp, height = 2400.dp)) {
                        PsmfTheme { LineupScreen(state = current, onEvent = {}) }
                    }
                }

                val before = onNodeWithText("Poupě Petr").getBoundsInRoot()
                current =
                    state(listOf(member(novak, present = true, cardPresent = false), member(poupe), member(kriz)))
                waitForIdle()

                onNodeWithText("Datum narození").assertExists()
                assertEquals(before.top, onNodeWithText("Poupě Petr").getBoundsInRoot().top, "'Nemá průkaz' moved it")
            }
        }

    @Test
    fun aSquadOfTwentyStaysUsableAndTheCountStaysInView() =
        runComposeUiTest {
            // After round five a team is about eleven; it only grows. The
            // count is pinned, or the referee scrolls back up to ask it.
            val squad =
                (1..20).map { n ->
                    val p = player("p$n", "Hráč${"abcdefghijklmnopqrst"[n - 1]}", "Jan", n)
                    member(p, present = n <= 8, card = if (n <= 8) "590%02d".format(n) else "")
                }
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(squad), onEvent = {}) } }

                onNodeWithText("Přítomno 8 z 20").assertIsDisplayed()
                onNode(hasScrollAction()).performScrollToNode(hasText("Hráčt Jan"))
                onNodeWithText("Hráčt Jan").assertIsDisplayed()
                onNodeWithText("Přítomno 8 z 20").assertIsDisplayed()
            }
        }

    // ------------------------------------------------------------------
    // Captain and goalkeeper
    // ------------------------------------------------------------------

    @Test
    fun theCaptainAndTheGoalkeeperArePickedFromThoseMarkedAndNobodyElse() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            val members =
                listOf(
                    member(novak, present = true, card = "59001"),
                    member(poupe, present = true, card = "59002"),
                    member(kriz),
                )
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(members), onEvent = events::add) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Kapitán a brankář"))
                onNodeWithText("Kapitán").performClick()

                // The menu says each present player's number beside the name,
                // which no list row does; nobody who did not come is in it.
                onNodeWithText("č. 9").assertExists()
                onNodeWithText("č. 11").assertExists()
                onNodeWithText("č. 18").assertDoesNotExist()

                onNodeWithText("č. 11").performClick()
            }

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.CaptainChosen(poupe.id)), events)
        }

    @Test
    fun theTwoRolesAreWrittenUnderTheNameOfWhoHoldsThem() =
        runComposeUiTest {
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(members, captain = novak.id, goalkeeper = novak.id), onEvent = {})
                    }
                }
            }

            // One person who is both reads as both, on one row.
            onNodeWithText("Kapitán · Brankář").assertIsDisplayed()
        }

    @Test
    fun theCaptainsOneTapNamesTheCaptainAndPicksNobodyAndHasNoDeputy() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            val members =
                listOf(member(novak, present = true, card = "59001"), member(poupe, present = true, card = "59002"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(members, captain = novak.id, goalkeeper = poupe.id),
                            onEvent = events::add,
                        )
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Kapitáni potvrzují", substring = true))
                // The form's own words, and the CAPTAIN's claim rather than the
                // app's. The app says nothing about who may play.
                onNodeWithText("Kapitáni potvrzují, že všichni hráči startují oprávněně.").assertIsDisplayed()

                onNodeWithText("Potvrzuje Novák Jan").performClick()
                // No list of names to pick from, and nobody to say is a deputy.
                onNodeWithText("Zástupce kapitána").assertDoesNotExist()
                onNodeWithText("Kdo sestavu potvrzuje?").assertDoesNotExist()
            }

            assertEquals<List<LineupEvent>>(listOf(LineupEvent.CaptainConfirms), events)
        }

    @Test
    fun withNoCaptainMarkedThereIsNobodyToConfirm() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(listOf(member(novak, present = true, card = "59001"))), onEvent = {})
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Nejdřív vyberte kapitána."))
                onNodeWithText("Nejdřív vyberte kapitána.").assertIsDisplayed()
                onNodeWithText("Potvrzuje", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun aConfirmedLineupSaysWhoConfirmedItAndOffersNoSecondTap() =
        runComposeUiTest {
            val members = listOf(member(novak, present = true, card = "59001"))
            val confirmation = LineupConfirmation(Instant.parse("2026-10-17T17:05:00Z"), PersonName.of("Novák Jan"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state =
                                state(
                                    members,
                                    captain = novak.id,
                                    goalkeeper = novak.id,
                                    confirmation = confirmation,
                                ),
                            onEvent = {},
                        )
                    }
                }

                onNode(hasScrollAction()).performScrollToNode(hasText("Potvrdil kapitán Novák Jan"))
                onNodeWithText("Potvrdil kapitán Novák Jan").assertIsDisplayed()
                onNodeWithText("Potvrzuje", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun theProblemsNameTheTeamAndWhatIsMissing() =
        runComposeUiTest {
            val whole = state(listOf(member(novak, present = true, card = "59001")))
            val shown = whole.copy(showProblems = true, problems = whole.entry!!.problems())
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = shown, onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("není vybraný kapitán", substring = true))
                onNodeWithText("Kominíci: není vybraný kapitán.").assertIsDisplayed()
                onNodeWithText("Kominíci: není vybraný brankář.").assertIsDisplayed()
            }
        }

    // ------------------------------------------------------------------
    // The number from the card
    // ------------------------------------------------------------------

    /** What psmf.cz gives us: a name and a team, nothing for Číslo RP. */
    private fun bilek() = player("bilek", "Bílek", "Ondřej", 7).copy(defaultJerseyNumber = null)

    @Test
    fun aPlayerWithNoLeagueRpIsAskedForTheNumberFromTheirCard() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(listOf(member(bilek(), present = true))), onEvent = {}) }
                }

                onNodeWithText("Bílek Ondřej").assertIsDisplayed()
                onNodeWithText("Číslo RP").assertIsDisplayed()
                onNodeWithText("Nemá průkaz").assertIsDisplayed()
            }
        }

    @Test
    fun typingTheNumberInTheRowReportsTheWholeFieldForThatPlayer() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(listOf(member(bilek(), present = true))), onEvent = events::add)
                    }
                }
                onNodeWithText("Číslo RP").performTextInput("59001")
            }

            assertEquals(LineupEvent.CardNumberTyped(PlayerId("bilek"), "59001"), events.last())
        }

    @Test
    fun aLeagueRpNumberIsShownAndThereIsNowhereToTypeOverIt() =
        runComposeUiTest {
            val registered = player("kominik", "Kominík", "Jan", 5, rp = RpNumber("59001"))
            withLanguage("cs") {
                setContent {
                    PsmfTheme { LineupScreen(state = state(listOf(member(registered, present = true))), onEvent = {}) }
                }

                onNodeWithText("Číslo RP: 59001").assertIsDisplayed()
                // Only the jersey number can be typed in this row.
                onAllNodes(hasSetTextAction()).assertCountEquals(1)
            }
        }

    @Test
    fun noCardTurnsTheSameRowOverToTheDateOfBirthAndTheButtonSaysWhichWayItWillGoBack() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            var current by mutableStateOf(state(listOf(member(bilek(), present = true))))
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = current, onEvent = events::add) } }

                onNodeWithText("Nemá průkaz").performClick()
                assertEquals<List<LineupEvent>>(listOf(LineupEvent.RegistrationCardToggled(PlayerId("bilek"))), events)

                current = state(listOf(member(bilek(), present = true, cardPresent = false)))
                waitForIdle()

                onNodeWithText("Datum narození").assertIsDisplayed()
                onNodeWithText("Má průkaz").assertIsDisplayed()
            }
        }

    @Test
    fun aTypedDateIsEchoedSoAMistakeIsVisible() =
        runComposeUiTest {
            val typed = member(bilek(), present = true, cardPresent = false).withDateOfBirthTyped("18.5.1992")
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(listOf(typed)), onEvent = {}) } }

                onNodeWithText("18. 5. 1992").assertIsDisplayed()
            }
        }

    @Test
    fun aDateOfBirthTheLeagueHoldsIsShownAndNotAskedFor() =
        runComposeUiTest {
            val withDate = player("dated", "Datovaný", "Jan", 3, born = LocalDate(1990, 6, 15))
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = state(listOf(member(withDate, present = true, cardPresent = false))),
                            onEvent = {},
                        )
                    }
                }

                onNodeWithText("Datum narození: 15. 6. 1990").assertIsDisplayed()
                onAllNodes(hasSetTextAction()).assertCountEquals(1)
            }
        }

    @Test
    fun theProblemNamesThePlayerAndSaysWhatToType() =
        runComposeUiTest {
            val missing =
                state(
                    listOf(member(bilek(), present = true)),
                    captain = PlayerId("bilek"),
                    goalkeeper = PlayerId("bilek"),
                )
            val shown = missing.copy(showProblems = true, problems = missing.entry!!.problems())
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = shown, onEvent = {}) } }

                onNode(hasScrollAction()).performScrollToNode(hasText("Bílek Ondřej:", substring = true))
                onNodeWithText(
                    "Bílek Ondřej: chybí údaj do sloupce Číslo RP. " +
                        "Opište číslo z průkazu, nebo u hráče bez průkazu zadejte datum narození.",
                ).assertIsDisplayed()
            }
        }

    @Test
    fun noRodneCisloIsOfferedOrHintedOrLabelledAnywhereOnTheScreen() =
        runComposeUiTest {
            // NON-NEGOTIABLE (DECISIONS 2026-10-17): the column takes the
            // number from the card, or the date of birth. A national
            // identifier is a far heavier obligation, and what the tester
            // says is evidence for A28, not an answer to it.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state =
                                state(
                                    listOf(
                                        member(bilek(), present = true),
                                        member(poupe, present = true, cardPresent = false),
                                    ),
                                    newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId),
                                ),
                            onEvent = {},
                        )
                    }
                }

                onAllNodes(hasText("rodn", substring = true, ignoreCase = true), useUnmergedTree = true)
                    .assertCountEquals(0)
                onAllNodes(hasText("birth number", substring = true, ignoreCase = true), useUnmergedTree = true)
                    .assertCountEquals(0)
            }
        }

    // ------------------------------------------------------------------
    // Jersey numbers
    // ------------------------------------------------------------------

    @Test
    fun editingAJerseyNumberReportsIt() =
        runComposeUiTest {
            val events = mutableListOf<LineupEvent>()
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(state = state(listOf(member(novak, present = true))), onEvent = events::add)
                    }
                }
            }

            onNodeWithText("9").performTextInput("7")

            assertTrue(events.any { it is LineupEvent.JerseyNumberChanged })
        }

    @Test
    fun aPlayerWhoDidNotComeHasNoJerseyNumberToEdit() =
        runComposeUiTest {
            // The number belongs to the appearance, and a player who did not
            // come has no appearance. A field they could type in would invite
            // a clash that cannot exist.
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(listOf(member(novak))), onEvent = {}) } }
            }

            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            // And it is laid out all the same, which is what stops rows moving.
            assertNotEquals(0.dp, onNodeWithText("Novák Jan").getBoundsInRoot().height)
        }

    // ------------------------------------------------------------------
    // Eligibility
    // ------------------------------------------------------------------

    @Test
    fun anEvenYellowTotalWarnsAndCarriesItsDate() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }
            }

            // Whether or not they have come: the referee sees it before marking.
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
                    PsmfTheme {
                        LineupScreen(
                            state = state(listOf(member(novak, present = true), member(odd, present = true))),
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("může mít stop", substring = true).assertDoesNotExist()
            onNode(hasScrollAction()).performScrollToNode(hasText("Aplikace nepotvrzuje", substring = true))
            onNodeWithText("Aplikace nepotvrzuje", substring = true).assertIsDisplayed()
        }

    // ------------------------------------------------------------------
    // Kit
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
    fun aSignedReportSaysBeforeTheFirstEditThatAnyChangeTakesTheSignaturesBack() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(signed = true), onEvent = {}) } }

                onNodeWithText("Zpráva je podepsaná. Každá změna tady zruší podpisy", substring = true)
                    .assertIsDisplayed()
            }
        }

    @Test
    fun anUnsignedReportShowsNoSuchNotice() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { PsmfTheme { LineupScreen(state = state(), onEvent = {}) } }

                onNodeWithText("Zpráva je podepsaná.", substring = true).assertDoesNotExist()
            }
        }
}
