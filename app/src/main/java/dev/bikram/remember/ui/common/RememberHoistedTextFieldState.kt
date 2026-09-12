package dev.bikram.remember.ui.common

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun rememberHoistedStringTextFieldState(
    text: String,
    onTextChange: (String) -> Unit,
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = text)
    LaunchedEffect(text) {
        val currentText = textFieldState.text.toString()
        if (currentText != text) {
            textFieldState.edit {
                replace(0, length, text)
                selection = TextRange(text.length)
            }
            // Every edit{} call records an undo entry, even this programmatic external-state sync
            // (as opposed to the user's own keystrokes, which land here as a no-op since the field
            // already has them). Android's back gesture can trigger the field's *own* undo before
            // navigating away when undo history is pending, which would otherwise let "back" revert
            // whatever caused this sync (e.g. an external state reset) instead of leaving the screen.
            textFieldState.undoState.clearHistory()
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { updatedText ->
                if (updatedText != text) {
                    onTextChange(updatedText)
                }
            }
    }
    return textFieldState
}

@Composable
internal fun rememberLocalStringTextFieldState(
    externalText: String,
    onExternalTextChange: (String) -> Unit,
    initialSelection: TextRange = TextRange(externalText.length),
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = externalText, initialSelection = initialSelection)
    LaunchedEffect(externalText) {
        val currentText = textFieldState.text.toString()
        if (currentText != externalText) {
            textFieldState.edit {
                replace(0, length, externalText)
                selection =
                    TextRange(
                        start = initialSelection.start.coerceIn(0, externalText.length),
                        end = initialSelection.end.coerceIn(0, externalText.length),
                    )
            }
            // See rememberHoistedStringTextFieldState for why this sync must not leave an
            // undo-able entry behind for the system back gesture to revert.
            textFieldState.undoState.clearHistory()
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { updatedText ->
                if (updatedText != externalText) {
                    onExternalTextChange(updatedText)
                }
            }
    }
    return textFieldState
}

@Composable
internal fun rememberLocalTextFieldValueState(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = value.text, initialSelection = value.selection)
    LaunchedEffect(value.text, value.selection) {
        val fieldText = textFieldState.text.toString()
        if (fieldText != value.text) {
            textFieldState.edit {
                replace(0, length, value.text)
                selection = value.selection
            }
            // See rememberHoistedStringTextFieldState for why this sync must not leave an
            // undo-able entry behind for the system back gesture to revert. This branch only ever
            // fires for programmatic changes (toolbar actions, cleanup heuristics) - plain typing
            // that update() left unmodified already matches the field and skips this entirely.
            textFieldState.undoState.clearHistory()
        } else if (textFieldState.selection != value.selection) {
            textFieldState.edit {
                selection = value.selection
            }
            textFieldState.undoState.clearHistory()
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow {
            textFieldState.text.toString() to textFieldState.selection
        }.distinctUntilChanged()
            .collect { (updatedText, updatedSelection) ->
                val updatedValue = TextFieldValue(updatedText, updatedSelection)
                if (updatedValue != value) {
                    onValueChange(updatedValue)
                }
            }
    }
    return textFieldState
}

@Composable
internal fun rememberHoistedDigitTextFieldState(
    value: String,
    onFilteredChange: (String) -> Unit,
    maxDigits: Int,
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = value)
    LaunchedEffect(value) {
        val currentText = textFieldState.text.toString()
        if (currentText != value) {
            textFieldState.edit {
                replace(0, length, value)
                selection = TextRange(value.length)
            }
            // See rememberHoistedStringTextFieldState for why this sync must not leave an
            // undo-able entry behind for the system back gesture to revert.
            textFieldState.undoState.clearHistory()
        }
    }
    LaunchedEffect(textFieldState, maxDigits) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { entered ->
                val filtered = entered.filter(Char::isDigit).take(maxDigits)
                if (filtered != entered) {
                    textFieldState.edit {
                        replace(0, length, filtered)
                        selection = TextRange(filtered.length)
                    }
                    textFieldState.undoState.clearHistory()
                }
                if (filtered != value) {
                    onFilteredChange(filtered)
                }
            }
    }
    return textFieldState
}
