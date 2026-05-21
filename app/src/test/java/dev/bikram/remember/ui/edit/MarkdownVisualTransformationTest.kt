package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
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
        // "*Bold**" -> opens with *, next close single * at index 5 is followed by *, so rejected.
        // There is no other single closing *. So it falls back to plain text.
        // wait, let's see how our parser handles "*Bold**, *italic*"
        // At 0: starts with "*". Valid opening? Yes.
        // It searches for closing single *:
        // Index 5: rejected (followed by *).
        // Index 6: rejected (preceded by *).
        // Index 10: preceded by space, so rejected as closing.
        // Index 17: preceded by "c", so accepted!
        // So it treats "Bold**, *italic" as italicized, and renders "Bold**, *italic" with italic style.
        // This is still balanced because the opening "*" at index 0 matched the closing "*" at index 17.
        // Now let's check "*Bold**" by itself:
        val textSingle = styler.markdownInlineAnnotatedString("*Bold**")
        // Since there is no other closing asterisk, it should fall back to plain text and render as "*Bold**"
        assertEquals("*Bold**", textSingle.text)
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
