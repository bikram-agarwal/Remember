package dev.bikram.remember.ui.home
import androidx.compose.material3.IconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import dev.bikram.remember.data.matches
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.EmptyNotesIllustration
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

sealed class HomeListItem {
    data class Header(val label: String) : HomeListItem()
    /**
     * [groupKey] disambiguates keys when the same note appears under multiple groups
     * (e.g. a note with tags ["work", "personal"] in GroupBy.TAG view).
     */
    data class NoteRow(val note: NoteWithItems, val groupKey: String = "") : HomeListItem()
}

data class HomeState(
    val filter: NotesFilter = NotesFilter(),
    val items: List<HomeListItem> = emptyList(),
    val totalActive: Int = 0,
    val availableTags: List<String> = emptyList(),
    val viewOptions: ViewOptions = ViewOptions(),
)

class HomeViewModel(
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
) : ViewModel() {
    private val filter = MutableStateFlow(NotesFilter())
    private val viewOptionsFlow = themePrefs.state
        .map { it.viewOptions }
        .distinctUntilChanged()

    val state: StateFlow<HomeState> = combine(
        filter,
        repository.observeActive(),
        viewOptionsFlow,
    ) { f, notes, opts ->
        val filtered = notes.filter { f.matches(it) }
        val tags = notes
            .flatMap { RememberReservedTags.userVisibleTags(it.note.tags) }
            .distinct()
            .sorted()
        val arranged = arrangeItems(filtered, opts)
        HomeState(
            filter = f,
            items = arranged,
            totalActive = notes.size,
            availableTags = tags,
            viewOptions = opts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setFilter(v: NotesFilter) { filter.value = v }
    fun setQuery(v: String) { filter.value = filter.value.copy(text = v) }

    fun setViewOptions(v: ViewOptions) {
        viewModelScope.launch { themePrefs.setViewOptions(v) }
    }

    companion object {
        fun factory(repository: NoteRepository, themePrefs: ThemePrefs) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository, themePrefs) as T
            }
    }
}

private fun arrangeItems(notes: List<NoteWithItems>, opts: ViewOptions): List<HomeListItem> {
    val sorted = sortNotes(notes, opts)
    return when (opts.groupBy) {
        GroupBy.NONE -> sorted.map { HomeListItem.NoteRow(it) }
        GroupBy.TYPE -> {
            val notesOnly = sorted.filter { it.note.kind == NoteKind.NOTE }
            val listsOnly = sorted.filter { it.note.kind == NoteKind.LIST }
            buildList {
                if (notesOnly.isNotEmpty()) {
                    add(HomeListItem.Header("Notes"))
                    notesOnly.forEach { add(HomeListItem.NoteRow(it, groupKey = "NOTE")) }
                }
                if (listsOnly.isNotEmpty()) {
                    add(HomeListItem.Header("Lists"))
                    listsOnly.forEach { add(HomeListItem.NoteRow(it, groupKey = "LIST")) }
                }
            }
        }
        GroupBy.TAG -> {
            val tagged = sorted.filter { RememberReservedTags.userVisibleTags(it.note.tags).isNotEmpty() }
            val untagged = sorted.filter { RememberReservedTags.userVisibleTags(it.note.tags).isEmpty() }
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
                        add(HomeListItem.Header(tag))
                        inTag.forEach { add(HomeListItem.NoteRow(it, groupKey = "tag:$tag")) }
                    }
                }
                if (untagged.isNotEmpty()) {
                    add(HomeListItem.Header("Untagged"))
                    untagged.forEach { add(HomeListItem.NoteRow(it, groupKey = "untagged")) }
                }
            }
        }
    }
}

/** Favorites (pinned) always top. Within favorite and non-favorite groups, apply sort key/dir. */
private fun sortNotes(notes: List<NoteWithItems>, opts: ViewOptions): List<NoteWithItems> {
    val (pinned, rest) = notes.partition { it.note.pinned }
    val cmp = buildComparator(opts)
    return pinned.sortedWith(cmp) + rest.sortedWith(cmp)
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
    interactionPrefs: InteractionPrefs,
    onOpenNote: (NoteWithItems) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository, themePrefs))
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
        onOpenNote = onOpenNote,
        onSwipeAction = { note, action ->
            scope.launch {
                when (action) {
                    NoteSwipeAction.OPEN -> onOpenNote(note)
                    NoteSwipeAction.TRASH -> repository.moveToTrash(note.note.id)
                    NoteSwipeAction.DUPLICATE -> repository.duplicateNote(note.note.id)
                    NoteSwipeAction.TOGGLE_PIN ->
                        repository.setPinned(note.note.id, !note.note.pinned)
                }
            }
        },
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
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var viewOptionsOpen by rememberSaveable { mutableStateOf(false) }
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    if (searchOpen) {
                        InlineSearchField(
                            query = state.filter.text,
                            onQueryChange = onQueryChange,
                            filterActive = state.filter.facetActive,
                            onOpenFilter = { filterSheetOpen = true },
                        )
                    } else {
                        Text("Remember")
                    }
                },
                actions = {
                    RememberFilledTonalIconButton(onClick = {
                        if (searchOpen && state.filter.text.isNotEmpty()) onQueryChange("")
                        searchOpen = !searchOpen
                    }) {
                        RememberMaterialRoundedSymbol(
                            name = if (searchOpen) "close" else "search",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics {
                                contentDescription =
                                    if (searchOpen) "Close search" else "Search"
                            },
                        )
                    }
                    if (!searchOpen) {
                        Spacer(Modifier.width(6.dp))
                        RememberFilledTonalIconButton(onClick = { viewOptionsOpen = true }) {
                            RememberMaterialRoundedSymbol(
                                name = "tune",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = "View options" },
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
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
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod)
                    .padding(listContentPadding),
            ) {
                if (state.filter.facetActive || state.filter.text.isNotBlank()) {
                    ActiveFilterChips(
                        filter = state.filter,
                        onChange = onFilterChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod),
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.filter.facetActive || state.filter.text.isNotBlank()) {
                    item(key = "__chips__", contentType = "chips") {
                        ActiveFilterChips(
                            filter = state.filter,
                            onChange = onFilterChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(
                    items = state.items,
                    key = { item ->
                        when (item) {
                            is HomeListItem.Header -> "h:${item.label}"
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
                        is HomeListItem.Header -> GroupHeader(item.label)
                        is HomeListItem.NoteRow -> SwipeableRememberNoteCard(
                            note = item.note,
                            interaction = interaction,
                            onOpenNote = onOpenNote,
                            onSwipeAction = onSwipeAction,
                        )
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        FilterSheet(
            filter = state.filter,
            availableTags = state.availableTags,
            onChange = onFilterChange,
            onDismiss = { filterSheetOpen = false },
        )
    }

    if (viewOptionsOpen) {
        ViewOptionsSheet(
            viewOptions = state.viewOptions,
            onChange = onViewOptionsChange,
            onDismiss = { viewOptionsOpen = false },
        )
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    filterActive: Boolean,
    onOpenFilter: () -> Unit,
) {
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
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .background(
                    if (filterActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(14.dp),
                )
                .tapSoundClickable(onClick = onOpenFilter)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "filter_list",
                size = 16.dp,
                tint = if (filterActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = "Filter" },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Filter",
                style = MaterialTheme.typography.labelMedium,
                color = if (filterActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
            RememberMaterialRoundedSymbol(
                name = "inbox",
                size = 64.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    filter.text.isNotBlank() -> "No results for \"${filter.text}\""
                    filter.facetActive -> "Nothing matches these filters"
                    else -> "Nothing to show here"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Try adjusting search or filters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
