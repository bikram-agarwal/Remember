package dev.bikram.remember.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

@Composable
internal fun rememberMarkdownStyler(bodyStyle: TextStyle): MarkdownStyler {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val quoteBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val syntaxMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val headingOneStyle = MaterialTheme.typography.headlineSmall
    val headingTwoStyle = MaterialTheme.typography.titleLarge
    val headingThreeStyle = MaterialTheme.typography.titleMedium
    val smallShape = MaterialTheme.shapes.small

    return remember(
        bodyStyle,
        linkColor,
        codeBackground,
        quoteColor,
        quoteBarColor,
        syntaxMarkerColor,
        headingOneStyle,
        headingTwoStyle,
        headingThreeStyle,
        smallShape,
    ) {
        MarkdownStyler(
            bodyStyle = bodyStyle,
            linkColor = linkColor,
            codeBackground = codeBackground,
            quoteColor = quoteColor,
            quoteBarColor = quoteBarColor,
            syntaxMarkerColor = syntaxMarkerColor,
            headingOneBaseStyle = headingOneStyle,
            headingTwoBaseStyle = headingTwoStyle,
            headingThreeBaseStyle = headingThreeStyle,
        )
    }
}

internal class MarkdownStyler(
    private val bodyStyle: TextStyle,
    val linkColor: Color,
    val codeBackground: Color,
    val quoteColor: Color,
    val quoteBarColor: Color,
    @Suppress("UNUSED_PARAMETER") syntaxMarkerColor: Color,
    private val headingOneBaseStyle: TextStyle,
    private val headingTwoBaseStyle: TextStyle,
    private val headingThreeBaseStyle: TextStyle,
) {
    val syntaxMarkerSpanStyle: SpanStyle = SpanStyle(color = Color.Transparent)
    val linkSpanStyle: SpanStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    val inlineCodeSpanStyle: SpanStyle = SpanStyle(background = codeBackground, fontFamily = FontFamily.Monospace)
    val quoteSpanStyle: SpanStyle = SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)
    val codeBlockSpanStyle: SpanStyle = SpanStyle(color = quoteColor, background = codeBackground, fontFamily = FontFamily.Monospace)
    val boldSpanStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicSpanStyle: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val underlineSpanStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    val strikethroughSpanStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val listParagraphStyle: ParagraphStyle = ParagraphStyle(textIndent = TextIndent(restLine = 24.sp))
    val quoteParagraphStyle: ParagraphStyle = ParagraphStyle(textIndent = TextIndent(restLine = 12.sp))

    fun headingTextStyle(headingLevel: Int): TextStyle {
        val baseHeadingStyle =
            when (headingLevel) {
                1 -> headingOneBaseStyle
                2 -> headingTwoBaseStyle
                else -> headingThreeBaseStyle
            }
        val bodyFontSize = bodyStyle.fontSize
        val headingFontSize =
            if (bodyFontSize.isSpecified) {
                val multiplier =
                    when (headingLevel) {
                        1 -> 1.45f
                        2 -> 1.25f
                        else -> 1.12f
                    }
                (bodyFontSize.value * multiplier).sp
            } else {
                baseHeadingStyle.fontSize
            }
        return baseHeadingStyle.copy(
            color = bodyStyle.color,
            fontSize = headingFontSize,
            fontWeight = FontWeight.Bold,
        )
    }

    fun headingSpanStyle(headingLevel: Int): SpanStyle = headingTextStyle(headingLevel).toSpanStyle()

    fun listStartPadding(
        rawIndent: String,
        baseIndent: Dp,
    ): Dp {
        val nestedIndent =
            if (rawIndent.isNotEmpty()) {
                24.dp
            } else {
                0.dp
            }
        return baseIndent + nestedIndent
    }

    fun inlineSpanStyle(kind: MarkdownInlineKind): SpanStyle =
        when (kind) {
            MarkdownInlineKind.Bold -> boldSpanStyle
            MarkdownInlineKind.Italic -> italicSpanStyle
            MarkdownInlineKind.BoldItalic -> boldSpanStyle.merge(italicSpanStyle)
            MarkdownInlineKind.Underline -> underlineSpanStyle
            MarkdownInlineKind.Strikethrough -> strikethroughSpanStyle
            MarkdownInlineKind.Code -> inlineCodeSpanStyle
            MarkdownInlineKind.Link -> linkSpanStyle
        }

    fun markdownInlineAnnotatedString(
        source: String,
        includeLinkAnnotations: Boolean = true,
    ): AnnotatedString = renderInline(MarkdownInlineProjection(source), includeLinkAnnotations)

    fun renderInline(
        projection: MarkdownInlineProjection,
        includeLinkAnnotations: Boolean = true,
        textDecoration: TextDecoration? = null,
    ): AnnotatedString =
        buildAnnotatedString {
            append(projection.text)
            if (textDecoration != null && length > 0) {
                addStyle(SpanStyle(textDecoration = textDecoration), 0, length)
            }
            for (span in projection.spans) {
                val start = projection.visibleOffsets[span.openEnd]
                val end = projection.visibleOffsets[span.closeStart]
                if (end <= start) continue
                addStyle(inlineSpanStyle(span.kind), start, end)
                if (includeLinkAnnotations && span.url != null) {
                    addLink(LinkAnnotation.Url(span.url.markdownLinkUrl()), start, end)
                }
            }
        }.withCombinedMarkdownDecorations()

    fun markdownPreviewAnnotatedString(
        markdown: String,
        includeLinkAnnotations: Boolean = true,
    ): AnnotatedString = markdownCardPreview(markdown, this, includeLinkAnnotations).text

    fun markdownEditingAnnotatedString(markdown: String): AnnotatedString {
        val syntax = parseMarkdownDocument(markdown)
        return buildAnnotatedString {
            append(markdown)
            for (line in syntax.lines) {
                if (line.contentStart > line.start) addStyle(syntaxMarkerSpanStyle, line.start, line.contentStart)
                when (line.kind) {
                    MarkdownBlockKind.Heading -> addStyle(headingSpanStyle(line.headingLevel), line.contentStart, line.end)
                    MarkdownBlockKind.Checklist, MarkdownBlockKind.Bullet, MarkdownBlockKind.Numbered -> addStyle(listParagraphStyle, line.start, line.end)
                    MarkdownBlockKind.Quote -> {
                        addStyle(quoteParagraphStyle, line.start, line.end)
                        addStyle(quoteSpanStyle, line.contentStart, line.end)
                    }
                    MarkdownBlockKind.Code -> addStyle(codeBlockSpanStyle, line.start, line.end)
                    else -> Unit
                }
                if (line.checked) addStyle(strikethroughSpanStyle, line.contentStart, line.end)
            }
            for (span in syntax.spans) {
                addStyle(inlineSpanStyle(span.kind), span.openEnd, span.closeStart)
                addStyle(syntaxMarkerSpanStyle, span.openStart, span.openEnd)
                addStyle(syntaxMarkerSpanStyle, span.closeStart, span.closeEnd)
            }
        }.withCombinedMarkdownDecorations()
    }
}
