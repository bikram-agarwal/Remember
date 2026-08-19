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
    fun horizontalRuleLineIsHiddenFromLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("before\n---\nafter"))

        assertEquals("before\n\nafter", transformedText.text.text)
    }

    @Test
    fun horizontalRuleBlankLineMapsBackToEndOfDashes() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("---\nafter"))

        // A backspace on the blank rule line has to land on the last dash, turning `---` into `--`.
        assertEquals("---".length, transformedText.offsetMapping.transformedToOriginal(0))
        assertEquals(0, transformedText.offsetMapping.originalToTransformed("---".length))
    }

    @Test
    fun twoDashesStayVisibleInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("--"))

        assertEquals("--", transformedText.text.text)
    }

    @Test
    fun bulletLineIsNotTreatedAsHorizontalRule() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("- item"))

        assertEquals("  \u2022 item", transformedText.text.text)
    }

    @Test
    fun dashRunInsideCodeBlockStaysVisible() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("```\n---\n```"))

        assertEquals("\n---\n", transformedText.text.text)
    }

    @Test
    fun horizontalRuleLineStartsSkipsBulletsAndFencedDashRuns() {
        val source = "intro\n---\n- item\n```\n---\n```\n----"

        assertEquals(
            listOf(source.indexOf("---"), source.lastIndexOf("----")),
            markdownHorizontalRuleLineStarts(source),
        )
    }

    @Test
    fun horizontalRuleLineStartsIgnoresBodiesWithoutDashRuns() {
        assertEquals(emptyList<Int>(), markdownHorizontalRuleLineStarts("plain body\n-- not a rule"))
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
