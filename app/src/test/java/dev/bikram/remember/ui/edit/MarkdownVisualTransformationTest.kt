package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownVisualTransformationTest {
    @Test
    fun boldItalicSyntaxIsHiddenInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("***Bold-italic***"))

        assertEquals("Bold-italic", transformedText.text.text)
        assertEquals(0, transformedText.offsetMapping.originalToTransformed(0))
        assertEquals(0, transformedText.offsetMapping.originalToTransformed(3))
        assertEquals(11, transformedText.offsetMapping.originalToTransformed(14))
        assertEquals(11, transformedText.offsetMapping.originalToTransformed(17))
    }

    @Test
    fun boldItalicSyntaxAppliesBoldAndItalicStyles() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("***Bold-italic***"))

        assertTrue(transformedText.text.spanStyles.any { spanRange -> spanRange.start == 0 && spanRange.end == 11 })
        assertTrue(transformedText.text.spanStyles.size >= 2)
    }

    @Test
    fun unmatchedAsteriskAtEndDoesNotCorruptSubsequentParsing() {
        val styler = testMarkdownStyler()

        val text = styler.markdownInlineAnnotatedString("*Bold**, *italic*")
        // The single "*" at index 9 (before "italic") and the one at index 16 (after) form their
        // own self-contained, already-closed italic span, so neither is available to the outer
        // "*" opened at index 0. That outer opener falls back to the only remaining candidate: the
        // glued "**" at index 5-6, using just its first "*" and leaving the second as a literal.
        // "italic" ends up correctly italicized on its own, separate match.
        assertEquals("Bold*, italic", text.text)

        // With no later closing "*" anywhere else in the string, the same glued fallback is all
        // that's available: "Bold" is italicized and the trailing "*" is left as a literal.
        val textSingle = styler.markdownInlineAnnotatedString("*Bold**")
        assertEquals("Bold*", textSingle.text)
    }

    @Test
    fun whitespaceFlankedAsterisksAreNotTreatedAsFormatting() {
        val styler = testMarkdownStyler()
        val text = styler.markdownInlineAnnotatedString("* bold *")
        // "* bold *" has space after opening * and space before closing *.
        // So both isValidOpening and indexOfClosingMarker reject them.
        // It should render exactly as "* bold *"
        assertEquals("* bold *", text.text)
    }

    @Test
    fun closingMarkerFollowedByWhitespaceIsTreatedAsFormatting() {
        val styler = testMarkdownStyler()

        assertEquals("bold", styler.markdownInlineAnnotatedString("*bold*").text)
        assertEquals("bold next", styler.markdownInlineAnnotatedString("*bold* next").text)
        assertEquals("code next", styler.markdownInlineAnnotatedString("`code` next").text)
    }

    @Test
    fun nestedFormattingResolvesCorrectly() {
        val styler = testMarkdownStyler()

        val text = styler.markdownInlineAnnotatedString("**bold and *italic bold***")
        assertEquals("bold and italic bold", text.text)

        assertTrue(
            text.spanStyles.any { spanRange ->
                spanRange.start == 0 && spanRange.end == 20 && spanRange.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
            },
        )

        assertTrue(
            text.spanStyles.any { spanRange ->
                spanRange.start == 9 && spanRange.end == 20 && spanRange.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic
            },
        )
    }

    @Test
    fun unclosedEmphasisDoesNotBleedToLaterSentences() {
        val styler = testMarkdownStyler()

        val text = styler.markdownInlineAnnotatedString("This is *italic. And now **bold**")
        assertEquals("This is *italic. And now bold", text.text)

        // The first '*' should not be treated as italic formatting since it is unclosed.
        assertTrue(
            text.spanStyles.none { spanRange ->
                spanRange.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic
            },
        )

        // The "**bold**" section should still be properly styled as bold.
        assertTrue(
            text.spanStyles.any { spanRange ->
                spanRange.start == 25 && spanRange.end == 29 && spanRange.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold
            },
        )
    }

    @Test
    fun inlineLinksKeepLinkAnnotationsByDefault() {
        val styler = testMarkdownStyler()
        val text = styler.markdownInlineAnnotatedString("[site](example.com)")

        val links = text.getLinkAnnotations(start = 0, end = text.length)

        assertEquals("site", text.text)
        assertEquals(1, links.size)
        assertEquals("https://example.com", (links.single().item as LinkAnnotation.Url).url)
    }

    @Test
    fun inlineLinksCanSuppressLinkAnnotationsForCustomGestureHandling() {
        val styler = testMarkdownStyler()
        val text =
            styler.markdownInlineAnnotatedString(
                source = "[site](example.com)",
                includeLinkAnnotations = false,
            )

        assertEquals("site", text.text)
        assertTrue(text.getLinkAnnotations(start = 0, end = text.length).isEmpty())
        assertTrue(text.spanStyles.any { spanRange -> spanRange.start == 0 && spanRange.end == 4 })
    }

    @Test
    fun largePlainTextPasteDoesNotDegradeToQuadraticTime() {
        // Regression test for an ANR: close-marker searches (backtick/underline/strike/bold-
        // italic/bold/italic) were computed unconditionally on every character before checking
        // whether the character was even a plausible opening marker, turning a plain-text paste
        // with no markdown syntax into an O(n^2) scan. 80k characters (roughly a 12k-word paste)
        // should still complete in well under a second on a normal machine; the old O(n^2)
        // behavior would take tens of seconds or more at this size.
        val largePlainText = "The quick brown fox jumps over the lazy dog. ".repeat(2000)
        val styler = testMarkdownStyler()

        val liveEditDuration =
            measureElapsedMillis {
                MarkdownVisualTransformation(styler).filter(AnnotatedString(largePlainText))
            }
        val viewModeDuration =
            measureElapsedMillis {
                styler.markdownInlineAnnotatedString(largePlainText)
            }

        assertTrue("Live-preview transform took ${liveEditDuration}ms, expected well under 2000ms", liveEditDuration < 2000)
        assertTrue("View-mode render took ${viewModeDuration}ms, expected well under 2000ms", viewModeDuration < 2000)
    }

    @Test
    fun manyBoldSpansDoNotDegradeToQuadraticTime() {
        // Regression test for a second ANR: indexOfMarkdownClosingMarker's nested-emphasis
        // resolution rebuilt its opener stack from scratch (rescanning from the search's start
        // index) for every asterisk-run candidate, which is O(n) per candidate. A note with many
        // bold/italic spans — completely ordinary usage, not just a giant plain-text paste — could
        // have many candidates, making the whole scan O(n^2). 2000 short bold spans should still
        // complete in well under a second.
        val manyBoldSpans = "word **bold** ".repeat(2000)
        val styler = testMarkdownStyler()

        val liveEditDuration =
            measureElapsedMillis {
                MarkdownVisualTransformation(styler).filter(AnnotatedString(manyBoldSpans))
            }
        val viewModeDuration =
            measureElapsedMillis {
                styler.markdownInlineAnnotatedString(manyBoldSpans)
            }

        assertTrue("Live-preview transform took ${liveEditDuration}ms, expected well under 2000ms", liveEditDuration < 2000)
        assertTrue("View-mode render took ${viewModeDuration}ms, expected well under 2000ms", viewModeDuration < 2000)
    }
}

private fun measureElapsedMillis(block: () -> Unit): Long {
    val start = System.currentTimeMillis()
    block()
    return System.currentTimeMillis() - start
}

private fun testMarkdownStyler(): MarkdownStyler =
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
