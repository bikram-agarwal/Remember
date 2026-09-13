package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.parseMarkdownLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockEditingTest {
    @Test
    fun convertingChecklistToBulletDoesNotMistakeItForAnExistingBullet() {
        val state = MarkdownEditorState("- [x] ***<u>~~item~~</u>***")
        state.applyBulletList()
        assertEquals("- ***<u>~~item~~</u>***", state.markdown)
        assertTrue(state.isBulletList)
        assertFalse(state.isChecklist)
    }

    @Test
    fun blockConversionsUseTheSameContentBoundaryAsTheRenderer() {
        val prefixes = listOf("", "# ", "###\t", "- ", "* ", "+  ", "  - ", "\t- ", "- [X] ", "  - [ ] ", "12)\t", "  3. ", ">", "  > ")
        val commands: List<Pair<MarkdownBlockKind, MarkdownEditorState.() -> Unit>> =
            listOf(
                MarkdownBlockKind.Heading to { applyHeading(2) },
                MarkdownBlockKind.Bullet to { applyBulletList() },
                MarkdownBlockKind.Checklist to { applyChecklist() },
                MarkdownBlockKind.Numbered to { applyNumberedList() },
                MarkdownBlockKind.Quote to { applyQuote() },
            )
        for (prefix in prefixes) {
            for ((kind, command) in commands) {
                val source = prefix + "***<u>~~Word~~</u>***"
                val originalLine = parseMarkdownLines(source).single()
                val expectedContent = source.substring(originalLine.contentStart)
                val state = MarkdownEditorState(source)
                command(state)
                val line = parseMarkdownLines(state.markdown).single()
                assertEquals(source, kind, line.kind)
                assertEquals(source, expectedContent, state.markdown.substring(line.contentStart))
                assertTrue(state.textFieldState.selection.end <= state.markdown.length)
                state.undo()
                assertEquals(source, state.markdown)
            }
        }
    }

    @Test
    fun singleSpaceAndTabIndentationOutdentConsistentlyWithRendering() {
        for (indent in listOf(" ", "\t", "  ")) {
            for (prefix in listOf("- ", "- [ ] ", "1. ")) {
                val source = indent + prefix + "item"
                val state = MarkdownEditorState(source)
                when (parseMarkdownLines(source).single().kind) {
                    MarkdownBlockKind.Bullet -> state.applyBulletList()
                    MarkdownBlockKind.Checklist -> state.applyChecklist()
                    else -> state.applyNumberedList()
                }
                assertEquals(prefix + "item", state.markdown)
            }
        }
    }

    @Test
    fun enterPreservesListMarkerAndSpacingButResetsChecklistCompletion() {
        for ((prefix, next) in listOf("+\t" to "+\t", "*  " to "*  ", "  9)\t" to "  10)\t", "- [X] " to "- [ ] ")) {
            val source = prefix + "item"
            val state = MarkdownEditorState(source)
            state.update(TextFieldValue(source + "\n", TextRange(source.length + 1)))
            assertEquals(source + "\n" + next, state.markdown)
        }
    }

    @Test
    fun blockCommandsAndEnterLeaveFencedCodeLiteral() {
        val source = "```\n- [x] literal\n```"
        val commands: List<MarkdownEditorState.() -> Unit> = listOf({ applyHeading(1) }, { applyBulletList() }, { applyNumberedList() }, { applyChecklist() }, { applyQuote() })
        for (command in commands) {
            val state = MarkdownEditorState(source)
            state.focusRangeAndShowKeyboard(0, source.length)
            command(state)
            assertEquals(source, state.markdown)
        }
        val state = MarkdownEditorState(source)
        val cursor = source.indexOf("\n```")
        state.focusAtOffsetAndShowKeyboard(cursor)
        val updated = source.substring(0, cursor) + "\n" + source.substring(cursor)
        state.update(TextFieldValue(updated, TextRange(cursor + 1)))
        assertEquals(updated, state.markdown)
    }

    @Test
    fun numberedConversionCountsOnlyEditableLinesAroundCodeBlocks() {
        val source = "Before\n```\n99. literal\n```\nAfter"
        val state = MarkdownEditorState(source)
        state.focusRangeAndShowKeyboard(0, source.length)
        state.applyNumberedList()
        assertEquals("1. Before\n```\n99. literal\n```\n2. After", state.markdown)
        state.undo()
        assertEquals(source, state.markdown)
    }

    @Test
    fun numberedOutdentDoesNotUseNumbersFromCodeExamples() {
        val source = "1. Parent\n```\n99. literal\n```\n  1. "
        val state = MarkdownEditorState(source)
        state.update(TextFieldValue(source + "\n", TextRange(source.length + 1)))
        assertEquals("1. Parent\n```\n99. literal\n```\n2. ", state.markdown)
    }
}
