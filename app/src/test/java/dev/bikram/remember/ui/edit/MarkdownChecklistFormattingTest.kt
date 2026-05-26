package dev.bikram.remember.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownChecklistFormattingTest {
    @Test
    fun checkingChecklistLineWrapsContentInStrikethrough() {
        val markdown = "- [ ] item"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = true)

        assertEquals("- [x] ~~item~~", updated)
    }

    @Test
    fun uncheckingChecklistLineRemovesContentStrikethrough() {
        val markdown = "- [x] ~~item~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = false)

        assertEquals("- [ ] item", updated)
    }

    @Test
    fun checkingChecklistLineDoesNotDoubleWrapContent() {
        val markdown = "- [ ] ~~item~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = true)

        assertEquals("- [x] ~~item~~", updated)
    }

    @Test
    fun uncheckingChecklistLinePreservesInlineFormattingInsideRemovedStrikethrough() {
        val markdown = "- [x] ~~**bold** and <u>underlined</u>~~"

        val updated = markdown.withChecklistLineToggled(lineIndex = 0, checked = false)

        assertEquals("- [ ] **bold** and <u>underlined</u>", updated)
    }
}
