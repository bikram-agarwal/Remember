package dev.bikram.remember.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.data.matches
import dev.bikram.remember.ui.common.BulkUndoableAction
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeEvent {
    data class OpenNote(
        val note: NoteWithItems,
        val forceEdit: Boolean,
    ) : HomeEvent

    /**
     * Emitted after a bulk action (archive / trash / mark-done) completes. The screen
     * uses this to surface a snackbar with an Undo affordance. The repository layer
     * has already coalesced all row writes into a single Flow emission so the list
     * has reflowed by the time this event fires.
     */
    data class BulkActionPerformed(
        val action: BulkUndoableAction,
    ) : HomeEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
        private val viewOptionsPrefs: ViewOptionsPrefs,
    ) : ViewModel() {
        private val filter = MutableStateFlow(NotesFilter())
        private val selectedIds = MutableStateFlow(persistentSetOf<Long>())
        private val viewOptionsFlow = viewOptionsPrefs.state.distinctUntilChanged()
        private val _events = MutableSharedFlow<HomeEvent>()
        val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

        private val notesSource: Flow<List<NoteWithItems>> =
            filter
                .map { it.text.trim() }
                .distinctUntilChanged()
                .flatMapLatest { text ->
                    if (text.isBlank()) {
                        repository.observeActive()
                    } else {
                        repository.searchNotes(text)
                    }
                }

        private val archivedSearchSource: Flow<List<NoteWithItems>> =
            filter
                .map { it.text.trim() }
                .distinctUntilChanged()
                .flatMapLatest { text ->
                    if (text.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        repository.searchArchivedNotes(text)
                    }
                }

        private val trashedSearchSource: Flow<List<NoteWithItems>> =
            filter
                .map { it.text.trim() }
                .distinctUntilChanged()
                .flatMapLatest { text ->
                    if (text.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        repository.searchTrashedNotes(text)
                    }
                }

        private val allActiveNotes: Flow<List<NoteWithItems>> = repository.observeActive()

        val state: StateFlow<HomeState> =
            combine(
                listOf(
                    filter,
                    notesSource,
                    allActiveNotes,
                    viewOptionsFlow,
                    selectedIds,
                    archivedSearchSource,
                    trashedSearchSource,
                ),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val currentFilter = values[0] as NotesFilter

                @Suppress("UNCHECKED_CAST")
                val searchResults = values[1] as List<NoteWithItems>

                @Suppress("UNCHECKED_CAST")
                val allActive = values[2] as List<NoteWithItems>

                val viewOptions = values[3] as ViewOptions

                @Suppress("UNCHECKED_CAST")
                val selected = values[4] as Set<Long>

                @Suppress("UNCHECKED_CAST")
                val archivedSearch = values[5] as List<NoteWithItems>

                @Suppress("UNCHECKED_CAST")
                val trashedSearch = values[6] as List<NoteWithItems>

                val filtered =
                    if (currentFilter.text.isBlank()) {
                        searchResults.filter { currentFilter.matches(it) }
                    } else {
                        val facetOnly = currentFilter.copy(text = "")
                        searchResults.filter { facetOnly.matches(it) }
                    }
                val facetOnly = currentFilter.copy(text = "")
                val filteredArchived =
                    if (currentFilter.text.isBlank()) {
                        emptyList()
                    } else {
                        archivedSearch.filter { facetOnly.matches(it) }
                    }
                val filteredTrashed =
                    if (currentFilter.text.isBlank()) {
                        emptyList()
                    } else {
                        trashedSearch.filter { facetOnly.matches(it) }
                    }
                val tags =
                    allActive
                        .flatMap { noteWithItems -> RememberReservedTags.userVisibleTags(noteWithItems.note.tags) }
                        .distinct()
                        .sorted()
                val arrangedItems = arrangeItems(filtered, viewOptions)
                val visibleIds =
                    arrangedItems
                        .mapNotNull { item -> (item as? HomeListItem.NoteRow)?.card?.id }
                        .toSet()
                val prunedSelection = selected.intersect(visibleIds).toPersistentSet()
                HomeState(
                    loading = false,
                    filter = currentFilter,
                    items = arrangedItems.toPersistentList(),
                    totalActive = allActive.size,
                    availableTags = tags.toPersistentList(),
                    viewOptions = viewOptions,
                    selectedIds = prunedSelection,
                    inSelectionMode = prunedSelection.isNotEmpty(),
                    archivedMatches = filteredArchived.toPersistentList(),
                    trashedMatches = filteredTrashed.toPersistentList(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

        fun setFilter(value: NotesFilter) {
            filter.value = value
        }

        fun setQuery(value: String) {
            filter.value = filter.value.copy(text = value)
        }

        fun setViewOptions(value: ViewOptions) {
            viewModelScope.launch { viewOptionsPrefs.setViewOptions(value) }
        }

        fun toggleSelection(noteId: Long) {
            selectedIds.value =
                if (noteId in selectedIds.value) {
                    selectedIds.value.remove(noteId)
                } else {
                    selectedIds.value.add(noteId)
                }
        }

        fun selectNotes(noteIds: Set<Long>) {
            selectedIds.value = noteIds.toPersistentSet()
        }

        fun clearSelection() {
            selectedIds.value = persistentSetOf()
        }

        fun openNote(note: NoteWithItems) {
            viewModelScope.launch {
                _events.emit(HomeEvent.OpenNote(note = note, forceEdit = false))
            }
        }

        fun handleSwipeAction(
            note: NoteWithItems,
            action: NoteSwipeAction,
        ) {
            val id = note.note.id
            viewModelScope.launch {
                when (action) {
                    NoteSwipeAction.EDIT -> _events.emit(HomeEvent.OpenNote(note = note, forceEdit = true))
                    NoteSwipeAction.TRASH -> {
                        repository.moveToTrash(id)
                        emitSingleSwipeAction(BulkUndoableAction.Trashed(setOf(id)))
                    }
                    // Duplicate is additive: a new card appears in the list. The visible
                    // new row is its own confirmation; no snackbar to avoid noise.
                    NoteSwipeAction.DUPLICATE -> repository.duplicateNote(id)
                    // Favorite toggles the heart icon in place. The state change is
                    // already visible on the card so an Undo snackbar would be redundant.
                    NoteSwipeAction.TOGGLE_FAVORITE ->
                        repository.setFavorite(id, !note.note.favorite)
                    NoteSwipeAction.ARCHIVE -> {
                        repository.archiveNote(id)
                        emitSingleSwipeAction(BulkUndoableAction.Archived(setOf(id)))
                    }
                    // The MARK_DONE swipe is a toggle: completed -> incomplete,
                    // incomplete -> completed. Recurrence-aware on the complete path
                    // (rolls reminder forward instead of going to Done). We surface a
                    // snackbar with Undo only on the FORWARD direction (incomplete ->
                    // complete); the reverse is the user's own undo so it stays silent.
                    NoteSwipeAction.MARK_DONE -> {
                        if (note.note.completedAt != null) {
                            repository.markIncomplete(id)
                        } else {
                            repository.markCompleted(id)
                            emitSingleSwipeAction(BulkUndoableAction.MarkedDone(setOf(id)))
                        }
                    }
                }
            }
        }

        /**
         * Records [action] as the most-recent undoable action and notifies the screen
         * so it can show a snackbar with an Undo button. Used by the single-card
         * swipe-action path to share the same snackbar plumbing as bulk-selection
         * mode; both paths route through [HomeEvent.BulkActionPerformed] and rely on
         * [undoLastBulkAction] for reversal.
         */
        private suspend fun emitSingleSwipeAction(action: BulkUndoableAction) {
            lastBulkAction = action
            _events.emit(HomeEvent.BulkActionPerformed(action))
        }

        /**
         * Most recent bulk action performed from selection mode. Held as nullable state so
         * [undoLastBulkAction] can dispatch the inverse repository call. Cleared after
         * undo or after the next bulk action replaces it.
         */
        @Volatile
        private var lastBulkAction: BulkUndoableAction? = null

        fun markSelectedDone() {
            val noteIds = selectedIds.value
            if (noteIds.isEmpty()) return
            // Filter out already-completed rows so undo only flips the rows we actually
            // touched. Done as a snapshot before the bulk call so we don't race with
            // the repository writes that follow.
            viewModelScope.launch {
                val toComplete =
                    noteIds.filter { id -> repository.get(id)?.note?.completedAt == null }.toSet()
                if (toComplete.isEmpty()) {
                    selectedIds.value = persistentSetOf()
                    return@launch
                }
                repository.markCompleted(toComplete)
                selectedIds.value = persistentSetOf()
                val action = BulkUndoableAction.MarkedDone(toComplete)
                lastBulkAction = action
                _events.emit(HomeEvent.BulkActionPerformed(action))
            }
        }

        fun archiveSelected() {
            val noteIds = selectedIds.value
            if (noteIds.isEmpty()) return
            val ids = noteIds.toSet()
            viewModelScope.launch {
                repository.archiveNotes(ids)
                selectedIds.value = persistentSetOf()
                val action = BulkUndoableAction.Archived(ids)
                lastBulkAction = action
                _events.emit(HomeEvent.BulkActionPerformed(action))
            }
        }

        fun trashSelected() {
            val noteIds = selectedIds.value
            if (noteIds.isEmpty()) return
            val ids = noteIds.toSet()
            viewModelScope.launch {
                repository.moveToTrash(ids)
                selectedIds.value = persistentSetOf()
                val action = BulkUndoableAction.Trashed(ids)
                lastBulkAction = action
                _events.emit(HomeEvent.BulkActionPerformed(action))
            }
        }

        /**
         * Reverses the most recent bulk action by issuing the inverse repository call.
         * No-op if there is nothing to undo (the snapshot was cleared, or the screen
         * has been recreated since the action). Only handles the actions originated
         * from this screen; permanent-delete is intentionally not undoable.
         */
        fun undoLastBulkAction() {
            val action = lastBulkAction ?: return
            lastBulkAction = null
            viewModelScope.launch {
                when (action) {
                    is BulkUndoableAction.Archived -> repository.unarchiveNotes(action.ids)
                    is BulkUndoableAction.Trashed -> repository.restoreFromTrash(action.ids)
                    is BulkUndoableAction.MarkedDone -> repository.markIncomplete(action.ids)
                    // The rest aren't produced by HomeViewModel, but exhaustiveness keeps
                    // the inverse mapping correct if a new variant is ever added.
                    is BulkUndoableAction.Restored -> repository.moveToTrash(action.ids)
                    is BulkUndoableAction.Unarchived -> repository.archiveNotes(action.ids)
                    is BulkUndoableAction.ArchivedFromTrash -> repository.moveToTrash(action.ids)
                    is BulkUndoableAction.MovedArchiveToTrash -> repository.archiveNotes(action.ids)
                }
            }
        }

        fun applyTagsToSelection(
            addTags: Set<String>,
            removeTags: Set<String>,
            newTagColors: Map<String, String> = emptyMap(),
        ) {
            val noteIds = selectedIds.value.toList()
            if (noteIds.isEmpty()) return
            val additions = addTags - removeTags
            viewModelScope.launch {
                newTagColors.forEach { (tagKey, hex) ->
                    repository.tagRepository?.setTagColor(tagKey, hex)
                }
                noteIds.forEach { noteId ->
                    val existing = repository.get(noteId) ?: return@forEach
                    val note = existing.note
                    val updatedTags = (note.tags.toSet() + additions - removeTags).toList()
                    if (updatedTags.toSet() == note.tags.toSet()) return@forEach
                    repository.updateNote(
                        id = noteId,
                        title = note.title,
                        body = note.body,
                        colorIndex = note.colorIndex,
                        options =
                            NoteOptions(
                                reminderAt = note.reminderAt,
                                importance = note.importance,
                                visibility = note.visibility,
                                pictureUri = note.pictureUri,
                                pictureHeroFraming = note.pictureHeroFraming,
                                locked = note.locked,
                                iconKey = note.iconKey,
                                actions = note.actions,
                                tags = updatedTags,
                                recurrence = note.recurrence,
                            ),
                    )
                }
                selectedIds.value = persistentSetOf()
            }
        }
    }
