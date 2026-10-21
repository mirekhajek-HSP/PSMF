package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The one place a field the referee types into is made.
 *
 * **Why there is a wrapper at all.** The app had no rule about the keyboard:
 * each field said what it happened to say, only three chose an action key,
 * and none of them ever let go. A tester on an iPhone: "the keyboard does not
 * go away, anywhere". A rule that every field has to remember to obey is a
 * rule that is broken by the next one added, so the rule lives here and
 * `KeyboardTest` fails the build if a field is made any other way.
 *
 * What it adds to a Material field:
 *  - [imeAction] says what the keyboard's action key does. `Next` moves to the
 *    next field of the form; the last field of a form says `Done`, and so does
 *    a field the referee edits by itself. `Done`, `Search`, `Go` and `Send`
 *    all end the same way: the focus goes and the keyboard with it. The
 *    platform default for `Search` does nothing at all.
 *  - It tells the enclosing [KeyboardDismissal] where it is, so a tap inside
 *    it is not mistaken for a tap outside.
 *  - It keeps the cursor where the referee put it, whatever the screen's
 *    state does. Material's own `String` field fits its cursor to the text it
 *    is given, and writes the fit back: a screen that hands the text back a
 *    frame after it was asked for (every one that goes through a ViewModel's
 *    `StateFlow`) is recomposed once with the old text, and the cursor stays
 *    at the start for good -- "Marek" typed came out "arekM" (0.3.0). Here
 *    the text on show is the screen's, the cursor is the referee's, and
 *    neither is written over the other.
 *  - It tells the enclosing [KeyboardDismissal] whether it has the focus, so
 *    that when the keyboard takes room from the screen the field being typed
 *    in is kept in view with the control under it.
 *
 * A number field is made the same way and ends the same way, but on iOS its
 * keyboard has no action key, so what ends it there is a tap anywhere else,
 * the start of a scroll, or leaving the screen (see [KeyboardDismissal]).
 *
 * Not for a pick-list: a read-only field that opens a menu raises no
 * keyboard and is not a place to type.
 */
@Composable
fun PsmfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    enabled: Boolean = true,
) {
    val registry = LocalFieldRegistry.current
    val place = remember { FieldPlace() }
    // Only while it can be typed in: a disabled field is not "a field" to
    // tap inside of, and a tap on the space it holds is a tap outside.
    DisposableEffect(registry, place, enabled) {
        if (enabled) registry?.add(place)
        onDispose { registry?.remove(place) }
    }

    val dismiss = rememberKeyboardDismisser()

    // What the referee did last, cursor included. Its text is only a record:
    // the text on show is always the screen's own (below), so a change the
    // screen refuses, or a text it sets itself, shows as it should.
    var typed by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    val shown = if (typed.text == value) typed else typed.copy(text = value)

    OutlinedTextField(
        value = shown,
        onValueChange = {
            typed = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier =
            modifier
                .bringIntoViewRequester(place.requester)
                .onFocusChanged { place.focused = it.hasFocus }
                .onGloballyPositioned { place.coordinates = it },
        // A disabled field takes no focus and no tap, so no keyboard: it is
        // how a row that is laid out but not yet in use stays out of the way.
        enabled = enabled,
        label = label,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions =
            KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
        // `Next` is left to the platform, which moves to the next field.
        keyboardActions =
            KeyboardActions(
                onDone = { dismiss() },
                onGo = { dismiss() },
                onSearch = { dismiss() },
                onSend = { dismiss() },
            ),
        shape = shape,
    )
}
