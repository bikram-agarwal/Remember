package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLazyParsingTest {
    @Test
    fun ordinaryTypingAndSelectionChangesDoNotParseForNewlineHandling() {
        val source = "**Formatted** text\n".repeat(4000)
        val previous = TextFieldValue(source, TextRange(source.length))
        val incomingValues =
            listOf(
                TextFieldValue(source + "x", TextRange(source.length + 1)),
                TextFieldValue(source, TextRange(2)),
                TextFieldValue(source, TextRange(2, 5)),
                TextFieldValue(source + "pasted text", TextRange(source.length + 11)),
            )
        for (incoming in incomingValues) {
            val result = incoming.withInlineWrapperEnterAdjusted(previous) { error("No newline: parsing is unnecessary") }
            assertEquals(incoming, result)
        }
    }

    @Test
    fun newlineAtTheEndOfNestedFormatsStillMovesPastTheirClosingMarkers() {
        val previous = TextFieldValue("***Word***", TextRange(7))
        val incoming = TextFieldValue("***Word\n***", TextRange(8))
        var parseCount = 0
        val result =
            incoming.withInlineWrapperEnterAdjusted(previous) {
                parseCount++
                markdownFormatSpans(previous.text)
            }

        assertEquals(TextFieldValue("***Word***\n", TextRange(11)), result)
        assertEquals(1, parseCount)
    }
}
