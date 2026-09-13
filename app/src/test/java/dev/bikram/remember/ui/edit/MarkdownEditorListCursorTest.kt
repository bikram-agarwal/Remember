@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorListCursorTest {
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
    fun enablingBoldKeepsTheDisplayedCursorAfterEachListMarkerBeforeTyping() {
        val lists =
            listOf(
                MarkdownEditorState::applyBulletList to "  \u2022 ",
                MarkdownEditorState::applyNumberedList to " 1. ",
                MarkdownEditorState::applyChecklist to "\u2610 ",
            )
        for ((applyList, displayedPrefix) in lists) {
            val state = MarkdownEditorState()
            applyList(state)
            val transformedState = transformed(state)
            assertDisplayedCaret(transformedState, displayedPrefix)

            state.toggleBold()

            assertTrue(state.isBold)
            assertDisplayedCaret(transformedState, displayedPrefix)
            type(state, "Word")
            assertDisplayedCaret(transformedState, displayedPrefix + "Word")
            assertTrue(state.isBold)
        }
    }

    @Test
    fun emptyInlineFormatsKeepTheCursorAfterIndentedAndContinuedListMarkers() {
        val prefixes =
            listOf(
                "  - " to "    \u2022 ",
                "  12. " to "   12. ",
                "  - [ ] " to "  \u2610 ",
                "- [x] " to "\u2611 ",
                "intro\n- " to "intro\n  \u2022 ",
                "1. first\n2. " to " 1. first\n 2. ",
                "- [ ] first\n- [ ] " to "\u2610 first\n\u2610 ",
            )
        val toggles =
            listOf(
                MarkdownEditorState::toggleBold,
                MarkdownEditorState::toggleItalic,
                MarkdownEditorState::toggleUnderline,
                MarkdownEditorState::toggleStrikethrough,
                MarkdownEditorState::toggleInlineCode,
            )
        for ((source, displayedPrefix) in prefixes) {
            for (toggle in toggles) {
                val state = MarkdownEditorState(source)
                val transformedState = transformed(state)
                assertDisplayedCaret(transformedState, displayedPrefix)
                toggle(state)
                assertDisplayedCaret(transformedState, displayedPrefix)
                toggle(state)
                assertDisplayedCaret(transformedState, displayedPrefix)
                assertEquals(source, state.markdown)
            }
        }
    }

    @Test
    fun undoRedoAndCombinedFormattingKeepTheEmptyListCursorInPlace() {
        for (applyList in listOf(MarkdownEditorState::applyBulletList, MarkdownEditorState::applyNumberedList, MarkdownEditorState::applyChecklist)) {
            val state = MarkdownEditorState()
            applyList(state)
            val transformedState = transformed(state)
            val displayedPrefix = output.preview(state.markdown).text.text
            state.toggleBold()
            assertDisplayedCaret(transformedState, displayedPrefix)
            state.undo()
            assertDisplayedCaret(transformedState, displayedPrefix)
            state.redo()
            assertDisplayedCaret(transformedState, displayedPrefix)
            state.toggleItalic()
            assertDisplayedCaret(transformedState, displayedPrefix)
            assertTrue(state.isBold && state.isItalic)
            state.toggleBold()
            assertDisplayedCaret(transformedState, displayedPrefix)
            assertTrue(state.isItalic)
            state.toggleItalic()
            assertDisplayedCaret(transformedState, displayedPrefix)
            assertTrue(state.inlineFormats.isEmpty())
        }
    }

    private fun assertDisplayedCaret(
        transformedState: Any,
        expectedText: String,
    ) {
        // Read Compose's actual displayed selection, rather than the preview's decoration map.
        val visualText = requireNotNull(transformedState.javaClass.getMethod("getVisualText").invoke(transformedState))
        val selection =
            visualText.javaClass.methods
                .single { it.name.startsWith("getSelection-") }
                .invoke(visualText) as Long
        assertEquals(expectedText, visualText.toString())
        assertEquals("Displayed cursor for '$expectedText'", TextRange(expectedText.length).packedValue, selection)
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

    private fun transformed(state: MarkdownEditorState): Any {
        return Class
            .forName("androidx.compose.foundation.text.input.internal.TransformedTextFieldState")
            .constructors
            .single { it.parameterCount == 4 }
            .newInstance(state.textFieldState, state.inputTransformation(livePreview = true), null, output)
    }
}
