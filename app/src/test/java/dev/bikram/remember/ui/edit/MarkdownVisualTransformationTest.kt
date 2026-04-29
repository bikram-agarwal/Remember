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
