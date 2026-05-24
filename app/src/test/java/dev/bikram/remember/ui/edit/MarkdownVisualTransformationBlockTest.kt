package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownVisualTransformationBlockTest {
    @Test
    fun transformedEndOffsetMapsToSourceEndWhenSourceEndsWithHiddenSyntax() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("<u>test</u>"))

        assertEquals("test", transformedText.text.text)
        assertEquals("<u>test</u>".length, transformedText.offsetMapping.transformedToOriginal(4))
    }

    @Test
    fun quoteMarkerAppearsInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("> quoted"))

        assertEquals("| quoted", transformedText.text.text)
    }

    @Test
    fun codeBlockContentGetsBackgroundInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("```\ncode\n```"))

        assertTrue(
            transformedText.text.spanStyles.any { spanRange ->
                spanRange.item.background == Color.LightGray &&
                    spanRange.start <= transformedText.text.text.indexOf("code") &&
                    spanRange.end >= transformedText.text.text.indexOf("code") + "code".length
            },
        )
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
