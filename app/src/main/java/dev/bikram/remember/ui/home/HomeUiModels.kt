package dev.bikram.remember.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.components.NoteCardUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
sealed class HomeListItem {
    /**
     * Section header. [count] renders alongside the label when non-null.
     * [stableKey] disambiguates LazyColumn item keys when two headers share a label
     * (a tag literally named "Done" vs the pinned Done section, etc.).
     * [collapsible] = true asks the UI to render a chevron and treat following NoteRows
     * with matching [NoteRow.groupKey] as collapse-toggle targets.
     */
    data class Header(
        val label: String,
        val count: Int? = null,
        val stableKey: String = label,
        val collapsible: Boolean = true,
        @param:StringRes val labelRes: Int? = null,
    ) : HomeListItem()

    /**
     * [groupKey] disambiguates keys when the same note appears under multiple groups
     * (e.g. a note with tags ["work", "personal"] in GroupBy.TAG view), and is reused
     * by collapsible sections to gate visibility (e.g. groupKey="DONE" rows hide when
     * the Done section is collapsed).
     */
    data class NoteRow(
        val note: NoteWithItems,
        val card: NoteCardUiModel,
        val groupKey: String = "",
    ) : HomeListItem()
}

@Suppress("ktlint:standard:function-expression-body")
internal fun selectableVisibleNoteIds(
    displayedItems: List<HomeListItem>,
    collapsedSectionKeys: Set<String>,
): Set<Long> {
    return displayedItems
        .mapNotNull { item ->
            val noteRow = item as? HomeListItem.NoteRow
            noteRow?.card?.id?.takeIf { noteRow.groupKey !in collapsedSectionKeys }
        }.toSet()
}

@Immutable
data class HomeState(
    val loading: Boolean = true,
    val filter: NotesFilter = NotesFilter(),
    val items: PersistentList<HomeListItem> = persistentListOf(),
    val totalActive: Int = 0,
    val availableTags: PersistentList<String> = persistentListOf(),
    val viewOptions: ViewOptions = ViewOptions(),
    /** Ids of notes currently selected in bulk-action mode. Empty when not in selection mode. */
    val selectedIds: PersistentSet<Long> = persistentSetOf(),
    val inSelectionMode: Boolean = false,
    /**
     * True when at least one selected note is not pinned. Drives the bulk Pin button: disabled
     * when every selected note is already pinned (a no-op tap).
     */
    val canPinSelected: Boolean = false,
    /**
     * True when at least one selected note is not starred. Same enablement rule as
     * [canPinSelected] for the bulk Star button.
     */
    val canStarSelected: Boolean = false,
    /**
     * Archived notes that match the current search query + facet filters. Only non-empty while
     * [NotesFilter.text] is non-blank; drives the collapsible "Archive (N)" section on Home.
     */
    val archivedMatches: PersistentList<NoteWithItems> = persistentListOf(),
    /**
     * Trashed notes that match the current search query + facet filters. Only non-empty while
     * [NotesFilter.text] is non-blank; drives the collapsible "Trash (N)" section on Home.
     */
    val trashedMatches: PersistentList<NoteWithItems> = persistentListOf(),
)
