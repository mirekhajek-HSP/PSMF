package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Where the text fields of one window are, so a tap can tell "in a field" from
 * "outside".
 *
 * Asked at the moment of the tap and answered from the fields' current
 * positions, rather than from which field has focus: a tap on a second field
 * and the focus change it causes arrive in one dispatch, and the order they
 * are noticed in is not something to build a rule on.
 */
internal class FieldRegistry {
    private val fields = mutableListOf<FieldPlace>()

    fun add(field: FieldPlace) {
        fields += field
    }

    fun remove(field: FieldPlace) {
        fields -= field
    }

    /** [windowPosition] is in the window's coordinates, as the fields' places are. */
    fun contains(windowPosition: Offset): Boolean = fields.any { it.contains(windowPosition) }
}

/** One field's place on screen: whatever it was last laid out at, if it still is. */
internal class FieldPlace {
    var coordinates: LayoutCoordinates? = null

    fun contains(windowPosition: Offset): Boolean =
        coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.contains(windowPosition) == true
}

internal val LocalFieldRegistry = compositionLocalOf<FieldRegistry?> { null }

/**
 * Takes the focus away from whatever has it, and asks the keyboard to go.
 *
 * Both. Clearing focus is what closes the keyboard on Android and on iOS
 * when a field had it. Asking as well is for the case where there is nothing
 * to clear and the keyboard is up anyway, which is a number pad: it has no
 * return key to say "done", and on iOS no other way off the screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

/**
 * The keyboard goes away when the referee is done with it, on every screen
 * inside.
 *
 * "Done with it" is any of: a tap that is not in a text field -- including one
 * on a button, a chip or a switch, which consume their own taps and would
 * otherwise leave the keyboard over the screen the referee has moved on from;
 * the start of a scroll; or, by [DismissKeyboardWhenChanged], leaving for
 * another screen. The keyboard's own action key is the field's business, in
 * [PsmfTextField].
 *
 * **Nothing is consumed.** This only listens, in the first pass on the way
 * down and the last on the way back, so the screen beneath behaves exactly as
 * it did and a field that is tapped still gets its own tap.
 *
 * One of these for each window: a dialog is composed in a window of its own,
 * and taps in it never reach the screen's. A field finds the nearest.
 */
@Composable
fun KeyboardDismissal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val registry = remember { FieldRegistry() }
    val dismiss = rememberUpdatedState(rememberKeyboardDismisser())
    val origin = remember { WindowOrigin() }

    val scrolling = remember { DismissOnScroll { dismiss.value() } }

    Box(
        modifier =
            modifier
                .onGloballyPositioned { origin.position = it.positionInWindow() }
                .nestedScroll(scrolling)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        scrolling.rearm()
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            val moved = (change.position - down.position).getDistance()
                            if (change.changedToUpIgnoreConsumed()) {
                                if (moved <= slop && !registry.contains(origin.position + change.position)) {
                                    dismiss.value()
                                }
                                return@awaitEachGesture
                            }
                            // Moved off: it is a drag, and a scroll will say so.
                            if (moved > slop) return@awaitEachGesture
                        }
                    }
                },
    ) {
        CompositionLocalProvider(LocalFieldRegistry provides registry, content = content)
    }
}

private class WindowOrigin {
    var position: Offset = Offset.Zero
}

/**
 * Says so once when a finger starts moving a list, and not again until the
 * next touch: every pixel of a drag passes through here and the keyboard has
 * only to be asked once.
 *
 * Only `UserInput`. A fling carrying on is not the referee doing anything,
 * and a list scrolling itself to keep the focused field in view -- which it
 * does when the keyboard opens -- is the keyboard being made room for.
 */
private class DismissOnScroll(
    private val dismiss: () -> Unit,
) : NestedScrollConnection {
    private var told = false

    fun rearm() {
        told = false
    }

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && !told) {
            told = true
            dismiss()
        }
        return Offset.Zero
    }
}

/**
 * Drops the keyboard whenever [key] changes: the step or the tab the referee
 * has moved to.
 *
 * Not only on the tap that caused it. A screen is also left by the system
 * back gesture and by itself -- the lineup moves on to the console when its
 * problems are gone -- and the keyboard has to go with it in every case.
 */
@Composable
fun DismissKeyboardWhenChanged(key: Any?) {
    val dismiss = rememberKeyboardDismisser()
    LaunchedEffect(key) { dismiss() }
}
