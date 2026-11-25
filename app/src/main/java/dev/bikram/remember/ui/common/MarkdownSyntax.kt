@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.common

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

internal const val MARKDOWN_DATE_SCHEME = "date:"

/** Source ranges are half-open and always refer to the unchanged Markdown string. */
internal data class MarkdownInlineSpan(
    val kind: MarkdownInlineKind,
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val url: String? = null,
)

internal enum class MarkdownInlineKind { Code, Underline, Strikethrough, BoldItalic, Bold, Italic, Link, Date }

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
internal val MarkdownLinkRegex = Regex("""\[([^\]\r\n]+)]\(([^)\r\n]+)\)""")

internal fun String.normalizeMarkdownLineEndings(): String {
    val firstCarriageReturn = indexOf('\r')
    if (firstCarriageReturn < 0) return this
    val normalized = StringBuilder(length)
    normalized.append(this, 0, firstCarriageReturn)
    var index = firstCarriageReturn
    while (index < length) {
        val character = this[index]
        if (character == '\r') {
            normalized.append('\n')
            if (index + 1 < length && this[index + 1] == '\n') {
                index += 2
                continue
            }
        } else {
            normalized.append(character)
        }
        index++
    }
    return normalized.toString()
}

private inline fun String.forEachMarkdownLine(action: (line: String, start: Int) -> Unit) {
    var lineStart = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        if (character == '\r' || character == '\n') {
            action(substring(lineStart, index), lineStart)
            index += if (character == '\r' && index + 1 < length && this[index + 1] == '\n') 2 else 1
            lineStart = index
        } else {
            index++
        }
    }
    action(substring(lineStart, length), lineStart)
}

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
    var inCodeBlock = false
    source.forEachMarkdownLine { text, start ->
        val line = parseMarkdownLine(text, start, inCodeBlock)
        lines.add(line)
        if (line.kind == MarkdownBlockKind.CodeFence) inCodeBlock = !inCodeBlock
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
    source.forEachMarkdownLine { line, start ->
        collectMarkdownInlineSpans(line, start, spans)
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
                val target = link.groupValues[2]
                val kind =
                    if (parseMarkdownDateTarget(target) != null) {
                        MarkdownInlineKind.Date
                    } else {
                        MarkdownInlineKind.Link
                    }
                MarkdownInlineSpan(kind, index, link.groups[1]!!.range.first, link.groups[1]!!.range.last + 1, link.range.last + 1, target)
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

private val MarkdownDateTargetRegex = Regex("""^date:(\d{4}-\d{2}-\d{2})$""")

internal fun parseMarkdownDateTarget(rawTarget: String): LocalDate? {
    val isoDate = MarkdownDateTargetRegex.matchEntire(rawTarget.trim())?.groupValues?.get(1) ?: return null
    return runCatching { LocalDate.parse(isoDate) }.getOrNull()
}

internal fun formatInlineNoteDate(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    return runCatching {
        val shortPattern =
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale,
            )
        val normalizedPattern = normalizeShortNumericDatePattern(shortPattern)
        DateTimeFormatter.ofPattern(normalizedPattern, locale).format(date)
    }.getOrElse {
        DateTimeFormatter.ISO_LOCAL_DATE.format(date)
    }
}

private val PlainDateTextRegex = Regex("^[0-9]{1,4}[-/. ][0-9]{1,4}[-/. ][0-9]{1,4}[.]?$")

/**
 * Parses plain text a user typed as a date (e.g. `11/24/2026`, `24.11.2026`, `2026-11-24`) so the
 * date picker can open on it. Numeric field order follows [locale]'s short date format; a leading
 * four-digit year is always read as year-month-day. Returns null for anything else.
 */
internal fun parseInlineNoteDate(
    text: String,
    locale: Locale = Locale.getDefault(),
): LocalDate? {
    val trimmed = text.trim()
    if (!PlainDateTextRegex.matches(trimmed)) return null
    val parts = Regex("[0-9]+").findAll(trimmed).map { it.value }.toList()
    if (parts.size != 3) return null
    val order =
        if (parts[0].length == 4) {
            listOf('y', 'M', 'd')
        } else {
            localeDateFieldOrder(locale)
        }
    val values = HashMap<Char, String>(3)
    order.forEachIndexed { index, field -> values[field] = parts[index] }
    val yearText = values['y'] ?: return null
    val year =
        when (yearText.length) {
            4 -> yearText.toInt()
            2 -> 2000 + yearText.toInt()
            else -> return null
        }
    val month = values['M']?.toIntOrNull() ?: return null
    val day = values['d']?.toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun localeDateFieldOrder(locale: Locale): List<Char> {
    val pattern =
        runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        }.getOrDefault("M/d/y")
    val order = LinkedHashSet<Char>()
    var inQuotes = false
    for (ch in pattern) {
        if (ch == '\'') inQuotes = !inQuotes
        if (inQuotes) continue
        when (ch) {
            'y', 'u' -> order.add('y')
            'M', 'L' -> order.add('M')
            'd' -> order.add('d')
        }
    }
    return if (order.size == 3) order.toList() else listOf('M', 'd', 'y')
}

internal fun String.withMarkdownDateReplaced(
    spanStartOffset: Int,
    spanEndOffset: Int,
    newDate: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    if (spanStartOffset < 0 || spanEndOffset > length || spanStartOffset >= spanEndOffset) return this
    val replacement = "[${formatInlineNoteDate(newDate, locale)}]($MARKDOWN_DATE_SCHEME$newDate)"
    return replaceRange(spanStartOffset, spanEndOffset, replacement)
}

private fun normalizeShortNumericDatePattern(rawPattern: String): String {
    val builder = StringBuilder(rawPattern.length + 4)
    var index = 0
    var inQuotes = false
    while (index < rawPattern.length) {
        val ch = rawPattern[index]
        if (ch == '\'') {
            builder.append(ch)
            if (index + 1 < rawPattern.length && rawPattern[index + 1] == '\'') {
                builder.append('\'')
                index += 2
                continue
            }
            inQuotes = !inQuotes
            index++
            continue
        }
        if (!inQuotes && (ch == 'y' || ch == 'M' || ch == 'L' || ch == 'd')) {
            while (index < rawPattern.length && rawPattern[index] == ch) {
                index++
            }
            when (ch) {
                'y' -> builder.append("yyyy")
                'M' -> builder.append("MM")
                'L' -> builder.append("LL")
                'd' -> builder.append("dd")
            }
            continue
        }
        builder.append(ch)
        index++
    }
    return builder.toString()
}
