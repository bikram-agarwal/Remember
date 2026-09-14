package dev.bikram.remember.ui.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.toTextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorTransactionsTest {
    private val styler =
        MarkdownStyler(
            bodyStyle = TextStyle.Default,
            linkColor = Color.Blue,
            codeBackground = Color.LightGray,
            quoteColor = Color.Gray,
            quoteBarColor = Color.Blue,
            syntaxMarkerColor = Color.Gray,
            headingOneBaseStyle = TextStyle.Default,
            headingTwoBaseStyle = TextStyle.Default,
            headingThreeBaseStyle = TextStyle.Default,
        )

    @Test
    fun inputTransformationKeepsCombinedFormatsThroughEverySpaceInEitherToggleOrder() {
        for (boldFirst in listOf(true, false)) {
            val state = MarkdownEditorState()
            if (boldFirst) {
                state.toggleBold()
                state.toggleItalic()
            } else {
                state.toggleItalic()
                state.toggleBold()
            }
            var visibleText = ""
            for (character in "New  line") {
                input(state, character.toString())
                visibleText += character
                assertTrue(state.isBold)
                assertTrue(state.isItalic)
                assertEquals(visibleText, output(state.markdown))
            }
        }
    }

    @Test
    fun togglingOffItalicPreservesEarlierFormattingAndBoldForSubsequentInput() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.toggleItalic()
        input(state, "New ")
        state.toggleItalic()
        assertTrue(state.isBold)
        assertFalse(state.isItalic)

        input(state, "text")

        assertEquals("New text", output(state.markdown))
        val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
        assertTrue(preview.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic && it.start == 0 && it.end >= 3 })
        assertFalse(preview.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic && it.end > 4 })
        assertTrue(preview.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold && it.end == 8 })
        assertTrue(state.isBold)
        assertFalse(state.isItalic)
    }

    @Test
    fun formattingOnlyUndoRestoresCursorAndFormatsBeforeAnySaveDebounce() {
        val state = MarkdownEditorState("**word**")
        state.textFieldState.edit { selection = TextRange(6) }
        state.toggleBold()
        assertEquals("**word**", state.markdown)
        assertEquals(TextRange(8), state.textFieldState.selection)
        assertFalse(state.isBold)
        assertTrue(state.canUndo)

        state.undo()

        assertEquals("**word**", state.markdown)
        assertEquals(TextRange(6), state.textFieldState.selection)
        assertTrue(state.isBold)
        state.redo()
        assertEquals(TextRange(8), state.textFieldState.selection)
        assertFalse(state.isBold)
    }

    @Test
    fun firstInputAfterUndoIsRecordedAndInvalidatesRedo() {
        val state = MarkdownEditorState("original")
        input(state, " first")
        state.undo()
        assertEquals("original", state.markdown)
        assertTrue(state.canRedo)

        input(state, " replacement")

        assertFalse(state.canRedo)
        assertTrue(state.canUndo)
        state.undo()
        assertEquals("original", state.markdown)
        state.redo()
        assertEquals("original replacement", state.markdown)
    }

    @Test
    fun selectionFormattingUndoRestoresOriginalReverseSelection() {
        val state = MarkdownEditorState("hello world")
        state.textFieldState.edit { selection = TextRange(11, 6) }
        state.toggleBold()
        assertEquals("hello **world**", state.markdown)

        state.undo()

        assertEquals("hello world", state.markdown)
        assertEquals(TextRange(11, 6), state.textFieldState.selection)
        state.redo()
        assertEquals("hello **world**", state.markdown)
        assertTrue(state.isBold)
    }

    @Test
    fun movingNativeSelectionImmediatelyChangesToolbarContext() {
        val state = MarkdownEditorState("**bold** plain")
        state.textFieldState.edit { selection = TextRange(4) }
        assertTrue(state.isBold)
        state.textFieldState.edit { selection = TextRange(12) }
        assertFalse(state.isBold)
        state.textFieldState.edit { selection = TextRange(2, 6) }
        assertTrue(state.isBold)
    }

    @Test
    fun disablingBoldInTheMiddleFormatsOnlySubsequentInput() {
        val state = MarkdownEditorState("**beforeafter**")
        state.textFieldState.edit { selection = TextRange(8) }
        state.toggleBold()
        input(state, " plain ")
        assertEquals("before plain after", output(state.markdown))
        val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
        assertFalse(preview.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold && it.start < 10 && it.end > 9 })
        assertFalse(state.isBold)
    }

    @Test
    fun viewModeChecklistActionIsUndoableWithoutClearingEarlierEdits() {
        val state = MarkdownEditorState("- [ ] item")
        input(state, " text")
        state.replaceMarkdown("- [x] item text")
        state.undo()
        assertEquals("- [ ] item text", state.markdown)
        state.undo()
        assertEquals("- [ ] item", state.markdown)
    }

    @Test
    fun disablingAnOuterFormatKeepsTheInnerFormatAtTheCursor() {
        val state = MarkdownEditorState()
        state.toggleUnderline()
        state.toggleBold()
        input(state, "beforeafter")
        state.textFieldState.edit { selection = TextRange(state.markdown.indexOf("before") + 6) }
        state.toggleUnderline()
        input(state, "X")

        assertEquals("beforeXafter", output(state.markdown))
        val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
        assertFalse(preview.spanStyles.any { it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline && it.start <= 6 && it.end > 6 })
        assertTrue(preview.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold && it.start <= 6 && it.end > 6 })
        assertFalse(state.isUnderline)
        assertTrue(state.isBold)
    }

    @Test
    fun loadingANoteCreatesANewHistoryBaseline() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.setMarkdown("loaded note")
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFalse(state.isBold)
        input(state, " edit")
        state.undo()
        assertEquals("loaded note", state.markdown)
    }

    @Test
    fun listContinuationAndItsUndoUseTheSameInputTransaction() {
        val state = MarkdownEditorState("1. first")
        input(state, "\n")
        assertEquals("1. first\n2. ", state.markdown)
        state.undo()
        assertEquals("1. first", state.markdown)
        assertEquals(TextRange(8), state.textFieldState.selection)
        state.redo()
        assertEquals("1. first\n2. ", state.markdown)
    }

    @Test
    fun outputTransformationDoesNotChangeStoredTextOrSelection() {
        val source = "# **Heading**\n- [x] done\n> quote\n---\n<u>text</u>"
        val state = TextFieldState(source, TextRange(source.indexOf("text") + 2))
        val originalSelection = state.selection
        val transformation = MarkdownOutputTransformation(styler)
        val buffer = state.toTextFieldBuffer()

        with(transformation) { buffer.transformOutput() }

        assertEquals("Heading\n\u2611 done\n| quote\n\ntext", buffer.toString())
        assertEquals(source, state.text.toString())
        assertEquals(originalSelection, state.selection)
        assertEquals(transformation.preview(source).text.text, buffer.toString())
    }

    private fun input(
        state: MarkdownEditorState,
        inserted: String,
    ) {
        val transformation = state.inputTransformation(livePreview = true)
        state.textFieldState.edit {
            val start = selection.min
            replace(start, selection.max, inserted)
            selection = TextRange(start + inserted.length)
            with(transformation) { transformInput() }
        }
    }

    private fun output(source: String): String {
        val transformation = MarkdownOutputTransformation(styler)
        val buffer = TextFieldState(source).toTextFieldBuffer()
        with(transformation) { buffer.transformOutput() }
        return buffer.toString()
    }
}
