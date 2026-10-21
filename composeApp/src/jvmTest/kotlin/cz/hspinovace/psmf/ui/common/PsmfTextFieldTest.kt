package cz.hspinovace.psmf.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a character lands (0.3.1, Gate 1).
 *
 * The testers of 0.3.0: in a dialog the first letter is typed and the cursor
 * jumps in front of it -- "Marek" comes out as "arekM". A field that is given
 * its text by a ViewModel's `StateFlow` is told what it asked for one frame
 * after it asked, and a Material field that is recomposed in between, with
 * the text it had before, used to fit its cursor to that and keep it there.
 *
 * The screens in these tests do what the app's do: the field's
 * `onValueChange` is *asked*, the state is *changed* a recomposition later.
 */
@OptIn(ExperimentalTestApi::class)
class PsmfTextFieldTest {
    @Test
    fun typingMarekGivesMarekWhenTheStateComesBackAFrameLate() =
        runComposeUiTest {
            var held by mutableStateOf("")
            var asked = ""
            setContent { PsmfTheme { PsmfTextField(value = held, onValueChange = { asked = it }) } }

            val field = onNode(hasSetTextAction())
            for (letter in "Marek") {
                field.performTextInput(letter.toString())
                // The field recomposes with the text it had, because the
                // screen has not been told yet ...
                waitForIdle()
                // ... and now it is.
                held = asked
                waitForIdle()
            }

            assertEquals("Marek", held)
            field.assertTextEquals("Marek")
        }

    @Test
    fun typingMarekGivesMarekWhenTheStateIsChangedAtOnce() =
        runComposeUiTest {
            // The case that always worked, and must go on: a field whose
            // text is plain Compose state, changed in the same breath.
            var held by mutableStateOf("")
            setContent { PsmfTheme { PsmfTextField(value = held, onValueChange = { held = it }) } }

            val field = onNode(hasSetTextAction())
            for (letter in "Marek") {
                field.performTextInput(letter.toString())
                waitForIdle()
            }

            assertEquals("Marek", held)
            field.assertTextEquals("Marek")
        }

    @Test
    fun typingMarekGivesMarekWhenTheStateTakesTwoFramesToCome() =
        runComposeUiTest {
            var held by mutableStateOf("")
            var asked = ""
            setContent { PsmfTheme { PsmfTextField(value = held, onValueChange = { asked = it }) } }

            val field = onNode(hasSetTextAction())
            for (letter in "Marek") {
                field.performTextInput(letter.toString())
                waitForIdle()
                waitForIdle()
                held = asked
                waitForIdle()
            }

            assertEquals("Marek", held)
            field.assertTextEquals("Marek")
        }

    @Test
    fun aChangeTheScreenRefusesIsNotLeftOnShow() =
        runComposeUiTest {
            // A number field whose screen keeps only digits. The state does
            // not change, so there is nothing to wait for and nothing to
            // deliver: what the field shows is what the screen holds.
            var held by mutableStateOf("1")
            setContent {
                PsmfTheme {
                    PsmfTextField(value = held, onValueChange = { held = it.filter(Char::isDigit) })
                }
            }

            val field = onNode(hasSetTextAction())
            field.performTextInput("a")
            waitForIdle()

            assertEquals("1", held)
            field.assertTextEquals("1")
        }

    @Test
    fun aTextTheScreenSetsItselfReplacesWhatWasTyped() =
        runComposeUiTest {
            // A form that is cleared, a card number filled in for the
            // referee: the screen speaks and the field follows.
            var held by mutableStateOf("")
            setContent { PsmfTheme { PsmfTextField(value = held, onValueChange = { held = it }) } }

            val field = onNode(hasSetTextAction())
            field.performTextInput("Marek")
            waitForIdle()
            held = ""
            waitForIdle()
            field.assertTextEquals("")

            held = "Novák"
            waitForIdle()
            field.assertTextEquals("Novák")
        }
}
