package cz.hspinovace.psmf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.MatchId
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.assessment.AssessmentScreen
import cz.hspinovace.psmf.ui.assessment.AssessmentUiState
import cz.hspinovace.psmf.ui.header.FixtureHeadline
import cz.hspinovace.psmf.ui.header.MatchHeaderScreen
import cz.hspinovace.psmf.ui.header.MatchHeaderUiState
import cz.hspinovace.psmf.ui.lineup.LineupScreen
import cz.hspinovace.psmf.ui.lineup.LineupUiState
import cz.hspinovace.psmf.ui.navigation.AppNavigator
import cz.hspinovace.psmf.ui.navigation.Destination
import cz.hspinovace.psmf.ui.navigation.Tab
import cz.hspinovace.psmf.ui.shell.AppShell
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.AssessmentDraft
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.MatchHeaderEntry
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MATCH = MatchId("m1")

/**
 * What a referee expects of the keyboard, on every screen (prompt 13, Part 1).
 *
 * The tester on an iPhone: "Nemizí klávesnice -- všude". Nothing in the app
 * ever cleared focus, so a keyboard opened for one field stayed open over the
 * next screen, the number pad had no way to close at all on iOS (which gives
 * it no return key), and the last field of a form still said "Next".
 *
 * Driven through the real [AppShell] and the real screens, because the point
 * is that the behaviour is the shell's and every screen gets it, not that one
 * screen remembered to ask.
 *
 * **What the keyboard *is* cannot be seen here** -- the test host has no
 * software keyboard. What can be seen is every instruction the app gives it:
 * the focus it drops and the `hide()` it asks for, which is what the
 * recording controller below counts.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KeyboardTest {
    private val headline =
        FixtureHeadline(
            venueCode = "ZAKOS",
            date = UiTestData.fixture.date,
            time = UiTestData.fixture.time,
            leagueCode = "6K",
            groupName = "6. liga K",
            homeTeamName = UiTestData.homeTeam.name,
            awayTeamName = UiTestData.awayTeam.name,
        )

    private fun headerState() =
        MatchHeaderUiState(
            loading = false,
            fixture = headline,
            entry = MatchHeaderEntry(),
            teamOptions = listOf("Sokol"),
            otherTeamSelected = false,
            problems = emptyList(),
        )

    private class RecordingKeyboard : SoftwareKeyboardController {
        var hidden = 0

        override fun hide() {
            hidden++
        }

        override fun show() = Unit
    }

    /**
     * The real navigator, mirrored into Compose state by hand (see
     * `AppShellTest` for why), with a way to move without a tap.
     */
    private class Rig {
        val navigator = AppNavigator().also { it.openReport(Destination.MatchHeader(MATCH)) }
        val navigation = mutableStateOf(navigator.state.value)

        fun goTo(destination: Destination) {
            navigator.goTo(destination)
            navigation.value = navigator.state.value
        }
    }

    /** The header in the real shell, with every instruction to the keyboard counted. */
    @Composable
    private fun HeaderInShell(
        keyboard: RecordingKeyboard,
        rig: Rig = Rig(),
    ) {
        PsmfTheme {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                AppShell(
                    navigation = rig.navigation.value,
                    title = "T",
                    reportInProgress = false,
                    onSelectTab = {
                        rig.navigator.select(it)
                        rig.navigation.value = rig.navigator.state.value
                    },
                    onBack = {
                        rig.navigator.back()
                        rig.navigation.value = rig.navigator.state.value
                    },
                ) { modifier ->
                    MatchHeaderScreen(state = headerState(), onEvent = {}, modifier = modifier)
                }
            }
        }
    }

    private fun ComposeUiTest.referee(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[0]

    private fun ComposeUiTest.assistant(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[1]

    private fun ComposeUiTest.nothingIsFocused() = onAllNodes(isFocused()).assertCountEquals(0)

    // -----------------------------------------------------------------
    // Getting rid of it
    // -----------------------------------------------------------------

    @Test
    fun tappingOutsideAFieldTakesTheFocusAwayFromIt() =
        runComposeUiTest {
            val keyboard = RecordingKeyboard()
            withLanguage("cs") {
                setContent { HeaderInShell(keyboard) }

                referee().performClick().assertIsFocused()
                // A label: not a field and not a control. The plainest "outside".
                onNodeWithText("Utkání").performClick()

                referee().assertIsNotFocused()
                assertTrue(keyboard.hidden > 0, "the keyboard was never asked to go")
            }
        }

    @Test
    fun tappingAControlWhileTypingDropsTheFocusToo() =
        runComposeUiTest {
            // A switch consumes its own tap, so a handler that only hears
            // unconsumed taps would leave the keyboard over the screen the
            // referee has just moved on from.
            val keyboard = RecordingKeyboard()
            withLanguage("cs") {
                setContent { HeaderInShell(keyboard) }

                referee().performClick().assertIsFocused()
                onAllNodes(isToggleable())[0].performClick()

                nothingIsFocused()
            }
        }

    @Test
    fun tappingTheFocusedFieldAgainLeavesItFocused() =
        runComposeUiTest {
            // The cost of the above if it were done carelessly: moving the
            // caret would close the keyboard it is being moved in.
            withLanguage("cs") {
                setContent { HeaderInShell(RecordingKeyboard()) }

                referee().performClick().assertIsFocused()
                referee().performClick().assertIsFocused()
            }
        }

    @Test
    fun tappingAnotherFieldMovesTheFocusInsteadOfDroppingIt() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { HeaderInShell(RecordingKeyboard()) }

                referee().performClick().assertIsFocused()
                assistant().performClick()

                assistant().assertIsFocused()
                referee().assertIsNotFocused()
            }
        }

    @Test
    fun startingToScrollTakesTheFocusAway() =
        runComposeUiTest {
            val keyboard = RecordingKeyboard()
            withLanguage("cs") {
                setContent { HeaderInShell(keyboard) }

                referee().performClick().assertIsFocused()
                onNode(hasScrollAction()).performTouchInput { swipeUp(startY = centerY, endY = centerY - SWIPE) }

                nothingIsFocused()
                assertTrue(keyboard.hidden > 0, "the keyboard was never asked to go")
            }
        }

    @Test
    fun leavingTheStepTakesTheKeyboardAway() =
        runComposeUiTest {
            // Not by a tap: a screen is also left by the system back gesture,
            // and by itself -- the lineup moves on when its problems are
            // gone. Nothing here touches the screen, and the keyboard still
            // has to go.
            val keyboard = RecordingKeyboard()
            val rig = Rig()
            withLanguage("cs") {
                setContent { HeaderInShell(keyboard, rig) }

                referee().performClick().assertIsFocused()
                val before = keyboard.hidden
                rig.goTo(Destination.Lineup(MATCH))
                waitForIdle()

                assertTrue(keyboard.hidden > before, "the keyboard stayed up over the next screen")
                nothingIsFocused()
            }
        }

    // -----------------------------------------------------------------
    // The action key
    // -----------------------------------------------------------------

    @Test
    fun nextMovesOnToTheNextFieldOfTheForm() =
        runComposeUiTest {
            // The add-player form, not the header: on this host a switch
            // between two fields takes focus in the traversal, which a phone
            // in touch mode does not do, and would make this say "Next
            // landed on a switch" about a screen where it is fine.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = lineupState(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId)),
                            onEvent = {},
                        )
                    }
                }

                val fields = onAllNodes(hasSetTextAction())
                fields[0].performClick().performImeAction()
                fields[1].assertIsFocused()
                fields[0].assertIsNotFocused()
            }
        }

    @Test
    fun theLastFieldOfTheHeaderSaysDoneAndLetsGo() =
        runComposeUiTest {
            val keyboard = RecordingKeyboard()
            withLanguage("cs") {
                setContent { HeaderInShell(keyboard) }

                assistant().performClick().assertIsFocused()
                assistant().performImeAction()

                // Not "moved on to whatever is next on the page": nothing
                // is focused and the keyboard has been told to go.
                nothingIsFocused()
                assertTrue(keyboard.hidden > 0, "Done did not close the keyboard")
            }
        }

    @Test
    fun theLastFieldOfTheAddPlayerFormSaysDoneToo() =
        runComposeUiTest {
            // No recording keyboard here: a dialog supplies its own
            // controller, over the one a test provides, so what can be seen
            // is the focus -- which is what closes the keyboard on a phone.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = lineupState(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId)),
                            onEvent = {},
                        )
                    }
                }

                // Surname, first name, date of birth.
                val fields = onAllNodes(hasSetTextAction())
                fields[0].performClick().performImeAction()
                fields[1].assertIsFocused().performImeAction()
                fields[2].assertIsFocused().performImeAction()

                nothingIsFocused()
            }
        }

    // -----------------------------------------------------------------
    // A dialog is another window
    // -----------------------------------------------------------------

    @Test
    fun tappingTheBodyOfADialogTakesTheFocusAwayFromItsField() =
        runComposeUiTest {
            // A dialog is composed in a window of its own, so the shell's
            // handler never hears a tap inside it.
            withLanguage("cs") {
                setContent {
                    PsmfTheme {
                        LineupScreen(
                            state = lineupState(newPlayer = NewPlayerRequest(teamId = UiTestData.homeTeamId)),
                            onEvent = {},
                        )
                    }
                }

                onAllNodes(hasSetTextAction())[0].performClick().assertIsFocused()
                onNodeWithText("Číslo RP se nezadává", substring = true).performClick()

                nothingIsFocused()
            }
        }

    // -----------------------------------------------------------------
    // The number pad
    // -----------------------------------------------------------------

    @Test
    fun aNumberFieldCanBeLeftWithoutAReturnKey() =
        runComposeUiTest {
            // iOS gives a number pad no return key at all, so tapping
            // outside is the only way off it. The assessment's fields are
            // all numbers but the commentary.
            val keyboard = RecordingKeyboard()
            val navigator = AppNavigator().also { it.openReport(Destination.Assessment(MATCH)) }
            withLanguage("cs") {
                setContent {
                    var navigation by remember { mutableStateOf(navigator.state.value) }
                    PsmfTheme {
                        CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                            AppShell(
                                navigation = navigation,
                                title = "T",
                                reportInProgress = false,
                                onSelectTab = { navigation = navigator.state.value },
                                onBack = { navigation = navigator.state.value },
                            ) { modifier ->
                                AssessmentScreen(
                                    state =
                                        AssessmentUiState(
                                            loading = false,
                                            draft = AssessmentDraft(),
                                            homeTeam = UiTestData.homeTeam.name,
                                            awayTeam = UiTestData.awayTeam.name,
                                        ),
                                    onEvent = {},
                                    modifier = modifier,
                                )
                            }
                        }
                    }
                }

                onAllNodes(hasSetTextAction())[0].performClick().assertIsFocused()
                onNodeWithText("Povinný komentář").performClick()

                nothingIsFocused()
            }
        }

    // -----------------------------------------------------------------
    // None missing
    // -----------------------------------------------------------------

    @Test
    fun everyTextFieldInTheAppGoesThroughTheOneThatKnowsTheKeyboard() {
        // "List every text field in the app ... None missing" is only
        // checkable if there is one place a field can come from. A raw
        // `OutlinedTextField` is how a field escapes the keyboard rules: it
        // takes the platform's default action key, registers nowhere, and so
        // is the one a tap inside will wrongly dismiss.
        val sources = java.io.File(System.getProperty("psmf.sourceDirectory"))
        val rawFields =
            sources
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .associate { it.name to RAW_FIELD.findAll(it.readText()).count() }
                .filterValues { it > 0 }

        assertEquals(ALLOWED_RAW_FIELDS, rawFields, "a text field that bypasses PsmfTextField")
    }

    // -----------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------

    private fun lineupState(newPlayer: NewPlayerRequest? = null): LineupUiState {
        val home =
            TeamLineupEntry(
                side = TeamSide.HOME,
                team = UiTestData.homeTeam,
                members = emptyList(),
                kitId = UiTestData.homeTeam.primaryKit.id,
            )
        val away =
            TeamLineupEntry(
                side = TeamSide.AWAY,
                team = UiTestData.awayTeam,
                members = emptyList(),
                kitId = UiTestData.awayTeam.primaryKit.id,
            )
        return LineupUiState(loading = false, entry = LineupEntry(home, away), newPlayer = newPlayer)
    }

    private companion object {
        const val SWIPE = 200f

        /**
         * Counted per file rather than allowed wholesale: `FixtureFilterRow`
         * has a real text field next to its pickers, and an exemption for the
         * file would hide it. One in `PsmfTextField` -- the wrapper itself --
         * and one in `FixtureFilterRow`, the select box, which is a read-only
         * field that opens a menu and never raises a keyboard.
         */
        val ALLOWED_RAW_FIELDS = mapOf("PsmfTextField.kt" to 1, "FixtureFilterRow.kt" to 1)

        val RAW_FIELD = Regex("""\b(Outlined)?TextField\(|BasicTextField\(""")
    }
}
