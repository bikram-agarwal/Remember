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
    fun deletingLastCharacterRemovesAllEmptyNestedWrappers() {
        for (source in listOf("***a***", "**<u>a</u>**", "<u>~~a~~</u>")) {
            val state = MarkdownEditorState(source)
            val cursor = source.indexOf('a') + 1
            state.update(TextFieldValue(source, TextRange(cursor)))
            state.update(TextFieldValue(source.removeRange(cursor - 1, cursor), TextRange(cursor - 1)))
            assertEquals("", state.markdown)
            assertEquals(TextRange(0), state.textFieldValue.selection)
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

    private fun type(
        state: MarkdownEditorState,
        inserted: String,
    ) {
        val cursor = state.textFieldValue.selection.start
        state.update(TextFieldValue(state.markdown.replaceRange(cursor, cursor, inserted), TextRange(cursor + inserted.length)))
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
