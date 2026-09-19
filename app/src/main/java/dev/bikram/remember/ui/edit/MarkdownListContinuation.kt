package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.parseMarkdownLine
import dev.bikram.remember.ui.common.parseMarkdownLines

/**
 * Continues a bullet, checklist or numbered list when the user presses enter, outdenting or
 * clearing the marker instead once the continued item is left empty.
 */
internal fun TextFieldValue.withListContinuationApplied(previousValue: TextFieldValue): TextFieldValue {
    val newlineInsertion = singleNewlineInsertion(previousValue = previousValue, updatedValue = this)
    if (newlineInsertion == null) {
        return this
    }

    val previousLineStart =
        text.lastIndexOf('\n', newlineInsertion.index - 1).let { newlineIndex ->
            if (newlineIndex < 0) 0 else newlineIndex + 1
        }
    val previousLine = text.substring(previousLineStart, newlineInsertion.index)
    val continuationPrefix =
        continuationPrefixForLine(
            line = previousLine,
            lineStart = previousLineStart,
            sourceText = text,
        ) ?: return this
    if (continuationPrefix.content.isBlank()) {
        if (continuationPrefix.outdentPrefix != null) {
            val replacedText =
                text.replaceRange(
                    startIndex = previousLineStart,
                    endIndex = newlineInsertion.index + 1,
                    replacement = continuationPrefix.outdentPrefix,
                )
            if (!replacedText.startsWith(continuationPrefix.outdentPrefix, previousLineStart)) {
                return this
            }
            val updatedText =
                if (continuationPrefix.outdentTopLevelNumber != null) {
                    renumberFollowingTopLevelNumberedItems(
                        sourceText = replacedText,
                        insertedLineStart = previousLineStart,
                        insertedNumber = continuationPrefix.outdentTopLevelNumber,
                    )
                } else {
                    replacedText
                }
            return copy(
                text = updatedText,
                selection = TextRange(previousLineStart + continuationPrefix.outdentPrefix.length),
                composition = null,
            )
        }
        val updatedText = text.replaceRange(previousLineStart, newlineInsertion.index, "")
        return copy(
            text = updatedText,
            selection = TextRange((previousLineStart + 1).coerceIn(0, updatedText.length)),
            composition = null,
        )
    }

    val updatedText =
        text.replaceRange(
            startIndex = newlineInsertion.index + 1,
            endIndex = newlineInsertion.index + 1,
            replacement = continuationPrefix.nextPrefix,
        )
    if (!updatedText.startsWith(continuationPrefix.nextPrefix, newlineInsertion.index + 1)) {
        return this
    }
    return copy(
        text = updatedText,
        selection = TextRange(newlineInsertion.index + 1 + continuationPrefix.nextPrefix.length),
        composition = null,
    )
}

private fun singleNewlineInsertion(
    previousValue: TextFieldValue,
    updatedValue: TextFieldValue,
): NewlineInsertion? {
    if (!previousValue.selection.collapsed || !updatedValue.selection.collapsed) {
        return null
    }

    val insertionIndex = updatedValue.selection.start - 1
    if (insertionIndex !in updatedValue.text.indices || updatedValue.text[insertionIndex] != '\n') {
        return null
    }

    if (updatedValue.text.length == previousValue.text.length + 1) {
        val expectedText =
            previousValue.text.replaceRange(
                startIndex = insertionIndex,
                endIndex = insertionIndex,
                replacement = "\n",
            )
        if (expectedText == updatedValue.text) {
            return NewlineInsertion(index = insertionIndex)
        }
    }

    val previousCursor = previousValue.selection.start.coerceIn(0, previousValue.text.length)
    val previousLineStart =
        if (previousCursor == 0) {
            0
        } else {
            previousValue.text.lastIndexOf('\n', previousCursor - 1).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        }
    val previousLineBeforeCursor = previousValue.text.substring(previousLineStart, previousCursor)
    if (
        continuationPrefixForLine(
            line = previousLineBeforeCursor,
            lineStart = previousLineStart,
            sourceText = previousValue.text,
        ) == null
    ) {
        return null
    }

    val updatedLineStart =
        if (insertionIndex == 0) {
            0
        } else {
            updatedValue.text.lastIndexOf('\n', insertionIndex - 1).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        }
    val textBeforeLineChanged = previousValue.text.substring(0, previousLineStart) != updatedValue.text.substring(0, updatedLineStart)
    val textAfterCursorChanged = previousValue.text.substring(previousCursor) != updatedValue.text.substring(insertionIndex + 1)
    if (textBeforeLineChanged || textAfterCursorChanged) {
        return null
    }
    return NewlineInsertion(index = insertionIndex)
}

private fun continuationPrefixForLine(
    line: String,
    lineStart: Int,
    sourceText: String,
): ContinuationPrefix? {
    val documentLine = parseMarkdownLines(sourceText).firstOrNull { it.start == lineStart } ?: return null
    if (documentLine.kind == MarkdownBlockKind.Code || documentLine.kind == MarkdownBlockKind.CodeFence) return null
    val syntax = parseMarkdownLine(line)
    val indent = syntax.indent
    if (syntax.kind == MarkdownBlockKind.Checklist) {
        return ContinuationPrefix(
            content = line.substring(syntax.contentStart),
            nextPrefix = indent + "- [ ] ",
            outdentPrefix = if (indent.isNotEmpty()) "- [ ] " else null,
        )
    }

    if (syntax.kind == MarkdownBlockKind.Numbered) {
        val currentNumber = syntax.number.toIntOrNull() ?: 1
        val markerSuffix = line.substring(indent.length + syntax.number.length, syntax.contentStart)
        val outdentNumber =
            if (indent.isNotEmpty()) {
                nextTopLevelNumberBefore(lineStart = lineStart, sourceText = sourceText)
            } else {
                null
            }
        return ContinuationPrefix(
            content = line.substring(syntax.contentStart),
            nextPrefix = indent + (currentNumber + 1) + markerSuffix,
            outdentPrefix =
                if (outdentNumber != null) {
                    "$outdentNumber$markerSuffix"
                } else {
                    null
                },
            outdentTopLevelNumber = outdentNumber,
        )
    }

    if (syntax.kind == MarkdownBlockKind.Bullet) {
        val marker = line.substring(indent.length, syntax.contentStart)
        return ContinuationPrefix(
            content = line.substring(syntax.contentStart),
            nextPrefix = indent + marker,
            outdentPrefix = if (indent.isNotEmpty()) marker else null,
        )
    }
    return null
}

private fun nextTopLevelNumberBefore(
    lineStart: Int,
    sourceText: String,
): Int {
    val previousTopLevelNumber =
        parseMarkdownLines(sourceText)
            .lastOrNull { it.start < lineStart && it.kind == MarkdownBlockKind.Numbered && it.indent.isEmpty() }
            ?.number
            ?.toIntOrNull()
            ?: 0
    return previousTopLevelNumber + 1
}

private fun renumberFollowingTopLevelNumberedItems(
    sourceText: String,
    insertedLineStart: Int,
    insertedNumber: Int,
): String {
    var updatedText = sourceText
    var lengthDelta = 0
    var nextNumber = insertedNumber + 1
    for (line in parseMarkdownLines(sourceText)) {
        if (line.start <= insertedLineStart) continue
        if (line.kind != MarkdownBlockKind.Numbered) break
        if (line.indent.isEmpty()) {
            val currentNumberText = line.number
            val nextNumberText = nextNumber.toString()
            val replacementStart = line.start + lengthDelta
            updatedText =
                updatedText.replaceRange(
                    startIndex = replacementStart,
                    endIndex = replacementStart + currentNumberText.length,
                    replacement = nextNumberText,
                )
            lengthDelta += nextNumberText.length - currentNumberText.length
            nextNumber++
        }
    }

    return updatedText
}

private data class NewlineInsertion(
    val index: Int,
)

private data class ContinuationPrefix(
    val content: String,
    val nextPrefix: String,
    val outdentPrefix: String? = null,
    val outdentTopLevelNumber: Int? = null,
)
