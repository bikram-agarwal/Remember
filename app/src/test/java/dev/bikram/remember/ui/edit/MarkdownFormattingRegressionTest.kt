package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFormattingRegressionTest {
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
    fun boldAndItalicCanBeEnabledInEitherOrderBeforeTyping() {
        for (boldFirst in listOf(true, false)) {
            val state = MarkdownEditorState()
            if (boldFirst) {
                state.toggleBold()
                state.toggleItalic()
            } else {
                state.toggleItalic()
                state.toggleBold()
            }
            assertEquals("******", state.markdown)
            assertEquals(TextRange(3), state.textFieldValue.selection)
            assertTrue(state.isBold)
            assertTrue(state.isItalic)
            type(state, "a")
            assertEquals("***A***", state.markdown)
            assertRendered(state, "A")
        }
    }

    @Test
    fun typingSpacesKeepsBoldItalicPreviewAndToolbarStable() {
        for (boldFirst in listOf(true, false)) {
            val state = MarkdownEditorState()
            if (boldFirst) {
                state.toggleBold()
                state.toggleItalic()
            } else {
                state.toggleItalic()
                state.toggleBold()
            }
            var expected = ""
            for (character in "New  line") {
                type(state, character.toString())
                expected += character
                assertEquals("***$expected***", state.markdown)
                assertEquals(TextRange(3 + expected.length), state.textFieldValue.selection)
                assertTrue(state.isBold)
                assertTrue(state.isItalic)
                val preview = MarkdownVisualTransformation(styler).filter(AnnotatedString(state.markdown))
                assertEquals(expected, preview.text.text)
                assertTrue(preview.text.spanStyles.any { it.start == 0 && it.end == expected.length && it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
                assertTrue(preview.text.spanStyles.any { it.start == 0 && it.end == expected.length && it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
                assertEquals(expected.length, preview.offsetMapping.originalToTransformed(state.textFieldValue.selection.start))
            }
            assertRendered(state, "New  line")
        }
    }

    @Test
    fun spaceBeforeClosingMarkersKeepsOtherInlineFormatsVisible() {
        for (source in listOf("**New **", "*New *", "~~New ~~", "`New `", "<u>***New ***</u>")) {
            val preview = MarkdownVisualTransformation(styler).filter(AnnotatedString(source))
            assertEquals(source, "New ", preview.text.text)
        }
    }

    @Test
    fun switchingOffEitherFormatAtEndKeepsOtherFormatForMoreTyping() {
        for (removeBold in listOf(true, false)) {
            val state = MarkdownEditorState("***one***")
            state.update(TextFieldValue(state.markdown, TextRange(6)))
            if (removeBold) state.toggleBold() else state.toggleItalic()
            type(state, "two")
            assertEquals(!removeBold, state.isBold)
            assertEquals(removeBold, state.isItalic)
            assertRendered(state, "onetwo")
        }
    }

    @Test
    fun mixedEmptyFormatsCanBeToggledOffIndependently() {
        val toggles: List<MarkdownEditorState.() -> Unit> =
            listOf({ toggleBold() }, { toggleItalic() }, { toggleUnderline() }, { toggleStrikethrough() })
        for (first in toggles.indices) {
            for (second in toggles.indices.filter { it != first }) {
                val state = MarkdownEditorState()
                toggles[first](state)
                toggles[second](state)
                toggles[first](state)
                toggles[second](state)
                assertEquals("Pair $first/$second", "", state.markdown)
            }
        }
    }

    @Test
    fun eitherFormatCanBeRemovedFromEmptyCombinedWrapper() {
        for (removeBold in listOf(true, false)) {
            val state = MarkdownEditorState("******")
            state.update(TextFieldValue(state.markdown, TextRange(3)))
            if (removeBold) state.toggleBold() else state.toggleItalic()
            assertEquals(if (removeBold) "**" else "****", state.markdown)
            assertEquals(!removeBold, state.isBold)
            assertEquals(removeBold, state.isItalic)
        }
    }

    @Test
    fun eitherFormatCanBeRemovedFromCombinedSelection() {
        for (removeBold in listOf(true, false)) {
            val state = MarkdownEditorState("***text***")
            state.update(TextFieldValue(state.markdown, TextRange(3, 7)))
            if (removeBold) state.toggleBold() else state.toggleItalic()
            assertEquals(if (removeBold) "*text*" else "**text**", state.markdown)
            assertEquals(!removeBold, state.isBold)
            assertEquals(removeBold, state.isItalic)
            assertRendered(state, "text")
        }
    }

    @Test
    fun enterClosesAllNestedWrappers() {
        for (source in listOf("***text***", "**<u>text</u>**", "<u>~~text~~</u>")) {
            val state = MarkdownEditorState(source)
            state.update(TextFieldValue(source, TextRange(source.indexOf("text") + 4)))
            type(state, "\n")
            assertEquals("$source\n", state.markdown)
            assertEquals(TextRange(state.markdown.length), state.textFieldValue.selection)
            assertFalse(state.isBold)
            assertFalse(state.isItalic)
        }
    }

    @Test
    fun deletingLastCharacterClearsAllEmptyNestedFormats() {
        for (source in listOf("***a***", "**<u>a</u>**", "<u>~~a~~</u>")) {
            val state = MarkdownEditorState(source)
            val cursor = source.indexOf('a') + 1
            state.update(TextFieldValue(source, TextRange(cursor)))
            state.update(TextFieldValue(source.removeRange(cursor - 1, cursor), TextRange(cursor - 1)))
            assertEquals("", state.markdown)
            assertEquals(TextRange(0), state.textFieldValue.selection)
            assertTrue(state.inlineFormats.isEmpty())
        }
    }

    @Test
    fun formattingInsideCodeIsNotReportedAsActive() {
        val source = "```\n**literal**\n```"
        val state = MarkdownEditorState(source)
        state.update(TextFieldValue(source, TextRange(8)))
        assertFalse(state.isBold)
        assertFalse(state.isInlineCode)
        assertTrue(state.isCodeBlock)
    }

    @Test
    fun formattingInLinkLabelIsReportedAsActive() {
        val source = "[**label**](https://example.com)"
        val state = MarkdownEditorState(source)
        state.update(TextFieldValue(source, TextRange(5)))
        assertTrue(state.isBold)
    }

    // Regression: after closing nested bold+italic with Enter (which jumps the cursor past the
    // hidden closing markers onto a fresh line - see withInlineWrapperEnterAdjusted) and then
    // backspacing the newline back out, the cursor sits right after a whole run of hidden marker
    // characters. A plain single-character backspace there used to delete one raw marker character
    // (not the visible trailing space the user is looking at), corrupting the marker run - bold
    // vanished entirely and a stray "*" appeared. See withInlineFormattingPreserved.
    @Test
    fun backspaceRightAfterClosedNestedFormatsRemovesVisibleCharacterNotMarker() {
        val state = MarkdownEditorState()
        state.toggleBold()
        type(state, "Bold ")
        state.toggleItalic()
        type(state, "Italic ")
        type(state, "\n")
        backspace(state)

        backspace(state)

        assertEquals("**Bold *Italic***", state.markdown)
        assertRendered(state, "Bold Italic")
        assertTrue(state.isBold)
        assertTrue(state.isItalic)
    }

    // Regression: toggling a second asterisk-based format right where the cursor sits at an
    // enclosing format's own closing marker (e.g. bold "**word **", cursor right before the
    // closing "**") glues a new empty wrapper onto that closing run into one bare run of
    // asterisks. Reading that run is ambiguous until the next keystroke lands, so both formats
    // used to read as inactive for one toolbar render. Existing content must not gain the new
    // format either - only text typed after the toggle should.
    @Test
    fun togglingSecondFormatRightBeforeEnclosingFormatsCloseKeepsBothActiveWithoutRetroactivelyFormattingExistingText() {
        val state = MarkdownEditorState()
        state.toggleBold()
        type(state, "word ")
        assertEquals("**word **", state.markdown)
        assertTrue(state.isBold)

        state.toggleItalic()

        assertTrue(state.isBold)
        assertTrue(state.isItalic)
        type(state, "X")
        assertRendered(state, "word X")
        assertFalse(
            styler.markdownInlineAnnotatedString(state.markdown).spanStyles.any {
                it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic && it.start < 5
            },
        )
    }

    @Test
    fun togglingBoldRightBeforeEnclosingItalicsCloseKeepsBothActiveWithoutRetroactivelyFormattingExistingText() {
        val state = MarkdownEditorState()
        state.toggleItalic()
        type(state, "word ")
        assertEquals("*word *", state.markdown)
        assertTrue(state.isItalic)

        state.toggleBold()

        assertTrue(state.isBold)
        assertTrue(state.isItalic)
        type(state, "X")
        assertRendered(state, "word X")
        assertFalse(
            styler.markdownInlineAnnotatedString(state.markdown).spanStyles.any {
                it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold && it.start < 5
            },
        )
    }

    private fun type(
        state: MarkdownEditorState,
        inserted: String,
    ) {
        val cursor = state.textFieldValue.selection.start
        state.update(TextFieldValue(state.markdown.replaceRange(cursor, cursor, inserted), TextRange(cursor + inserted.length)))
    }

    private fun backspace(state: MarkdownEditorState) {
        val cursor = state.textFieldValue.selection.start
        if (cursor == 0) return
        state.update(TextFieldValue(state.markdown.removeRange(cursor - 1, cursor), TextRange(cursor - 1)))
    }

    private fun assertRendered(
        state: MarkdownEditorState,
        expected: String,
    ) {
        val transformed = MarkdownVisualTransformation(styler).filter(AnnotatedString(state.markdown))
        assertEquals(expected, transformed.text.text)
        assertEquals(expected, styler.markdownInlineAnnotatedString(state.markdown).text)
        var previousOffset = 0
        for (offset in 0..state.markdown.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(offset)
            assertTrue(mapped in previousOffset..expected.length)
            previousOffset = mapped
        }
    }
}
