package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.bikram.remember.ui.common.MarkdownBulletLineRegex
import dev.bikram.remember.ui.common.MarkdownChecklistLineRegex
import dev.bikram.remember.ui.common.MarkdownCodeFenceLineRegex
import dev.bikram.remember.ui.common.MarkdownHeadingLineRegex
import dev.bikram.remember.ui.common.MarkdownNumberedLineRegex
import dev.bikram.remember.ui.common.MarkdownQuoteLineRegex
import dev.bikram.remember.ui.common.MarkdownStyler

private val MarkdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private const val LivePreviewUncheckedChecklistMarker = "\u2610 "
private const val LivePreviewCheckedChecklistMarker = "\u2611 "
private const val LivePreviewQuoteMarker = "| "

internal class MarkdownVisualTransformation(
    private val styler: MarkdownStyler,
) : VisualTransformation {
    private var lastSource: String? = null
    private var lastTransformedText: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        if (lastSource == source) {
            return lastTransformedText ?: TransformedText(text, OffsetMapping.Identity)
        }

        val transformedMarkdown = MarkdownPreviewTransformationBuilder(source, styler).build()
        val transformedText =
            TransformedText(
                text = transformedMarkdown.text,
                offsetMapping = transformedMarkdown.offsetMapping,
            )
        lastSource = source
        lastTransformedText = transformedText
        return transformedText
    }
}

private class MarkdownPreviewTransformationBuilder(
    private val source: String,
    private val styler: MarkdownStyler,
) {
    private val hiddenRanges = mutableListOf<HiddenRange>()
    private val styleRanges = mutableListOf<MarkdownStyleRange>()
    private val insertedTextBeforeSourceIndex = mutableMapOf<Int, String>()

    fun build(): TransformedMarkdown {
        collectMarkdownRanges()
        val normalizedHiddenRanges = hiddenRanges.normalized()
        val transformedText = StringBuilder(source.length)
        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginal = mutableListOf<Int>()
        var hiddenRangeIndex = 0
        var transformedOffset = 0

        for (sourceIndex in source.indices) {
            insertedTextBeforeSourceIndex[sourceIndex]?.let { insertedText ->
                insertedText.forEach { character ->
                    transformedToOriginal.add(sourceIndex)
                    transformedText.append(character)
                    transformedOffset++
                }
            }
            while (
                hiddenRangeIndex < normalizedHiddenRanges.size &&
                sourceIndex >= normalizedHiddenRanges[hiddenRangeIndex].end
            ) {
                hiddenRangeIndex++
            }
            originalToTransformed[sourceIndex] = transformedOffset
            val hidden =
                hiddenRangeIndex < normalizedHiddenRanges.size &&
                    sourceIndex >= normalizedHiddenRanges[hiddenRangeIndex].start &&
                    sourceIndex < normalizedHiddenRanges[hiddenRangeIndex].end
            if (!hidden) {
                if (transformedToOriginal.size == transformedOffset) {
                    transformedToOriginal.add(sourceIndex)
                }
                transformedText.append(source[sourceIndex])
                transformedOffset++
            }
        }
        originalToTransformed[source.length] = transformedOffset
        transformedToOriginal.add(source.length)

        val annotatedString =
            buildAnnotatedString {
                append(transformedText.toString())
                styleRanges.forEach { styleRange ->
                    val transformedStart = originalToTransformed[styleRange.start.coerceIn(0, source.length)]
                    val transformedEnd = originalToTransformed[styleRange.end.coerceIn(0, source.length)]
                    if (transformedStart < transformedEnd) {
                        addStyle(styleRange.style, transformedStart, transformedEnd)
                    }
                }
            }

        return TransformedMarkdown(
            text = annotatedString,
            offsetMapping =
                MarkdownPreviewOffsetMapping(
                    originalToTransformed = originalToTransformed,
                    transformedToOriginal = transformedToOriginal.toIntArray(),
                ),
        )
    }

    private fun collectMarkdownRanges() {
        var lineStartIndex = 0
        var inCodeBlock = false
        while (lineStartIndex <= source.length) {
            val lineEndIndex =
                source.indexOf('\n', lineStartIndex).let { newlineIndex ->
                    if (newlineIndex < 0) source.length else newlineIndex
                }
            val line = source.substring(lineStartIndex, lineEndIndex)
            if (MarkdownCodeFenceLineRegex.matches(line)) {
                hiddenRanges.add(HiddenRange(lineStartIndex, lineEndIndex))
                inCodeBlock = !inCodeBlock
            } else if (inCodeBlock) {
                styleRanges.add(MarkdownStyleRange(lineStartIndex, lineEndIndex, styler.codeBlockSpanStyle))
            } else {
                collectLineRanges(line = line, lineStartIndex = lineStartIndex, lineEndIndex = lineEndIndex)
            }

            if (lineEndIndex == source.length) {
                break
            }
            lineStartIndex = lineEndIndex + 1
        }
    }

    private fun collectLineRanges(
        line: String,
        lineStartIndex: Int,
        lineEndIndex: Int,
    ) {
        MarkdownHeadingLineRegex.matchEntire(line)?.let { match ->
            val headingLevel = match.groupValues[1].length
            val contentStartIndex = lineStartIndex + match.groupValues[1].length + 1
            hiddenRanges.add(HiddenRange(lineStartIndex, contentStartIndex))
            styleRanges.add(MarkdownStyleRange(contentStartIndex, lineEndIndex, styler.headingSpanStyle(headingLevel)))
            collectInlineRanges(startIndex = contentStartIndex, endIndex = lineEndIndex)
            return
        }

        MarkdownChecklistLineRegex.matchEntire(line)?.let { match ->
            val checked = match.groupValues[2].equals("x", ignoreCase = true)
            val contentStartIndex = lineStartIndex + match.groups[3]!!.range.first
            insertedTextBeforeSourceIndex[lineStartIndex] =
                match.groupValues[1] +
                    if (checked) {
                        LivePreviewCheckedChecklistMarker
                    } else {
                        LivePreviewUncheckedChecklistMarker
                    }
            hiddenRanges.add(HiddenRange(lineStartIndex, contentStartIndex))
            collectInlineRanges(startIndex = contentStartIndex, endIndex = lineEndIndex)
            return
        }

        MarkdownBulletLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[2]!!.range.first
            insertedTextBeforeSourceIndex[lineStartIndex] = "  " + match.groupValues[1] + "\u2022 "
            hiddenRanges.add(HiddenRange(lineStartIndex, contentStartIndex))
            collectInlineRanges(startIndex = contentStartIndex, endIndex = lineEndIndex)
            return
        }

        MarkdownNumberedLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[3]!!.range.first
            insertedTextBeforeSourceIndex[lineStartIndex] = " " + match.groupValues[1] + match.groupValues[2] + ". "
            hiddenRanges.add(HiddenRange(lineStartIndex, contentStartIndex))
            collectInlineRanges(startIndex = contentStartIndex, endIndex = lineEndIndex)
            return
        }

        MarkdownQuoteLineRegex.matchEntire(line)?.let { match ->
            val contentStartIndex = lineStartIndex + match.groups[1]!!.range.first
            insertedTextBeforeSourceIndex[lineStartIndex] = LivePreviewQuoteMarker
            hiddenRanges.add(HiddenRange(lineStartIndex, contentStartIndex))
            styleRanges.add(MarkdownStyleRange(contentStartIndex, lineEndIndex, styler.quoteSpanStyle))
            collectInlineRanges(startIndex = contentStartIndex, endIndex = lineEndIndex)
            return
        }

        collectInlineRanges(startIndex = lineStartIndex, endIndex = lineEndIndex)
    }

    private fun collectInlineRanges(
        startIndex: Int,
        endIndex: Int,
    ) {
        var currentIndex = startIndex
        while (currentIndex < endIndex) {
            val linkMatch = MarkdownLinkRegex.find(source, currentIndex)
            if (linkMatch != null && linkMatch.range.first == currentIndex && linkMatch.range.last < endIndex) {
                val labelGroup = linkMatch.groups[1]
                val urlGroup = linkMatch.groups[2]
                if (labelGroup != null && urlGroup != null) {
                    val labelStartIndex = labelGroup.range.first
                    val labelEndIndex = labelGroup.range.last + 1
                    hiddenRanges.add(HiddenRange(linkMatch.range.first, labelStartIndex))
                    hiddenRanges.add(HiddenRange(labelEndIndex, linkMatch.range.last + 1))
                    styleRanges.add(MarkdownStyleRange(labelStartIndex, labelEndIndex, styler.linkSpanStyle))
                    collectInlineRanges(startIndex = labelStartIndex, endIndex = labelEndIndex)
                }
                currentIndex = linkMatch.range.last + 1
                continue
            }

            val inlineCodeClose = source.indexOf('`', currentIndex + 1)
            if (source.startsWith("`", currentIndex) && inlineCodeClose in (currentIndex + 1)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 1))
                hiddenRanges.add(HiddenRange(inlineCodeClose, inlineCodeClose + 1))
                styleRanges.add(MarkdownStyleRange(currentIndex + 1, inlineCodeClose, styler.inlineCodeSpanStyle))
                currentIndex = inlineCodeClose + 1
                continue
            }

            val underlineClose = source.indexOf("</u>", currentIndex + 3, ignoreCase = true)
            if (source.startsWith("<u>", currentIndex, ignoreCase = true) && underlineClose in (currentIndex + 3)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 3))
                hiddenRanges.add(HiddenRange(underlineClose, underlineClose + 4))
                styleRanges.add(MarkdownStyleRange(currentIndex + 3, underlineClose, styler.underlineSpanStyle))
                collectInlineRanges(startIndex = currentIndex + 3, endIndex = underlineClose)
                currentIndex = underlineClose + 4
                continue
            }

            val strikeClose = source.indexOf("~~", currentIndex + 2)
            if (source.startsWith("~~", currentIndex) && strikeClose in (currentIndex + 2)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 2))
                hiddenRanges.add(HiddenRange(strikeClose, strikeClose + 2))
                styleRanges.add(MarkdownStyleRange(currentIndex + 2, strikeClose, styler.strikethroughSpanStyle))
                collectInlineRanges(startIndex = currentIndex + 2, endIndex = strikeClose)
                currentIndex = strikeClose + 2
                continue
            }

            val boldItalicClose = source.indexOf("***", currentIndex + 3)
            if (source.startsWith("***", currentIndex) && boldItalicClose in (currentIndex + 3)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 3))
                hiddenRanges.add(HiddenRange(boldItalicClose, boldItalicClose + 3))
                styleRanges.add(MarkdownStyleRange(currentIndex + 3, boldItalicClose, styler.boldSpanStyle))
                styleRanges.add(MarkdownStyleRange(currentIndex + 3, boldItalicClose, styler.italicSpanStyle))
                collectInlineRanges(startIndex = currentIndex + 3, endIndex = boldItalicClose)
                currentIndex = boldItalicClose + 3
                continue
            }

            val boldClose = source.indexOf("**", currentIndex + 2)
            if (source.startsWith("**", currentIndex) && boldClose in (currentIndex + 2)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 2))
                hiddenRanges.add(HiddenRange(boldClose, boldClose + 2))
                styleRanges.add(MarkdownStyleRange(currentIndex + 2, boldClose, styler.boldSpanStyle))
                collectInlineRanges(startIndex = currentIndex + 2, endIndex = boldClose)
                currentIndex = boldClose + 2
                continue
            }

            val italicClose = source.indexOf('*', currentIndex + 1)
            if (source.startsWith("*", currentIndex) && italicClose in (currentIndex + 1)..<endIndex) {
                hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 1))
                hiddenRanges.add(HiddenRange(italicClose, italicClose + 1))
                styleRanges.add(MarkdownStyleRange(currentIndex + 1, italicClose, styler.italicSpanStyle))
                collectInlineRanges(startIndex = currentIndex + 1, endIndex = italicClose)
                currentIndex = italicClose + 1
                continue
            }

            currentIndex++
        }
    }
}

private class MarkdownPreviewOffsetMapping(
    private val originalToTransformed: IntArray,
    private val transformedToOriginal: IntArray,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

    override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
}

private fun List<HiddenRange>.normalized(): List<HiddenRange> {
    if (isEmpty()) {
        return emptyList()
    }
    val sortedRanges = sortedBy { range -> range.start }
    val normalizedRanges = mutableListOf<HiddenRange>()
    var currentRange = sortedRanges.first()
    sortedRanges.drop(1).forEach { range ->
        if (range.start <= currentRange.end) {
            currentRange = HiddenRange(currentRange.start, maxOf(currentRange.end, range.end))
        } else {
            normalizedRanges.add(currentRange)
            currentRange = range
        }
    }
    normalizedRanges.add(currentRange)
    return normalizedRanges
}

private data class HiddenRange(
    val start: Int,
    val end: Int,
)

private data class MarkdownStyleRange(
    val start: Int,
    val end: Int,
    val style: androidx.compose.ui.text.SpanStyle,
)

private data class TransformedMarkdown(
    val text: AnnotatedString,
    val offsetMapping: OffsetMapping,
)
