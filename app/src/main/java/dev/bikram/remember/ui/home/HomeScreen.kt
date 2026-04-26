package dev.bikram.remember.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.data.matches
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.EmptyFilterIllustration
import dev.bikram.remember.ui.components.EmptyNotesIllustration
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.rememberPlayTapSound

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
    data class NoteRow(val note: NoteWithItems, val groupKey: String = "") : HomeListItem()
}

/** Drives the M3 Expressive AnimatedContent that swaps the top-bar title. */
private enum class TopBarTitleTarget { Selection, Search, AppName }

data class HomeState(
    val loading: Boolean = true,
    val filter: NotesFilter = NotesFilter(),
    val items: List<HomeListItem> = emptyList(),
    val totalActive: Int = 0,
    val availableTags: List<String> = emptyList(),
    val viewOptions: ViewOptions = ViewOptions(),
    /** Ids of notes currently selected in bulk-action mode. Empty when not in selection mode. */
    val selectedIds: Set<Long> = emptySet(),
    val inSelectionMode: Boolean = false,
    /**
     * Archived notes that match the current search query + facet filters. Only non-empty while
     * [NotesFilter.text] is non-blank; drives the collapsible "Archive (N)" section on Home.
     */
    val archivedMatches: List<NoteWithItems> = emptyList(),
    /**
     * Trashed notes that match the current search query + facet filters. Only non-empty while
     * [NotesFilter.text] is non-blank; drives the collapsible "Trash (N)" section on Home.
     */
    val trashedMatches: List<NoteWithItems> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val viewOptionsPrefs: ViewOptionsPrefs,
) : ViewModel() {
    private val filter = MutableStateFlow(NotesFilter())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val viewOptionsFlow = viewOptionsPrefs.state
        .distinctUntilChanged()

    /**
     * When the search text is non-blank we push the query down to SQLite via Room FTS4
     * rather than filtering in-memory. This keeps the main list responsive on large vaults
     * and gives diacritic-insensitive, prefix-matched results. Facet filters (tags,
     * reminder, etc.) still apply on top of the FTS result set.
     */
    private val notesSource: Flow<List<NoteWithItems>> = filter
        .map { it.text.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) repository.observeActive()
            else repository.searchNotes(text)
        }

    /**
     * Archived notes matching the current query. Empty when search text is blank so the
     * "Archive (N)" section only appears while the user is actively searching.
     */
    private val archivedSearchSource: Flow<List<NoteWithItems>> = filter
        .map { it.text.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList())
            else repository.searchArchivedNotes(text)
        }

    /**
     * Trashed notes matching the current query. Same "only while searching" gate as
     * [archivedSearchSource]; drives the "Trash (N)" collapsed section on Home.
     */
    private val trashedSearchSource: Flow<List<NoteWithItems>> = filter
        .map { it.text.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList())
            else repository.searchTrashedNotes(text)
        }

    /**
     * Tag suggestions always come from the full active set even while searching, so the
     * filter sheet's tag chips don't collapse to only the currently-matched notes.
     */
    private val allActiveNotes: Flow<List<NoteWithItems>> = repository.observeActive()

    val state: StateFlow<HomeState> = combine(
        listOf(
            filter,
            notesSource,
            allActiveNotes,
            viewOptionsFlow,
            selectedIds,
            archivedSearchSource,
            trashedSearchSource,
        ),
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val f = arr[0] as NotesFilter
        @Suppress("UNCHECKED_CAST")
        val searchResults = arr[1] as List<NoteWithItems>
        @Suppress("UNCHECKED_CAST")
        val allActive = arr[2] as List<NoteWithItems>
        val opts = arr[3] as ViewOptions
        @Suppress("UNCHECKED_CAST")
        val selected = arr[4] as Set<Long>
        @Suppress("UNCHECKED_CAST")
        val archivedSearch = arr[5] as List<NoteWithItems>
        @Suppress("UNCHECKED_CAST")
        val trashedSearch = arr[6] as List<NoteWithItems>
        // When FTS is active, skip the in-memory text check (SQL already did it) but keep
        // facet filters; when not searching, the normal full predicate runs.
        val filtered = if (f.text.isBlank()) {
            searchResults.filter { f.matches(it) }
        } else {
            val facetOnly = f.copy(text = "")
            searchResults.filter { facetOnly.matches(it) }
        }
        // Archive and trash results always get facet-only filtering - the FTS query has
        // already matched text, and running the full matcher would double-check text
        // against in-memory fields that may differ in edge cases.
        val facetOnly = f.copy(text = "")
        val filteredArchived =
            if (f.text.isBlank()) emptyList()
            else archivedSearch.filter { facetOnly.matches(it) }
        val filteredTrashed =
            if (f.text.isBlank()) emptyList()
            else trashedSearch.filter { facetOnly.matches(it) }
        val tags = allActive
            .flatMap { RememberReservedTags.userVisibleTags(it.note.tags) }
            .distinct()
            .sorted()
        val arranged = arrangeItems(filtered, opts)
        // Prune selections whose underlying note no longer appears (trashed, archived, etc.).
        val visibleIds = arranged.mapNotNull { (it as? HomeListItem.NoteRow)?.note?.note?.id }.toSet()
        val prunedSelection = selected.intersect(visibleIds)
        HomeState(
            loading = false,
            filter = f,
            items = arranged,
            totalActive = allActive.size,
            availableTags = tags,
            viewOptions = opts,
            selectedIds = prunedSelection,
            inSelectionMode = prunedSelection.isNotEmpty(),
            archivedMatches = filteredArchived,
            trashedMatches = filteredTrashed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setFilter(v: NotesFilter) { filter.value = v }
    fun setQuery(v: String) { filter.value = filter.value.copy(text = v) }

    fun setViewOptions(v: ViewOptions) {
        viewModelScope.launch { viewOptionsPrefs.setViewOptions(v) }
    }

    fun toggleSelection(noteId: Long) {
        selectedIds.value = selectedIds.value.toMutableSet().also {
            if (!it.add(noteId)) it.remove(noteId)
        }
    }

    fun selectNotes(noteIds: Set<Long>) {
        selectedIds.value = noteIds
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun markSelectedDone() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { noteId ->
                val existing = repository.get(noteId)?.note ?: return@forEach
                if (existing.completedAt == null) repository.markCompleted(noteId)
            }
            selectedIds.value = emptySet()
        }
    }

    fun archiveSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.archiveNote(it) }
            selectedIds.value = emptySet()
        }
    }

    fun trashSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.moveToTrash(it) }
            selectedIds.value = emptySet()
        }
    }

    /**
     * Apply [addTags] to every selected note (union) and remove [removeTags] from every
     * selected note (difference). Passing a tag in both sets is resolved in favour of removal.
     */
    fun applyTagsToSelection(
        addTags: Set<String>,
        removeTags: Set<String>,
        newTagColors: Map<String, String> = emptyMap(),
    ) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        val additions = addTags - removeTags
        viewModelScope.launch {
            newTagColors.forEach { (tagKey, hex) ->
                repository.tagRepository?.setTagColor(tagKey, hex) ?: themePrefs.setTagColor(tagKey, hex)
            }
            ids.forEach { noteId ->
                val existing = repository.get(noteId) ?: return@forEach
                val note = existing.note
                val updated = (note.tags.toSet() + additions - removeTags).toList()
                if (updated.toSet() == note.tags.toSet()) return@forEach
                repository.updateNote(
                    id = noteId,
                    title = note.title,
                    body = note.body,
                    colorIndex = note.colorIndex,
                    options = dev.bikram.remember.data.NoteOptions(
                        reminderAt = note.reminderAt,
                        importance = note.importance,
                        visibility = note.visibility,
                        pictureUri = note.pictureUri,
                        pictureHeroFraming = note.pictureHeroFraming,
                        locked = note.locked,
                        iconKey = note.iconKey,
                        actions = note.actions,
                        tags = updated,
                        recurrence = note.recurrence,
                    ),
                )
            }
            selectedIds.value = emptySet()
        }
    }

    companion object {
        fun factory(
            repository: NoteRepository,
            themePrefs: ThemePrefs,
            viewOptionsPrefs: ViewOptionsPrefs,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository, themePrefs, viewOptionsPrefs) as T
            }
    }
}

/**
 * Lay out the home list with the task-first section model:
 *
 *   [Overdue]            — always pinned at top when non-empty (active notes whose
 *                          reminder is in the past). Stronger signal than user sort.
 *   [middle]             — when sort = REMINDER asc: Today / Upcoming / No date
 *                          sub-sections. Otherwise: existing GroupBy (NONE/TAG/TYPE)
 *                          applied to the active middle.
 *   [Done] (collapsible) — always pinned at bottom when non-empty (notes with
 *                          completedAt != null). Per the task model, recurring
 *                          notes never enter Done while a future occurrence exists -
 *                          markCompleted in the repository rolls them forward instead.
 *
 * Pinned-first ordering is preserved within each bucket because [sortNotes] runs
 * before partitioning and already pushes pinned items to the top globally.
 *
 * Note kinds (NoteKind.NOTE vs NoteKind.LIST) are still differentiated by the existing
 * GroupBy.TYPE pathway in the middle - that is orthogonal to the task model.
 */
private fun arrangeItems(notes: List<NoteWithItems>, opts: ViewOptions): List<HomeListItem> {
    // Capture once per emission so all section bucketing uses a consistent "now".
    // Today's cutoff drifts at midnight; we accept that the section won't roll until
    // the next StateFlow emit, which is acceptable for a notes/tasks app.
    val now = System.currentTimeMillis()
    val sorted = sortNotes(notes, opts)

    val (done, active) = sorted.partition { it.note.completedAt != null }
    val (overdue, activeRest) = active.partition {
        val r = it.note.reminderAt
        r != null && r < now
    }

    return buildList {
        if (overdue.isNotEmpty()) {
            add(HomeListItem.Header(
                label = "",
                count = overdue.size,
                stableKey = "OVERDUE",
                labelRes = R.string.home_section_overdue,
            ))
            overdue.forEach { add(HomeListItem.NoteRow(it, groupKey = "OVERDUE")) }
        }
        addAll(arrangeMiddle(activeRest, opts, now))
        if (done.isNotEmpty()) {
            add(HomeListItem.Header(
                label = "",
                count = done.size,
                stableKey = "DONE",
                labelRes = R.string.home_section_done,
            ))
            done.forEach { add(HomeListItem.NoteRow(it, groupKey = "DONE")) }
        }
    }
}

/**
 * The "middle" between Overdue and Done. With reminder-asc sort we explode this into
 * Today / Upcoming / No date sections - this is the flagship task-first view.
 * For any other sort we fall back to the existing GroupBy.NONE / TAG / TYPE rendering
 * so users who picked "Sort by created" still get a flat (or tag-grouped) middle.
 */
private fun arrangeMiddle(
    active: List<NoteWithItems>,
    opts: ViewOptions,
    now: Long,
): List<HomeListItem> {
    if (opts.groupBy != GroupBy.DATE) {
        // Without sub-section breakdown the user couldn't tell where Overdue ended -
        // cards directly below the Overdue header read as if they were also overdue.
        // Insert an "Active" header to delimit the middle when there is no other grouping.
        val grouped = arrangeByGroupBy(active, opts)
        return if (opts.groupBy == GroupBy.NONE && grouped.isNotEmpty()) {
            buildList {
                add(HomeListItem.Header(
                    label = "",
                    count = active.size,
                    stableKey = "ACTIVE",
                    labelRes = R.string.home_section_active,
                ))
                addAll(grouped)
            }
        } else {
            grouped
        }
    }
    // Reminder sort (either direction): always emit Today / Upcoming / No date
    // sub-sections when Group by Date is selected. "Today" is everything with a future reminder strictly before
    // tomorrow's local midnight (overdue is already lifted out above). Sort
    // direction controls the SECTION ORDER:
    //   asc  -> Today, Upcoming, No date    (earliest reminder first)
    //   desc -> No date, Upcoming, Today    (mirror image - no-reminder at top,
    //                                        then furthest future, then closest to now)
    // Within each section the items are already ordered by [sortNotes] using the
    // chosen sort direction.
    val zone = java.time.ZoneId.systemDefault()
    val tomorrowMidnightMillis = java.time.ZonedDateTime.now(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    val today = mutableListOf<NoteWithItems>()
    val upcoming = mutableListOf<NoteWithItems>()
    val noDate = mutableListOf<NoteWithItems>()
    active.forEach { nwi ->
        val r = nwi.note.reminderAt
        when {
            r == null -> noDate += nwi
            r < tomorrowMidnightMillis -> today += nwi
            else -> upcoming += nwi
        }
    }

    data class SectionDef(val label: String, val key: String, val items: List<NoteWithItems>)
    val ascendingSections = listOf(
        SectionDef("", "TODAY", today),
        SectionDef("", "UPCOMING", upcoming),
        SectionDef("", "NO_DATE", noDate),
    )
    val sectionLabelRes = mapOf(
        "TODAY" to R.string.home_section_today,
        "UPCOMING" to R.string.home_section_upcoming,
        "NO_DATE" to R.string.home_section_no_date,
    )
    val orderedSections = if (opts.sortDir == SortDir.ASC) ascendingSections else ascendingSections.reversed()

    return buildList {
        orderedSections.forEach { section ->
            if (section.items.isNotEmpty()) {
                add(HomeListItem.Header(
                    label = section.label,
                    count = section.items.size,
                    stableKey = section.key,
                    labelRes = sectionLabelRes[section.key],
                ))
                section.items.forEach {
                    add(HomeListItem.NoteRow(it, groupKey = section.key))
                }
            }
        }
    }
}

/** Existing GroupBy logic, now scoped to the active middle (post Overdue / Done extraction). */
private fun arrangeByGroupBy(active: List<NoteWithItems>, opts: ViewOptions): List<HomeListItem> {
    return when (opts.groupBy) {
        GroupBy.DATE -> active.map { HomeListItem.NoteRow(it) }
        GroupBy.NONE -> active.map { HomeListItem.NoteRow(it, groupKey = "ACTIVE") }
        GroupBy.TYPE -> {
            val notesOnly = active.filter { it.note.kind == NoteKind.NOTE }
            val listsOnly = active.filter { it.note.kind == NoteKind.LIST }
            buildList {
                if (notesOnly.isNotEmpty()) {
                    add(HomeListItem.Header(
                        label = "",
                        count = notesOnly.size,
                        stableKey = "TYPE_NOTE",
                        labelRes = R.string.home_section_notes,
                    ))
                    notesOnly.forEach { add(HomeListItem.NoteRow(it, groupKey = "TYPE_NOTE")) }
                }
                if (listsOnly.isNotEmpty()) {
                    add(HomeListItem.Header(
                        label = "",
                        count = listsOnly.size,
                        stableKey = "TYPE_LIST",
                        labelRes = R.string.home_section_lists,
                    ))
                    listsOnly.forEach { add(HomeListItem.NoteRow(it, groupKey = "TYPE_LIST")) }
                }
            }
        }
        GroupBy.TAG -> {
            val tagged = active.filter { RememberReservedTags.userVisibleTags(it.note.tags).isNotEmpty() }
            val untagged = active.filter { RememberReservedTags.userVisibleTags(it.note.tags).isEmpty() }
            val tags = tagged
                .flatMap { RememberReservedTags.userVisibleTags(it.note.tags) }
                .distinct()
                .sorted()
            buildList {
                tags.forEach { tag ->
                    val inTag = tagged.filter {
                        RememberReservedTags.userVisibleTags(it.note.tags)
                            .any { tagName -> tagName.equals(tag, ignoreCase = true) }
                    }
                    if (inTag.isNotEmpty()) {
                        val sectionKey = "TAG_$tag"
                        add(HomeListItem.Header(label = tag, count = inTag.size, stableKey = sectionKey))
                        inTag.forEach { add(HomeListItem.NoteRow(it, groupKey = sectionKey)) }
                    }
                }
                if (untagged.isNotEmpty()) {
                    add(HomeListItem.Header(
                        label = "",
                        count = untagged.size,
                        stableKey = "TAG_UNTAGGED",
                        labelRes = R.string.home_section_untagged,
                    ))
                    untagged.forEach { add(HomeListItem.NoteRow(it, groupKey = "TAG_UNTAGGED")) }
                }
            }
        }
    }
}

private fun sortNotes(notes: List<NoteWithItems>, opts: ViewOptions): List<NoteWithItems> {
    return notes.sortedWith(buildComparator(opts))
}

private fun buildComparator(opts: ViewOptions): Comparator<NoteWithItems> {
    val ascBase: Comparator<NoteWithItems> = when (opts.sortKey) {
        SortKey.LAST_MODIFIED -> compareBy { it.note.updatedAt }
        SortKey.CREATED -> compareBy { it.note.createdAt }
        SortKey.REMINDER -> compareBy { it.note.reminderAt ?: Long.MAX_VALUE }
    }
    val directed = if (opts.sortDir == SortDir.DESC) ascBase.reversed() else ascBase
    return if (opts.sortKey == SortKey.REMINDER) {
        // Items without a reminder sink to the bottom regardless of direction.
        Comparator { a, b ->
            val aNull = a.note.reminderAt == null
            val bNull = b.note.reminderAt == null
            when {
                aNull && bNull -> 0
                aNull -> 1
                bNull -> -1
                else -> directed.compare(a, b)
            }
        }
    } else {
        directed
    }
}

@Composable
fun HomeRoute(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    viewOptionsPrefs: ViewOptionsPrefs,
    interactionPrefs: InteractionPrefs,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(repository, themePrefs, viewOptionsPrefs),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val interaction by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )
    val scope = rememberCoroutineScope()
    HomeScreen(
        state = state,
        interaction = interaction,
        onQueryChange = vm::setQuery,
        onFilterChange = vm::setFilter,
        onViewOptionsChange = vm::setViewOptions,
        onOpenNote = { note -> onOpenNote(note, false) },
        onSwipeAction = { note, action ->
            scope.launch {
                when (action) {
                    NoteSwipeAction.EDIT -> onOpenNote(note, true)
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
        },
        onToggleSelection = vm::toggleSelection,
        onSelectAllVisible = vm::selectNotes,
        onClearSelection = vm::clearSelection,
        onMarkSelectedDone = vm::markSelectedDone,
        onArchiveSelected = vm::archiveSelected,
        onTrashSelected = vm::trashSelected,
        onApplyTagsToSelection = vm::applyTagsToSelection,
        onCreateNote = onCreateNote,
        onCreateList = onCreateList,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    interaction: InteractionState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (NotesFilter) -> Unit,
    onViewOptionsChange: (ViewOptions) -> Unit,
    onOpenNote: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAllVisible: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onMarkSelectedDone: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onApplyTagsToSelection: (Set<String>, Set<String>, Map<String, String>) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Collapsed by default so the search results feel focused on active notes. Each section
    // remembers its own expansion state when the user switches away and comes back.
    var archiveSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var trashSectionExpanded by rememberSaveable { mutableStateOf(false) }
    // Every section header is collapsible. Done starts collapsed by default because those
    // tasks are already finished; every other section starts expanded.
    var collapsedSectionKeys by rememberSaveable { mutableStateOf(setOf("DONE")) }
    val listState = rememberLazyListState()
    val listScrollEnabled = rememberContentOverflowScrollEnabled(
        listState = listState,
        additionalScrollEnabled = topBarState.collapsedFraction > 0f,
    )
    val filterControlScrollState = rememberScrollState()
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra

    // Back gesture exits selection mode before the default handler runs.
    androidx.activity.compose.BackHandler(enabled = state.inSelectionMode) {
        onClearSelection()
    }
    val displayedItems = remember(state.items, collapsedSectionKeys) {
        state.items.filterNot { item ->
            item is HomeListItem.NoteRow && item.groupKey in collapsedSectionKeys
        }
    }
    val selectableVisibleIds = remember(displayedItems) {
        displayedItems.mapNotNull { item ->
            (item as? HomeListItem.NoteRow)?.note?.note?.id
        }.toSet()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            HomeSelectionActionBar(
                visible = state.inSelectionMode,
                selectedCount = state.selectedIds.size,
                onTagSelected = { tagSheetOpen = true },
                onMarkDoneSelected = onMarkSelectedDone,
                onArchiveSelected = onArchiveSelected,
                onTrashSelected = onTrashSelected,
                bottomPadding = navBarInset + PillBottomBarHeight + PillBottomScrimExtra + 24.dp,
            )
        },
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    // M3 Expressive motion: the search field expands out of the search
                    // button (right edge) with a spring, and collapses back into it on close.
                    // Selection-mode and app-name changes just crossfade.
                    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
                    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    val titleTarget = when {
                        state.inSelectionMode -> TopBarTitleTarget.Selection
                        searchOpen -> TopBarTitleTarget.Search
                        else -> TopBarTitleTarget.AppName
                    }
                    AnimatedContent(
                        targetState = titleTarget,
                        transitionSpec = {
                            val enteringSearch = targetState == TopBarTitleTarget.Search
                            val leavingSearch = initialState == TopBarTitleTarget.Search
                            val enter = if (enteringSearch) {
                                fadeIn(fadeInSpec) +
                                    expandHorizontally(spatialSpec, expandFrom = Alignment.End)
                            } else {
                                fadeIn(fadeInSpec)
                            }
                            val exit = if (leavingSearch) {
                                fadeOut(fadeOutSpec) +
                                    shrinkHorizontally(spatialSpec, shrinkTowards = Alignment.End)
                            } else {
                                fadeOut(fadeOutSpec)
                            }
                            (enter togetherWith exit).using(SizeTransform(clip = false))
                        },
                        label = "topBarTitle",
                    ) { target ->
                        when (target) {
                            TopBarTitleTarget.Selection -> Text(
                                text = stringResource(
                                    R.string.home_select_count,
                                    state.selectedIds.size,
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            TopBarTitleTarget.Search -> InlineSearchField(
                                query = state.filter.text,
                                onQueryChange = onQueryChange,
                            )
                            TopBarTitleTarget.AppName -> Text(stringResource(R.string.app_name))
                        }
                    }
                },
                navigationIcon = {
                    if (state.inSelectionMode) {
                        val cdExit = stringResource(R.string.home_select_exit_cd)
                        RememberFilledTonalIconButton(onClick = onClearSelection) {
                            RememberMaterialRoundedSymbol(
                                name = "close",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdExit },
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                },
                actions = {
                    if (state.inSelectionMode) {
                        val cdSelectAll = stringResource(R.string.home_select_all)
                        RememberFilledTonalIconButton(
                            onClick = { onSelectAllVisible(selectableVisibleIds) },
                            enabled = selectableVisibleIds.isNotEmpty(),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "select_all",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdSelectAll },
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        val cdUnselectAll = stringResource(R.string.home_unselect_all)
                        RememberFilledTonalIconButton(onClick = onClearSelection) {
                            RememberMaterialRoundedSymbol(
                                name = "deselect",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdUnselectAll },
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    } else {
                        // Motion specs for the trailing action cluster. Matches the title
                        // slot so the search icon swap feels tied to the search field.
                        val actionFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        val actionFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                        val scaleIconSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                        RememberFilledTonalIconButton(onClick = {
                            if (searchOpen && state.filter.text.isNotEmpty()) onQueryChange("")
                            searchOpen = !searchOpen
                        }) {
                            val cdCloseSearch = stringResource(R.string.cd_close_search)
                            val cdSearch = stringResource(R.string.cd_search)
                            AnimatedContent(
                                targetState = searchOpen,
                                transitionSpec = {
                                    (scaleIn(scaleIconSpec) + fadeIn(actionFadeIn)) togetherWith
                                        (scaleOut(scaleIconSpec) + fadeOut(actionFadeOut))
                                },
                                label = "searchIconSwap",
                            ) { open ->
                                RememberMaterialRoundedSymbol(
                                    name = if (open) "close" else "search",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics {
                                        contentDescription =
                                            if (open) cdCloseSearch else cdSearch
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }
        val topInset = padding.calculateTopPadding() + 4.dp
        val bottomPadding = bottomInset + 24.dp
        val listContentPadding = remember(topInset, bottomPadding) {
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topInset,
                bottom = bottomPadding,
            )
        }
        // If the active list is empty we may still have archive/trash matches when the user
        // is searching - only fall through to the empty-state screen if NOTHING matched.
        val hasExtendedMatches =
            state.archivedMatches.isNotEmpty() || state.trashedMatches.isNotEmpty()
        val showEmptyState = !state.loading && state.items.isEmpty() && !hasExtendedMatches
        val showFilterControls =
            state.totalActive > 0 || state.filter.active || state.viewOptions != ViewOptions()
        if (showEmptyState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod)
                    .padding(listContentPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showFilterControls) {
                    ActiveFilterChips(
                        filter = state.filter,
                        onChange = onFilterChange,
                        viewOptions = state.viewOptions,
                        onViewOptionsChange = onViewOptionsChange,
                        availableTags = state.availableTags,
                        scrollState = filterControlScrollState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    NotesEmptyState(
                        filter = state.filter,
                        totalUnfilteredNotes = state.totalActive,
                        onCreateNote = onCreateNote,
                        onCreateList = onCreateList,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod),
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = listScrollEnabled,
            ) {
                if (showFilterControls) {
                    item(key = "__chips__", contentType = "chips") {
                        ActiveFilterChips(
                            filter = state.filter,
                            onChange = onFilterChange,
                            viewOptions = state.viewOptions,
                            onViewOptionsChange = onViewOptionsChange,
                            availableTags = state.availableTags,
                            scrollState = filterControlScrollState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                ),
                        )
                    }
                }
                items(
                    items = displayedItems,
                    key = { item ->
                        when (item) {
                            is HomeListItem.Header -> "h:${item.stableKey}"
                            is HomeListItem.NoteRow -> "n:${item.groupKey}:${item.note.note.id}"
                        }
                    },
                    contentType = { item ->
                        when (item) {
                            is HomeListItem.Header -> "header"
                            is HomeListItem.NoteRow -> "noteRow"
                        }
                    },
                ) { item ->
                    when (item) {
                        is HomeListItem.Header -> GroupHeader(
                            label = item.labelRes?.let { stringResource(it) } ?: item.label,
                            count = item.count,
                            collapsible = item.collapsible,
                            collapsed = item.stableKey in collapsedSectionKeys,
                            onToggle = if (item.collapsible) {
                                {
                                    collapsedSectionKeys = if (item.stableKey in collapsedSectionKeys) {
                                        collapsedSectionKeys - item.stableKey
                                    } else {
                                        collapsedSectionKeys + item.stableKey
                                    }
                                }
                            } else null,
                            // Bookend sections (Overdue at top, Done at bottom) stay
                            // put when the user reverses sort. The pin icon advertises
                            // that to avoid surprise.
                            pinned = item.stableKey == "OVERDUE" || item.stableKey == "DONE",
                            modifier = Modifier.animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            ),
                        )
                        is HomeListItem.NoteRow -> {
                            val noteId = item.note.note.id
                            val isSelected = noteId in state.selectedIds
                            SwipeableRememberNoteCard(
                                note = item.note,
                                interaction = interaction,
                                onOpenNote = { n ->
                                    if (state.inSelectionMode) onToggleSelection(n.note.id)
                                    else onOpenNote(n)
                                },
                                onSwipeAction = onSwipeAction,
                                modifier = Modifier.animateItem(
                                    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                ),
                                selected = isSelected,
                                onLongClick = { onToggleSelection(noteId) },
                                // Swipes are meaningless during bulk-select and would visually
                                // fight the tap-to-toggle gesture.
                                swipeEnabled = !state.inSelectionMode,
                            )
                        }
                    }
                }
                // Archive section: collapsed pill divider, expands in place to reveal dimmed
                // cards tagged with an ARCHIVE badge. Only appears while a search is running
                // and archived notes match the query.
                if (state.archivedMatches.isNotEmpty()) {
                    item(key = "__archive_divider__", contentType = "sectionDivider") {
                        SearchSectionPillDivider(
                            label = stringResource(R.string.home_search_section_archive),
                            count = state.archivedMatches.size,
                            expanded = archiveSectionExpanded,
                            onToggle = { archiveSectionExpanded = !archiveSectionExpanded },
                            muted = false,
                            modifier = Modifier.animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            ),
                        )
                    }
                    if (archiveSectionExpanded) {
                        items(
                            items = state.archivedMatches,
                            key = { note -> "arch:${note.note.id}" },
                            contentType = { "archivedRow" },
                        ) { note ->
                            StateBadgedNoteCard(
                                note = note,
                                interaction = interaction,
                                onOpen = onOpenNote,
                                onSwipeAction = onSwipeAction,
                                badgeText = stringResource(R.string.home_search_section_badge_archive),
                                badgeStyle = SectionBadgeStyle.ARCHIVE,
                                modifier = Modifier.animateItem(
                                    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                ),
                            )
                        }
                    }
                }
                // Trash section: same pattern as Archive but rendered a shade quieter because
                // these notes are already on their way out.
                if (state.trashedMatches.isNotEmpty()) {
                    item(key = "__trash_divider__", contentType = "sectionDivider") {
                        SearchSectionPillDivider(
                            label = stringResource(R.string.home_search_section_trash),
                            count = state.trashedMatches.size,
                            expanded = trashSectionExpanded,
                            onToggle = { trashSectionExpanded = !trashSectionExpanded },
                            muted = true,
                            modifier = Modifier.animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            ),
                        )
                    }
                    if (trashSectionExpanded) {
                        items(
                            items = state.trashedMatches,
                            key = { note -> "trash:${note.note.id}" },
                            contentType = { "trashedRow" },
                        ) { note ->
                            StateBadgedNoteCard(
                                note = note,
                                interaction = interaction,
                                onOpen = onOpenNote,
                                onSwipeAction = onSwipeAction,
                                badgeText = stringResource(R.string.home_search_section_badge_trash),
                                badgeStyle = SectionBadgeStyle.TRASH,
                                modifier = Modifier.animateItem(
                                    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (tagSheetOpen) {
        BulkTagSheet(
            availableTags = state.availableTags,
            onApply = onApplyTagsToSelection,
            onDismiss = { tagSheetOpen = false },
        )
    }
}

@Composable
private fun HomeSelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    onTagSelected: () -> Unit,
    onMarkDoneSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = selectedCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    val tagLabel = stringResource(R.string.home_bulk_tag)
                    val cdTag = stringResource(R.string.home_bulk_tag_cd)
                    RememberFilledTonalIconButton(
                        onClick = onTagSelected,
                        tooltipLabel = tagLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "label",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = cdTag },
                        )
                    }
                    val markDoneLabel = stringResource(R.string.edit_bottom_bar_mark_done)
                    val cdMarkDone = stringResource(R.string.home_bulk_mark_done_cd)
                    RememberFilledTonalIconButton(
                        onClick = onMarkDoneSelected,
                        tooltipLabel = markDoneLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check_circle",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = cdMarkDone },
                        )
                    }
                    val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                    val cdArchive = stringResource(R.string.home_bulk_archive_cd)
                    RememberFilledTonalIconButton(
                        onClick = onArchiveSelected,
                        tooltipLabel = archiveLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "archive",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = cdArchive },
                        )
                    }
                    val trashLabel = stringResource(R.string.home_bulk_trash)
                    val cdTrash = stringResource(R.string.home_bulk_trash_cd)
                    RememberFilledTonalIconButton(
                        onClick = onTrashSelected,
                        tooltipLabel = trashLabel,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = cdTrash },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    label: String,
    count: Int? = null,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
    pinned: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val baseModifier = modifier
        .fillMaxWidth()
        // More vertical breathing room than before. titleMedium is denser than
        // labelLarge, so the additional padding keeps the cards from butting against
        // the header text.
        .padding(top = 18.dp, bottom = 6.dp, start = 4.dp)
    val headerInteractionSource = remember { MutableInteractionSource() }
    val playTap = rememberPlayTapSound()
    val rowModifier = if (collapsible && onToggle != null) {
        baseModifier.clickable(
            indication = null,
            interactionSource = headerInteractionSource,
        ) {
            playTap()
            onToggle()
        }
    } else {
        baseModifier
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) {
            // Glanceable indicator that this section is pinned - it doesn't move
            // when the user reverses the sort direction. Material's push_pin glyph
            // is the conventional "stuck in place" affordance. Slight rotation
            // matches Material spec for "actively pinned" status.
            RememberMaterialRoundedSymbol(
                name = "push_pin",
                filled = true,
                size = 14.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .graphicsLayer { rotationZ = 30f },
            )
        }
        Text(
            text = label,
            // Bumped from labelLarge - section headers are first-class navigation
            // markers in the task-first layout, not subtle dividers.
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
        if (collapsible) {
            Spacer(Modifier.weight(1f))
            // Single chevron glyph rotated 90 degrees on expand instead of
            // shipping both "chevron_right" and "expand_more" through the
            // font subset. animateFloatAsState handles the smooth rotation.
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (collapsed) 0f else 90f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
                label = "section_chevron_rotation",
            )
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (query != searchFieldValue.text) {
            searchFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = searchFieldValue,
            onValueChange = { newValue ->
                searchFieldValue = newValue
                if (newValue.text != query) onQueryChange(newValue.text)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                if (searchFieldValue.text.isEmpty()) {
                    Text(
                        stringResource(R.string.home_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

/**
 * Picks the accent palette for the state badge shown on archive/trash search results.
 * Archive stays "yours but quiet" in a soft primary-tinted container; Trash reads as
 * "already dismissed" in a muted error-tinted container so the two can't be confused.
 */
private enum class SectionBadgeStyle { ARCHIVE, TRASH }

/**
 * Centered pill sitting on a divider line - the tap target that expands an extended
 * results section. Mirrors the collapsed/expanded affordance from the search mockup:
 * chevron rotates on expand and the pill sits on top of a thin rule so the divider
 * reads as a section break even when collapsed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchSectionPillDivider(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spatialSpec,
        label = "sectionChevron",
    )
    val cdExpand = stringResource(R.string.section_expand_cd, label)
    val cdCollapse = stringResource(R.string.section_collapse_cd, label)
    val pillBackground = if (muted) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
    val countBg = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.secondaryContainer
    val countColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Divider line behind the pill - the pill's solid background occludes the center.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier
                .background(pillBackground, RoundedCornerShape(999.dp))
                .semantics { contentDescription = if (expanded) cdCollapse else cdExpand }
                .tapSoundClickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            Box(
                modifier = Modifier
                    .background(countBg, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = countColor,
                )
            }
            RememberMaterialRoundedSymbol(
                name = "expand_more",
                size = 18.dp,
                tint = labelColor,
                weight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

/**
 * A read-only card used inside the Archive/Trash expanded sections. Wraps a regular
 * [SwipeableRememberNoteCard] with a top-right state pill so the origin of the match
 * stays unambiguous after the user scrolls past the section header. The whole wrapper
 * is dimmed a touch - archived/trashed notes should feel present but not competitive
 * with the active result list. Swipes are disabled because the actions available for
 * archived/trashed notes live on the Archive / Trash tabs.
 */
@Composable
private fun StateBadgedNoteCard(
    note: NoteWithItems,
    interaction: InteractionState,
    onOpen: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    badgeText: String,
    badgeStyle: SectionBadgeStyle,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (badgeStyle) {
        SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.secondaryContainer
        SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.errorContainer
    }
    val fgColor = when (badgeStyle) {
        SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.onSecondaryContainer
        SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.onErrorContainer
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.graphicsLayer { alpha = 0.88f }) {
            SwipeableRememberNoteCard(
                note = note,
                interaction = interaction,
                onOpenNote = onOpen,
                onSwipeAction = onSwipeAction,
                swipeEnabled = false,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 14.dp)
                .background(bgColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NotesEmptyState(
    filter: NotesFilter,
    totalUnfilteredNotes: Int,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pristineVault =
        totalUnfilteredNotes == 0 && filter.text.isBlank() && !filter.facetActive
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pristineVault) {
            EmptyNotesIllustration()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_no_notes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_no_notes_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            RememberButton(
                onClick = onCreateNote,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 20.dp,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_create_note))
            }
            Spacer(Modifier.height(12.dp))
            RememberOutlinedButton(
                onClick = onCreateList,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 20.dp,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_create_list))
            }
        } else {
            EmptyFilterIllustration()
            Spacer(Modifier.height(18.dp))
            val titleText = when {
                filter.text.isNotBlank() ->
                    stringResource(R.string.home_no_results_for, filter.text)
                filter.facetActive ->
                    stringResource(R.string.home_no_results_filters_title)
                else -> stringResource(R.string.home_nothing_here)
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            val hintText = if (filter.text.isNotBlank()) {
                stringResource(R.string.home_no_results_hint)
            } else {
                stringResource(R.string.home_no_results_filters_hint)
            }
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
