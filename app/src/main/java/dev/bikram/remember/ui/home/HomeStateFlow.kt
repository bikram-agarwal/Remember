package dev.bikram.remember.ui.home

import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.matches
import dev.bikram.remember.ui.components.NoteCardUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal fun homeStateFlow(
    filter: Flow<NotesFilter>,
    notesSource: Flow<List<NoteWithItems>>,
    allActiveNotes: Flow<List<NoteWithItems>>,
    viewOptions: Flow<ViewOptions>,
    selectedIds: Flow<Set<Long>>,
    archivedSearchSource: Flow<List<NoteWithItems>>,
    trashedSearchSource: Flow<List<NoteWithItems>>,
    computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<HomeState> {
    // Tags depend on active notes only, not the current search, layout, or selection.
    val activeSummary =
        allActiveNotes
            .map { notes ->
                ActiveNotesSummary(
                    totalActive = notes.size,
                    availableTags =
                        notes
                            .asSequence()
                            .flatMap { RememberReservedTags.userVisibleTags(it.note.tags) }
                            .distinct()
                            .sorted()
                            .toList()
                            .toPersistentList(),
                )
            }.distinctUntilChanged()
            .flowOn(computationDispatcher)

    // Prepare list content and its selection lookup once per content change. Selection
    // updates downstream reuse these collections and never repeat filtering or sorting.
    val content =
        combine(
            filter,
            notesSource,
            viewOptions,
            archivedSearchSource,
            trashedSearchSource,
        ) { currentFilter, searchResults, options, archivedSearch, trashedSearch ->
            // Repository search already applies FTS, so only apply facets to its results.
            val facetOnly = currentFilter.copy(text = "")
            val filtered = searchResults.filter { facetOnly.matches(it) }
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
            val items = arrangeItems(filtered, options).toPersistentList()
            PreparedHomeContent(
                state =
                    HomeState(
                        loading = false,
                        filter = currentFilter,
                        items = items,
                        viewOptions = options,
                        archivedMatches = filteredArchived.toPersistentList(),
                        trashedMatches = filteredTrashed.toPersistentList(),
                    ),
                cardsById =
                    buildMap {
                        for (item in items) {
                            if (item is HomeListItem.NoteRow) put(item.card.id, item.card)
                        }
                    },
            )
        }.flowOn(computationDispatcher)

    return combine(content, activeSummary, selectedIds) { prepared, summary, selected ->
        val prunedSelection = selected.filter { it in prepared.cardsById }.toPersistentSet()
        var canPinSelected = false
        var canStarSelected = false
        for (noteId in prunedSelection) {
            val card = prepared.cardsById.getValue(noteId)
            if (!card.pinned) canPinSelected = true
            if (!card.starred) canStarSelected = true
            if (canPinSelected && canStarSelected) break
        }
        prepared.state.copy(
            totalActive = summary.totalActive,
            availableTags = summary.availableTags,
            selectedIds = prunedSelection,
            inSelectionMode = prunedSelection.isNotEmpty(),
            canPinSelected = canPinSelected,
            canStarSelected = canStarSelected,
        )
    }
}

private data class ActiveNotesSummary(
    val totalActive: Int,
    val availableTags: PersistentList<String>,
)

private data class PreparedHomeContent(
    val state: HomeState,
    val cardsById: Map<Long, NoteCardUiModel>,
)
