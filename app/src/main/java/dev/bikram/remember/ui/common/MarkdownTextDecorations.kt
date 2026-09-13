package dev.bikram.remember.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

/** Compose replaces overlapping textDecoration values; Markdown's U and strike must add up. */
internal fun AnnotatedString.withCombinedMarkdownDecorations(): AnnotatedString {
    if (spanStyles.none { it.item.textDecoration?.contains(TextDecoration.Underline) == true } ||
        spanStyles.none { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true }
    ) {
        return this
    }
    // Sweep style boundaries instead of comparing every span with every other span.
    val changes = sortedMapOf<Int, MarkdownDecorationChange>()
    for (span in spanStyles) {
        val decoration = span.item.textDecoration ?: continue
        if (span.start == span.end) continue
        val start = changes.getOrPut(span.start) { MarkdownDecorationChange() }
        val end = changes.getOrPut(span.end) { MarkdownDecorationChange() }
        if (TextDecoration.Underline in decoration) {
            start.underline++
            end.underline--
        }
        if (TextDecoration.LineThrough in decoration) {
            start.strike++
            end.strike--
        }
    }
    var underlineCount = 0
    var strikeCount = 0
    var overlapStart: Int? = null
    var builder: AnnotatedString.Builder? = null
    for ((offset, change) in changes) {
        underlineCount += change.underline
        strikeCount += change.strike
        if (underlineCount > 0 && strikeCount > 0) {
            if (overlapStart == null) overlapStart = offset
        } else if (overlapStart != null) {
            if (builder == null) builder = AnnotatedString.Builder(this)
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.Underline + TextDecoration.LineThrough),
                overlapStart,
                offset,
            )
            overlapStart = null
        }
    }
    return builder?.toAnnotatedString() ?: this
}

private class MarkdownDecorationChange(
    var underline: Int = 0,
    var strike: Int = 0,
)
