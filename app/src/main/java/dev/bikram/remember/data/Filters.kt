package dev.bikram.remember.data

enum class FilterType { ALL, NOTE, LIST }

data class NotesFilter(
    val text: String = "",
    val type: FilterType = FilterType.ALL,
    val tags: Set<String> = emptySet(),
    val hasReminder: Boolean? = null,
    val hasPicture: Boolean? = null,
    val hasAttachment: Boolean? = null,
    val pinned: Boolean? = null,
) {
    /** True if any non-text facet is narrowing the results. */
    val facetActive: Boolean
        get() = type != FilterType.ALL || tags.isNotEmpty() ||
            hasReminder != null || hasPicture != null ||
            hasAttachment != null || pinned != null

    val active: Boolean
        get() = text.isNotBlank() || facetActive
}

fun NotesFilter.matches(n: NoteWithItems): Boolean {
    val note = n.note
    val typeOk = when (type) {
        FilterType.ALL -> true
        FilterType.NOTE -> note.kind == NoteKind.NOTE
        FilterType.LIST -> note.kind == NoteKind.LIST
    }
    if (!typeOk) return false
    if (tags.isNotEmpty() && !tags.all { t -> note.tags.any { it.equals(t, ignoreCase = true) } }) return false
    hasReminder?.let { if ((note.reminderAt != null) != it) return false }
    hasPicture?.let { if ((!note.pictureUri.isNullOrBlank()) != it) return false }
    hasAttachment?.let { if (n.attachments.isNotEmpty() != it) return false }
    pinned?.let { if (note.pinned != it) return false }
    if (text.isBlank()) return true
    val needle = text.trim().lowercase()
    if (note.title.lowercase().contains(needle)) return true
    if (note.body.lowercase().contains(needle)) return true
    if (note.tags.any { it.lowercase().contains(needle) }) return true
    return n.items.any { it.text.lowercase().contains(needle) }
}
