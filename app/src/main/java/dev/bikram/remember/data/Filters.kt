package dev.bikram.remember.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

enum class FilterType { ALL, NOTE, LIST }

@Immutable
data class NotesFilter(
    val text: String = "",
    val type: FilterType = FilterType.ALL,
    val tags: PersistentSet<String> = persistentSetOf(),
    val hasReminder: Boolean? = null,
    val hasPicture: Boolean? = null,
    val hasAttachment: Boolean? = null,
    val starred: Boolean? = null,
    /**
     * Filtering is the one thing that can hide a pinned note from Home (grouping and sorting
     * cannot), so this facet is also the way to see *only* the pinned ones.
     */
    val pinned: Boolean? = null,
) {
    /** True if any non-text facet is narrowing the results. */
    val facetActive: Boolean
        get() =
            type != FilterType.ALL ||
                tags.isNotEmpty() ||
                hasReminder != null ||
                hasPicture != null ||
                hasAttachment != null ||
                starred != null ||
                pinned != null

    val active: Boolean
        get() = text.isNotBlank() || facetActive
}

fun NotesFilter.matches(n: NoteWithItems): Boolean {
    val note = n.note
    val visibleTags = RememberReservedTags.userVisibleTags(note.tags)
    val typeOk =
        when (type) {
            FilterType.ALL -> true
            FilterType.NOTE -> note.kind == NoteKind.NOTE
            FilterType.LIST -> note.kind == NoteKind.LIST
        }
    if (!typeOk) return false
    if (tags.isNotEmpty() &&
        !tags.all { filterTag ->
            visibleTags.any { it.equals(filterTag, ignoreCase = true) }
        }
    ) {
        return false
    }
    hasReminder?.let { if ((note.reminderAt != null) != it) return false }
    hasPicture?.let { if ((!note.pictureUri.isNullOrBlank()) != it) return false }
    hasAttachment?.let { if (n.attachments.isNotEmpty() != it) return false }
    starred?.let { if (note.starred != it) return false }
    pinned?.let { if (note.pinned != it) return false }
    if (text.isBlank()) return true
    val needle = text.trim().lowercase()
    if (note.title.lowercase().contains(needle)) return true
    if (note.body.lowercase().contains(needle)) return true
    if (visibleTags.any { it.lowercase().contains(needle) }) return true
    if (n.items.any { it.text.lowercase().contains(needle) }) return true
    if (n.attachments.any { it.displayName.lowercase().contains(needle) }) return true
    return n.note.actions.any { action ->
        action.title.lowercase().contains(needle) ||
            action.details.lowercase().contains(needle)
    }
}
