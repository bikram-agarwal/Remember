@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDecorationRegressionTest {
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
    private val both = TextDecoration.Underline + TextDecoration.LineThrough

    @Test
    fun typingWithBoldItalicUnderlineAndStrikeKeepsAllFourRenderedStyles() {
        for (underlineFirst in listOf(true, false)) {
            val state = MarkdownEditorState()
            state.toggleBold()
            state.toggleItalic()
            if (underlineFirst) {
                state.toggleUnderline()
                state.toggleStrikethrough()
            } else {
                state.toggleStrikethrough()
                state.toggleUnderline()
            }
            var visible = ""
            for (character in "Word next") {
                type(state, character.toString())
                visible += character
                val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
                assertEquals(visible, preview.text)
                assertTrue(state.isBold && state.isItalic && state.isUnderline && state.isStrikethrough)
                for (index in preview.indices) {
                    val style = resolvedStyle(preview, index)
                    assertEquals(FontWeight.Bold, style.fontWeight)
                    assertEquals(FontStyle.Italic, style.fontStyle)
                    assertEquals(both, style.textDecoration)
                }
            }
        }
    }

    @Test
    fun boldStrikethroughWithTrailingWhitespaceRendersTheSameAfterSaving() {
        for (boldFirst in listOf(true, false)) {
            for (whitespace in listOf(" ", "  ", "\t")) {
                val state = MarkdownEditorState()
                if (boldFirst) {
                    state.toggleBold()
                    state.toggleStrikethrough()
                } else {
                    state.toggleStrikethrough()
                    state.toggleBold()
                }
                type(state, "strike$whitespace")
                val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
                val saved = styler.markdownInlineAnnotatedString(state.markdown)
                assertEquals("strike$whitespace", preview.text)
                assertEquals(preview.text, saved.text)
                for (index in saved.indices) {
                    val style = resolvedStyle(saved, index)
                    assertEquals(FontWeight.Bold, style.fontWeight)
                    assertEquals(TextDecoration.LineThrough, style.textDecoration)
                }
            }
        }
    }

    @Test
    fun savedWhitespaceKeepsAllCombinedFormatsAndItsVisibleTapOffsets() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.toggleItalic()
        state.toggleUnderline()
        state.toggleStrikethrough()
        type(state, "strike ")
        val saved = styler.markdownInlineAnnotatedString(state.markdown)
        assertEquals("strike ", saved.text)
        for (index in saved.indices) {
            val style = resolvedStyle(saved, index)
            assertEquals(FontWeight.Bold, style.fontWeight)
            assertEquals(FontStyle.Italic, style.fontStyle)
            assertEquals(both, style.textDecoration)
        }

        // Exercise the map used by saved-note tap/long-press handling as well as its renderer.
        val constructor =
            Class
                .forName("dev.bikram.remember.ui.common.MarkdownInlineInteractionBuilder")
                .declaredConstructors
                .single { it.parameterCount == 3 }
                .apply { isAccessible = true }
        val builder = constructor.newInstance(state.markdown, 17, null)
        val mapping =
            requireNotNull(
                builder.javaClass
                    .getDeclaredMethod("build")
                    .apply { isAccessible = true }
                    .invoke(builder),
            )
        val offsets =
            mapping.javaClass
                .getDeclaredMethod("getSourceOffsetByVisibleOffset")
                .apply { isAccessible = true }
                .invoke(mapping) as IntArray
        assertEquals(saved.length + 1, offsets.size)
        val sourceStart = state.markdown.indexOf("strike ")
        for (index in saved.indices) {
            assertEquals(17 + sourceStart + index, offsets[index])
        }
        assertEquals(17 + state.markdown.length, offsets.last())
    }

    @Test
    fun savedTrailingSpacesMatchPreviewForIndividualAndNestedFormats() {
        for (source in listOf("~~strike ~~", "**bold **", "*italic *", "***both ***", "`code `", "~~**strike **~~", "<u>~~strike ~~</u>")) {
            val preview = MarkdownOutputTransformation(styler).preview(source).text
            val saved = styler.markdownInlineAnnotatedString(source)
            assertEquals(source, preview.text, saved.text)
            for (index in saved.indices) {
                val expected = resolvedStyle(preview, index)
                val actual = resolvedStyle(saved, index)
                assertEquals(expected.fontWeight, actual.fontWeight)
                assertEquals(expected.fontStyle, actual.fontStyle)
                assertEquals(expected.textDecoration, actual.textDecoration)
                assertEquals(expected.fontFamily, actual.fontFamily)
            }
        }
    }

    @Test
    fun unmatchedAndLeadingWhitespaceMarkersRemainLiteral() {
        for (source in listOf("~~unfinished", "~~ leading ~~", "* leading *", "` leading `")) {
            assertEquals(source, styler.markdownInlineAnnotatedString(source).text)
        }
    }

    @Test
    fun savedTextKeepsUnderlineAndStrikeInEitherNestingOrder() {
        for (source in listOf("***<u>~~Word~~</u>***", "***~~<u>Word</u>~~***")) {
            val rendered = styler.markdownInlineAnnotatedString(source)
            assertEquals("Word", rendered.text)
            for (index in rendered.indices) {
                val style = resolvedStyle(rendered, index)
                assertEquals(FontWeight.Bold, style.fontWeight)
                assertEquals(FontStyle.Italic, style.fontStyle)
                assertEquals(both, style.textDecoration)
            }
            val editing = styler.markdownEditingAnnotatedString(source)
            val wordStart = source.indexOf("Word")
            for (index in wordStart until wordStart + 4) {
                assertEquals(both, resolvedStyle(editing, index).textDecoration)
            }
        }
    }

    @Test
    fun decorationsCombineOnlyWhereTheirRangesOverlap() {
        val cases =
            listOf(
                "<u>under ~~both~~ under</u> ~~strike~~" to
                    listOf(
                        "under " to TextDecoration.Underline,
                        "both" to both,
                        " under" to TextDecoration.Underline,
                        " " to TextDecoration.None,
                        "strike" to TextDecoration.LineThrough,
                    ),
                "~~strike <u>both</u> strike~~ <u>under</u>" to
                    listOf(
                        "strike " to TextDecoration.LineThrough,
                        "both" to both,
                        " strike" to TextDecoration.LineThrough,
                        " " to TextDecoration.None,
                        "under" to TextDecoration.Underline,
                    ),
            )
        for ((source, segments) in cases) {
            for (rendered in listOf(styler.markdownInlineAnnotatedString(source), MarkdownOutputTransformation(styler).preview(source).text)) {
                assertEquals(segments.joinToString("") { it.first }, rendered.text)
                var start = 0
                for ((text, expectedDecoration) in segments) {
                    for (index in start until start + text.length) {
                        assertEquals("At $index in $rendered", expectedDecoration, resolvedStyle(rendered, index).textDecoration ?: TextDecoration.None)
                    }
                    start += text.length
                }
            }
        }
    }

    @Test
    fun turningOffEitherDecorationPreservesTheOtherAndEarlierText() {
        for (removeUnderline in listOf(true, false)) {
            val state = MarkdownEditorState()
            state.toggleBold()
            state.toggleItalic()
            state.toggleUnderline()
            state.toggleStrikethrough()
            type(state, "Word")
            if (removeUnderline) state.toggleUnderline() else state.toggleStrikethrough()
            type(state, "Next")
            val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
            assertEquals("WordNext", preview.text)
            assertEquals(!removeUnderline, state.isUnderline)
            assertEquals(removeUnderline, state.isStrikethrough)
            for (index in preview.indices) {
                val style = resolvedStyle(preview, index)
                val expectedDecoration =
                    if (index < 4) {
                        both
                    } else if (removeUnderline) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.Underline
                    }
                assertEquals(expectedDecoration, style.textDecoration)
                assertEquals(FontWeight.Bold, style.fontWeight)
                assertEquals(FontStyle.Italic, style.fontStyle)
            }
        }
    }

    @Test
    fun struckThroughLinksRetainTheirUnderlineAndLinkAnnotation() {
        val source = "[~~Word~~](https://example.com)"
        val rendered = styler.markdownInlineAnnotatedString(source)
        val preview = MarkdownOutputTransformation(styler).preview(source).text
        assertEquals("Word", rendered.text)
        assertEquals(1, rendered.getLinkAnnotations(0, rendered.length).size)
        for (index in rendered.indices) {
            assertEquals(both, resolvedStyle(rendered, index).textDecoration)
            assertEquals(both, resolvedStyle(preview, index).textDecoration)
        }
    }

    private fun resolvedStyle(
        text: AnnotatedString,
        index: Int,
    ): SpanStyle {
        // Resolve overlapping spans as Compose does. Separate U/S spans alone are not enough:
        // SpanStyle.merge replaces textDecoration with the later span's value.
        return text.spanStyles
            .filter { index in it.start until it.end }
            .fold(SpanStyle()) { style, range -> style.merge(range.item) }
    }

    private fun type(
        state: MarkdownEditorState,
        text: String,
    ) {
        state.textFieldState.editAsUserForTest(state.inputTransformation(livePreview = true)) {
            val start = selection.min
            replace(start, selection.max, text)
            selection = TextRange(start + text.length)
        }
    }
}
