package dev.bikram.remember.ui.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    @Test
    fun checklistCheckboxSizeFallsBackWhenFontSizeIsZero() {
        assertEquals(
            24.dp,
            markdownChecklistCheckboxSize(
                style = TextStyle(fontSize = 0.sp),
                density = Density(1f),
            ),
        )
    }

    @Test
    fun checklistCheckboxSizeKeepsSmallSpecifiedFontsVisible() {
        assertEquals(
            18.dp,
            markdownChecklistCheckboxSize(
                style = TextStyle(fontSize = 8.sp),
                density = Density(1f),
            ),
        )
    }
}
