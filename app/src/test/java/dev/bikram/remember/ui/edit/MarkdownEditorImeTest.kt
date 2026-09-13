package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercise Compose's actual user-edit commit path, including composition. Its test-only access
 * is intentional: TextFieldState.edit is programmatic input and cannot create IME composition.
 * No Android device or emulator is involved.
 */
class MarkdownEditorImeTest {
    @Test
    fun composingWordAndAutocorrectionKeepTheCompositionAndActiveFormats() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.toggleItalic()

        compose(state, "N")
        assertEquals(TextRange(3, 4), state.textFieldState.composition)
        compose(state, "Ne")
        assertEquals(TextRange(3, 5), state.textFieldState.composition)
        compose(state, "New")
        assertEquals("***New***", state.markdown)
        assertEquals(TextRange(3, 6), state.textFieldState.composition)
        assertTrue(state.isBold)
        assertTrue(state.isItalic)

        compose(state, "News")
        assertEquals("***News***", state.markdown)
        assertEquals(TextRange(3, 7), state.textFieldState.composition)
        assertTrue(state.isBold)
        assertTrue(state.isItalic)
    }

    @Test
    fun correctionCommittedWithEnterContinuesTheListInTheSameTransaction() {
        val state = MarkdownEditorState("1. ")
        compose(state, "firs")
        state.textFieldState.editAsUserForTest(state.inputTransformation(livePreview = true)) {
            replace(3, 7, "first\n")
            commitImeComposition()
            selection = TextRange(9)
        }

        assertEquals("1. first\n2. ", state.markdown)
        assertNull(state.textFieldState.composition)
        state.undo()
        assertEquals("1. firs", state.markdown)
        state.redo()
        assertEquals("1. first\n2. ", state.markdown)
    }

    @Test
    fun undoEndsCompositionAndNewInputDoesNotRestoreTheDiscardedWord() {
        val state = MarkdownEditorState("start ")
        compose(state, "draft")
        state.undo()
        assertEquals("start ", state.markdown)
        assertNull(state.textFieldState.composition)

        compose(state, "replacement")

        assertEquals("start replacement", state.markdown)
        assertFalse(state.canRedo)
        state.undo()
        assertEquals("start ", state.markdown)
    }

    @Test
    fun multilingualCompositionDoesNotLoseOrSplitCharacters() {
        val state = MarkdownEditorState()
        state.toggleBold()
        compose(state, "न")
        compose(state, "नम")
        compose(state, "नमस्ते")
        assertEquals("**नमस्ते**", state.markdown)
        assertEquals(TextRange(2, 8), state.textFieldState.composition)
        assertTrue(state.isBold)
    }

    private fun compose(
        state: MarkdownEditorState,
        word: String,
    ) {
        state.textFieldState.editAsUserForTest(state.inputTransformation(livePreview = true)) {
            val start = imeComposition?.start ?: selection.min
            val end = imeComposition?.end ?: selection.max
            replace(start, end, word)
            setImeComposition(start, start + word.length)
            selection = TextRange(start + word.length)
        }
    }
}
