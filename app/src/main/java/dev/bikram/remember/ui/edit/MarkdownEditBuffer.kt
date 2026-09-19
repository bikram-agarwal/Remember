package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownHorizontalRuleLineRegex
import dev.bikram.remember.ui.common.MarkdownInlineKind
import dev.bikram.remember.ui.common.MarkdownLineSyntax
import dev.bikram.remember.ui.common.markdownLinkUrl
import dev.bikram.remember.ui.common.parseMarkdownDocument
import dev.bikram.remember.ui.common.parseMarkdownLine
import dev.bikram.remember.ui.common.parseMarkdownLines

private const val BODY_AUTO_FOCUS_MAX_CHARS = 280
private const val BODY_AUTO_FOCUS_MAX_PARAGRAPHS = 6

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
            parsedSpans = markdownFormatSpans(markdown)
            parsedMarkdown = markdown
        }
        return parsedSpans
    }

    private fun selectedBlockKinds(): List<MarkdownLineSyntax> = selectedLines().map { it.syntax }

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
        // Resolve spans only when a transform needs them, while textFieldValue is still previousValue.
        val newlineAdjustedValue =
            selectionAdjustedValue.withInlineWrapperEnterAdjusted(previousValue = previousValue, spans = ::formatSpans)
        val updatedValue =
            newlineAdjustedValue.withListContinuationApplied(previousValue = previousValue)
        val cleanedValue =
            if (cleanUpEmptyMarkdownWrappers && updatedValue == newlineAdjustedValue) {
                updatedValue.withLivePreviewDeletionApplied(previousValue = previousValue, spans = ::formatSpans)
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
            if (line.syntax.kind == MarkdownBlockKind.Heading && line.syntax.headingLevel == headingLevel) {
                line.content
            } else {
                prefix + line.content
            }
        }
    }

    fun applyBulletList() {
        val lines = selectedLines()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> line.syntax.kind == MarkdownBlockKind.Bullet && line.syntax.indent.isEmpty() }
        replaceSelectedLines { line ->
            (if (nested) "  - " else "- ") + line.content
        }
    }

    fun applyChecklist() {
        val lines = selectedLines()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> line.syntax.kind == MarkdownBlockKind.Checklist && line.syntax.indent.isEmpty() }
        replaceSelectedLines { line ->
            (if (nested) "  - [ ] " else "- [ ] ") + line.content
        }
    }

    fun applyNumberedList() {
        val lines = selectedLines().filter { it.syntax.kind != MarkdownBlockKind.Code && it.syntax.kind != MarkdownBlockKind.CodeFence }
        val numbers = lines.mapIndexed { index, line -> line.start to index + 1 }.toMap()
        val nested =
            lines.isNotEmpty() &&
                lines.all { line -> line.syntax.kind == MarkdownBlockKind.Numbered && line.syntax.indent.isEmpty() }
        replaceSelectedLines { line ->
            (if (nested) "  " else "") + "${numbers.getValue(line.start)}. " + line.content
        }
    }

    fun applyQuote() {
        replaceSelectedLines { line ->
            "> " + line.content
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
        val before = textFieldValue
        textFieldValue = textFieldValue.withSelectionSurrounded(open = open, close = close)
        textFieldValue = preserveMarkdownSelectionFormatting(before, textFieldValue, open)
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
            if (line.syntax.kind == MarkdownBlockKind.Code || line.syntax.kind == MarkdownBlockKind.CodeFence) return@forEach
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
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart = text.lineStartBefore(start)
        return parseMarkdownLines(text)
            .filter { it.start >= firstLineStart && it.start <= end }
            .map { syntax -> EditorLine(syntax.start, syntax.end, text.substring(syntax.start, syntax.end), syntax) }
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

    private fun isSentenceStartBefore(index: Int): Boolean {
        val lineStart = markdown.lineStartBefore(index)
        val currentLinePrefix = markdown.substring(lineStart, index)
        val prefix = parseMarkdownLine(currentLinePrefix)
        val prefixWithoutBlockMarker = if (prefix.hasEditablePrefix) currentLinePrefix.substring(prefix.contentStart) else currentLinePrefix
        if (prefixWithoutBlockMarker.isBlank()) {
            return true
        }
        val previousCharacter = prefixWithoutBlockMarker.trimEnd().lastOrNull() ?: return true
        return previousCharacter == '.' || previousCharacter == '!' || previousCharacter == '?'
    }

    private fun isCursorAfterEmptyHeadingPrefix(cursor: Int): Boolean {
        val lineStart = markdown.lineStartBefore(cursor)
        val prefix = parseMarkdownLine(markdown.substring(lineStart, cursor))
        return prefix.kind == MarkdownBlockKind.Heading && prefix.isEmptyBlock
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
        val spans = if (text == markdown) formatSpans() else markdownFormatSpans(text)
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
}

private data class EditorLine(
    val start: Int,
    val end: Int,
    val text: String,
    val syntax: MarkdownLineSyntax,
) {
    val content: String
        get() = if (syntax.hasEditablePrefix) text.substring(syntax.contentStart - start) else text
}

private data class MarkdownLinkRange(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val url: String,
)

internal enum class MarkdownInlineFormat {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    INLINE_CODE,
}
