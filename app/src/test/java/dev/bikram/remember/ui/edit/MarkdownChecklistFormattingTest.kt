package dev.bikram.remember.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownChecklistFormattingTest {
    @Test
    fun checkingChecklistLineChangesOnlyTheCheckboxState() {
        val markdown = "- [ ] item"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = true)

        assertEquals("- [x] item", updated)
    }

    @Test
    fun uncheckingChecklistLinePreservesAuthoredStrikethrough() {
        val markdown = "- [x] ~~item~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = false)

        assertEquals("- [ ] ~~item~~", updated)
    }

    @Test
    fun checkingChecklistLinePreservesAuthoredStrikethrough() {
        val markdown = "- [ ] ~~item~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = true)

        assertEquals("- [x] ~~item~~", updated)
    }

    @Test
    fun uncheckingChecklistLinePreservesNestedInlineFormatting() {
        val markdown = "- [x] ~~**bold** and <u>underlined</u>~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = false)

        assertEquals("- [ ] ~~**bold** and <u>underlined</u>~~", updated)
    }

    @Test
    fun bulkCheckingPreservesLabelsIndentationWhitespaceAndCodeExamples() {
        val source =
            listOf(
                "- [ ] ***<u>~~item 1 ~~</u>***",
                "  - [ ] child 1  ",
                "\t- [ ] [~~link~~](example.com)",
                "- [ ] ",
                "```",
                "- [ ] **literal example**",
                "```",
                "ordinary [ ] text",
                "",
            ).joinToString("\n")
        val expected =
            listOf(
                "- [x] ***<u>~~item 1 ~~</u>***",
                "  - [x] child 1  ",
                "\t- [x] [~~link~~](example.com)",
                "- [x] ",
                "```",
                "- [ ] **literal example**",
                "```",
                "ordinary [ ] text",
                "",
            ).joinToString("\n")

        assertEquals(expected, source.withAllChecklistLinesToggled(true))
        assertEquals(expected, expected.withAllChecklistLinesToggled(true))
        assertEquals(source, expected.withAllChecklistLinesToggled(false))
        assertEquals(source, source.withChecklistLineToggled(5, true))
        assertEquals(source, source.withChecklistLineToggled(7, true))
        assertEquals(source, source.withChecklistLineToggled(-1, true))
        assertEquals(source, source.withChecklistLineToggled(99, true))
    }

    @Test
    fun alreadyCheckedUppercaseMarkerIsPreserved() {
        val source = "- [X] ***<u>~~item~~</u>***"

        assertEquals(source, source.withChecklistLineToggled(0, true))
        assertEquals(source, source.withAllChecklistLinesToggled(true))
        assertEquals("- [ ] ***<u>~~item~~</u>***", source.withChecklistLineToggled(0, false))
    }
}
