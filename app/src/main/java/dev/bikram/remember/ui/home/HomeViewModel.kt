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
                        .mapNotNull { item -> (item as? HomeListItem.NoteRow)?.note?.note?.id }
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
            viewModelScope.launch {
                when (action) {
                    NoteSwipeAction.EDIT -> _events.emit(HomeEvent.OpenNote(note = note, forceEdit = true))
                    NoteSwipeAction.TRASH -> repository.moveToTrash(note.note.id)
                    NoteSwipeAction.DUPLICATE -> repository.duplicateNote(note.note.id)
                    NoteSwipeAction.TOGGLE_FAVORITE ->
                        repository.setFavorite(note.note.id, !note.note.favorite)
                    NoteSwipeAction.ARCHIVE -> repository.archiveNote(note.note.id)
                    // The MARK_DONE swipe is a toggle: completed -> incomplete,
                    // incomplete -> completed. Recurrence-aware on the complete path
                    // (rolls reminder forward instead of going to Done).
                    NoteSwipeAction.MARK_DONE -> {
                        if (note.note.completedAt != null) {
                            repository.markIncomplete(note.note.id)
                        } else {
                            repository.markCompleted(note.note.id)
                        }
                    }
                }
            }
        }

        fun markSelectedDone() {
            val noteIds = selectedIds.value.toList()
            if (noteIds.isEmpty()) return
            viewModelScope.launch {
                noteIds.forEach { noteId ->
                    val existing = repository.get(noteId)?.note ?: return@forEach
                    if (existing.completedAt == null) {
                        repository.markCompleted(noteId)
                    }
                }
                selectedIds.value = persistentSetOf()
            }
        }

        fun archiveSelected() {
            val noteIds = selectedIds.value.toList()
            if (noteIds.isEmpty()) return
            viewModelScope.launch {
                noteIds.forEach { noteId -> repository.archiveNote(noteId) }
                selectedIds.value = persistentSetOf()
            }
        }

        fun trashSelected() {
            val noteIds = selectedIds.value.toList()
            if (noteIds.isEmpty()) return
            viewModelScope.launch {
                noteIds.forEach { noteId -> repository.moveToTrash(noteId) }
                selectedIds.value = persistentSetOf()
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
