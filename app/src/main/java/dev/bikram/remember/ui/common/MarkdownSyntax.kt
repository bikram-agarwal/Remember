@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.common

/** Source ranges are half-open and always refer to the unchanged Markdown string. */
internal data class MarkdownInlineSpan(
    val kind: MarkdownInlineKind,
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val url: String? = null,
)

internal enum class MarkdownInlineKind { Code, Underline, Strikethrough, BoldItalic, Bold, Italic, Link }

internal enum class MarkdownBlockKind { Plain, Heading, Checklist, Bullet, Numbered, Quote, Rule, CodeFence, Code }

internal data class MarkdownLineSyntax(
    val start: Int,
    val end: Int,
    val contentStart: Int,
    val kind: MarkdownBlockKind,
    val indent: String = "",
    val number: String = "",
    val checked: Boolean = false,
    val headingLevel: Int = 0,
) {
    val hasEditablePrefix: Boolean
        get() =
            when (kind) {
                MarkdownBlockKind.Heading, MarkdownBlockKind.Checklist, MarkdownBlockKind.Bullet, MarkdownBlockKind.Numbered, MarkdownBlockKind.Quote -> true
                else -> false
            }

    val isEmptyBlock: Boolean
        get() = hasEditablePrefix && contentStart == end
}

internal data class MarkdownDocumentSyntax(
    val source: String,
    val lines: List<MarkdownLineSyntax>,
    val spans: List<MarkdownInlineSpan>,
)

internal val MarkdownHeadingLineRegex = Regex("""^(#{1,3})\s+(.*)$""")
internal val MarkdownChecklistLineRegex = Regex("""^(\s*)- \[([ xX])\]\s+(.*)$""")
internal val MarkdownBulletLineRegex = Regex("""^(\s*)[-*+]\s+(.*)$""")
internal val MarkdownNumberedLineRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
internal val MarkdownQuoteLineRegex = Regex("""^\s*>\s?(.*)$""")

// Only dash rules are supported: star runs remain available for in-progress emphasis.
internal val MarkdownHorizontalRuleLineRegex = Regex("""^ {0,3}-{3,}[ \t]*$""")
internal val MarkdownCodeFenceLineRegex = Regex("""^\s*```\s*$""")
internal val MarkdownLinkRegex = Regex("""\[([^\]\n]+)]\(([^)\n]+)\)""")

// Incomplete input is intentionally distinct from complete syntax: Backspace may leave half
// a checklist marker or a heading without its space while deleting an empty block prefix.
internal val MarkdownPartialBlockPrefixRegex = Regex("""^\s*(#{1,3}|- \[[ xX]?\]?|\d+[.)]?|[-*+]|>)\s*$""")

internal fun parseMarkdownLine(
    line: String,
    start: Int = 0,
    inCodeBlock: Boolean = false,
): MarkdownLineSyntax {
    val end = start + line.length
    if (MarkdownCodeFenceLineRegex.matches(line)) {
        return MarkdownLineSyntax(start, end, end, MarkdownBlockKind.CodeFence)
    }
    if (inCodeBlock) {
        return MarkdownLineSyntax(start, end, start, MarkdownBlockKind.Code)
    }
    if (MarkdownHorizontalRuleLineRegex.matches(line)) {
        return MarkdownLineSyntax(start, end, end, MarkdownBlockKind.Rule)
    }
    MarkdownHeadingLineRegex.matchEntire(line)?.let { match ->
        return MarkdownLineSyntax(start, end, start + match.groups[2]!!.range.first, MarkdownBlockKind.Heading, headingLevel = match.groupValues[1].length)
    }
    MarkdownChecklistLineRegex.matchEntire(line)?.let { match ->
        return MarkdownLineSyntax(start, end, start + match.groups[3]!!.range.first, MarkdownBlockKind.Checklist, indent = match.groupValues[1], checked = match.groupValues[2].equals("x", ignoreCase = true))
    }
    MarkdownBulletLineRegex.matchEntire(line)?.let { match ->
        return MarkdownLineSyntax(start, end, start + match.groups[2]!!.range.first, MarkdownBlockKind.Bullet, indent = match.groupValues[1])
    }
    MarkdownNumberedLineRegex.matchEntire(line)?.let { match ->
        return MarkdownLineSyntax(start, end, start + match.groups[3]!!.range.first, MarkdownBlockKind.Numbered, indent = match.groupValues[1], number = match.groupValues[2])
    }
    MarkdownQuoteLineRegex.matchEntire(line)?.let { match ->
        return MarkdownLineSyntax(start, end, start + match.groups[1]!!.range.first, MarkdownBlockKind.Quote)
    }
    return MarkdownLineSyntax(start, end, start, MarkdownBlockKind.Plain)
}

internal fun parseMarkdownLines(source: String): List<MarkdownLineSyntax> {
    val lines = mutableListOf<MarkdownLineSyntax>()
    var start = 0
    var inCodeBlock = false
    source.splitToSequence('\n').forEach { text ->
        val line = parseMarkdownLine(text, start, inCodeBlock)
        lines.add(line)
        if (line.kind == MarkdownBlockKind.CodeFence) inCodeBlock = !inCodeBlock
        start += text.length + 1
    }
    return lines
}

internal fun parseMarkdownDocument(source: String): MarkdownDocumentSyntax {
    val lines = parseMarkdownLines(source)
    val spans = mutableListOf<MarkdownInlineSpan>()
    for (line in lines) {
        when (line.kind) {
            MarkdownBlockKind.Code, MarkdownBlockKind.CodeFence, MarkdownBlockKind.Rule -> Unit
            else -> collectMarkdownInlineSpans(source.substring(line.contentStart, line.end), line.contentStart, spans)
        }
    }
    return MarkdownDocumentSyntax(source, lines, spans)
}

/** Inline fragments obey the same line boundaries as the editor, even in compact card text. */
internal fun parseMarkdownInline(source: String): List<MarkdownInlineSpan> {
    val spans = mutableListOf<MarkdownInlineSpan>()
    var start = 0
    source.splitToSequence('\n').forEach { line ->
        collectMarkdownInlineSpans(line, start, spans)
        start += line.length + 1
    }
    return spans
}

private data class MarkdownInlineFrame(
    var index: Int,
    val end: Int,
)

private fun collectMarkdownInlineSpans(
    source: String,
    sourceOffset: Int,
    spans: MutableList<MarkdownInlineSpan>,
) {
    val matcher = MarkdownInlineMatcher(source)
    val links = MarkdownLinkRegex.findAll(source).iterator()
    var nextLink = if (links.hasNext()) links.next() else null
    // Explicit frames keep deeply nested pasted markup off the JVM call stack.
    val frames = ArrayDeque<MarkdownInlineFrame>()
    frames.addLast(MarkdownInlineFrame(0, source.length))
    while (frames.isNotEmpty()) {
        val frame = frames.last()
        if (frame.index >= frame.end) {
            frames.removeLast()
            continue
        }
        val index = frame.index
        while (nextLink != null && nextLink.range.first < index) {
            nextLink = if (links.hasNext()) links.next() else null
        }
        val link = nextLink?.takeIf { it.range.first == index && it.range.last < frame.end }
        val span =
            if (link != null) {
                MarkdownInlineSpan(MarkdownInlineKind.Link, index, link.groups[1]!!.range.first, link.groups[1]!!.range.last + 1, link.range.last + 1, link.groupValues[2])
            } else {
                findMarkdownInlineSpan(source, index, frame.end, matcher)
            }
        if (span == null) {
            frame.index++
            continue
        }
        spans.add(span.copy(openStart = span.openStart + sourceOffset, openEnd = span.openEnd + sourceOffset, closeStart = span.closeStart + sourceOffset, closeEnd = span.closeEnd + sourceOffset))
        frame.index = span.closeEnd
        if (span.kind != MarkdownInlineKind.Code) {
            frames.addLast(MarkdownInlineFrame(span.openEnd, span.closeStart))
        }
    }
}

private val MarkdownInlineMarkers =
    listOf(
        "`" to MarkdownInlineKind.Code,
        "<u>" to MarkdownInlineKind.Underline,
        "~~" to MarkdownInlineKind.Strikethrough,
        "***" to MarkdownInlineKind.BoldItalic,
        "**" to MarkdownInlineKind.Bold,
        "*" to MarkdownInlineKind.Italic,
    )

private fun findMarkdownInlineSpan(
    source: String,
    index: Int,
    end: Int,
    matcher: MarkdownInlineMatcher,
): MarkdownInlineSpan? {
    if (source.startsWith("****", index) && source.getOrNull(index + 4) != '*' && index + 4 <= end) {
        return MarkdownInlineSpan(MarkdownInlineKind.Bold, index, index + 2, index + 2, index + 4)
    }
    for ((marker, kind) in MarkdownInlineMarkers) {
        // Never search for closers at ordinary characters: that previously caused large-paste ANRs.
        if (!source.startsWith(marker, index, ignoreCase = kind == MarkdownInlineKind.Underline)) continue
        if (kind != MarkdownInlineKind.Underline && !source.isValidOpening(index, marker.length)) continue
        val close = matcher.closingMarker(marker, index + marker.length, end)
        val closingLength = if (kind == MarkdownInlineKind.Underline) 4 else marker.length
        if (close >= index + marker.length && close + closingLength <= end) {
            return MarkdownInlineSpan(kind, index, index + marker.length, close, close + closingLength)
        }
    }
    return null
}

/** The saved text and its hit targets use exactly the same removed marker ranges. */
internal class MarkdownInlineProjection(
    val source: String,
    val spans: List<MarkdownInlineSpan> = parseMarkdownInline(source),
) {
    val text: String
    val sourceOffsets: IntArray
    val visibleOffsets = IntArray(source.length + 1)

    init {
        val markers = spans.flatMap { listOf(it.openStart until it.openEnd, it.closeStart until it.closeEnd) }.sortedBy { it.first }
        val visible = StringBuilder(source.length)
        val offsets = IntArray(source.length + 1)
        var markerIndex = 0
        for (index in source.indices) {
            while (markerIndex < markers.size && markers[markerIndex].last < index) markerIndex++
            visibleOffsets[index] = visible.length
            if (markerIndex >= markers.size || index < markers[markerIndex].first) {
                offsets[visible.length] = index
                visible.append(source[index])
            }
        }
        visibleOffsets[source.length] = visible.length
        offsets[visible.length] = source.length
        text = visible.toString()
        sourceOffsets = offsets.copyOf(visible.length + 1)
    }
}

internal fun String.markdownLinkUrl(): String {
    return if (startsWith("http://") || startsWith("https://")) this else "https://$this"
}
