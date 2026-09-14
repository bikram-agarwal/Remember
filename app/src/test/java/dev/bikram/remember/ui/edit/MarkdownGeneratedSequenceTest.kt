@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import dev.bikram.remember.ui.common.markdownCardPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MarkdownGeneratedSequenceTest {
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
    private val output = MarkdownOutputTransformation(styler)

    @Test
    fun seededInlineEditSequencesPreserveTextMappingsAndUndoRedo() {
        val failures = mutableListOf<String>()
        for (seed in 0 until 12) {
            val random = Random(seed)
            val state = MarkdownEditorState("Alpha Beta")
            val transformed = transformed(state)
            var expected = "Alpha Beta"
            val trace = mutableListOf<String>()
            for (step in 0 until 80) {
                try {
                    val boundaries = expected.indices.filter { !expected[it].isLowSurrogate() } + expected.length
                    val start = boundaries.random(random)
                    val end = if (random.nextBoolean()) start else boundaries.random(random)
                    val selection = TextRange(start, end)
                    // A deliberate selection change starts a separate undo transaction.
                    state.focusRangeAndShowKeyboard(state.textFieldState.selection.start, state.textFieldState.selection.end)
                    select(transformed, selection)
                    val before = snapshot(state)
                    val operation = random.nextInt(6)
                    trace.add("$step: operation=$operation selection=$selection source=${state.markdown}")
                    when (operation) {
                        0, 1, 2 -> {
                            val inserted = if (operation == 2) "" else listOf("X", "Word", " ", "🙂").random(random)
                            val replacedRange =
                                if (operation == 2 && selection.collapsed && start > 0) TextRange(boundaries.last { it < start }, start) else selection
                            trace.add("Replace $replacedRange with '$inserted'")
                            if (!replacedRange.collapsed || inserted.isNotEmpty()) replace(transformed, replacedRange, inserted)
                            expected = expected.replaceRange(replacedRange.min, replacedRange.max, inserted)
                        }
                        3 -> {
                            val format = random.nextInt(4)
                            trace.add("Toggle $format")
                            toggle(state, format)
                        }
                        4 -> {
                            val reopened = MarkdownEditorState(state.markdown)
                            assertEquals(state.markdown, reopened.markdown)
                            assertInlineContent(reopened, expected)
                        }
                        else -> {
                            if (state.canUndo) {
                                state.undo()
                                state.redo()
                                assertEquals(before, snapshot(state))
                            }
                        }
                    }
                    assertInlineContent(state, expected)
                    val after = snapshot(state)
                    if (before.text != after.text || (operation == 3 && before != after)) {
                        state.undo()
                        assertEquals("Undo", before, snapshot(state))
                        state.redo()
                        assertEquals("Redo", after, snapshot(state))
                        assertInlineContent(state, expected)
                    }
                } catch (failure: Throwable) {
                    failures.add("Seed $seed at step $step: ${failure.message}\n${trace.joinToString("\n")}")
                    break
                }
            }
        }
        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
    }

    @Test
    fun replacingTheGapBetweenItalicSpansWithSpaceJoinsTheirFormatting() {
        val state = MarkdownEditorState(" *W🙂*o🙂rd*🙂d*")
        val transformed = transformed(state)
        select(transformed, TextRange(2, 9))
        replace(transformed, TextRange(2, 9), " ")
        assertInlineContent(state, " W 🙂d")
        assertEquals(" *W 🙂d*", state.markdown)
        state.undo()
        assertEquals(" *W🙂*o🙂rd*🙂d*", state.markdown)
        state.redo()
        assertEquals(" *W 🙂d*", state.markdown)
    }

    @Test
    fun seededChecklistEditingCheckingAndSelectAllDeletionRoundTrip() {
        for (seed in 0 until 32) {
            val random = Random(seed)
            val state = MarkdownEditorState()
            val transformed = transformed(state)
            val itemCount = random.nextInt(2, 6)
            repeat(itemCount) { item ->
                state.applyChecklist()
                for (format in (0 until 4).shuffled(random).take(random.nextInt(5))) toggle(state, format)
                val visible = output.preview(state.markdown).text.text
                replace(transformed, TextRange(visible.length), "Item${item + 1} ")
                if (item < itemCount - 1) {
                    val end = output.preview(state.markdown).text.length
                    replace(transformed, TextRange(end), "\n")
                }
            }
            val source = state.markdown
            val labels = output.preview(source).text.text
            for (line in (0 until itemCount).shuffled(random)) {
                val original = state.markdown
                state.replaceMarkdown(original.withChecklistLineToggled(line, true))
                val expectedLines = original.lines().toMutableList()
                expectedLines[line] = expectedLines[line].replaceFirst("[ ]", "[x]")
                assertEquals(expectedLines.joinToString("\n"), state.markdown)
                state.undo()
                assertEquals(original, state.markdown)
                state.redo()
            }
            state.replaceMarkdown(state.markdown.withAllChecklistLinesToggled(false))
            assertEquals("Seed $seed", source, state.markdown)
            assertEquals(labels, output.preview(state.markdown).text.text)
            val saved = state.markdown
            val size = output.preview(saved).text.length
            val selection = if (random.nextBoolean()) TextRange(0, size) else TextRange(size, 0)
            select(transformed, selection)
            replace(transformed, selection, "")
            assertEquals("Select All seed $seed", "", state.markdown)
            assertTrue(state.inlineFormats.isEmpty())
            state.undo()
            assertEquals(saved, state.markdown)
            state.redo()
            assertEquals("", state.markdown)
        }
    }

    private fun assertInlineContent(
        state: MarkdownEditorState,
        expected: String,
    ) {
        val preview = output.preview(state.markdown)
        val saved = styler.markdownInlineAnnotatedString(state.markdown)
        val card = markdownCardPreview(state.markdown, styler)
        assertEquals("Visible text for ${state.markdown}", expected, preview.text.text)
        assertEquals(expected, saved.text)
        assertEquals(expected, card.text.text)
        for (index in expected.indices) {
            assertEquals(resolvedStyle(saved, index), resolvedStyle(preview.text, index))
            assertEquals(resolvedStyle(saved, index), resolvedStyle(card.text, index))
            assertEquals(expected[index], state.markdown[card.interactions.sourceOffsetByVisibleOffset[index]])
        }
        var previousOffset = 0
        for (offset in 0..state.markdown.length) {
            val mapped = preview.originalToTransformed(offset)
            assertTrue(mapped in previousOffset..expected.length)
            previousOffset = mapped
        }
        assertTrue(state.textFieldState.selection.start in 0..state.markdown.length)
        assertTrue(state.textFieldState.selection.end in 0..state.markdown.length)
    }

    private fun snapshot(state: MarkdownEditorState): MarkdownEditorSnapshot {
        return MarkdownEditorSnapshot(state.markdown, state.textFieldState.selection, state.inlineFormats)
    }

    private fun resolvedStyle(
        text: AnnotatedString,
        index: Int,
    ): SpanStyle {
        return text.spanStyles.filter { index in it.start until it.end }.fold(SpanStyle()) { style, span -> style.merge(span.item) }
    }

    private fun toggle(
        state: MarkdownEditorState,
        format: Int,
    ) {
        when (format) {
            0 -> state.toggleBold()
            1 -> state.toggleItalic()
            2 -> state.toggleUnderline()
            else -> state.toggleStrikethrough()
        }
    }

    private fun select(
        transformed: Any,
        selection: TextRange,
    ) {
        transformed.javaClass.methods
            .single { it.name.startsWith("selectCharsIn-") }
            .invoke(transformed, selection.toComposeTestRange())
    }

    private fun replace(
        transformed: Any,
        selection: TextRange,
        text: String,
    ) {
        val method = transformed.javaClass.methods.single { it.name.startsWith("replaceText-") && it.parameterCount == 5 }
        val undoBehavior = requireNotNull(method.parameterTypes[2].enumConstants).single { it.toString() == "MergeIfPossible" }
        method.invoke(transformed, text, selection.toComposeTestRange(), undoBehavior, true, false)
    }

    private fun transformed(state: MarkdownEditorState): Any {
        return Class
            .forName("androidx.compose.foundation.text.input.internal.TransformedTextFieldState")
            .constructors
            .single { it.parameterCount == 4 }
            .newInstance(state.textFieldState, state.inputTransformation(livePreview = true), null, output)
    }
}
