@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownStyler
import dev.bikram.remember.ui.common.parseMarkdownDocument
import dev.bikram.remember.ui.common.parseMarkdownLines
import dev.bikram.remember.ui.common.withCombinedMarkdownDecorations

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

// Rule drawing uses the same block interpretation as preview and saved rendering.
internal fun markdownHorizontalRuleLineStarts(source: String): List<Int> {
    if (!source.contains("---")) return emptyList()
    return parseMarkdownLines(source).filter { it.kind == MarkdownBlockKind.Rule }.map { it.start }
}

/**
 * Emit individual display edits so Compose owns the field's cursor/selection mapping.
 * The preview's source positions are only used for styling and drawing editor decorations.
 */
internal class MarkdownOutputTransformation(
    private val styler: MarkdownStyler,
    private val settledSource: String = "",
) : OutputTransformation {
    private var lastSource: String? = null
    private var lastHighlighted: Boolean? = null
    private var lastPreview: MarkdownPreview? = null

    override fun TextFieldBuffer.transformOutput() {
        val preview = preview(toString())
        preview.edits.forEach { edit -> replace(edit.start, edit.end, edit.text) }
        preview.text.spanStyles.forEach { span -> addStyle(span.item, span.start, span.end) }
    }

    fun preview(source: String): MarkdownPreview {
        val shouldHighlight =
            source.length <= LIVE_PREVIEW_HIGHLIGHT_MAX_CHARS &&
                (source.length < LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS || source == settledSource)
        if (lastSource == source && lastHighlighted == shouldHighlight) {
            lastPreview?.let { return it }
        }
        val preview =
            if (shouldHighlight) {
                MarkdownPreviewTransformationBuilder(source, styler).build()
            } else {
                MarkdownPreview(
                    text = AnnotatedString(source),
                    originalPositions = IntArray(source.length + 1) { it },
                    displayedPositions = IntArray(source.length + 1) { it },
                    edits = emptyList(),
                )
            }
        lastSource = source
        lastHighlighted = shouldHighlight
        lastPreview = preview
        return preview
    }
}

private class MarkdownPreviewTransformationBuilder(
    private val source: String,
    private val styler: MarkdownStyler,
) {
    private val hiddenRanges = mutableListOf<HiddenRange>()
    private val styleRanges = mutableListOf<MarkdownStyleRange>()
    private val insertedTextBeforeSourceIndex = mutableMapOf<Int, String>()

    fun build(): MarkdownPreview {
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

        return MarkdownPreview(
            text = annotatedString.withCombinedMarkdownDecorations(),
            originalPositions = originalToTransformed,
            displayedPositions = transformedToOriginal.toIntArray(),
            edits =
                normalizedHiddenRanges
                    .map { range ->
                        MarkdownPreviewEdit(range.start, range.end, insertedTextBeforeSourceIndex[range.start].orEmpty())
                    }.sortedByDescending { it.start },
        )
    }

    private fun collectMarkdownRanges() {
        val syntax = parseMarkdownDocument(source)
        for (line in syntax.lines) {
            if (line.contentStart > line.start) hiddenRanges.add(HiddenRange(line.start, line.contentStart))
            when (line.kind) {
                MarkdownBlockKind.Heading -> styleRanges.add(MarkdownStyleRange(line.contentStart, line.end, styler.headingSpanStyle(line.headingLevel)))
                MarkdownBlockKind.Checklist -> {
                    insertedTextBeforeSourceIndex[line.start] = line.indent + if (line.checked) LIVE_PREVIEW_CHECKED_CHECKLIST_MARKER else LIVE_PREVIEW_UNCHECKED_CHECKLIST_MARKER
                    if (line.checked) styleRanges.add(MarkdownStyleRange(line.contentStart, line.end, styler.strikethroughSpanStyle))
                }
                MarkdownBlockKind.Bullet -> insertedTextBeforeSourceIndex[line.start] = "  " + line.indent + "\u2022 "
                MarkdownBlockKind.Numbered -> insertedTextBeforeSourceIndex[line.start] = " " + line.indent + line.number + ". "
                MarkdownBlockKind.Quote -> {
                    insertedTextBeforeSourceIndex[line.start] = LIVE_PREVIEW_QUOTE_MARKER
                    styleRanges.add(MarkdownStyleRange(line.contentStart, line.end, styler.quoteSpanStyle))
                }
                MarkdownBlockKind.Code -> styleRanges.add(MarkdownStyleRange(line.start, line.end, styler.codeBlockSpanStyle))
                else -> Unit
            }
        }
        for (span in syntax.spans) {
            hiddenRanges.add(HiddenRange(span.openStart, span.openEnd))
            hiddenRanges.add(HiddenRange(span.closeStart, span.closeEnd))
            styleRanges.add(MarkdownStyleRange(span.openEnd, span.closeStart, styler.inlineSpanStyle(span.kind)))
        }
    }
}

private fun List<HiddenRange>.normalized(): List<HiddenRange> {
    if (isEmpty()) {
        return emptyList()
    }
    val sortedRanges = sortedBy { range -> range.start }
    val normalizedRanges = mutableListOf<HiddenRange>()
    var currentRange = sortedRanges.first()
    sortedRanges.drop(1).forEach { range ->
        // Adjacent ranges must remain separate output edits. Merging a replaced list prefix
        // with empty inline markers maps the caret inside that replacement, before the bullet.
        if (range.start < currentRange.end) {
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

internal data class MarkdownPreviewEdit(
    val start: Int,
    val end: Int,
    val text: String,
)

internal class MarkdownPreview(
    val text: AnnotatedString,
    private val originalPositions: IntArray,
    private val displayedPositions: IntArray,
    val edits: List<MarkdownPreviewEdit>,
) {
    fun originalToTransformed(offset: Int): Int {
        return originalPositions[offset.coerceIn(0, originalPositions.lastIndex)]
    }

    fun transformedToOriginal(offset: Int): Int {
        return displayedPositions[offset.coerceIn(0, displayedPositions.lastIndex)]
    }
}
