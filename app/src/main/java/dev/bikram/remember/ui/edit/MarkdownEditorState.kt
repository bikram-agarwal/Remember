@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownInlineProjection
import dev.bikram.remember.ui.common.parseMarkdownDocument

/**
 * The field owns the document. Input, toolbar commands and history operate on it immediately;
 * the persistence bridge only observes Markdown. TextFieldValue is a transient rules argument,
 * never a second stored editing value.
 */
@Stable
internal class MarkdownEditorState(
    initialMarkdown: String = "",
) {
    val textFieldState = TextFieldState(initialMarkdown)
    private val history = MarkdownEditorHistory()
    private var explicitFormatting by mutableStateOf<MarkdownEditorSnapshot?>(null)
    private val context by derivedStateOf { MarkdownEditBuffer(textFieldValue) }

    var focusRequestRevision by mutableIntStateOf(0)
        private set

    val markdown: String get() = textFieldState.text.toString()
    val textFieldValue: TextFieldValue get() = TextFieldValue(markdown, textFieldState.selection, textFieldState.composition)
    val hasSelection: Boolean get() = !textFieldState.selection.collapsed
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val headingLevel: Int get() = context.headingLevel
    val isBulletList: Boolean get() = context.isBulletList
    val isNumberedList: Boolean get() = context.isNumberedList
    val isChecklist: Boolean get() = context.isChecklist
    val isQuote: Boolean get() = context.isQuote
    val isCodeBlock: Boolean get() = context.isCodeBlock
    val selectedLinkUrl: String? get() = context.selectedLinkUrl
    val shouldCapitalizeNextInputInEmptyInlineWrapper: Boolean get() = context.shouldCapitalizeNextInputInEmptyInlineWrapper

    val inlineFormats: Set<MarkdownInlineFormat>
        get() {
            val explicit = explicitFormatting
            return if (explicit?.text == markdown && explicit.selection == textFieldState.selection) {
                explicit.formats
            } else {
                context.inlineFormats()
            }
        }

    val isBold: Boolean get() = MarkdownInlineFormat.BOLD in inlineFormats
    val isItalic: Boolean get() = MarkdownInlineFormat.ITALIC in inlineFormats
    val isUnderline: Boolean get() = MarkdownInlineFormat.UNDERLINE in inlineFormats
    val isStrikethrough: Boolean get() = MarkdownInlineFormat.STRIKETHROUGH in inlineFormats
    val isInlineCode: Boolean get() = MarkdownInlineFormat.INLINE_CODE in inlineFormats

    fun inputTransformation(livePreview: Boolean): InputTransformation {
        return InputTransformation {
            val previousValue = TextFieldValue(originalText.toString(), originalSelection)
            val before = snapshot(previousValue)
            val incoming = TextFieldValue(toString(), selection)
            val edited = MarkdownEditBuffer(previousValue)
            edited.update(incoming, livePreview, livePreview)
            if (livePreview) applyPendingFormats(before, edited)
            val after = inputSnapshot(before, incoming, edited)
            // Compose commits this buffer. Never call TextFieldState.edit from this callback.
            applyMarkdownValue(edited.textFieldValue)
            finishInput(before, after)
        }
    }

    /** Deterministic input entry point for non-IME callers and edit-sequence tests. */
    fun update(
        value: TextFieldValue,
        cleanUpEmptyMarkdownWrappers: Boolean = true,
        breakLineAfterHorizontalRule: Boolean = true,
    ) {
        val before = snapshot(textFieldValue)
        val edited = MarkdownEditBuffer(textFieldValue)
        edited.update(value, cleanUpEmptyMarkdownWrappers, breakLineAfterHorizontalRule)
        if (cleanUpEmptyMarkdownWrappers) applyPendingFormats(before, edited)
        val after = inputSnapshot(before, value, edited)
        Snapshot.withMutableSnapshot {
            textFieldState.edit { applyMarkdownValue(edited.textFieldValue) }
            finishInput(before, after)
            textFieldState.undoState.clearHistory()
        }
    }

    fun setMarkdown(
        value: String,
        moveCursorToEnd: Boolean = true,
    ) {
        val selection =
            if (moveCursorToEnd) {
                TextRange(value.length)
            } else {
                TextRange(
                    textFieldState.selection.start.coerceIn(0, value.length),
                    textFieldState.selection.end.coerceIn(0, value.length),
                )
            }
        Snapshot.withMutableSnapshot {
            textFieldState.edit { applyMarkdownValue(TextFieldValue(value, selection)) }
            explicitFormatting = null
            history.clear()
            textFieldState.undoState.clearHistory()
        }
    }

    fun undo() {
        val previous = history.undo(snapshot(textFieldValue)) ?: return
        restore(previous)
    }

    fun replaceMarkdown(value: String) {
        command { setMarkdown(value, moveCursorToEnd = false) }
    }

    fun redo() {
        val next = history.redo(snapshot(textFieldValue)) ?: return
        restore(next)
    }

    private fun restore(value: MarkdownEditorSnapshot) {
        Snapshot.withMutableSnapshot {
            textFieldState.edit { applyMarkdownValue(TextFieldValue(value.text, value.selection)) }
            explicitFormatting = value
            textFieldState.undoState.clearHistory()
        }
    }

    fun toggleBold() {
        toggleInline(MarkdownInlineFormat.BOLD) { toggleBold() }
    }

    fun toggleItalic() {
        toggleInline(MarkdownInlineFormat.ITALIC) { toggleItalic() }
    }

    fun toggleUnderline() {
        toggleInline(MarkdownInlineFormat.UNDERLINE) { toggleUnderline() }
    }

    fun toggleStrikethrough() {
        toggleInline(MarkdownInlineFormat.STRIKETHROUGH) { toggleStrikethrough() }
    }

    fun toggleInlineCode() {
        toggleInline(MarkdownInlineFormat.INLINE_CODE) { toggleInlineCode() }
    }

    fun applyCodeBlock() {
        command { applyCodeBlock() }
    }

    fun applyHeading(level: Int) {
        command { applyHeading(level) }
    }

    fun applyBulletList() {
        command { applyBulletList() }
    }

    fun applyChecklist() {
        command { applyChecklist() }
    }

    fun applyNumberedList() {
        command { applyNumberedList() }
    }

    fun applyQuote() {
        command { applyQuote() }
    }

    fun addOrUpdateLink(
        displayText: String,
        rawUrl: String,
    ) {
        command { addOrUpdateLink(displayText, rawUrl) }
    }

    fun selectedText(): String {
        return context.selectedText()
    }

    fun shouldAutoFocusBodyOnEdit(): Boolean {
        return context.shouldAutoFocusBodyOnEdit()
    }

    fun focusAtEndAndShowKeyboard() {
        focusRangeAndShowKeyboard(markdown.length, markdown.length)
    }

    fun focusAtOffsetAndShowKeyboard(markdownOffset: Int) {
        focusRangeAndShowKeyboard(markdownOffset, markdownOffset)
    }

    fun focusRangeAndShowKeyboard(
        startOffset: Int,
        endOffset: Int,
    ) {
        textFieldState.edit {
            selection = TextRange(startOffset.coerceIn(0, length), endOffset.coerceIn(0, length))
        }
        history.breakTypingGroup()
        focusRequestRevision++
    }

    private fun toggleInline(
        format: MarkdownInlineFormat,
        edit: MarkdownEditBuffer.() -> Unit,
    ) {
        val formats = inlineFormats
        if (textFieldState.selection.collapsed && format != MarkdownInlineFormat.INLINE_CODE) {
            val pending = snapshot(textFieldValue).pendingFormats
            val candidate = MarkdownEditBuffer(textFieldValue)
            candidate.edit()
            val before = MarkdownInlineProjection(markdown, parseMarkdownDocument(markdown).spans)
            val after = MarkdownInlineProjection(candidate.markdown, parseMarkdownDocument(candidate.markdown).spans)
            if (pending || before.text != after.text) {
                // Adjacent empty emphasis merges into ambiguous star runs. Keep the typing
                // intent in explicit state and apply its markers to the next inserted text.
                command(if (format in formats) formats - format else formats + format, pendingFormats = true) {}
                return
            }
        }
        command(if (format in formats) formats - format else formats + format, edit = edit)
    }

    private fun applyPendingFormats(
        before: MarkdownEditorSnapshot,
        edited: MarkdownEditBuffer,
    ) {
        if (!before.selection.collapsed || !before.pendingFormats) return
        val change = markdownChangedRange(before.text, edited.markdown)
        if (change.originalEnd != change.start || change.updatedEnd == change.start || '\n' in edited.markdown.substring(change.start, change.updatedEnd)) return
        edited.update(TextFieldValue(edited.markdown, TextRange(change.start, change.updatedEnd)), false, false)
        edited.update(preserveMarkdownSelectionFormatting(edited.textFieldValue, edited.textFieldValue, desiredFormats = before.formats), false, false)
        edited.update(TextFieldValue(edited.markdown, TextRange(edited.textFieldValue.selection.max)), false, false)
    }

    private fun command(
        formats: Set<MarkdownInlineFormat>? = null,
        pendingFormats: Boolean = false,
        edit: MarkdownEditBuffer.() -> Unit,
    ) {
        val before = snapshot(textFieldValue)
        val edited = MarkdownEditBuffer(textFieldValue)
        edited.edit()
        val after = MarkdownEditorSnapshot(edited.markdown, edited.textFieldValue.selection, formats ?: edited.inlineFormats(), pendingFormats)
        Snapshot.withMutableSnapshot {
            textFieldState.edit { applyMarkdownValue(edited.textFieldValue) }
            explicitFormatting = after
            history.record(before, after, MarkdownEditKind.Command)
            textFieldState.undoState.clearHistory()
        }
    }

    private fun snapshot(value: TextFieldValue): MarkdownEditorSnapshot {
        val explicit = explicitFormatting
        if (explicit?.text == value.text && explicit.selection == value.selection) return explicit
        return MarkdownEditorSnapshot(value.text, value.selection, MarkdownEditBuffer(value).inlineFormats())
    }

    private fun inputSnapshot(
        before: MarkdownEditorSnapshot,
        incoming: TextFieldValue,
        edited: MarkdownEditBuffer,
    ): MarkdownEditorSnapshot {
        val change = markdownChangedRange(before.text, incoming.text)
        val inserted = incoming.text.substring(change.start, change.updatedEnd)
        val removed = before.text.substring(change.start, change.originalEnd)
        val plainEdit =
            before.selection.collapsed &&
                incoming.selection.collapsed &&
                before.selection.start in change.start..change.originalEnd &&
                edited.markdown == incoming.text &&
                (inserted.isNotEmpty() || removed.isNotEmpty()) &&
                (inserted + removed).none { it in "*~\u0060<>\n" }
        val pendingInput = before.pendingFormats && inserted.isNotEmpty() && '\n' !in inserted
        return MarkdownEditorSnapshot(
            edited.markdown,
            edited.textFieldValue.selection,
            if (plainEdit || pendingInput) before.formats else edited.inlineFormats(),
            pendingInput,
        )
    }

    private fun finishInput(
        before: MarkdownEditorSnapshot,
        after: MarkdownEditorSnapshot,
    ) {
        explicitFormatting = after
        if (before.text != after.text) {
            history.record(before, after, markdownEditKind(before, after))
        } else if (before.selection != after.selection) {
            history.breakTypingGroup()
        }
    }
}

internal fun MarkdownEditBuffer.inlineFormats(): Set<MarkdownInlineFormat> {
    return buildSet {
        if (isBold) add(MarkdownInlineFormat.BOLD)
        if (isItalic) add(MarkdownInlineFormat.ITALIC)
        if (isUnderline) add(MarkdownInlineFormat.UNDERLINE)
        if (isStrikethrough) add(MarkdownInlineFormat.STRIKETHROUGH)
        if (isInlineCode) add(MarkdownInlineFormat.INLINE_CODE)
    }
}

/** Keep edits local so unchanged keyboard composition is preserved by the field's buffer. */
internal fun TextFieldBuffer.applyMarkdownValue(value: TextFieldValue) {
    val currentText = toString()
    if (currentText != value.text) {
        val change = markdownChangedRange(currentText, value.text)
        replace(change.start, change.originalEnd, value.text.substring(change.start, change.updatedEnd))
    }
    if (selection != value.selection) {
        selection = value.selection
    }
}
