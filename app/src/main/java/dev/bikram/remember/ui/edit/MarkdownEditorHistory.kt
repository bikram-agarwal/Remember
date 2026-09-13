package dev.bikram.remember.ui.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.TextRange

private const val MARKDOWN_UNDO_LIMIT = 100
private const val TYPING_GROUP_NANOS = 750_000_000L

internal data class MarkdownEditorSnapshot(
    val text: String,
    val selection: TextRange,
    val formats: Set<MarkdownInlineFormat>,
)

internal enum class MarkdownEditKind { Insert, Delete, Replace, Command }

private data class MarkdownHistoryEntry(
    val before: MarkdownEditorSnapshot,
    val after: MarkdownEditorSnapshot,
    val kind: MarkdownEditKind,
)

@Stable
internal class MarkdownEditorHistory(
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val undoEntries = mutableStateListOf<MarkdownHistoryEntry>()
    private val redoEntries = mutableStateListOf<MarkdownHistoryEntry>()
    private var lastInputNanos: Long? = null

    val canUndo: Boolean get() = undoEntries.isNotEmpty()
    val canRedo: Boolean get() = redoEntries.isNotEmpty()

    fun record(
        before: MarkdownEditorSnapshot,
        after: MarkdownEditorSnapshot,
        kind: MarkdownEditKind,
    ) {
        if (before == after) return
        val now = nowNanos()
        val previous = undoEntries.lastOrNull()
        val elapsed = lastInputNanos?.let { now - it }
        val merge =
            kind in setOf(MarkdownEditKind.Insert, MarkdownEditKind.Delete) &&
                previous?.kind == kind &&
                previous.after == before &&
                elapsed != null && elapsed in 0..TYPING_GROUP_NANOS
        if (merge) {
            undoEntries[undoEntries.lastIndex] = previous.copy(after = after)
        } else {
            undoEntries.add(MarkdownHistoryEntry(before, after, kind))
            if (undoEntries.size > MARKDOWN_UNDO_LIMIT) undoEntries.removeAt(0)
        }
        redoEntries.clear()
        lastInputNanos = if (kind == MarkdownEditKind.Command || kind == MarkdownEditKind.Replace) null else now
    }

    fun undo(current: MarkdownEditorSnapshot): MarkdownEditorSnapshot? {
        if (undoEntries.isEmpty()) return null
        val entry = undoEntries.removeAt(undoEntries.lastIndex)
        redoEntries.add(entry.copy(after = current))
        breakTypingGroup()
        return entry.before
    }

    fun redo(current: MarkdownEditorSnapshot): MarkdownEditorSnapshot? {
        if (redoEntries.isEmpty()) return null
        val entry = redoEntries.removeAt(redoEntries.lastIndex)
        undoEntries.add(entry.copy(before = current))
        breakTypingGroup()
        return entry.after
    }

    fun clear() {
        undoEntries.clear()
        redoEntries.clear()
        breakTypingGroup()
    }

    fun breakTypingGroup() {
        lastInputNanos = null
    }
}

internal data class MarkdownChangedRange(
    val start: Int,
    val originalEnd: Int,
    val updatedEnd: Int,
)

internal fun markdownChangedRange(
    original: String,
    updated: String,
): MarkdownChangedRange {
    var start = 0
    while (start < original.length && start < updated.length && original[start] == updated[start]) start++
    // Do not split a surrogate pair when applying a minimal replacement.
    if (start > 0 && start < original.length && original[start].isLowSurrogate() && original[start - 1].isHighSurrogate()) start--
    var originalEnd = original.length
    var updatedEnd = updated.length
    while (originalEnd > start && updatedEnd > start && original[originalEnd - 1] == updated[updatedEnd - 1]) {
        originalEnd--
        updatedEnd--
    }
    if (originalEnd < original.length && originalEnd > start && original[originalEnd].isLowSurrogate() && original[originalEnd - 1].isHighSurrogate()) {
        originalEnd++
        updatedEnd++
    }
    return MarkdownChangedRange(start, originalEnd, updatedEnd)
}

internal fun markdownEditKind(
    before: MarkdownEditorSnapshot,
    after: MarkdownEditorSnapshot,
): MarkdownEditKind {
    if (!before.selection.collapsed || !after.selection.collapsed || before.formats != after.formats) return MarkdownEditKind.Replace
    val change = markdownChangedRange(before.text, after.text)
    val inserted = after.text.substring(change.start, change.updatedEnd)
    val removed = before.text.substring(change.start, change.originalEnd)
    return when {
        removed.isEmpty() && inserted.codePointCount(0, inserted.length) == 1 && inserted != "\n" &&
            change.start == before.selection.end -> MarkdownEditKind.Insert
        inserted.isEmpty() && removed.codePointCount(0, removed.length) == 1 && removed != "\n" &&
            change.originalEnd == before.selection.end -> MarkdownEditKind.Delete
        else -> MarkdownEditKind.Replace
    }
}
