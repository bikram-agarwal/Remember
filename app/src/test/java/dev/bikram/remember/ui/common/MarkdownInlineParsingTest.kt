package dev.bikram.remember.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInlineParsingTest {
    @Test
    fun closingMarkerAllowsFollowingWhitespacePunctuationAndEndOfText() {
        assertEquals(5, "*bold*".indexOfMarkdownClosingMarker("*", startIndex = 1))
        assertEquals(5, "*bold* next".indexOfMarkdownClosingMarker("*", startIndex = 1))
        assertEquals(5, "*bold*.".indexOfMarkdownClosingMarker("*", startIndex = 1))
        assertEquals(5, "`code` next".indexOfMarkdownClosingMarker("`", startIndex = 1))
    }

    @Test
    fun closingMarkerRejectsPrecedingWhitespace() {
        assertEquals(-1, "*bold *".indexOfMarkdownClosingMarker("*", startIndex = 1))
    }
}
