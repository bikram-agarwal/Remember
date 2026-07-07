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
import dev.bikram.remember.ui.common.indexOfMarkdownClosingMarker
import dev.bikram.remember.ui.common.isValidOpening

private val MarkdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private const val LIVE_PREVIEW_UNCHECKED_CHECKLIST_MARKER = "\u2610 "
private const val LIVE_PREVIEW_CHECKED_CHECKLIST_MARKER = "\u2611 "
private const val LIVE_PREVIEW_QUOTE_MARKER = "| "

// Hard safety net: never parse markdown for live-preview highlighting past this size, regardless
// of debounce state, so a huge paste/import can never reintroduce per-keystroke cost.
internal const val LIVE_PREVIEW_HIGHLIGHT_MAX_CHARS = 20_000

// Below this size, highlighting recomputes on every keystroke (cheap enough to feel instant).
// At or above it, callers should debounce the `settledSource` they pass in so highlighting only
// recomputes once typing pauses instead of on every keystroke; see MarkdownTextEditor.
internal const val LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS = 4_000

internal class MarkdownVisualTransformation(
    private val styler: MarkdownStyler,
    private val settledSource: String = "",
) : VisualTransformation {
    private var lastSource: String? = null
    private var lastHighlighted: Boolean? = null
    private var lastTransformedText: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val shouldHighlight =
            source.length <= LIVE_PREVIEW_HIGHLIGHT_MAX_CHARS &&
                (source.length < LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS || source == settledSource)

        if (lastSource == source && lastHighlighted == shouldHighlight) {
            return lastTransformedText ?: TransformedText(text, OffsetMapping.Identity)
        }

        val transformedText =
            if (shouldHighlight) {
                val transformedMarkdown = MarkdownPreviewTransformationBuilder(source, styler).build()
                TransformedText(
                    text = transformedMarkdown.text,
                    offsetMapping = transformedMarkdown.offsetMapping,
                )
            } else {
                TransformedText(text, OffsetMapping.Identity)
            }
        lastSource = source
        lastHighlighted = shouldHighlight
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

    // Line/inline scanning below always advances currentIndex forward (never rescans earlier
    // positions), so a single forward-only cursor over all link matches is sufficient and turns
    // link lookups from O(n) rescans per character into O(1) amortized.
    private val linkMatches = MarkdownLinkRegex.findAll(source).toList()
    private var linkMatchCursor = 0

    private fun nextLinkMatchAt(currentIndex: Int): MatchResult? {
        while (linkMatchCursor < linkMatches.size && linkMatches[linkMatchCursor].range.first < currentIndex) {
            linkMatchCursor++
        }
        return linkMatches.getOrNull(linkMatchCursor)?.takeIf { it.range.first == currentIndex }
    }

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
                    LIVE_PREVIEW_CHECKED_CHECKLIST_MARKER
                } else {
                    LIVE_PREVIEW_UNCHECKED_CHECKLIST_MARKER
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
            insertedTextBeforeSourceIndex[lineStartIndex] = LIVE_PREVIEW_QUOTE_MARKER
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
            val linkMatch = nextLinkMatchAt(currentIndex)
            if (linkMatch != null && linkMatch.range.last < endIndex) {
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

            // Closing-marker lookups below match MarkdownStyler/MarkdownText's view-mode renderer
            // (indexOfMarkdownClosingMarker + isValidOpening reject a marker preceded/followed by
            // whitespace) so live-preview highlighting agrees with how the saved note renders.
            //
            // The close-marker search itself is only O(1)-ish amortized when it actually finds a
            // close nearby, but scans to the end of the string when it doesn't. It must only run
            // once the cheap startsWith/isValidOpening checks confirm the current position is a
            // plausible opening marker — otherwise, for plain text with no markdown syntax, every
            // character would trigger up to five full forward scans, making the whole pass O(n^2).
            if (source.startsWith("`", currentIndex) && source.isValidOpening(currentIndex, 1)) {
                val inlineCodeClose = source.indexOfMarkdownClosingMarker("`", currentIndex + 1)
                if (inlineCodeClose in (currentIndex + 1)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 1))
                    hiddenRanges.add(HiddenRange(inlineCodeClose, inlineCodeClose + 1))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 1, inlineCodeClose, styler.inlineCodeSpanStyle))
                    currentIndex = inlineCodeClose + 1
                    continue
                }
            }

            if (source.startsWith("<u>", currentIndex, ignoreCase = true)) {
                val underlineClose = source.indexOf("</u>", currentIndex + 3, ignoreCase = true)
                if (underlineClose in (currentIndex + 3)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 3))
                    hiddenRanges.add(HiddenRange(underlineClose, underlineClose + 4))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 3, underlineClose, styler.underlineSpanStyle))
                    collectInlineRanges(startIndex = currentIndex + 3, endIndex = underlineClose)
                    currentIndex = underlineClose + 4
                    continue
                }
            }

            if (source.startsWith("~~", currentIndex) && source.isValidOpening(currentIndex, 2)) {
                val strikeClose = source.indexOfMarkdownClosingMarker("~~", currentIndex + 2)
                if (strikeClose in (currentIndex + 2)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 2))
                    hiddenRanges.add(HiddenRange(strikeClose, strikeClose + 2))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 2, strikeClose, styler.strikethroughSpanStyle))
                    collectInlineRanges(startIndex = currentIndex + 2, endIndex = strikeClose)
                    currentIndex = strikeClose + 2
                    continue
                }
            }

            if (source.startsWith("***", currentIndex) && source.isValidOpening(currentIndex, 3)) {
                val boldItalicClose = source.indexOfMarkdownClosingMarker("***", currentIndex + 3)
                if (boldItalicClose in (currentIndex + 3)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 3))
                    hiddenRanges.add(HiddenRange(boldItalicClose, boldItalicClose + 3))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 3, boldItalicClose, styler.boldSpanStyle))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 3, boldItalicClose, styler.italicSpanStyle))
                    collectInlineRanges(startIndex = currentIndex + 3, endIndex = boldItalicClose)
                    currentIndex = boldItalicClose + 3
                    continue
                }
            }

            if (source.startsWith("**", currentIndex) && source.isValidOpening(currentIndex, 2)) {
                val boldClose = source.indexOfMarkdownClosingMarker("**", currentIndex + 2)
                if (boldClose in (currentIndex + 2)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 2))
                    hiddenRanges.add(HiddenRange(boldClose, boldClose + 2))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 2, boldClose, styler.boldSpanStyle))
                    collectInlineRanges(startIndex = currentIndex + 2, endIndex = boldClose)
                    currentIndex = boldClose + 2
                    continue
                }
            }

            if (source.startsWith("*", currentIndex) && source.isValidOpening(currentIndex, 1)) {
                val italicClose = source.indexOfMarkdownClosingMarker("*", currentIndex + 1)
                if (italicClose in (currentIndex + 1)..<endIndex) {
                    hiddenRanges.add(HiddenRange(currentIndex, currentIndex + 1))
                    hiddenRanges.add(HiddenRange(italicClose, italicClose + 1))
                    styleRanges.add(MarkdownStyleRange(currentIndex + 1, italicClose, styler.italicSpanStyle))
                    collectInlineRanges(startIndex = currentIndex + 1, endIndex = italicClose)
                    currentIndex = italicClose + 1
                    continue
                }
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
