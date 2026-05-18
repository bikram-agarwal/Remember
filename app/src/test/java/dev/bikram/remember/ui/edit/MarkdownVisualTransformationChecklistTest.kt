package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import dev.bikram.remember.ui.common.MarkdownStyler
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownVisualTransformationChecklistTest {
    @Test
    fun uncheckedChecklistMarkerAppearsInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("- [ ] item"))

        assertEquals("\u2610 item", transformedText.text.text)
    }

    @Test
    fun checkedChecklistMarkerAppearsInLivePreviewText() {
        val transformation = MarkdownVisualTransformation(testMarkdownStyler())

        val transformedText = transformation.filter(AnnotatedString("- [x] done"))

        assertEquals("\u2611 done", transformedText.text.text)
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
