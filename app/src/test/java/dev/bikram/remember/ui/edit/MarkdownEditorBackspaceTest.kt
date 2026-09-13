@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses Compose's real mapping from displayed deletion ranges to the source document. */
class MarkdownEditorBackspaceTest {
    private val output =
        MarkdownOutputTransformation(
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
            ),
        )

    @Test
    fun backspaceCursorUpdateKeepsBoldHighlightedUntilTheLastLetterIsDeleted() {
        val state = MarkdownEditorState()
        state.toggleBold()
        val transformedState = transformed(state)
        assertTrue(state.isBold)
        type(state, "Word")
        for (remaining in 3 downTo 0) {
            pressBackspace(transformedState)
            assertEquals("Word".take(remaining), output.preview(state.markdown).text.text)
            assertEquals("Toolbar after deleting to $remaining letters; ${state.textFieldState.selection}", remaining > 0, state.isBold)
            assertTrue(state.textFieldState.selection.collapsed)
        }
        assertEquals("", state.markdown)
        type(state, "Plain")
        assertEquals("Plain", state.markdown)
        assertFalse(state.isBold)
    }

    @Test
    fun backspaceAfterTypingWordKeepsTheRemainingTextBold() {
        val state = MarkdownEditorState()
        state.toggleBold()
        val transformedState = transformed(state)
        type(state, "Word")
        assertEquals("**Word**", state.markdown)

        deleteDisplayedRange(transformedState, TextRange(3, 4))

        assertEquals("**Wor**", state.markdown)
        assertEquals("Wor", output.preview(state.markdown).text.text)
        assertTrue(state.isBold)
        assertTrue(
            output
                .preview(state.markdown)
                .text.spanStyles
                .any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 3 },
        )
    }

    @Test
    fun repeatedBackspaceKeepsEachFormatHighlightedUntilItsLastCharacterIsGone() {
        for (format in MarkdownInlineFormat.entries) {
            val state = MarkdownEditorState()
            when (format) {
                MarkdownInlineFormat.BOLD -> state.toggleBold()
                MarkdownInlineFormat.ITALIC -> state.toggleItalic()
                MarkdownInlineFormat.UNDERLINE -> state.toggleUnderline()
                MarkdownInlineFormat.STRIKETHROUGH -> state.toggleStrikethrough()
                MarkdownInlineFormat.INLINE_CODE -> state.toggleInlineCode()
            }
            val transformedState = transformed(state)
            assertEquals(setOf(format), state.inlineFormats)
            type(state, "Word")
            for (remaining in 3 downTo 0) {
                pressBackspace(transformedState)
                assertFormatted(state, "Word".take(remaining), if (remaining > 0) setOf(format) else emptySet())
                assertTrue(state.textFieldState.selection.collapsed)
            }
            pressBackspace(transformedState)
            assertFormatted(state, "", emptySet())
            assertEquals("", state.markdown)
            type(state, "Next")
            assertFormatted(state, "Next", emptySet())
            assertEquals("Next", state.markdown)
        }
    }

    @Test
    fun nestedBoldAndItalicSurviveBackspaceInEitherToggleOrder() {
        for (boldFirst in listOf(true, false)) {
            val state = MarkdownEditorState()
            if (boldFirst) {
                state.toggleBold()
                state.toggleItalic()
            } else {
                state.toggleItalic()
                state.toggleBold()
            }
            val transformedState = transformed(state)
            type(state, "Word ")
            for (remaining in 4 downTo 1) {
                pressBackspace(transformedState)
                assertFormatted(state, "Word ".take(remaining), setOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.ITALIC))
            }
            type(state, "ord")
            state.toggleItalic()
            type(state, " bold")
            assertTrue(state.isBold)
            assertFalse(state.isItalic)
            assertEquals("Word bold", output.preview(state.markdown).text.text)
        }
    }

    @Test
    fun deletingASelectionAcrossHiddenMarkersKeepsTheSurvivingTextFormatted() {
        for (selection in listOf(TextRange(1, 4), TextRange(4, 1), TextRange(0, 3), TextRange(0, 4))) {
            val state = MarkdownEditorState()
            state.toggleUnderline()
            state.toggleBold()
            type(state, "Word")
            val transformedState = transformed(state)
            transformedState.javaClass.methods
                .single { it.name.startsWith("selectCharsIn-") }
                .invoke(transformedState, selection.packedValue)
            deleteDisplayedRange(transformedState, selection)
            val remaining = "Word".removeRange(selection.min, selection.max)
            val formats = if (remaining.isEmpty()) emptySet() else setOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.UNDERLINE)
            assertFormatted(state, remaining, formats)
            type(state, "X")
            assertFormatted(state, remaining.substring(0, selection.min) + "X" + remaining.substring(selection.min), formats)
        }
    }

    @Test
    fun undoAndRedoRestoreFormattedDeletionAndItsTypingState() {
        val state = MarkdownEditorState()
        state.toggleBold()
        type(state, "Word")
        val transformedState = transformed(state)
        pressBackspace(transformedState)
        state.undo()
        assertFormatted(state, "Word", setOf(MarkdownInlineFormat.BOLD))
        assertEquals(TextRange(6), state.textFieldState.selection)
        state.redo()
        assertFormatted(state, "Wor", setOf(MarkdownInlineFormat.BOLD))
        assertEquals(TextRange(5), state.textFieldState.selection)
        type(state, "k")
        assertFormatted(state, "Work", setOf(MarkdownInlineFormat.BOLD))
    }

    @Test
    fun deletingTheLastLetterClearsNestedFormatsAndUndoRestoresThem() {
        val state = MarkdownEditorState()
        state.toggleUnderline()
        state.toggleBold()
        state.toggleItalic()
        type(state, "W")
        val transformedState = transformed(state)
        pressBackspace(transformedState)
        assertEquals("", state.markdown)
        assertFormatted(state, "", emptySet())
        state.undo()
        assertFormatted(state, "W", setOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.ITALIC, MarkdownInlineFormat.UNDERLINE))
        state.redo()
        assertEquals("", state.markdown)
        assertFormatted(state, "", emptySet())
        type(state, "Plain")
        assertEquals("Plain", state.markdown)
    }

    @Test
    fun cursorRemappingPreservesAnExplicitToggleAtTheSameVisiblePosition() {
        val state = MarkdownEditorState()
        state.toggleBold()
        val transformedState = transformed(state)
        val select = transformedState.javaClass.methods.single { it.name.startsWith("selectCharsIn-") }
        select.invoke(transformedState, TextRange.Zero.packedValue)
        assertTrue(state.isBold)
        assertEquals(TextRange(2), state.textFieldState.selection)
        type(state, "Word")
        pressBackspace(transformedState)
        assertTrue(state.isBold)
        state.toggleBold()
        select.invoke(transformedState, TextRange(3).packedValue)
        assertFalse(state.isBold)
        type(state, " plain")
        assertEquals("**Wor** plain", state.markdown)
        assertFalse(state.isBold)
    }

    @Test
    fun rawImeBackspaceOnClosingMarkersDeletesThePreviousVisibleGrapheme() {
        for (ending in listOf("d", "😀", "e\u0301", "👨‍👩‍👧‍👦")) {
            val state = MarkdownEditorState("**Wor$ending**")
            state.textFieldState.editAsUser(state.inputTransformation(livePreview = true)) {
                replace(length - 1, length, "")
                selection = TextRange(length)
            }
            assertFormatted(state, "Wor", setOf(MarkdownInlineFormat.BOLD))
        }
    }

    @Test
    fun composingBackspaceKeepsCompositionAndFormatting() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.textFieldState.editAsUser(state.inputTransformation(livePreview = true)) {
            replace(2, 2, "Word")
            setComposition(2, 6)
            selection = TextRange(6)
        }
        state.textFieldState.editAsUser(state.inputTransformation(livePreview = true)) {
            replace(5, 6, "")
            setComposition(2, 5)
            selection = TextRange(5)
        }
        assertFormatted(state, "Wor", setOf(MarkdownInlineFormat.BOLD))
        assertEquals(TextRange(2, 5), state.textFieldState.composition)
        state.textFieldState.editAsUser(state.inputTransformation(livePreview = true)) {
            replace(2, 5, "")
            commitComposition()
            selection = TextRange(2)
        }
        assertEquals("", state.markdown)
        assertFalse(state.isBold)
        assertNull(state.textFieldState.composition)
    }

    @Test
    fun sourceModeStillAllowsDeletingMarkdownSyntax() {
        val state = MarkdownEditorState("**Word**")
        state.textFieldState.editAsUser(state.inputTransformation(livePreview = false)) {
            replace(length - 1, length, "")
        }
        assertEquals("**Word*", state.markdown)
    }

    @Test
    fun selectAllDeleteClearsTheEntireMixedNoteIncludingHiddenFormatting() {
        val source =
            listOf(
                "***<u>~~what will happen?~~</u>***",
                "Nice. ",
                "# ~~Cuuuuuut~~",
                "1. ***<u>~~now wht ~~</u>***",
                "2. newline",
                "3. ",
            ).joinToString("\n")
        val displayed = output.preview(source).text.text
        for (selection in listOf(TextRange(0, displayed.length), TextRange(displayed.length, 0))) {
            val state = MarkdownEditorState(source)
            val transformedState = transformed(state)
            transformedState.javaClass.methods
                .single { it.name.startsWith("selectCharsIn-") }
                .invoke(transformedState, selection.packedValue)

            deleteDisplayedRange(transformedState, selection)

            assertEquals("Stored source after Select All + Backspace", "", state.markdown)
            assertEquals(TextRange.Zero, state.textFieldState.selection)
            assertTrue(state.inlineFormats.isEmpty())
            assertEquals("", MarkdownEditorState(state.markdown).markdown)
            state.undo()
            assertEquals(source, state.markdown)
            state.redo()
            assertEquals("", state.markdown)
            type(state, "Plain")
            assertEquals("Plain", state.markdown)
            assertTrue(state.inlineFormats.isEmpty())
        }
    }

    @Test
    fun deletingSeveralCompleteSpansRemovesTheirWrappersAndKeepsUnselectedFormats() {
        val state = MarkdownEditorState("**Keep** **gone** <u>lost</u> *end*")
        val transformedState = transformed(state)
        val displayed = output.preview(state.markdown).text.text
        val selection = TextRange(displayed.indexOf("gone"), displayed.indexOf(" end"))
        transformedState.javaClass.methods
            .single { it.name.startsWith("selectCharsIn-") }
            .invoke(transformedState, selection.packedValue)

        deleteDisplayedRange(transformedState, selection)

        assertEquals("**Keep**  *end*", state.markdown)
        assertEquals("Keep  end", output.preview(state.markdown).text.text)
    }

    @Test
    fun deletionAcrossSpansKeepsPartialEndpointsAndRemovesEmptyMiddleSpans() {
        val state = MarkdownEditorState("**AB** <u>CD</u> ~~EF~~")
        val transformedState = transformed(state)
        val selection = TextRange(1, 7)
        transformedState.javaClass.methods
            .single { it.name.startsWith("selectCharsIn-") }
            .invoke(transformedState, selection.packedValue)

        deleteDisplayedRange(transformedState, selection)

        assertEquals("**A**~~F~~", state.markdown)
        assertEquals("AF", output.preview(state.markdown).text.text)
    }

    @Test
    fun deletionDoesNotCleanUpUnrelatedEmptyFormattingOutsideTheSelection() {
        val state = MarkdownEditorState("**Keep** and ~~gone~~ **")
        val transformedState = transformed(state)
        val displayed = output.preview(state.markdown).text.text
        val selection = TextRange(displayed.indexOf("gone"), displayed.indexOf("gone") + 4)
        transformedState.javaClass.methods
            .single { it.name.startsWith("selectCharsIn-") }
            .invoke(transformedState, selection.packedValue)

        deleteDisplayedRange(transformedState, selection)

        assertEquals("**Keep** and  **", state.markdown)
    }

    private fun assertFormatted(
        state: MarkdownEditorState,
        text: String,
        formats: Set<MarkdownInlineFormat>,
    ) {
        val preview = output.preview(state.markdown).text
        assertEquals(text, preview.text)
        assertEquals(formats, state.inlineFormats)
        if (text.isEmpty()) return
        for (format in formats) {
            assertTrue(
                "$format missing from $preview",
                preview.spanStyles.any { span ->
                    span.start == 0 && span.end == text.length &&
                        when (format) {
                            MarkdownInlineFormat.BOLD -> span.item.fontWeight == FontWeight.Bold
                            MarkdownInlineFormat.ITALIC -> span.item.fontStyle == FontStyle.Italic
                            MarkdownInlineFormat.UNDERLINE -> span.item.textDecoration == TextDecoration.Underline
                            MarkdownInlineFormat.STRIKETHROUGH -> span.item.textDecoration == TextDecoration.LineThrough
                            MarkdownInlineFormat.INLINE_CODE -> span.item.fontFamily == FontFamily.Monospace
                        }
                },
            )
        }
    }

    private fun type(
        state: MarkdownEditorState,
        text: String,
    ) {
        state.textFieldState.editAsUser(state.inputTransformation(livePreview = true)) {
            val start = selection.min
            replace(start, selection.max, text)
            selection = TextRange(start + text.length)
        }
    }

    private fun deleteDisplayedRange(
        transformedState: Any,
        range: TextRange,
    ) {
        // Invoke the actual Compose implementation, whose internal members are absent from
        // the Kotlin compile API. This includes its output-to-source mapping and user commit.
        val replace = transformedState.javaClass.methods.single { it.name.startsWith("replaceText-") && it.parameterCount == 5 }
        val undoBehavior = requireNotNull(replace.parameterTypes[2].enumConstants).single { it.toString() == "MergeIfPossible" }
        replace.invoke(transformedState, "", range.packedValue, undoBehavior, true, false)
    }

    private fun pressBackspace(transformedState: Any) {
        val preparedState =
            Class
                .forName("androidx.compose.foundation.text.input.internal.selection.TextFieldPreparedSelectionState")
                .getConstructor()
                .newInstance()
        val context =
            Class
                .forName("androidx.compose.foundation.text.input.internal.selection.SelectionMovementDeletionContext")
                .constructors
                .single { it.parameterCount == 6 }
                .newInstance(transformedState, null, false, true, 0f, preparedState)
        context.javaClass.getMethod("moveCursorPrevByCodePointOrEmoji").invoke(context)
        context.javaClass.getMethod("deleteMovement").invoke(context)
        val selection =
            context.javaClass.methods
                .single { it.name.startsWith("getSelection-") }
                .invoke(context) as Long
        // TextFieldKeyEventHandler reapplies the displayed selection after the deletion.
        transformedState.javaClass.methods
            .single { it.name.startsWith("selectCharsIn-") }
            .invoke(transformedState, selection)
        val affinity = context.javaClass.getMethod("getWedgeAffinity").invoke(context)
        if (affinity != null) {
            val selectionAffinity =
                Class
                    .forName("androidx.compose.foundation.text.input.internal.SelectionWedgeAffinity")
                    .constructors
                    .single { it.parameterCount == 1 }
                    .newInstance(affinity)
            transformedState.javaClass.methods
                .single { it.name == "setSelectionWedgeAffinity" }
                .invoke(transformedState, selectionAffinity)
        }
    }

    private fun transformed(state: MarkdownEditorState): Any =
        Class
            .forName("androidx.compose.foundation.text.input.internal.TransformedTextFieldState")
            .constructors
            .single { it.parameterCount == 4 }
            .newInstance(state.textFieldState, state.inputTransformation(livePreview = true), null, output)
}
