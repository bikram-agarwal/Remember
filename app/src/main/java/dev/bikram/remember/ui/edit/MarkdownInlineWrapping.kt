package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownInlineProjection
import dev.bikram.remember.ui.common.parseMarkdownDocument

/**
 * Toggles the inline format delimited by [open]/[close] over the current selection, reusing or
 * removing existing markers instead of stacking redundant ones.
 */
internal fun TextFieldValue.withSelectionSurrounded(
    open: String,
    close: String,
): TextFieldValue {
    val cleanedValue = withSelectedEmptyWrappersRemoved()
    val source = cleanedValue.text
    val spans = markdownFormatSpans(source)
    val selection = cleanedValue.selection
    val rawStart = selection.min.coerceIn(0, source.length)
    val rawEnd = selection.max.coerceIn(rawStart, source.length)
    val adjustedRange = adjustedSelectionBoundaries(source = source, spans = spans, start = rawStart, end = rawEnd)
    val start = adjustedRange.start
    val end = adjustedRange.end
    if (start == end) {
        val adjacentOpening =
            spans.firstOrNull {
                it.openStart == start && source.substring(it.openStart, it.openEnd) == open
            }
        if (adjacentOpening != null) {
            // Enabling the same format immediately before its existing opener can reuse
            // that opener. Adding an empty * pair here would turn *Word* into ***Word*.
            return cleanedValue.copy(selection = TextRange(adjacentOpening.openEnd))
        }
    }
    // A triple delimiter represents two independently toggleable formats. Removing one
    // must preserve the other's markers, including when the wrapper is still empty.
    val combined =
        spans.firstOrNull { span ->
            span.openEnd - span.openStart == 3 &&
                source.startsWith("***", span.openStart) &&
                (
                    (start == span.openEnd && end == span.closeStart) ||
                        (start == span.openStart && end == span.closeEnd)
                )
        }
    if ((open == "*" || open == "**") && combined != null) {
        val remaining = "*".repeat(3 - open.length)
        val content = source.substring(combined.openEnd, combined.closeStart)
        val updatedText = source.replaceRange(combined.openStart, combined.closeEnd, remaining + content + remaining)
        val contentStart = combined.openStart + remaining.length
        return TextFieldValue(updatedText, TextRange(contentStart, contentStart + content.length))
    }
    val emptyEnclosingWrapper =
        spans.firstOrNull { span ->
            start == end && start in span.openEnd..span.closeStart &&
                source.substring(span.openStart, span.openEnd) == open &&
                TextFieldValue(
                    source.substring(span.openEnd, span.closeStart),
                    TextRange(start - span.openEnd),
                ).withCompleteEmptyWrappersRemoved().text.isEmpty()
        }
    if (emptyEnclosingWrapper != null) {
        val updatedText =
            source
                .removeRange(emptyEnclosingWrapper.closeStart, emptyEnclosingWrapper.closeEnd)
                .removeRange(emptyEnclosingWrapper.openStart, emptyEnclosingWrapper.openEnd)
        return TextFieldValue(updatedText, TextRange(start - open.length))
    }
    if (start == end) {
        val splitValue =
            valueWithActiveFormatSplitAtCursor(
                source = source,
                spans = spans,
                open = open,
                close = close,
                cursor = start,
            )
        if (splitValue != null) {
            return splitValue
        }
    }
    val externalWrapper = externalWrapperRange(source = source, start = start, end = end, open = open, close = close)
    if (externalWrapper != null) {
        val updatedText =
            source
                .removeRange(externalWrapper.closeStart, externalWrapper.closeEnd)
                .removeRange(externalWrapper.openStart, externalWrapper.openEnd)
        return TextFieldValue(
            text = updatedText,
            selection = TextRange(externalWrapper.openStart, externalWrapper.closeStart - open.length),
        )
    }

    // Cursor sits right before this wrapper's own close marker (e.g. toggling Bold off right
    // after typing the bolded text) - reuse the existing marker instead of inserting a
    // redundant one, which would otherwise leave a broken run of markers behind. Preserve
    // the command's existing serialization policy: trailing spaces move outside a wrapper
    // when the user explicitly ends that format, even though saved rendering accepts them.
    if (start == end && source.startsWith(close, start)) {
        val lineStart = source.lineStartBefore(start)
        val lineBeforeCursor = source.substring(lineStart, start)
        val trimmedLength = lineBeforeCursor.length - lineBeforeCursor.trimEnd().length
        val normalizedSource = source.removeRange(start - trimmedLength, start)
        val closingSpan =
            markdownFormatSpans(normalizedSource).firstOrNull { span ->
                span.closeStart == start - trimmedLength &&
                    when (open) {
                        "**" -> span.format == MarkdownInlineFormat.BOLD
                        "*" -> span.format == MarkdownInlineFormat.ITALIC
                        "<u>" -> span.format == MarkdownInlineFormat.UNDERLINE
                        "~~" -> span.format == MarkdownInlineFormat.STRIKETHROUGH
                        "`" -> span.format == MarkdownInlineFormat.INLINE_CODE
                        else -> false
                    }
            }
        if (closingSpan != null) {
            val contentEnd = start - (lineBeforeCursor.length - lineBeforeCursor.trimEnd().length)
            val updatedText =
                source.substring(0, contentEnd) +
                    close +
                    source.substring(contentEnd, start) +
                    source.substring(start + close.length)
            return TextFieldValue(updatedText, selection = TextRange(start + close.length))
        }
    }

    val selectedText = source.substring(start, end)
    if (selectedText.hasExactWrapper(open = open, close = close)) {
        val replacement = selectedText.removeSurrounding(open, close)
        val updatedText = source.replaceRange(start, end, replacement)
        return TextFieldValue(
            text = updatedText,
            selection = TextRange(start, start + replacement.length),
        )
    }

    // Emphasis cannot open directly before whitespace. Keep selected leading spaces
    // outside the new markers; a whitespace-only selection primes an empty format.
    val leadingWhitespace = if (open == "<u>") "" else selectedText.takeWhile { it.isWhitespace() }
    val content = selectedText.substring(leadingWhitespace.length)
    val replacement = leadingWhitespace + open + content + close
    val updatedText = source.replaceRange(start, end, replacement)
    val contentStart = start + leadingWhitespace.length + open.length
    val updatedSelection = TextRange(contentStart, contentStart + content.length)
    return TextFieldValue(updatedText, selection = updatedSelection)
}

private fun TextFieldValue.withSelectedEmptyWrappersRemoved(): TextFieldValue {
    if (selection.collapsed) return this
    val syntax = parseMarkdownDocument(text)
    val projection = MarkdownInlineProjection(text, syntax.spans)
    val removed = BooleanArray(text.length)
    for (span in syntax.spans) {
        if (span.openStart >= selection.min && span.closeEnd <= selection.max &&
            projection.visibleOffsets[span.openEnd] == projection.visibleOffsets[span.closeStart]
        ) {
            removed.fill(true, span.openStart, span.closeEnd)
        }
    }
    if (removed.none { it }) return this
    val start = selection.start - (0 until selection.start).count { removed[it] }
    val end = selection.end - (0 until selection.end).count { removed[it] }
    return TextFieldValue(text.filterIndexed { index, _ -> !removed[index] }, TextRange(start, end))
}

/** Splits the run of [open]/[close] the cursor sits inside, so the format ends at the cursor. */
private fun valueWithActiveFormatSplitAtCursor(
    source: String,
    spans: List<MarkdownWrapperRange>,
    open: String,
    close: String,
    cursor: Int,
): TextFieldValue? {
    val format =
        when (open) {
            "**" -> MarkdownInlineFormat.BOLD
            "*" -> MarkdownInlineFormat.ITALIC
            "<u>" -> MarkdownInlineFormat.UNDERLINE
            "~~" -> MarkdownInlineFormat.STRIKETHROUGH
            else -> MarkdownInlineFormat.INLINE_CODE
        }
    val target = spans.firstOrNull { it.format == format && cursor in it.openEnd..it.closeStart } ?: return null
    val inner =
        spans
            .filter { it.openStart > target.openStart && it.closeEnd <= target.closeEnd && cursor in it.openEnd..it.closeStart }
            .distinctBy { it.openStart }
            .sortedBy { it.openStart }
    // The existing end-of-run path also moves trailing spaces outside the closing marker.
    if (inner.isEmpty() && cursor == target.closeStart) return null

    val leftInner = inner.filter { span -> cursor - span.openEnd > inner.filter { it.openStart > span.openStart }.sumOf { it.openEnd - it.openStart } }
    val rightInner = inner.filter { span -> span.closeStart - cursor > inner.filter { it.openStart > span.openStart }.sumOf { it.closeEnd - it.closeStart } }
    val hasLeft = cursor - target.openEnd > inner.sumOf { it.openEnd - it.openStart }
    val hasRight = target.closeStart - cursor > inner.sumOf { it.closeEnd - it.closeStart }
    var prefix = source.substring(0, cursor)
    inner.filter { it !in leftInner }.asReversed().forEach { span ->
        prefix = prefix.removeRange(span.openStart, span.openEnd)
    }
    if (!hasLeft) prefix = prefix.removeRange(target.openEnd - open.length, target.openEnd)
    var suffix = source.substring(cursor)
    val removedClosers = inner.filter { it !in rightInner }.map { TextRange(it.closeStart, it.closeEnd) }.toMutableList()
    if (!hasRight) removedClosers.add(TextRange(target.closeStart, target.closeStart + close.length))
    removedClosers.sortedByDescending { it.start }.forEach { range ->
        suffix = suffix.removeRange(range.start - cursor, range.end - cursor)
    }
    val leftClose = leftInner.asReversed().joinToString("") { source.substring(it.closeStart, it.closeEnd) } + if (hasLeft) close else ""
    val middleOpen = inner.joinToString("") { source.substring(it.openStart, it.openEnd) }
    val middleClose = inner.asReversed().joinToString("") { source.substring(it.closeStart, it.closeEnd) }
    val rightOpen = (if (hasRight) open else "") + rightInner.joinToString("") { source.substring(it.openStart, it.openEnd) }
    return TextFieldValue(
        prefix + leftClose + middleOpen + middleClose + rightOpen + suffix,
        TextRange(prefix.length + leftClose.length + middleOpen.length),
    )
}

private fun externalWrapperRange(
    source: String,
    start: Int,
    end: Int,
    open: String,
    close: String,
): MarkdownWrapperRange? {
    val openStart = start - open.length
    val closeEnd = end + close.length
    if (
        openStart < 0 ||
        closeEnd > source.length ||
        source.substring(openStart, start) != open ||
        source.substring(end, closeEnd) != close
    ) {
        return null
    }
    if (!source.isExactDelimiterBoundary(openStart = openStart, openEnd = start, closeStart = end, closeEnd = closeEnd, marker = open)) {
        return null
    }
    return MarkdownWrapperRange(
        openStart = openStart,
        openEnd = start,
        closeStart = end,
        closeEnd = closeEnd,
    )
}

private fun String.isExactDelimiterBoundary(
    openStart: Int,
    openEnd: Int,
    closeStart: Int,
    closeEnd: Int,
    marker: String,
): Boolean {
    if (marker != "*") {
        return true
    }
    return getOrNull(openStart - 1) != '*' &&
        (openEnd == closeStart || (getOrNull(openEnd) != '*' && getOrNull(closeStart - 1) != '*')) &&
        getOrNull(closeEnd) != '*'
}

private fun String.hasExactWrapper(
    open: String,
    close: String,
): Boolean {
    if (length < open.length + close.length || !startsWith(open) || !endsWith(close)) {
        return false
    }
    if (open != "*") {
        return true
    }
    return getOrNull(open.length) != '*' && getOrNull(length - close.length - 1) != '*'
}

private fun adjustedSelectionBoundaries(
    source: String,
    spans: List<MarkdownWrapperRange>,
    start: Int,
    end: Int,
): TextRange {
    var adjustedStart = start
    var adjustedEnd = end

    if (start == end) return TextRange(start)
    val markers = BooleanArray(source.length)
    for (span in spans) {
        markers.fill(true, span.openStart, span.openEnd)
        markers.fill(true, span.closeStart, span.closeEnd)
    }
    while (adjustedStart < adjustedEnd && markers[adjustedStart]) adjustedStart++
    while (adjustedEnd > adjustedStart && markers[adjustedEnd - 1]) adjustedEnd--
    return TextRange(adjustedStart, maxOf(adjustedStart, adjustedEnd))
}
