package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownPartialBlockPrefixRegex
import dev.bikram.remember.ui.common.parseMarkdownLine

/**
 * Moves a newline typed inside inline wrappers past their closing markers, so the break lands
 * after the formatted run instead of splitting it. [spans] lazily supplies the previous text's spans.
 */
internal fun TextFieldValue.withInlineWrapperEnterAdjusted(
    previousValue: TextFieldValue,
    spans: () -> List<MarkdownWrapperRange>,
): TextFieldValue {
    if (!previousValue.selection.collapsed || !selection.collapsed) {
        return this
    }
    if (text.length != previousValue.text.length + 1) {
        return this
    }
    val previousCursor = previousValue.selection.start.coerceIn(0, previousValue.text.length)
    val newlineIndex = selection.start - 1
    if (newlineIndex !in text.indices || text[newlineIndex] != '\n') {
        return this
    }
    val expectedText =
        previousValue.text.replaceRange(
            startIndex = previousCursor,
            endIndex = previousCursor,
            replacement = "\n",
        )
    if (expectedText != text) {
        return this
    }

    val wrapperSpans = spans()
    var adjustedNewlineIndex = previousCursor
    while (true) {
        val closing = wrapperSpans.filter { it.closeStart == adjustedNewlineIndex }.maxByOrNull { it.closeEnd } ?: break
        adjustedNewlineIndex = closing.closeEnd
    }
    if (adjustedNewlineIndex > previousCursor) {
        val updatedText =
            previousValue.text.replaceRange(
                startIndex = adjustedNewlineIndex,
                endIndex = adjustedNewlineIndex,
                replacement = "\n",
            )
        return copy(
            text = updatedText,
            selection = TextRange(adjustedNewlineIndex + 1),
            composition = null,
        )
    }
    return this
}

/**
 * A date chip is atomic, but a user fixing its text ("11/24/2026" -> delete the 4, type 5) edits
 * the label from the inside. The label is only a display string for the `date:` target, so
 * editing it would silently desync the two; instead the chip falls back to its plain label text
 * with the edit applied, which can be re-chipped by selecting it and using the date button.
 * Returns null when the edit isn't strictly inside a chip label. [spans] lazily supplies the
 * previous text's spans.
 */
internal fun TextFieldValue.withDateChipLabelEditUnwrapped(
    previousValue: TextFieldValue,
    spans: () -> List<MarkdownWrapperRange>,
): DateChipUnwrap? {
    if (text == previousValue.text || !previousValue.text.contains("](date:")) return null
    val change = markdownChangedRange(previousValue.text, text)
    val inserted = text.substring(change.start, change.updatedEnd)
    val deletionStart = change.start
    val deletionEnd = change.originalEnd
    val chip =
        spans().firstOrNull { span ->
            span.isAtomicDate &&
                if (deletionStart == deletionEnd) {
                    deletionStart > span.openEnd && deletionStart < span.closeStart
                } else {
                    deletionStart >= span.openEnd &&
                        deletionEnd <= span.closeStart &&
                        !(inserted.isEmpty() && deletionStart == span.openEnd && deletionEnd == span.closeStart)
                }
        } ?: return null
    val label = previousValue.text.substring(chip.openEnd, chip.closeStart)
    val editedLabel = label.replaceRange(deletionStart - chip.openEnd, deletionEnd - chip.openEnd, inserted)
    val updatedText =
        previousValue.text.substring(0, chip.openStart) + editedLabel + previousValue.text.substring(chip.closeEnd)
    val cursor = chip.openStart + (deletionStart - chip.openEnd) + inserted.length
    return DateChipUnwrap(
        value = copy(text = updatedText, selection = TextRange(cursor), composition = null),
        textRange = TextRange(chip.openStart, chip.openStart + editedLabel.length),
    )
}

/** Result of [withDateChipLabelEditUnwrapped]: the edited value and where the plain label text now sits. */
internal class DateChipUnwrap(
    val value: TextFieldValue,
    val textRange: TextRange,
)

/**
 * Applies deletions the way live preview displays them, dropping hidden Markdown syntax the user
 * cannot see instead of the raw characters. [spans] lazily supplies the previous text's spans.
 */
internal fun TextFieldValue.withLivePreviewDeletionApplied(
    previousValue: TextFieldValue,
    spans: () -> List<MarkdownWrapperRange>,
): TextFieldValue {
    if (!selection.collapsed) {
        return this
    }
    withInlineFormattingPreserved(previousValue, spans())?.let { return it }
    if (text.length >= previousValue.text.length) return this
    val cursor = selection.start.coerceIn(0, text.length)
    val previousCursor = previousValue.selection.start.coerceIn(0, previousValue.text.length)
    if (previousValue.selection.collapsed) {
        val previousLineStart = previousValue.text.lineStartBefore(previousCursor)
        val previousLineEnd = previousValue.text.indexOf('\n', previousCursor).let { if (it < 0) previousValue.text.length else it }
        val previousLine = previousValue.text.substring(previousLineStart, previousLineEnd)
        val currentLineStart = text.lineStartBefore(cursor)
        val currentLineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val currentLine = text.substring(currentLineStart, currentLineEnd)
        if (
            parseMarkdownLine(previousLine).isEmptyBlock &&
            MarkdownPartialBlockPrefixRegex.matches(currentLine) &&
            !parseMarkdownLine(currentLine).isEmptyBlock
        ) {
            val removeStart = if (currentLineStart > 0) currentLineStart - 1 else currentLineStart
            val removeEnd =
                if (currentLineStart == 0 && currentLineEnd < text.length) currentLineEnd + 1 else currentLineEnd
            val updatedText = text.removeRange(removeStart, removeEnd)
            return copy(text = updatedText, selection = TextRange(removeStart), composition = null)
        }
    }
    val lineStart = text.lineStartBefore(cursor)
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    val lineSyntax = parseMarkdownLine(text.substring(lineStart, lineEnd))
    if (lineSyntax.kind == MarkdownBlockKind.Heading && lineSyntax.isEmptyBlock) {
        return copy(text = text.removeRange(lineStart, lineEnd), selection = TextRange(lineStart), composition = null)
    }
    return this
}

/**
 * Compose can expand displayed replacements over hidden opening/closing markers. Replace
 * content from those ranges, keeping wrappers that still own original or inserted text.
 * Source-mode edits bypass this rule and can still edit Markdown syntax directly.
 */
private fun TextFieldValue.withInlineFormattingPreserved(
    previousValue: TextFieldValue,
    spans: List<MarkdownWrapperRange>,
): TextFieldValue? {
    val removedCount = previousValue.text.length - text.length
    val cursor = selection.start
    val change = markdownChangedRange(previousValue.text, text)
    val inserted = text.substring(change.start, change.updatedEnd)
    val deletion =
        if (inserted.isEmpty() && removedCount > 0 && cursor + removedCount <= previousValue.text.length &&
            text == previousValue.text.removeRange(cursor, cursor + removedCount)
        ) {
            TextRange(cursor, cursor + removedCount)
        } else {
            TextRange(change.start, change.originalEnd)
        }
    if (spans.none { deletion.min < it.closeEnd && deletion.max > it.openStart }) return null
    val markers = BooleanArray(previousValue.text.length)
    for (span in spans) {
        markers.fill(true, span.openStart, span.openEnd)
        markers.fill(true, span.closeStart, span.closeEnd)
    }
    if (inserted.isNotEmpty() && (deletion.min until deletion.max).none { markers[it] } &&
        !(inserted.first().isWhitespace() && spans.any { it.openEnd == deletion.min || it.closeStart == deletion.min })
    ) {
        return null
    }
    val removed = BooleanArray(previousValue.text.length)
    for (index in deletion.min until deletion.max) {
        removed[index] = !markers[index]
    }
    if (inserted.isEmpty() && deletion.min < deletion.max) {
        for (span in spans) {
            if (span.isAtomicDate && deletion.min < span.closeEnd && deletion.max > span.openStart) {
                removed.fill(true, span.openStart, span.closeEnd)
            }
        }
    }
    if (inserted.isEmpty() && removed.none { it }) {
        // An IME can send a raw Backspace on a hidden marker instead of a displayed range.
        // Redirect that deletion to the previous visible grapheme, preserving its boundaries.
        if (!previousValue.selection.collapsed || cursor >= previousValue.selection.start) return previousValue
        var contentEnd = deletion.min
        while (contentEnd > 0 && markers[contentEnd - 1]) contentEnd--
        if (contentEnd == 0) return previousValue.withCompleteEmptyWrappersRemoved()
        val boundaries = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        boundaries.setText(previousValue.text)
        val contentStart = boundaries.preceding(contentEnd)
        for (index in contentStart until contentEnd) {
            removed[index] = !markers[index]
        }
    }
    val firstDeletedContent = removed.indexOfFirst { it }
    if (firstDeletedContent < 0 && inserted.isEmpty()) return previousValue
    var insertionPoint =
        resolveLivePreviewInsertionPointAndAtomicRemovals(
            spans = spans,
            removed = removed,
            deletion = deletion,
            previousCursor = previousValue.selection.start,
        )
    if (inserted.firstOrNull()?.isWhitespace() == true) {
        // A space inserted at the start of an emphasis span belongs before its opener;
        // otherwise a valid *word* becomes the literal Markdown * word*.
        while (true) {
            val opening = spans.filter { !it.isAtomicDate && it.openEnd == insertionPoint }.minByOrNull { it.openStart } ?: break
            insertionPoint = opening.openStart
        }
    }
    val adjustedFirstDeletedContent = removed.indexOfFirst { it }
    val lastDeletedContent = removed.indexOfLast { it }
    // A selection can empty several separate wrappers. Cursor-local cleanup only removes
    // the first one, leaving invisible Markdown elsewhere in an apparently empty note.
    // Count surviving content once so nested spans do not repeatedly scan the whole text.
    val survivingContent = IntArray(previousValue.text.length + 1)
    for (index in previousValue.text.indices) {
        survivingContent[index + 1] = survivingContent[index] + if (!markers[index] && !removed[index]) 1 else 0
    }
    for (span in spans) {
        if (span.isAtomicDate) continue
        val touchedContent = adjustedFirstDeletedContent >= 0 && span.openEnd <= lastDeletedContent && span.closeStart > adjustedFirstDeletedContent
        val selectedWrapper =
            !previousValue.selection.collapsed && deletion.min <= span.openStart && deletion.max >= span.closeEnd
        val ownsInsertion = inserted.isNotEmpty() && insertionPoint in span.openEnd..span.closeStart
        if ((touchedContent || selectedWrapper) && !ownsInsertion && survivingContent[span.closeStart] == survivingContent[span.openEnd]) {
            // This also clears empty nested wrappers owned by the deleted span.
            removed.fill(true, span.openStart, span.closeEnd)
        }
    }
    for ((left, right) in spans.distinctBy { it.openStart }.sortedBy { it.openStart }.zipWithNext()) {
        if (left.format == null || left.format != right.format || left.closeEnd > right.openStart) continue
        if (removed[left.openStart] || removed[right.closeStart]) continue
        if (previousValue.text.substring(left.closeStart, left.closeEnd) != previousValue.text.substring(right.openStart, right.openEnd)) continue
        if ((left.closeEnd until right.openStart).any { !removed[it] }) continue
        if (inserted.isNotEmpty() && insertionPoint in left.closeEnd..right.openStart) continue
        // Deleting the gap between equal formats joins their contents. Keeping both
        // marker pairs would create an ambiguous run such as *left **right*.
        removed.fill(true, left.closeStart, left.closeEnd)
        removed.fill(true, right.openStart, right.openEnd)
    }
    var updatedCursor = removed.indexOfFirst { it }
    val updatedPositions = IntArray(previousValue.text.length)
    var updatedText =
        buildString(previousValue.text.length + inserted.length) {
            previousValue.text.forEachIndexed { index, character ->
                if (inserted.isNotEmpty() && index == insertionPoint) {
                    append(inserted)
                    updatedCursor = length
                }
                updatedPositions[index] = length
                if (!removed[index]) append(character)
            }
            if (inserted.isNotEmpty() && insertionPoint == previousValue.text.length) {
                append(inserted)
                updatedCursor = length
            }
        }
    val relocated = relocateEdgeWhitespaceOutsideWrappers(spans, removed, updatedPositions, updatedText, updatedCursor)
    return copy(text = relocated.first, selection = TextRange(relocated.second), composition = null).withCompleteEmptyWrappersRemoved()
}

private fun resolveLivePreviewInsertionPointAndAtomicRemovals(
    spans: List<MarkdownWrapperRange>,
    removed: BooleanArray,
    deletion: TextRange,
    previousCursor: Int,
): Int {
    for (span in spans) {
        if (span.isAtomicDate && (span.openEnd until span.closeStart).any { removed[it] }) {
            removed.fill(true, span.openStart, span.closeEnd)
        }
    }
    val adjustedFirstDeletedContent = removed.indexOfFirst { it }
    if (adjustedFirstDeletedContent >= 0) {
        return adjustedFirstDeletedContent
    }
    var wedgePoint = previousCursor.coerceIn(deletion.min, deletion.max)
    for (span in spans) {
        if (!span.isAtomicDate) continue
        if (deletion.min >= span.closeStart && deletion.max <= span.closeEnd) {
            wedgePoint = maxOf(wedgePoint, span.closeEnd)
        } else if (deletion.min >= span.openStart && deletion.max <= span.openEnd) {
            wedgePoint = minOf(wedgePoint, span.openStart)
        }
    }
    return wedgePoint
}

private fun relocateEdgeWhitespaceOutsideWrappers(
    spans: List<MarkdownWrapperRange>,
    removed: BooleanArray,
    updatedPositions: IntArray,
    initialText: String,
    initialCursor: Int,
): Pair<String, Int> {
    var updatedText = initialText
    var updatedCursor = initialCursor
    for (span in spans.distinctBy { it.openStart }.sortedByDescending { it.openStart }) {
        if (span.format == null || span.format == MarkdownInlineFormat.UNDERLINE || removed[span.openStart] || removed[span.closeStart]) continue
        val openStart = updatedPositions[span.openStart]
        val openLength = span.openEnd - span.openStart
        val openEnd = openStart + openLength
        val closeStart = updatedPositions[span.closeStart]
        val closeEnd = closeStart + span.closeEnd - span.closeStart
        val following = updatedText.getOrNull(closeEnd)
        if (following != null && !following.isWhitespace() && following !in "*~`" && !updatedText.startsWith("</u>", closeEnd, ignoreCase = true)) {
            var whitespaceStart = closeStart
            while (whitespaceStart > openEnd && updatedText[whitespaceStart - 1] in " \t") whitespaceStart--
            if (whitespaceStart < closeStart) {
                val closing = updatedText.substring(closeStart, closeEnd)
                val whitespace = updatedText.substring(whitespaceStart, closeStart)
                updatedText = updatedText.replaceRange(whitespaceStart, closeEnd, closing + whitespace)
                updatedCursor =
                    when {
                        updatedCursor in whitespaceStart..closeStart -> updatedCursor + closing.length
                        updatedCursor in closeStart + 1..closeEnd -> updatedCursor - whitespace.length
                        else -> updatedCursor
                    }
            }
        }
        var whitespaceEnd = openEnd
        while (whitespaceEnd < closeStart && updatedText[whitespaceEnd] in " \t") whitespaceEnd++
        if (whitespaceEnd == openEnd) continue
        val whitespace = updatedText.substring(openEnd, whitespaceEnd)
        updatedText = updatedText.replaceRange(openStart, whitespaceEnd, whitespace + updatedText.substring(openStart, openEnd))
        updatedCursor =
            when {
                updatedCursor in openEnd..whitespaceEnd -> updatedCursor - openLength
                updatedCursor in openStart until openEnd -> updatedCursor + whitespace.length
                else -> updatedCursor
            }
    }
    return updatedText to updatedCursor
}
