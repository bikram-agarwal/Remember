@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownInlineKind
import dev.bikram.remember.ui.common.MarkdownInlineProjection
import dev.bikram.remember.ui.common.parseMarkdownDocument

/** Rebuild only affected lines when inserting raw delimiters would cross existing wrappers. */
internal fun preserveMarkdownSelectionFormatting(
    before: TextFieldValue,
    candidate: TextFieldValue,
    marker: String? = null,
    desiredFormats: Set<MarkdownInlineFormat>? = null,
): TextFieldValue {
    val bit =
        when (marker) {
            "**" -> 1
            "*" -> 2
            "<u>" -> 4
            "~~" -> 8
            else -> if (desiredFormats != null) 0 else return candidate
        }
    if (before.selection.collapsed) return candidate
    val original = MarkdownSelectionContent(before.text)
    val start = original.projection.visibleOffsets[before.selection.min]
    val end = original.projection.visibleOffsets[before.selection.max]
    if (start == end) return candidate
    val remove = (start until end).filter { !original.projection.text[it].isWhitespace() }.let { it.isNotEmpty() && it.all { index -> original.formats[index] and bit != 0 } }
    val desired =
        desiredFormats?.sumOf {
            when (it) {
                MarkdownInlineFormat.BOLD -> 1
                MarkdownInlineFormat.ITALIC -> 2
                MarkdownInlineFormat.UNDERLINE -> 4
                MarkdownInlineFormat.STRIKETHROUGH -> 8
                else -> 0
            }
        }
    for (index in start until end) {
        original.formats[index] = desired ?: if (remove) original.formats[index] and bit.inv() else original.formats[index] or bit
    }
    val updated = MarkdownSelectionContent(candidate.text)
    if (original.projection.text == updated.projection.text &&
        original.formats.indices.all {
            original.projection.text[it].isWhitespace() || original.formats[it] == updated.formats[it]
        }
    ) {
        return candidate
    }

    val sourceOffsets = IntArray(original.projection.text.length)
    val result = StringBuilder(before.text.length)
    for (line in original.syntax.lines) {
        val visibleStart = original.projection.visibleOffsets[line.contentStart]
        val visibleEnd = original.projection.visibleOffsets[line.end]
        val affected = start < visibleEnd && end > visibleStart && line.kind != MarkdownBlockKind.Code && line.kind != MarkdownBlockKind.CodeFence
        if (!affected) {
            val outputStart = result.length
            result.append(before.text.substring(line.start, line.end))
            for (index in original.projection.visibleOffsets[line.start] until visibleEnd) sourceOffsets[index] = outputStart + original.projection.sourceOffsets[index] - line.start
        } else {
            val outputStart = result.length
            result.append(before.text.substring(line.start, line.contentStart))
            for (index in original.projection.visibleOffsets[line.start] until visibleStart) {
                sourceOffsets[index] = outputStart + original.projection.sourceOffsets[index] - line.start
            }
            // Whitespace at the edge of emphasis must sit outside its delimiters, especially
            // when another unformatted word follows the closing marker without punctuation.
            for (formatBit in listOf(1, 2, 8)) {
                var index = visibleStart
                while (index < visibleEnd) {
                    if (original.formats[index] and formatBit == 0) {
                        index++
                        continue
                    }
                    val runStart = index
                    while (index < visibleEnd && original.formats[index] and formatBit != 0) index++
                    var contentStart = runStart
                    var contentEnd = index
                    while (contentStart < contentEnd && original.projection.text[contentStart].isWhitespace()) contentStart++
                    while (index < visibleEnd && contentEnd > contentStart && original.projection.text[contentEnd - 1].isWhitespace()) contentEnd--
                    for (space in runStart until contentStart) original.formats[space] = original.formats[space] and formatBit.inv()
                    for (space in contentEnd until index) original.formats[space] = original.formats[space] and formatBit.inv()
                }
            }
            val formatEnds =
                listOf(1, 2, 8, 4).associateWith { formatBit ->
                    IntArray(visibleEnd - visibleStart + 1).also { ends ->
                        for (index in visibleEnd - 1 downTo visibleStart) {
                            val offset = index - visibleStart
                            ends[offset] = if (original.formats[index] and formatBit != 0) maxOf(index + 1, ends[offset + 1]) else index
                        }
                    }
                }
            var openTokens = emptyList<Pair<String, String>>()
            for (index in visibleStart until visibleEnd) {
                val formats = original.formats[index]
                val tokens =
                    buildList {
                        original.links[index]?.let { add("[" to "]($it)") }
                        // Keep the longest continuing format outside shorter ones. Otherwise
                        // ending bold while italic continues can join closers/openers into ****.
                        for (formatBit in formatEnds.keys.sortedByDescending { formatEnds.getValue(it)[index - visibleStart] }) {
                            if (formats and formatBit == 0) continue
                            add(
                                when (formatBit) {
                                    1 -> "**" to "**"
                                    2 -> "*" to "*"
                                    8 -> "~~" to "~~"
                                    else -> "<u>" to "</u>"
                                },
                            )
                        }
                        if (original.code[index]) add("`" to "`")
                    }
                var shared = 0
                while (shared < minOf(openTokens.size, tokens.size) && openTokens[shared] == tokens[shared]) shared++
                for (closing in openTokens.lastIndex downTo shared) result.append(openTokens[closing].second)
                for (opening in shared until tokens.size) result.append(tokens[opening].first)
                sourceOffsets[index] = result.length
                result.append(original.projection.text[index])
                openTokens = tokens
            }
            for (token in openTokens.asReversed()) result.append(token.second)
        }
        if (line.end < before.text.length) {
            sourceOffsets[visibleEnd] = result.length
            result.append('\n')
        }
    }
    val selectionStart = sourceOffsets[start]
    val selectionEnd = sourceOffsets[end - 1] + 1
    return TextFieldValue(result.toString(), if (before.selection.reversed) TextRange(selectionEnd, selectionStart) else TextRange(selectionStart, selectionEnd))
}

private class MarkdownSelectionContent(
    source: String,
) {
    val syntax = parseMarkdownDocument(source)
    val projection = MarkdownInlineProjection(source, syntax.spans)

    // Four bits per visible character retain overlapping styles without allocating a set
    // for every character: bold=1, italic=2, underline=4, strikethrough=8.
    val formats = IntArray(projection.text.length)
    val links = arrayOfNulls<String>(projection.text.length)
    val code = BooleanArray(projection.text.length)

    init {
        for (span in syntax.spans) {
            val bit =
                when (span.kind) {
                    MarkdownInlineKind.Bold -> 1
                    MarkdownInlineKind.Italic -> 2
                    MarkdownInlineKind.BoldItalic -> 3
                    MarkdownInlineKind.Underline -> 4
                    MarkdownInlineKind.Strikethrough -> 8
                    else -> 0
                }
            for (index in projection.visibleOffsets[span.openEnd] until projection.visibleOffsets[span.closeStart]) {
                formats[index] = formats[index] or bit
                if (span.kind == MarkdownInlineKind.Link) links[index] = span.url
                if (span.kind == MarkdownInlineKind.Code) code[index] = true
            }
        }
    }
}
