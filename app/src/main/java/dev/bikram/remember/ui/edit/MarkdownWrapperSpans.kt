package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownInlineKind
import dev.bikram.remember.ui.common.parseMarkdownDocument

internal val MarkdownEmptyInlineWrappers =
    listOf(
        "***" to "***",
        "**" to "**",
        "<u>" to "</u>",
        "~~" to "~~",
        "`" to "`",
        "*" to "*",
    )

internal data class MarkdownWrapperRange(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val format: MarkdownInlineFormat? = null,
)

/** Inline spans of [source] expanded to one range per individually toggleable format. */
internal fun markdownFormatSpans(source: String): List<MarkdownWrapperRange> =
    parseMarkdownDocument(source).spans.flatMap { span ->
        val formats =
            when (span.kind) {
                MarkdownInlineKind.Bold -> listOf(MarkdownInlineFormat.BOLD)
                MarkdownInlineKind.Italic -> listOf(MarkdownInlineFormat.ITALIC)
                MarkdownInlineKind.BoldItalic -> listOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.ITALIC)
                MarkdownInlineKind.Underline -> listOf(MarkdownInlineFormat.UNDERLINE)
                MarkdownInlineKind.Strikethrough -> listOf(MarkdownInlineFormat.STRIKETHROUGH)
                MarkdownInlineKind.Code -> listOf(MarkdownInlineFormat.INLINE_CODE)
                MarkdownInlineKind.Link -> emptyList()
            }
        formats.map { format -> MarkdownWrapperRange(span.openStart, span.openEnd, span.closeStart, span.closeEnd, format) }
    }

internal fun String.lineStartBefore(cursor: Int): Int {
    val boundedCursor = cursor.coerceIn(0, length)
    if (boundedCursor == 0) {
        return 0
    }
    return lastIndexOf('\n', boundedCursor - 1).let { newlineIndex ->
        if (newlineIndex < 0) 0 else newlineIndex + 1
    }
}

internal fun TextFieldValue.withCompleteEmptyWrappersRemoved(): TextFieldValue {
    var cleanedText = text
    var cleanedCursor = selection.start.coerceIn(0, text.length)
    while (true) {
        val wrapper =
            MarkdownEmptyInlineWrappers.firstOrNull { (open, close) ->
                cleanedCursor >= open.length &&
                    cleanedText.substring(cleanedCursor - open.length, cleanedCursor) == open &&
                    cleanedText.startsWith(close, cleanedCursor)
            } ?: break
        cleanedText = cleanedText.removeRange(cleanedCursor - wrapper.first.length, cleanedCursor + wrapper.second.length)
        cleanedCursor -= wrapper.first.length
    }
    return if (cleanedText == text) this else copy(text = cleanedText, selection = TextRange(cleanedCursor), composition = null)
}
