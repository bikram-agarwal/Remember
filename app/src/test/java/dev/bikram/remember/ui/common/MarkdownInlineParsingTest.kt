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
    fun closingMarkerFallsBackToGluedMarkerForEmptyWrapper() {
        assertEquals(2, "****".indexOfMarkdownClosingMarker("**", startIndex = 2))
        assertEquals(2, "~~~~".indexOfMarkdownClosingMarker("~~", startIndex = 2))
        assertEquals(1, "``".indexOfMarkdownClosingMarker("`", startIndex = 1))
    }

    @Test
    fun closingMarkerPrefersLaterNonGluedCandidateOverEarlierGluedFallback() {
        // The glued candidate at index 2 (part of the leading "****", an empty bold wrapper) is
        // recorded as a fallback. The later "**bold**" (indices 9-16) is a separate, already
        // self-closed span — its own opener and closer fully consume each other, so neither is
        // available to close this outer, unrelated search. With no better candidate, the fallback
        // wins: index 2.
        assertEquals(2, "**** and **bold**".indexOfMarkdownClosingMarker("**", startIndex = 2))
    }

    @Test
    fun closingMarkerCorrectlyResolvesRunsForNestedEmphasis() {
        // **bold and *italic bold***
        // outer bold starts at 0, searching for closing "**" starting at 2.
        // It should match index 24 (the last two asterisks of "***"), leaving the first asterisk to close the inner "*".
        assertEquals(24, "**bold and *italic bold***".indexOfMarkdownClosingMarker("**", startIndex = 2))

        // *italic and **bold***
        // outer italic starts at 0, searching for closing "*" starting at 1.
        // It should match index 20 (the last asterisk of "***"), leaving the first two to close the inner "**".
        assertEquals(20, "*italic and **bold***".indexOfMarkdownClosingMarker("*", startIndex = 1))
    }

    @Test
    fun closingMarkerRejectsStealingFromLaterSentences() {
        // "This is *italic. And now **bold**"
        // Outer unclosed "*" starts at 8, searching for closing "*" starting at 9.
        // The candidate "**" closer at the end (index 31) belongs to the inner "**" opener at index 25.
        // It should reject this run and return -1 or fallback because the "**" is fully consumed by the inner "**".
        assertEquals(-1, "This is *italic. And now **bold**".indexOfMarkdownClosingMarker("*", startIndex = 9))
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
