package dev.bikram.remember.ui.common

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Note titles never contain a line break: a field that wraps visually (so it cannot be a
 * `SingleLine` field) must still turn any typed or pasted newline into a space.
 */
internal val TitleNoNewlinesTransformation =
    InputTransformation {
        if (asCharSequence().any { char -> char == '\n' || char == '\r' }) replaceLineBreaksWithSpaces()
    }

private fun TextFieldBuffer.replaceLineBreaksWithSpaces() {
    val selectionBefore = selection
    val sanitized = asCharSequence().toString().replace('\r', ' ').replace('\n', ' ')
    replace(0, length, sanitized)
    selection = selectionBefore
}

/**
 * Makes Enter on a hardware keyboard behave like the on-screen "Next" action - move to the next
 * field - instead of inserting a line break. A multi-line-capable field gets a newline from a
 * physical Enter (the IME action only fires from the soft keyboard), which a title would then
 * have to swallow as a stray space.
 */
@Composable
internal fun Modifier.enterMovesFocusNext(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!isEnter || event.isShiftPressed) {
            false
        } else {
            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Next)
            true
        }
    }
}
