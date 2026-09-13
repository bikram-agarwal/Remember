package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownHorizontalRuleLineRegex
import dev.bikram.remember.ui.common.MarkdownInlineKind
import dev.bikram.remember.ui.common.MarkdownLineSyntax
import dev.bikram.remember.ui.common.markdownLinkUrl
import dev.bikram.remember.ui.common.parseMarkdownDocument
import dev.bikram.remember.ui.common.parseMarkdownLines
import kotlin.math.max
import kotlin.math.min

private val MarkdownHeadingPrefixRegex = Regex("""^#{1,6}\s+""")
private val MarkdownBlockPrefixRegex = Regex("""^(#{1,6}\s+|\s*- \[[ xX]\]\s+|\s*\d+[.)]\s+|\s*[-*+]\s+|>\s*)""")
private val MarkdownBulletPrefixRegex = Regex("""^\s*[-*+]\s+""")
private val MarkdownNestedBulletPrefixRegex = Regex("""^\s{2,}[-*+]\s+""")
private val MarkdownChecklistPrefixRegex = Regex("""^\s*- \[[ xX]\]\s+""")
private val MarkdownNestedChecklistPrefixRegex = Regex("""^\s{2,}- \[[ xX]\]\s+""")
private val MarkdownNumberedPrefixRegex = Regex("""^\s*\d+[.)]\s+""")
private val MarkdownNestedNumberedPrefixRegex = Regex("""^\s{2,}\d+[.)]\s+""")
private val MarkdownChecklistContinuationRegex = Regex("""^(\s*)- \[[ xX]\]\s+(.*)$""")
private val MarkdownNumberedContinuationRegex = Regex("""^(\s*)(\d+)([.)]\s+)(.*)$""")
private val MarkdownBulletContinuationRegex = Regex("""^(\s*)([-*+]\s+)(.*)$""")
private val MarkdownTopLevelNumberedRegex = Regex("""^(\d+)[.)]\s+.*$""")
private val MarkdownEmptyInlineWrappers =
    listOf(
        "***" to "***",
        "**" to "**",
        "<u>" to "</u>",
        "~~" to "~~",
        "`" to "`",
        "*" to "*",
    )
private val MarkdownEmptyHeadingPrefixRegex = Regex("""^#{1,3}\s+$""")
private val MarkdownEmptyBlockPrefixRegex = Regex("""^(#{1,3}\s+|\s*- \[[ xX]\]\s+|\s*\d+[.)]\s+|\s*[-*+]\s+|>\s*)$""")
private val MarkdownPartialBlockPrefixRegex = Regex("""^\s*(#{1,3}|- \[[ xX]?\]?|\d+[.)]?|[-*+]|>)\s*$""")
private const val BODY_AUTO_FOCUS_MAX_CHARS = 280
private const val BODY_AUTO_FOCUS_MAX_PARAGRAPHS = 6

@Suppress("LargeClass")
internal class MarkdownEditBuffer(
    initialValue: TextFieldValue,
) {
    var textFieldValue: TextFieldValue = initialValue
        private set

    val markdown: String
        get() = textFieldValue.text

    val hasSelection: Boolean
        get() = !textFieldValue.selection.collapsed

    private var parsedMarkdown: String? = null
    private var parsedSpans: List<MarkdownWrapperRange> = emptyList()

    private fun formatSpans(): List<MarkdownWrapperRange> {
        if (parsedMarkdown != markdown) {
            parsedSpans = formatSpans(markdown)
            parsedMarkdown = markdown
        }
        return parsedSpans
    }

    private fun formatSpans(source: String): List<MarkdownWrapperRange> =
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

    private fun selectedBlockKinds(): List<MarkdownLineSyntax> {
        val selectedStarts = selectedLines().map { it.start }.toSet()
        return parseMarkdownLines(markdown).filter { it.start in selectedStarts }
    }

    val headingLevel: Int
        get() = selectedBlockKinds().firstOrNull { it.kind == MarkdownBlockKind.Heading }?.headingLevel ?: 0

    val isBulletList: Boolean
        get() = selectedBlockKinds().all { it.kind == MarkdownBlockKind.Bullet }

    val isChecklist: Boolean
        get() = selectedBlockKinds().all { it.kind == MarkdownBlockKind.Checklist }

    val isNumberedList: Boolean
        get() = selectedBlockKinds().all { it.kind == MarkdownBlockKind.Numbered }

    val isQuote: Boolean
        get() = selectedBlockKinds().all { it.kind == MarkdownBlockKind.Quote }

    val isBold: Boolean
        get() = isFormatActive(MarkdownInlineFormat.BOLD)

    val isItalic: Boolean
        get() = isFormatActive(MarkdownInlineFormat.ITALIC)

    val isUnderline: Boolean
        get() = isFormatActive(MarkdownInlineFormat.UNDERLINE)

    val isStrikethrough: Boolean
        get() = isFormatActive(MarkdownInlineFormat.STRIKETHROUGH)

    val isInlineCode: Boolean
        get() = isFormatActive(MarkdownInlineFormat.INLINE_CODE)

    val isCodeBlock: Boolean
        get() = selectedBlockKinds().any { it.kind == MarkdownBlockKind.Code || it.kind == MarkdownBlockKind.CodeFence }

    val selectedLinkUrl: String?
        get() = markdownLinkAtSelection()?.url

    val shouldCapitalizeNextInputInEmptyInlineWrapper: Boolean
        get() {
            val selection = textFieldValue.selection
            if (!selection.collapsed) {
                return false
            }
            val cursor = selection.start.coerceIn(0, markdown.length)
            return MarkdownEmptyInlineWrappers.any { (open, close) ->
                val markerStart = cursor - open.length
                markerStart >= 0 &&
                    markdown.substring(markerStart, cursor) == open &&
                    markdown.startsWith(close, cursor) &&
                    isSentenceStartBefore(markerStart)
            } ||
                isCursorAfterEmptyHeadingPrefix(cursor)
        }

    fun update(
        value: TextFieldValue,
        cleanUpEmptyMarkdownWrappers: Boolean = true,
        breakLineAfterHorizontalRule: Boolean = true,
    ) {
        val previousValue = textFieldValue
        // Read before textFieldValue is reassigned below: reflects whether the cursor sat inside
        // an empty wrapper/heading prefix at a sentence start *before* this keystroke landed.
        val shouldCapitalizeNext = shouldCapitalizeNextInputInEmptyInlineWrapper
        val selectionAdjustedValue =
            if (cleanUpEmptyMarkdownWrappers) value.withHiddenMarkerSelectionCollapsed(previousValue) else value
        val newlineAdjustedValue =
            selectionAdjustedValue.withInlineWrapperEnterAdjusted(previousValue = previousValue)
        val updatedValue =
            newlineAdjustedValue.withListContinuationApplied(previousValue = previousValue)
        val cleanedValue =
            if (cleanUpEmptyMarkdownWrappers && updatedValue == newlineAdjustedValue) {
                updatedValue.withLivePreviewDeletionApplied(previousValue = previousValue)
            } else {
                updatedValue
            }
        val capitalizedValue =
            cleanedValue.withAutoCapitalizedSingleCharacterInsertion(
                previousValue = previousValue,
                shouldCapitalize = shouldCapitalizeNext,
            )
        // Runs last so it cannot disturb the single-character/single-newline detection the
        // transforms above do against previousValue.
        textFieldValue =
            if (breakLineAfterHorizontalRule) {
                capitalizedValue.withLineBreakAfterCompletedHorizontalRule(previousValue = previousValue)
            } else {
                capitalizedValue
            }
    }

    /** A displayed caret can map to a source selection containing only hidden delimiters. */
    private fun TextFieldValue.withHiddenMarkerSelectionCollapsed(previousValue: TextFieldValue): TextFieldValue {
        if (text != previousValue.text || selection.collapsed) return this
        val spans = formatSpans()
        val onlyMarkers =
            (selection.min until selection.max).all { index ->
                spans.any { index in it.openStart until it.openEnd || index in it.closeStart until it.closeEnd }
            }
        if (!onlyMarkers) return this
        val previousCursor = previousValue.selection.start
        val cursor =
            if (previousValue.selection.collapsed && previousCursor in selection.min..selection.max) {
                // Keep the side chosen by the previous input or explicit toolbar toggle.
                previousCursor
            } else {
                spans.map { it.openEnd }.filter { it in selection.min..selection.max }.maxOrNull()
                    ?: spans.map { it.closeStart }.filter { it in selection.min..selection.max }.minOrNull()
                    ?: selection.min
            }
        return copy(selection = TextRange(cursor))
    }

    // Compensates for keeping the IME's keyboardOptions.capitalization permanently fixed at
    // Sentences (see MarkdownTextEditor): switching that value at runtime forces Compose to
    // renegotiate the IME session, which is what caused the keyboard to visibly hide/reshow every
    // time typing began inside an empty bold/italic/heading marker at a sentence start. Doing the
    // capitalization ourselves, on the actual inserted character, avoids touching keyboardOptions
    // at all.
    private fun TextFieldValue.withAutoCapitalizedSingleCharacterInsertion(
        previousValue: TextFieldValue,
        shouldCapitalize: Boolean,
    ): TextFieldValue {
        if (!shouldCapitalize || !previousValue.selection.collapsed || !selection.collapsed) {
            return this
        }
        val insertionIndex = previousValue.selection.start
        if (text.length != previousValue.text.length + 1 || selection.start != insertionIndex + 1) {
            return this
        }
        val insertedChar = text.getOrNull(insertionIndex) ?: return this
        if (!insertedChar.isLowerCase()) {
            return this
        }
        return copy(text = text.replaceRange(insertionIndex, insertionIndex + 1, insertedChar.uppercase()))
    }

    fun setMarkdown(
        value: String,
        moveCursorToEnd: Boolean = true,
    ) {
        val selection =
            if (moveCursorToEnd) {
                TextRange(value.length)
            } else {
                val existingSelection = textFieldValue.selection
                TextRange(
                    existingSelection.start.coerceIn(0, value.length),
                    existingSelection.end.coerceIn(0, value.length),
                )
            }
        textFieldValue = TextFieldValue(text = value, selection = selection)
    }

    fun toggleBold() {
        surroundSelection(open = "**", close = "**")
    }

    fun toggleItalic() {
        surroundSelection(open = "*", close = "*")
    }

    fun toggleUnderline() {
        surroundSelection(open = "<u>", close = "</u>")
    }

    fun toggleStrikethrough() {
        surroundSelection(open = "~~", close = "~~")
    }

    fun toggleInlineCode() {
        surroundSelection(open = "`", close = "`")
    }

    fun applyCodeBlock() {
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, markdown.length)
        val end = selection.max.coerceIn(start, markdown.length)
        val selectedText = markdown.substring(start, end)
        val replacement = "```\n$selectedText\n```"
        val updatedText = markdown.replaceRange(start, end, replacement)
        val updatedSelection =
            if (start == end) {
                TextRange(start + 4)
            } else {
                TextRange(start + 4, start + 4 + selectedText.length)
            }
        textFieldValue = TextFieldValue(updatedText, selection = updatedSelection)
    }

    fun applyHeading(level: Int) {
        val headingLevel = level.coerceIn(1, 3)
        val prefix = "#".repeat(headingLevel) + " "
        replaceSelectedLines { line ->
            val headingPrefix = MarkdownHeadingPrefixRegex.find(line.text)
            if (headingPrefix != null && headingPrefix.value.count { character -> character == '#' } == headingLevel) {
                line.text.replaceFirst(MarkdownHeadingPrefixRegex, "")
            } else {
                prefix + line.text.replaceFirst(MarkdownBlockPrefixRegex, "")
            }
        }
    }

    fun applyBulletList() {
        val lines = selectedLines()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> MarkdownBulletPrefixRegex.containsMatchIn(line.text) } &&
                lines.none { line -> MarkdownNestedBulletPrefixRegex.containsMatchIn(line.text) }
        replaceSelectedLines { line ->
            (if (nested) "  - " else "- ") + line.text.replaceFirst(MarkdownBlockPrefixRegex, "")
        }
    }

    fun applyChecklist() {
        val lines = selectedLines()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> MarkdownChecklistPrefixRegex.containsMatchIn(line.text) } &&
                lines.none { line -> MarkdownNestedChecklistPrefixRegex.containsMatchIn(line.text) }
        replaceSelectedLines { line ->
            (if (nested) "  - [ ] " else "- [ ] ") + line.text.replaceFirst(MarkdownBlockPrefixRegex, "")
        }
    }

    fun applyNumberedList() {
        val lines = selectedLines()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> MarkdownNumberedPrefixRegex.containsMatchIn(line.text) } &&
                lines.none { line -> MarkdownNestedNumberedPrefixRegex.containsMatchIn(line.text) }
        replaceSelectedLines { line ->
            (if (nested) "  " else "") + "${line.index + 1}. " + line.text.replaceFirst(MarkdownBlockPrefixRegex, "")
        }
    }

    fun applyQuote() {
        replaceSelectedLines { line ->
            "> " + line.text.replaceFirst(MarkdownBlockPrefixRegex, "")
        }
    }

    fun addOrUpdateLink(
        displayText: String,
        rawUrl: String,
    ) {
        val normalizedUrl = rawUrl.markdownLinkUrl()
        val existingLink = markdownLinkAtSelection()
        if (existingLink != null) {
            replaceExistingLink(existingLink, displayText, normalizedUrl)
            return
        }

        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, markdown.length)
        val end = selection.max.coerceIn(start, markdown.length)
        val selectedText = if (start < end) markdown.substring(start, end) else ""
        val linkText = displayText.ifBlank { selectedText.ifBlank { normalizedUrl } }
        val replacement = "[$linkText]($normalizedUrl)"
        val updatedText = markdown.replaceRange(start, end, replacement)
        val labelStart = start + 1
        val labelEnd = labelStart + linkText.length
        textFieldValue = TextFieldValue(updatedText, selection = TextRange(labelStart, labelEnd))
    }

    fun selectedText(): String {
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, markdown.length)
        val end = selection.max.coerceIn(start, markdown.length)
        if (start < end) {
            return markdown.substring(start, end)
        }
        return markdownLinkAtSelection()?.text.orEmpty()
    }

    fun shouldAutoFocusBodyOnEdit(): Boolean {
        if (markdown.isEmpty()) {
            return true
        }
        return markdown.length <= BODY_AUTO_FOCUS_MAX_CHARS &&
            markdown.lineSequence().count() <= BODY_AUTO_FOCUS_MAX_PARAGRAPHS
    }

    private fun surroundSelection(
        open: String,
        close: String,
    ) {
        val selection = textFieldValue.selection
        val rawStart = selection.min.coerceIn(0, markdown.length)
        val rawEnd = selection.max.coerceIn(rawStart, markdown.length)
        val adjustedRange = adjustSelectionBoundaries(rawStart, rawEnd)
        val start = adjustedRange.start
        val end = adjustedRange.end
        // A triple delimiter represents two independently toggleable formats. Removing one
        // must preserve the other's markers, including when the wrapper is still empty.
        val combined =
            formatSpans().firstOrNull { span ->
                span.openEnd - span.openStart == 3 &&
                    markdown.startsWith("***", span.openStart) &&
                    (
                        (start == span.openEnd && end == span.closeStart) ||
                            (start == span.openStart && end == span.closeEnd)
                    )
            }
        if ((open == "*" || open == "**") && combined != null) {
            val remaining = "*".repeat(3 - open.length)
            val content = markdown.substring(combined.openEnd, combined.closeStart)
            val updatedText = markdown.replaceRange(combined.openStart, combined.closeEnd, remaining + content + remaining)
            val contentStart = combined.openStart + remaining.length
            textFieldValue = TextFieldValue(updatedText, TextRange(contentStart, contentStart + content.length))

            return
        }
        val emptyEnclosingWrapper =
            formatSpans().firstOrNull { span ->
                start == end && start in span.openEnd..span.closeStart &&
                    markdown.substring(span.openStart, span.openEnd) == open &&
                    TextFieldValue(
                        markdown.substring(span.openEnd, span.closeStart),
                        TextRange(start - span.openEnd),
                    ).withCompleteEmptyWrappersRemoved().text.isEmpty()
            }
        if (emptyEnclosingWrapper != null) {
            val updatedText =
                markdown
                    .removeRange(emptyEnclosingWrapper.closeStart, emptyEnclosingWrapper.closeEnd)
                    .removeRange(emptyEnclosingWrapper.openStart, emptyEnclosingWrapper.openEnd)
            textFieldValue = TextFieldValue(updatedText, TextRange(start - open.length))

            return
        }
        if (start == end && splitActiveFormatAtCursor(open, close, start)) {
            return
        }
        val externalWrapper = externalWrapperRange(start = start, end = end, open = open, close = close)
        if (externalWrapper != null) {
            val updatedText =
                markdown
                    .removeRange(externalWrapper.closeStart, externalWrapper.closeEnd)
                    .removeRange(externalWrapper.openStart, externalWrapper.openEnd)
            textFieldValue =
                TextFieldValue(
                    text = updatedText,
                    selection = TextRange(externalWrapper.openStart, externalWrapper.closeStart - open.length),
                )

            return
        }

        // Cursor sits right before this wrapper's own close marker (e.g. toggling Bold off right
        // after typing the bolded text) — reuse the existing marker instead of inserting a
        // redundant one, which would otherwise leave a broken run of markers behind. Preserve
        // the command's existing serialization policy: trailing spaces move outside a wrapper
        // when the user explicitly ends that format, even though saved rendering accepts them.
        if (start == end && markdown.startsWith(close, start)) {
            val lineStart = markdown.lineStartBefore(start)
            val lineBeforeCursor = markdown.substring(lineStart, start)
            val trimmedLength = lineBeforeCursor.length - lineBeforeCursor.trimEnd().length
            val normalizedSource = markdown.removeRange(start - trimmedLength, start)
            val closingSpan =
                formatSpans(normalizedSource).firstOrNull { span ->
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
                    markdown.substring(0, contentEnd) +
                        close +
                        markdown.substring(contentEnd, start) +
                        markdown.substring(start + close.length)
                textFieldValue = TextFieldValue(updatedText, selection = TextRange(start + close.length))

                return
            }
        }

        val selectedText = markdown.substring(start, end)
        if (selectedText.hasExactWrapper(open = open, close = close)) {
            val replacement = selectedText.removeSurrounding(open, close)
            val updatedText = markdown.replaceRange(start, end, replacement)
            textFieldValue =
                TextFieldValue(
                    text = updatedText,
                    selection = TextRange(start, start + replacement.length),
                )

            return
        }

        val replacement = open + selectedText + close
        val updatedText = markdown.replaceRange(start, end, replacement)
        val updatedSelection =
            if (start == end) {
                TextRange(start + open.length)
            } else {
                TextRange(start + open.length, start + open.length + selectedText.length)
            }
        textFieldValue = TextFieldValue(updatedText, selection = updatedSelection)
    }

    private fun splitActiveFormatAtCursor(
        open: String,
        close: String,
        cursor: Int,
    ): Boolean {
        val format =
            when (open) {
                "**" -> MarkdownInlineFormat.BOLD
                "*" -> MarkdownInlineFormat.ITALIC
                "<u>" -> MarkdownInlineFormat.UNDERLINE
                "~~" -> MarkdownInlineFormat.STRIKETHROUGH
                else -> MarkdownInlineFormat.INLINE_CODE
            }
        val spans = formatSpans()
        val target = spans.firstOrNull { it.format == format && cursor in it.openEnd..it.closeStart } ?: return false
        val inner =
            spans
                .filter { it.openStart > target.openStart && it.closeEnd <= target.closeEnd && cursor in it.openEnd..it.closeStart }
                .distinctBy { it.openStart }
                .sortedBy { it.openStart }
        // The existing end-of-run path also moves trailing spaces outside the closing marker.
        if (inner.isEmpty() && cursor == target.closeStart) return false

        val leftInner = inner.filter { span -> cursor - span.openEnd > inner.filter { it.openStart > span.openStart }.sumOf { it.openEnd - it.openStart } }
        val rightInner = inner.filter { span -> span.closeStart - cursor > inner.filter { it.openStart > span.openStart }.sumOf { it.closeEnd - it.closeStart } }
        val hasLeft = cursor - target.openEnd > inner.sumOf { it.openEnd - it.openStart }
        val hasRight = target.closeStart - cursor > inner.sumOf { it.closeEnd - it.closeStart }
        var prefix = markdown.substring(0, cursor)
        inner.filter { it !in leftInner }.asReversed().forEach { span ->
            prefix = prefix.removeRange(span.openStart, span.openEnd)
        }
        if (!hasLeft) prefix = prefix.removeRange(target.openEnd - open.length, target.openEnd)
        var suffix = markdown.substring(cursor)
        val removedClosers = inner.filter { it !in rightInner }.map { TextRange(it.closeStart, it.closeEnd) }.toMutableList()
        if (!hasRight) removedClosers.add(TextRange(target.closeStart, target.closeStart + close.length))
        removedClosers.sortedByDescending { it.start }.forEach { range ->
            suffix = suffix.removeRange(range.start - cursor, range.end - cursor)
        }
        val leftClose = leftInner.asReversed().joinToString("") { markdown.substring(it.closeStart, it.closeEnd) } + if (hasLeft) close else ""
        val middleOpen = inner.joinToString("") { markdown.substring(it.openStart, it.openEnd) }
        val middleClose = inner.asReversed().joinToString("") { markdown.substring(it.closeStart, it.closeEnd) }
        val rightOpen = (if (hasRight) open else "") + rightInner.joinToString("") { markdown.substring(it.openStart, it.openEnd) }
        textFieldValue =
            TextFieldValue(
                prefix + leftClose + middleOpen + middleClose + rightOpen + suffix,
                TextRange(prefix.length + leftClose.length + middleOpen.length),
            )
        return true
    }

    private fun externalWrapperRange(
        start: Int,
        end: Int,
        open: String,
        close: String,
    ): MarkdownWrapperRange? {
        val openStart = start - open.length
        val closeEnd = end + close.length
        if (
            openStart < 0 ||
            closeEnd > markdown.length ||
            markdown.substring(openStart, start) != open ||
            markdown.substring(end, closeEnd) != close
        ) {
            return null
        }
        if (!isExactDelimiterBoundary(openStart = openStart, openEnd = start, closeStart = end, closeEnd = closeEnd, marker = open)) {
            return null
        }
        return MarkdownWrapperRange(
            openStart = openStart,
            openEnd = start,
            closeStart = end,
            closeEnd = closeEnd,
        )
    }

    private fun isExactDelimiterBoundary(
        openStart: Int,
        openEnd: Int,
        closeStart: Int,
        closeEnd: Int,
        marker: String,
    ): Boolean {
        if (marker != "*") {
            return true
        }
        return markdown.getOrNull(openStart - 1) != '*' &&
            (openEnd == closeStart || (markdown.getOrNull(openEnd) != '*' && markdown.getOrNull(closeStart - 1) != '*')) &&
            markdown.getOrNull(closeEnd) != '*'
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

    private fun replaceSelectedLines(transform: (EditorLine) -> String) {
        val lines = selectedLines()
        if (lines.isEmpty()) {
            return
        }

        var updatedText = markdown
        var updatedSelectionStart = textFieldValue.selection.start
        var updatedSelectionEnd = textFieldValue.selection.end
        lines.asReversed().forEach { line ->
            val replacement = transform(line)
            val lengthDelta = replacement.length - line.text.length
            updatedText = updatedText.replaceRange(line.start, line.end, replacement)
            updatedSelectionStart =
                adjustPositionForLineTransform(
                    position = updatedSelectionStart,
                    line = line,
                    lengthDelta = lengthDelta,
                    replacementLength = replacement.length,
                )
            updatedSelectionEnd =
                adjustPositionForLineTransform(
                    position = updatedSelectionEnd,
                    line = line,
                    lengthDelta = lengthDelta,
                    replacementLength = replacement.length,
                )
        }

        textFieldValue =
            TextFieldValue(
                text = updatedText,
                selection =
                    TextRange(
                        updatedSelectionStart.coerceIn(0, updatedText.length),
                        updatedSelectionEnd.coerceIn(0, updatedText.length),
                    ),
            )
    }

    private fun selectedLines(): List<EditorLine> {
        val text = markdown
        if (text.isEmpty()) {
            return listOf(EditorLine(start = 0, end = 0, text = ""))
        }

        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart = text.lineStartBefore(start)
        val targetEnd = if (end > start) end else start
        val lines = mutableListOf<EditorLine>()
        var currentLineStart = firstLineStart
        while (currentLineStart <= text.length) {
            val currentLineEnd =
                text.indexOf('\n', currentLineStart).let { newlineIndex ->
                    if (newlineIndex < 0) text.length else newlineIndex
                }
            lines.add(
                EditorLine(
                    start = currentLineStart,
                    end = currentLineEnd,
                    text = text.substring(currentLineStart, currentLineEnd),
                    index = lines.size,
                ),
            )
            if (currentLineEnd >= targetEnd || currentLineEnd == text.length) {
                break
            }
            currentLineStart = currentLineEnd + 1
        }
        return lines
    }

    private fun adjustPositionForLineTransform(
        position: Int,
        line: EditorLine,
        lengthDelta: Int,
        replacementLength: Int,
    ): Int {
        if (position > line.end) {
            return position + lengthDelta
        }
        if (position >= line.start) {
            return (position + lengthDelta).coerceIn(line.start, line.start + replacementLength)
        }
        return position
    }

    private fun TextFieldValue.withInlineWrapperEnterAdjusted(previousValue: TextFieldValue): TextFieldValue {
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

        val spans = formatSpans(previousValue.text)
        var adjustedNewlineIndex = previousCursor
        while (true) {
            val closing = spans.filter { it.closeStart == adjustedNewlineIndex }.maxByOrNull { it.closeEnd } ?: break
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

    private fun TextFieldValue.withLivePreviewDeletionApplied(previousValue: TextFieldValue): TextFieldValue {
        if (text.length >= previousValue.text.length || !selection.collapsed) {
            return this
        }
        withInlineFormattingPreserved(previousValue)?.let { return it }
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
                MarkdownEmptyBlockPrefixRegex.matches(previousLine) &&
                MarkdownPartialBlockPrefixRegex.matches(currentLine) &&
                !MarkdownEmptyBlockPrefixRegex.matches(currentLine)
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
        if (MarkdownEmptyHeadingPrefixRegex.matches(text.substring(lineStart, lineEnd))) {
            return copy(text = text.removeRange(lineStart, lineEnd), selection = TextRange(lineStart), composition = null)
        }
        return this
    }

    /**
     * Compose can expand a displayed deletion over hidden opening/closing markers. Delete only
     * content from those ranges. Clear a wrapper only when its last character has been deleted.
     * Source-mode edits bypass this rule and can still edit Markdown syntax directly.
     */
    private fun TextFieldValue.withInlineFormattingPreserved(previousValue: TextFieldValue): TextFieldValue? {
        val removedCount = previousValue.text.length - text.length
        val cursor = selection.start
        val deletion =
            if (cursor + removedCount <= previousValue.text.length &&
                text == previousValue.text.removeRange(cursor, cursor + removedCount)
            ) {
                TextRange(cursor, cursor + removedCount)
            } else {
                val change = markdownChangedRange(previousValue.text, text)
                if (change.start != change.updatedEnd) return null
                TextRange(change.start, change.originalEnd)
            }
        val spans = formatSpans()
        if (spans.none { deletion.min < it.closeEnd && deletion.max > it.openStart }) return null
        val markers = BooleanArray(previousValue.text.length)
        for (span in spans) {
            markers.fill(true, span.openStart, span.openEnd)
            markers.fill(true, span.closeStart, span.closeEnd)
        }
        val removed = BooleanArray(previousValue.text.length)
        for (index in deletion.min until deletion.max) {
            removed[index] = !markers[index]
        }
        if (removed.none { it }) {
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
        if (firstDeletedContent < 0) return previousValue
        val lastDeletedContent = removed.indexOfLast { it }
        // A selection can empty several separate wrappers. Cursor-local cleanup only removes
        // the first one, leaving invisible Markdown elsewhere in an apparently empty note.
        // Count surviving content once so nested spans do not repeatedly scan the whole text.
        val survivingContent = IntArray(previousValue.text.length + 1)
        for (index in previousValue.text.indices) {
            survivingContent[index + 1] = survivingContent[index] + if (!markers[index] && !removed[index]) 1 else 0
        }
        for (span in spans) {
            val touchedContent = span.openEnd <= lastDeletedContent && span.closeStart > firstDeletedContent
            val selectedWrapper =
                !previousValue.selection.collapsed && deletion.min <= span.openStart && deletion.max >= span.closeEnd
            if ((touchedContent || selectedWrapper) && survivingContent[span.closeStart] == survivingContent[span.openEnd]) {
                // This also clears empty nested wrappers owned by the deleted span.
                removed.fill(true, span.openStart, span.closeEnd)
            }
        }
        val contentStart = removed.indexOfFirst { it }
        val updatedText =
            buildString(previousValue.text.length) {
                previousValue.text.forEachIndexed { index, character ->
                    if (!removed[index]) append(character)
                }
            }
        return copy(text = updatedText, selection = TextRange(contentStart), composition = null).withCompleteEmptyWrappersRemoved()
    }

    private fun isSentenceStartBefore(index: Int): Boolean {
        val lineStart = markdown.lineStartBefore(index)
        val currentLinePrefix = markdown.substring(lineStart, index)
        val prefixWithoutBlockMarker = currentLinePrefix.replaceFirst(MarkdownBlockPrefixRegex, "")
        if (prefixWithoutBlockMarker.isBlank()) {
            return true
        }
        val previousCharacter = prefixWithoutBlockMarker.trimEnd().lastOrNull() ?: return true
        return previousCharacter == '.' || previousCharacter == '!' || previousCharacter == '?'
    }

    private fun isCursorAfterEmptyHeadingPrefix(cursor: Int): Boolean {
        val lineStart = markdown.lineStartBefore(cursor)
        return MarkdownEmptyHeadingPrefixRegex.matches(markdown.substring(lineStart, cursor))
    }

    private fun markdownLinkAtSelection(): MarkdownLinkRange? {
        val selection = textFieldValue.selection
        val selectionStart = selection.min.coerceIn(0, markdown.length)
        val selectionEnd = selection.max.coerceIn(selectionStart, markdown.length)
        return parseMarkdownDocument(markdown)
            .spans
            .firstOrNull { span ->
                span.kind == MarkdownInlineKind.Link &&
                    if (selectionStart == selectionEnd) {
                        selectionStart in span.openStart..span.closeEnd
                    } else {
                        selectionStart < span.closeEnd && selectionEnd > span.openStart
                    }
            }?.let { span ->
                MarkdownLinkRange(span.openStart, span.closeEnd, markdown.substring(span.openEnd, span.closeStart), requireNotNull(span.url))
            }
    }

    private fun replaceExistingLink(
        existingLink: MarkdownLinkRange,
        displayText: String,
        normalizedUrl: String,
    ) {
        val linkText = displayText.ifBlank { existingLink.text }
        val replacement = "[$linkText]($normalizedUrl)"
        val updatedText = markdown.replaceRange(existingLink.start, existingLink.endExclusive, replacement)
        val labelStart = existingLink.start + 1
        val labelEnd = labelStart + linkText.length
        textFieldValue = TextFieldValue(updatedText, selection = TextRange(labelStart, labelEnd))
    }

    private fun TextFieldValue.withListContinuationApplied(previousValue: TextFieldValue): TextFieldValue {
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

    // Live preview hides a completed rule line outright, so leaving the cursor on it is a trap:
    // the next character typed lands between the dashes and silently dissolves the rule back into
    // text. Completing one therefore moves the cursor to a fresh line below it, exactly as if the
    // user had pressed enter. Only the keystroke that *completes* a rule qualifies - anything else
    // (a paste, an edit mid-line, a fourth dash on a line that is already a rule) is left alone.
    private fun TextFieldValue.withLineBreakAfterCompletedHorizontalRule(previousValue: TextFieldValue): TextFieldValue {
        if (!selection.collapsed || !previousValue.selection.collapsed) {
            return this
        }
        if (text.length != previousValue.text.length + 1) {
            return this
        }
        val cursor = selection.start
        val insertionIndex = cursor - 1
        if (insertionIndex !in text.indices || text[insertionIndex] != '-') {
            return this
        }
        if (text.removeRange(insertionIndex, cursor) != previousValue.text) {
            return this
        }
        // Typing anywhere but the end of the line means the dashes are part of something else.
        val lineEndIndex =
            text.indexOf('\n', cursor).let { newlineIndex ->
                if (newlineIndex < 0) text.length else newlineIndex
            }
        if (cursor != lineEndIndex) {
            return this
        }
        val lineStartIndex =
            text.lastIndexOf('\n', insertionIndex).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        // markdownHorizontalRuleLineStarts is the same check live preview renders by, so a dash run
        // inside a fenced code block stays literal text here too.
        if (lineStartIndex !in markdownHorizontalRuleLineStarts(text)) {
            return this
        }
        // Already a rule before this dash landed, so nothing was completed.
        if (MarkdownHorizontalRuleLineRegex.matches(text.substring(lineStartIndex, insertionIndex))) {
            return this
        }
        return TextFieldValue(
            text = text.replaceRange(startIndex = cursor, endIndex = cursor, replacement = "\n"),
            selection = TextRange(cursor + 1),
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

    private fun String.lineStartBefore(cursor: Int): Int {
        val boundedCursor = cursor.coerceIn(0, length)
        if (boundedCursor == 0) {
            return 0
        }
        return lastIndexOf('\n', boundedCursor - 1).let { newlineIndex ->
            if (newlineIndex < 0) 0 else newlineIndex + 1
        }
    }

    private fun continuationPrefixForLine(
        line: String,
        lineStart: Int,
        sourceText: String,
    ): ContinuationPrefix? {
        val checklistMatch = MarkdownChecklistContinuationRegex.matchEntire(line)
        if (checklistMatch != null) {
            val indent = checklistMatch.groupValues[1]
            return ContinuationPrefix(
                content = checklistMatch.groupValues[2],
                nextPrefix = indent + "- [ ] ",
                outdentPrefix = if (indent.isNotEmpty()) "- [ ] " else null,
            )
        }

        val numberedMatch = MarkdownNumberedContinuationRegex.matchEntire(line)
        if (numberedMatch != null) {
            val indent = numberedMatch.groupValues[1]
            val currentNumber = numberedMatch.groupValues[2].toIntOrNull() ?: 1
            val outdentNumber =
                if (indent.isNotEmpty()) {
                    nextTopLevelNumberBefore(lineStart = lineStart, sourceText = sourceText)
                } else {
                    null
                }
            return ContinuationPrefix(
                content = numberedMatch.groupValues[4],
                nextPrefix = indent + (currentNumber + 1) + numberedMatch.groupValues[3],
                outdentPrefix =
                    if (outdentNumber != null) {
                        "$outdentNumber${numberedMatch.groupValues[3]}"
                    } else {
                        null
                    },
                outdentTopLevelNumber = outdentNumber,
            )
        }

        val bulletMatch = MarkdownBulletContinuationRegex.matchEntire(line)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1]
            val marker = bulletMatch.groupValues[2]
            return ContinuationPrefix(
                content = bulletMatch.groupValues[3],
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
            sourceText
                .substring(0, lineStart.coerceIn(0, sourceText.length))
                .lineSequence()
                .mapNotNull { line ->
                    MarkdownTopLevelNumberedRegex
                        .matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.lastOrNull()
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
        var scanLineStart =
            sourceText.indexOf('\n', insertedLineStart).let { newlineIndex ->
                if (newlineIndex < 0) {
                    return sourceText
                }
                newlineIndex + 1
            }

        while (scanLineStart <= sourceText.length) {
            val scanLineEnd =
                sourceText.indexOf('\n', scanLineStart).let { newlineIndex ->
                    if (newlineIndex < 0) sourceText.length else newlineIndex
                }
            val line = sourceText.substring(scanLineStart, scanLineEnd)
            if (line.isBlank()) {
                break
            }

            val topLevelMatch = MarkdownTopLevelNumberedRegex.matchEntire(line)
            if (topLevelMatch != null) {
                val currentNumberText = topLevelMatch.groupValues[1]
                val nextNumberText = nextNumber.toString()
                val replacementStart = scanLineStart + lengthDelta
                updatedText =
                    updatedText.replaceRange(
                        startIndex = replacementStart,
                        endIndex = replacementStart + currentNumberText.length,
                        replacement = nextNumberText,
                    )
                lengthDelta += nextNumberText.length - currentNumberText.length
                nextNumber++
            } else if (!MarkdownNestedNumberedPrefixRegex.containsMatchIn(line)) {
                break
            }

            if (scanLineEnd == sourceText.length) {
                break
            }
            scanLineStart = scanLineEnd + 1
        }

        return updatedText
    }

    private fun TextFieldValue.withCompleteEmptyWrappersRemoved(): TextFieldValue {
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

    private fun isFormatActive(format: MarkdownInlineFormat): Boolean {
        val selection = textFieldValue.selection
        val selectionStart = selection.min.coerceIn(0, markdown.length)
        val selectionEnd = selection.max.coerceIn(selectionStart, markdown.length)

        if (isFormatActiveIn(markdown, selectionStart, selectionEnd, format)) {
            return true
        }
        // A collapsed cursor landing inside a bare run of asterisks - e.g. right after toggling a
        // second asterisk-based format while the cursor sat at another format's own closing marker
        // (bold "**word **", then toggling italic glues an empty "*"+"*" onto that close) - is
        // genuinely ambiguous from the raw characters alone: the very next keystroke resolves it
        // cleanly (indexOfAsteriskClosingMarker can then tell the runs apart), but until then the
        // run reads as closing one span rather than opening an empty nested one. Probe with a
        // placeholder character inserted at the cursor, mirroring what typing would resolve to, so
        // the toolbar doesn't flicker off in the gap.
        if (selectionStart == selectionEnd &&
            markdown.getOrNull(selectionStart - 1) == '*' &&
            markdown.getOrNull(selectionStart) == '*'
        ) {
            val probeText = markdown.substring(0, selectionStart) + '\u0000' + markdown.substring(selectionStart)
            return isFormatActiveIn(probeText, selectionStart, selectionStart, format)
        }
        return false
    }

    private fun isFormatActiveIn(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        format: MarkdownInlineFormat,
    ): Boolean {
        val spans = if (text == markdown) formatSpans() else formatSpans(text)
        return if (selectionStart == selectionEnd) {
            spans.any { span ->
                span.format == format && selectionStart in span.openEnd..span.closeStart
            }
        } else {
            spans.any { span ->
                span.format == format && (
                    (selectionStart >= span.openEnd && selectionEnd <= span.closeStart) ||
                        (selectionStart == span.openStart && selectionEnd == span.closeEnd)
                )
            }
        }
    }

    private fun adjustSelectionBoundaries(
        start: Int,
        end: Int,
    ): TextRange {
        val spans = formatSpans()

        var adjustedStart = start
        var adjustedEnd = end

        for (span in spans) {
            val openInside = span.openStart >= adjustedStart && span.openEnd <= adjustedEnd
            val closeInside = span.closeStart >= adjustedStart && span.closeEnd <= adjustedEnd

            if (closeInside && !openInside) {
                adjustedEnd = minOf(adjustedEnd, span.closeStart)
            } else if (openInside && !closeInside) {
                adjustedStart = maxOf(adjustedStart, span.openEnd)
            }
        }

        return TextRange(adjustedStart, maxOf(adjustedStart, adjustedEnd))
    }
}

private data class EditorLine(
    val start: Int,
    val end: Int,
    val text: String,
    val index: Int = 0,
)

private data class MarkdownLinkRange(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val url: String,
)

private data class NewlineInsertion(
    val index: Int,
)

internal enum class MarkdownInlineFormat {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    INLINE_CODE,
}

private data class MarkdownWrapperRange(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val format: MarkdownInlineFormat? = null,
)

private data class ContinuationPrefix(
    val content: String,
    val nextPrefix: String,
    val outdentPrefix: String? = null,
    val outdentTopLevelNumber: Int? = null,
)
