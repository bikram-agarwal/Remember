package dev.bikram.remember.ui.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
private val MarkdownQuotePrefixRegex = Regex("""^>\s*""")
private val MarkdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private val MarkdownChecklistContinuationRegex = Regex("""^(\s*)- \[[ xX]\]\s+(.*)$""")
private val MarkdownNumberedContinuationRegex = Regex("""^(\s*)(\d+)([.)]\s+)(.*)$""")
private val MarkdownBulletContinuationRegex = Regex("""^(\s*)([-*+]\s+)(.*)$""")
private val MarkdownTopLevelNumberedRegex = Regex("""^(\d+)[.)]\s+.*$""")
private val MarkdownEmptyInlineWrappers =
    listOf(
        "**" to "**",
        "<u>" to "</u>",
        "~~" to "~~",
        "`" to "`",
        "*" to "*",
    )
private val MarkdownEmptyFormattingWrappers = MarkdownEmptyInlineWrappers + ("```\n" to "\n```")
private val MarkdownEmptyHeadingPrefixRegex = Regex("""^#{1,3}\s+$""")
private val MarkdownEmptyBlockPrefixRegex = Regex("""^(#{1,3}\s+|\s*- \[[ xX]\]\s+|\s*\d+[.)]\s+|\s*[-*+]\s+|>\s*)$""")
private val MarkdownPartialBlockPrefixRegex = Regex("""^\s*(#{1,3}|- \[[ xX]?\]?|\d+[.)]?|[-*+]|>)\s*$""")
private const val BODY_AUTO_FOCUS_MAX_CHARS = 280
private const val BODY_AUTO_FOCUS_MAX_PARAGRAPHS = 6

@Stable
internal class MarkdownEditorState(
    initialMarkdown: String = "",
) {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = initialMarkdown,
            selection = TextRange(initialMarkdown.length),
        ),
    )
        private set

    var selectionRevision by mutableIntStateOf(0)
        private set

    var focusRequestRevision by mutableIntStateOf(0)
        private set

    val markdown: String
        get() = textFieldValue.text

    val hasSelection: Boolean
        get() = !textFieldValue.selection.collapsed

    val headingLevel: Int
        get() {
            val lines = selectedLines()
            return lines
                .firstNotNullOfOrNull { line ->
                    MarkdownHeadingPrefixRegex
                        .find(line.text)
                        ?.value
                        ?.takeIf { it.isNotBlank() }
                        ?.count { character -> character == '#' }
                }
                ?: 0
        }

    val isBulletList: Boolean
        get() {
            val lines = selectedLines()
            return lines.isNotEmpty() &&
                lines.all { line ->
                    MarkdownBulletPrefixRegex.containsMatchIn(line.text) &&
                        !MarkdownChecklistPrefixRegex.containsMatchIn(line.text)
                }
        }

    val isChecklist: Boolean
        get() {
            val lines = selectedLines()
            return lines.isNotEmpty() &&
                lines.all { line ->
                    MarkdownChecklistPrefixRegex.containsMatchIn(line.text)
                }
        }

    val isNumberedList: Boolean
        get() {
            val lines = selectedLines()
            return lines.isNotEmpty() &&
                lines.all { line ->
                    MarkdownNumberedPrefixRegex.containsMatchIn(line.text)
                }
        }

    val isQuote: Boolean
        get() {
            val lines = selectedLines()
            return lines.isNotEmpty() &&
                lines.all { line ->
                    MarkdownQuotePrefixRegex.containsMatchIn(line.text)
                }
        }

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
    ) {
        val previousValue = textFieldValue
        val newlineAdjustedValue =
            value.withInlineWrapperEnterAdjusted(previousValue = previousValue)
        val updatedValue =
            newlineAdjustedValue.withListContinuationApplied(previousValue = previousValue)
        textFieldValue =
            if (cleanUpEmptyMarkdownWrappers && updatedValue == newlineAdjustedValue) {
                updatedValue.withEmptyMarkdownWrapperRemoved(previousValue = previousValue)
            } else {
                updatedValue
            }
        selectionRevision++
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
        selectionRevision++
    }

    fun focusAtEndAndShowKeyboard() {
        textFieldValue = textFieldValue.copy(selection = TextRange(markdown.length))
        selectionRevision++
        focusRequestRevision++
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
        selectionRevision++
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
        val normalizedUrl = rawUrl.withHttpScheme()
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
        selectionRevision++
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
        val start = selection.min.coerceIn(0, markdown.length)
        val end = selection.max.coerceIn(start, markdown.length)
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
            selectionRevision++
            return
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
            selectionRevision++
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
        selectionRevision++
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
            markdown.getOrNull(openEnd) != '*' &&
            markdown.getOrNull(closeStart - 1) != '*' &&
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
        selectionRevision++
    }

    private fun selectedLines(): List<EditorLine> {
        val text = markdown
        if (text.isEmpty()) {
            return listOf(EditorLine(start = 0, end = 0, text = ""))
        }

        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart =
            text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
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

        MarkdownEmptyInlineWrappers.forEach { (open, close) ->
            if (!previousValue.text.startsWith(close, previousCursor)) {
                return@forEach
            }
            val lineStart =
                previousValue.text.lastIndexOf('\n', (previousCursor - 1).coerceAtLeast(0)).let { lineBreakIndex ->
                    if (lineBreakIndex < 0) 0 else lineBreakIndex + 1
                }
            if (previousValue.text.substring(lineStart, previousCursor).lastIndexOf(open) < 0) {
                return@forEach
            }

            val adjustedNewlineIndex = previousCursor + close.length
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

    private fun TextFieldValue.withEmptyMarkdownWrapperRemoved(previousValue: TextFieldValue): TextFieldValue {
        if (text.length >= previousValue.text.length || !selection.collapsed) {
            return this
        }
        val cursor = selection.start.coerceIn(0, text.length)
        val previousSelection = previousValue.selection
        if (previousSelection.collapsed) {
            val removedCharacterCount = previousValue.text.length - text.length
            val previousCursor = previousSelection.start.coerceIn(0, previousValue.text.length)
            MarkdownEmptyInlineWrappers.forEach { (open, close) ->
                var closeStart = previousValue.text.indexOf(close)
                while (closeStart >= 0) {
                    val closeEnd = closeStart + close.length
                    val deletedInsideCloseMarker = previousCursor in (closeStart + 1)..closeEnd
                    val deletedSingleMarkerCharacter =
                        removedCharacterCount == 1 &&
                            previousCursor > 0 &&
                            text == previousValue.text.removeRange(previousCursor - 1, previousCursor)
                    if (deletedInsideCloseMarker && deletedSingleMarkerCharacter) {
                        val lineStart =
                            previousValue.text.lastIndexOf('\n', (closeStart - 1).coerceAtLeast(0)).let { newlineIndex ->
                                if (newlineIndex < 0) 0 else newlineIndex + 1
                            }
                        val openStart = previousValue.text.lastIndexOf(open, closeStart - 1)
                        val openEnd = openStart + open.length
                        if (openStart >= lineStart && openEnd < closeStart) {
                            val updatedText =
                                if (openEnd == closeStart) {
                                    previousValue.text.removeRange(openStart, closeEnd)
                                } else {
                                    previousValue.text.removeRange(closeStart - 1, closeStart)
                                }
                            val updatedSelection =
                                if (openEnd == closeStart) {
                                    openStart
                                } else {
                                    closeStart - 1
                                }
                            return copy(
                                text = updatedText,
                                selection = TextRange(updatedSelection.coerceIn(0, updatedText.length)),
                                composition = null,
                            )
                        }
                    }
                    closeStart = previousValue.text.indexOf(close, closeStart + 1)
                }
            }
            MarkdownEmptyFormattingWrappers.forEach { (open, close) ->
                val wrapperStart = previousCursor - open.length
                val wrapperEnd = previousCursor + close.length
                if (
                    wrapperStart >= 0 &&
                    wrapperEnd <= previousValue.text.length &&
                    previousValue.text.substring(wrapperStart, previousCursor) == open &&
                    previousValue.text.substring(previousCursor, wrapperEnd) == close
                ) {
                    val updatedWrapperStart = wrapperStart.coerceIn(0, text.length)
                    val updatedWrapperEnd = (wrapperEnd - removedCharacterCount).coerceIn(updatedWrapperStart, text.length)
                    val updatedText = text.removeRange(updatedWrapperStart, updatedWrapperEnd)
                    return copy(
                        text = updatedText,
                        selection = TextRange(updatedWrapperStart.coerceIn(0, updatedText.length)),
                        composition = null,
                    )
                }
            }

            val previousLineStart =
                previousValue.text.lastIndexOf('\n', (previousCursor - 1).coerceAtLeast(0)).let { newlineIndex ->
                    if (newlineIndex < 0) 0 else newlineIndex + 1
                }
            val previousLineEnd =
                previousValue.text.indexOf('\n', previousCursor).let { newlineIndex ->
                    if (newlineIndex < 0) previousValue.text.length else newlineIndex
                }
            val previousLine = previousValue.text.substring(previousLineStart, previousLineEnd)
            val currentLineStart =
                text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { newlineIndex ->
                    if (newlineIndex < 0) 0 else newlineIndex + 1
                }
            val currentLineEnd =
                text.indexOf('\n', cursor).let { newlineIndex ->
                    if (newlineIndex < 0) text.length else newlineIndex
                }
            val currentLine = text.substring(currentLineStart, currentLineEnd)
            if (
                MarkdownEmptyBlockPrefixRegex.matches(previousLine) &&
                MarkdownPartialBlockPrefixRegex.matches(currentLine) &&
                !MarkdownEmptyBlockPrefixRegex.matches(currentLine)
            ) {
                val removeStart = if (currentLineStart > 0) currentLineStart - 1 else currentLineStart
                val removeEnd =
                    if (currentLineStart == 0 && currentLineEnd < text.length) {
                        currentLineEnd + 1
                    } else {
                        currentLineEnd
                    }
                val updatedText = text.removeRange(removeStart, removeEnd)
                return copy(
                    text = updatedText,
                    selection = TextRange(removeStart.coerceIn(0, updatedText.length)),
                    composition = null,
                )
            }
        }
        MarkdownEmptyInlineWrappers.forEach { (open, close) ->
            val wrapperStart = cursor - open.length
            val wrapperEnd = cursor + close.length
            if (
                wrapperStart >= 0 &&
                wrapperEnd <= text.length &&
                text.substring(wrapperStart, cursor) == open &&
                text.substring(cursor, wrapperEnd) == close
            ) {
                val updatedText = text.removeRange(wrapperStart, wrapperEnd)
                return copy(
                    text = updatedText,
                    selection = TextRange(wrapperStart.coerceIn(0, updatedText.length)),
                    composition = null,
                )
            }
        }
        val lineStart =
            text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        val lineEnd =
            text.indexOf('\n', cursor).let { newlineIndex ->
                if (newlineIndex < 0) text.length else newlineIndex
            }
        val line = text.substring(lineStart, lineEnd)
        if (MarkdownEmptyHeadingPrefixRegex.matches(line)) {
            val updatedText = text.removeRange(lineStart, lineEnd)
            return copy(
                text = updatedText,
                selection = TextRange(lineStart.coerceIn(0, updatedText.length)),
                composition = null,
            )
        }
        return this
    }

    private fun isSentenceStartBefore(index: Int): Boolean {
        val lineStart =
            markdown.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        val currentLinePrefix = markdown.substring(lineStart, index)
        val prefixWithoutBlockMarker = currentLinePrefix.replaceFirst(MarkdownBlockPrefixRegex, "")
        if (prefixWithoutBlockMarker.isBlank()) {
            return true
        }
        val previousCharacter = prefixWithoutBlockMarker.trimEnd().lastOrNull() ?: return true
        return previousCharacter == '.' || previousCharacter == '!' || previousCharacter == '?'
    }

    private fun isCursorAfterEmptyHeadingPrefix(cursor: Int): Boolean {
        val lineStart =
            markdown.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { newlineIndex ->
                if (newlineIndex < 0) 0 else newlineIndex + 1
            }
        return MarkdownEmptyHeadingPrefixRegex.matches(markdown.substring(lineStart, cursor))
    }

    private fun markdownLinkAtSelection(): MarkdownLinkRange? {
        val selection = textFieldValue.selection
        val selectionStart = selection.min.coerceIn(0, markdown.length)
        val selectionEnd = selection.max.coerceIn(selectionStart, markdown.length)
        return MarkdownLinkRegex
            .findAll(markdown)
            .firstOrNull { match ->
                val matchStart = match.range.first
                val matchEndExclusive = match.range.last + 1
                if (selectionStart == selectionEnd) {
                    selectionStart in matchStart..matchEndExclusive
                } else {
                    selectionStart < matchEndExclusive && selectionEnd > matchStart
                }
            }?.let { match ->
                MarkdownLinkRange(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    text = match.groupValues[1],
                    url = match.groupValues[2],
                )
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
        selectionRevision++
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

private data class MarkdownWrapperRange(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
)

private data class ContinuationPrefix(
    val content: String,
    val nextPrefix: String,
    val outdentPrefix: String? = null,
    val outdentTopLevelNumber: Int? = null,
)

private fun String.withHttpScheme(): String {
    if (startsWith("http://") || startsWith("https://")) {
        return this
    }
    return "https://$this"
}
