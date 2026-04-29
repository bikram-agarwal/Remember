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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

internal val MarkdownHeadingLineRegex = Regex("""^(#{1,3})\s+(.*)$""")
internal val MarkdownChecklistLineRegex = Regex("""^(\s*)- \[([ xX])\]\s+(.*)$""")
internal val MarkdownBulletLineRegex = Regex("""^(\s*)[-*+]\s+(.*)$""")
internal val MarkdownNumberedLineRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
internal val MarkdownQuoteLineRegex = Regex("""^\s*>\s?(.*)$""")
internal val MarkdownCodeFenceLineRegex = Regex("""^\s*```\s*$""")
private val MarkdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")

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
    val codeBlockSpanStyle: SpanStyle = SpanStyle(color = quoteColor, fontFamily = FontFamily.Monospace)
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

    fun markdownInlineAnnotatedString(source: String): AnnotatedString =
        buildAnnotatedString {
            appendInlineMarkdown(source = source)
        }

    fun markdownPreviewAnnotatedString(markdown: String): AnnotatedString {
        val previewSource =
            markdown.lines().joinToString("\n") { line ->
                visibleLineContent(line)
            }
        return markdownInlineAnnotatedString(previewSource)
    }

    fun markdownEditingAnnotatedString(markdown: String): AnnotatedString =
        buildAnnotatedString {
            append(markdown)
            applyBlockStyles(
                source = markdown,
                builder = this,
            )
        }

    fun quoteContentForLine(line: String): String? {
        val quoteMatch = MarkdownQuoteLineRegex.matchEntire(line)
        if (quoteMatch != null) {
            return quoteMatch.groupValues[1]
        }
        return null
    }

    fun visibleLineContent(line: String): String {
        MarkdownHeadingLineRegex.matchEntire(line)?.let { match ->
            return match.groupValues[2]
        }
        MarkdownChecklistLineRegex.matchEntire(line)?.let { match ->
            return match.groupValues[3]
        }
        MarkdownBulletLineRegex.matchEntire(line)?.let { match ->
            return match.groupValues[2]
        }
        MarkdownNumberedLineRegex.matchEntire(line)?.let { match ->
            return match.groupValues[3]
        }
        MarkdownQuoteLineRegex.matchEntire(line)?.let { match ->
            return match.groupValues[1]
        }
        return line
    }

    private fun AnnotatedString.Builder.appendInlineMarkdown(source: String) {
        var currentIndex = 0
        while (currentIndex < source.length) {
            val linkMatch = MarkdownLinkRegex.find(source, currentIndex)
            if (linkMatch != null && linkMatch.range.first == currentIndex) {
                val url = linkMatch.groupValues[2]
                withLink(LinkAnnotation.Url(url.withHttpScheme())) {
                    withStyle(linkSpanStyle) {
                        appendInlineMarkdown(source = linkMatch.groupValues[1])
                    }
                }
                currentIndex = linkMatch.range.last + 1
                continue
            }

            val inlineCodeClose = source.indexOf('`', currentIndex + 1)
            if (source.startsWith("`", currentIndex) && inlineCodeClose > currentIndex) {
                withStyle(inlineCodeSpanStyle) {
                    append(source.substring(currentIndex + 1, inlineCodeClose))
                }
                currentIndex = inlineCodeClose + 1
                continue
            }

            val underlineClose = source.indexOf("</u>", currentIndex + 3, ignoreCase = true)
            if (source.startsWith("<u>", currentIndex, ignoreCase = true) && underlineClose > currentIndex) {
                withStyle(underlineSpanStyle) {
                    appendInlineMarkdown(source = source.substring(currentIndex + 3, underlineClose))
                }
                currentIndex = underlineClose + 4
                continue
            }

            val strikeClose = source.indexOf("~~", currentIndex + 2)
            if (source.startsWith("~~", currentIndex) && strikeClose > currentIndex) {
                withStyle(strikethroughSpanStyle) {
                    appendInlineMarkdown(source = source.substring(currentIndex + 2, strikeClose))
                }
                currentIndex = strikeClose + 2
                continue
            }

            val boldItalicClose = source.indexOf("***", currentIndex + 3)
            if (source.startsWith("***", currentIndex) && boldItalicClose > currentIndex) {
                withStyle(boldSpanStyle) {
                    withStyle(italicSpanStyle) {
                        appendInlineMarkdown(source = source.substring(currentIndex + 3, boldItalicClose))
                    }
                }
                currentIndex = boldItalicClose + 3
                continue
            }

            val boldClose = source.indexOf("**", currentIndex + 2)
            if (source.startsWith("**", currentIndex) && boldClose > currentIndex) {
                withStyle(boldSpanStyle) {
                    appendInlineMarkdown(source = source.substring(currentIndex + 2, boldClose))
                }
                currentIndex = boldClose + 2
                continue
            }

            val italicClose = source.indexOf('*', currentIndex + 1)
            if (source.startsWith("*", currentIndex) && italicClose > currentIndex) {
                withStyle(italicSpanStyle) {
                    appendInlineMarkdown(source = source.substring(currentIndex + 1, italicClose))
                }
                currentIndex = italicClose + 1
                continue
            }

            append(source[currentIndex])
            currentIndex++
        }
    }

    private fun applyBlockStyles(
        source: String,
        builder: AnnotatedString.Builder,
    ) {
        var lineStartIndex = 0
        var inCodeBlock = false
        while (lineStartIndex <= source.length) {
            val lineEndIndex =
                source.indexOf('\n', lineStartIndex).let { newlineIndex ->
                    if (newlineIndex < 0) source.length else newlineIndex
                }
            val line = source.substring(lineStartIndex, lineEndIndex)
            if (MarkdownCodeFenceLineRegex.matches(line)) {
                builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, lineEndIndex)
                inCodeBlock = !inCodeBlock
            } else if (inCodeBlock) {
                builder.addStyle(codeBlockSpanStyle, lineStartIndex, lineEndIndex)
            } else {
                applyMarkdownLineStyles(
                    source = source,
                    line = line,
                    lineStartIndex = lineStartIndex,
                    lineEndIndex = lineEndIndex,
                    builder = builder,
                )
            }

            if (lineEndIndex == source.length) {
                break
            }
            lineStartIndex = lineEndIndex + 1
        }
    }

    private fun applyMarkdownLineStyles(
        source: String,
        line: String,
        lineStartIndex: Int,
        lineEndIndex: Int,
        builder: AnnotatedString.Builder,
    ) {
        MarkdownHeadingLineRegex.matchEntire(line)?.let { match ->
            val headingLevel = match.groupValues[1].length
            val contentStartIndex = lineStartIndex + match.groupValues[1].length + 1
            builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, contentStartIndex)
            builder.addStyle(headingSpanStyle(headingLevel), contentStartIndex, lineEndIndex)
            applyInlineEditingStyles(source = source, startIndex = contentStartIndex, endIndex = lineEndIndex, builder = builder)
            return
        }

        MarkdownChecklistLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[3]!!.range.first
            builder.addStyle(listParagraphStyle, lineStartIndex, lineEndIndex)
            builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, contentStartIndex)
            applyInlineEditingStyles(source = source, startIndex = contentStartIndex, endIndex = lineEndIndex, builder = builder)
            return
        }

        MarkdownBulletLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[2]!!.range.first
            builder.addStyle(listParagraphStyle, lineStartIndex, lineEndIndex)
            builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, contentStartIndex)
            applyInlineEditingStyles(source = source, startIndex = contentStartIndex, endIndex = lineEndIndex, builder = builder)
            return
        }

        MarkdownNumberedLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[3]!!.range.first
            builder.addStyle(listParagraphStyle, lineStartIndex, lineEndIndex)
            builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, contentStartIndex)
            applyInlineEditingStyles(source = source, startIndex = contentStartIndex, endIndex = lineEndIndex, builder = builder)
            return
        }

        MarkdownQuoteLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[1]!!.range.first
            builder.addStyle(quoteParagraphStyle, lineStartIndex, lineEndIndex)
            builder.addStyle(syntaxMarkerSpanStyle, lineStartIndex, contentStartIndex)
            builder.addStyle(quoteSpanStyle, contentStartIndex, lineEndIndex)
            applyInlineEditingStyles(source = source, startIndex = contentStartIndex, endIndex = lineEndIndex, builder = builder)
            return
        }

        applyInlineEditingStyles(
            source = source,
            startIndex = lineStartIndex,
            endIndex = lineEndIndex,
            builder = builder,
        )
    }

    private fun applyInlineEditingStyles(
        source: String,
        startIndex: Int,
        endIndex: Int,
        builder: AnnotatedString.Builder,
    ) {
        var currentIndex = startIndex
        while (currentIndex < endIndex) {
            val linkMatch = MarkdownLinkRegex.find(source, currentIndex)
            if (linkMatch != null && linkMatch.range.first == currentIndex && linkMatch.range.last < endIndex) {
                applyLinkEditingStyles(
                    match = linkMatch,
                    builder = builder,
                    source = source,
                    endIndex = endIndex,
                )
                currentIndex = linkMatch.range.last + 1
                continue
            }

            val inlineCodeClose = source.indexOf('`', currentIndex + 1)
            if (source.startsWith("`", currentIndex) && inlineCodeClose in (currentIndex + 1)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 1)
                builder.addStyle(inlineCodeSpanStyle, currentIndex + 1, inlineCodeClose)
                builder.addStyle(syntaxMarkerSpanStyle, inlineCodeClose, inlineCodeClose + 1)
                currentIndex = inlineCodeClose + 1
                continue
            }

            val underlineClose = source.indexOf("</u>", currentIndex + 3, ignoreCase = true)
            if (source.startsWith("<u>", currentIndex, ignoreCase = true) && underlineClose in (currentIndex + 3)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 3)
                builder.addStyle(underlineSpanStyle, currentIndex + 3, underlineClose)
                applyInlineEditingStyles(source = source, startIndex = currentIndex + 3, endIndex = underlineClose, builder = builder)
                builder.addStyle(syntaxMarkerSpanStyle, underlineClose, underlineClose + 4)
                currentIndex = underlineClose + 4
                continue
            }

            val strikeClose = source.indexOf("~~", currentIndex + 2)
            if (source.startsWith("~~", currentIndex) && strikeClose in (currentIndex + 2)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 2)
                builder.addStyle(strikethroughSpanStyle, currentIndex + 2, strikeClose)
                applyInlineEditingStyles(source = source, startIndex = currentIndex + 2, endIndex = strikeClose, builder = builder)
                builder.addStyle(syntaxMarkerSpanStyle, strikeClose, strikeClose + 2)
                currentIndex = strikeClose + 2
                continue
            }

            val boldItalicClose = source.indexOf("***", currentIndex + 3)
            if (source.startsWith("***", currentIndex) && boldItalicClose in (currentIndex + 3)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 3)
                builder.addStyle(boldSpanStyle, currentIndex + 3, boldItalicClose)
                builder.addStyle(italicSpanStyle, currentIndex + 3, boldItalicClose)
                applyInlineEditingStyles(source = source, startIndex = currentIndex + 3, endIndex = boldItalicClose, builder = builder)
                builder.addStyle(syntaxMarkerSpanStyle, boldItalicClose, boldItalicClose + 3)
                currentIndex = boldItalicClose + 3
                continue
            }

            val boldClose = source.indexOf("**", currentIndex + 2)
            if (source.startsWith("**", currentIndex) && boldClose in (currentIndex + 2)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 2)
                builder.addStyle(boldSpanStyle, currentIndex + 2, boldClose)
                applyInlineEditingStyles(source = source, startIndex = currentIndex + 2, endIndex = boldClose, builder = builder)
                builder.addStyle(syntaxMarkerSpanStyle, boldClose, boldClose + 2)
                currentIndex = boldClose + 2
                continue
            }

            val italicClose = source.indexOf('*', currentIndex + 1)
            if (source.startsWith("*", currentIndex) && italicClose in (currentIndex + 1)..<endIndex) {
                builder.addStyle(syntaxMarkerSpanStyle, currentIndex, currentIndex + 1)
                builder.addStyle(italicSpanStyle, currentIndex + 1, italicClose)
                applyInlineEditingStyles(source = source, startIndex = currentIndex + 1, endIndex = italicClose, builder = builder)
                builder.addStyle(syntaxMarkerSpanStyle, italicClose, italicClose + 1)
                currentIndex = italicClose + 1
                continue
            }

            currentIndex++
        }
    }

    private fun applyLinkEditingStyles(
        match: MatchResult,
        builder: AnnotatedString.Builder,
        source: String,
        endIndex: Int,
    ) {
        val labelGroup = match.groups[1] ?: return
        val urlGroup = match.groups[2] ?: return
        val labelStartIndex = labelGroup.range.first
        val labelEndIndex = labelGroup.range.last + 1
        val urlStartIndex = urlGroup.range.first
        val linkEndIndex = match.range.last + 1
        if (linkEndIndex > endIndex) {
            return
        }
        builder.addStyle(syntaxMarkerSpanStyle, match.range.first, labelStartIndex)
        builder.addStyle(linkSpanStyle, labelStartIndex, labelEndIndex)
        applyInlineEditingStyles(source = source, startIndex = labelStartIndex, endIndex = labelEndIndex, builder = builder)
        builder.addStyle(syntaxMarkerSpanStyle, labelEndIndex, urlStartIndex)
        builder.addStyle(syntaxMarkerSpanStyle, urlStartIndex, linkEndIndex)
    }
}

private fun String.withHttpScheme(): String {
    if (startsWith("http://") || startsWith("https://")) {
        return this
    }
    return "https://$this"
}
