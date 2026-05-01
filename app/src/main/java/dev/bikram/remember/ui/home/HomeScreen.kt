package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors

@Composable
fun HomeRoute(
    interactionPrefs: InteractionPrefs,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val interaction by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )
    LaunchedEffect(vm, onOpenNote) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.OpenNote -> onOpenNote(event.note, event.forceEdit)
            }
        }
    }
    HomeScreen(
        state = state,
        interaction = interaction,
        onQueryChange = vm::setQuery,
        onFilterChange = vm::setFilter,
        onViewOptionsChange = vm::setViewOptions,
        onOpenNote = vm::openNote,
        onSwipeAction = vm::handleSwipeAction,
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
    var initialListLiftApplied by rememberSaveable { mutableStateOf(false) }
    // Every section header is collapsible. Done starts collapsed by default because those
    // tasks are already finished; every other section starts expanded.
    var collapsedSectionKeys by rememberSaveable { mutableStateOf(setOf("DONE")) }
    val listState = rememberLazyListState()
    val listScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = listState,
            additionalScrollEnabled = topBarState.collapsedFraction > 0f,
        )
    val filterControlScrollState = rememberScrollState()
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    val initialListLiftPx =
        with(LocalDensity.current) {
            16.dp.roundToPx()
        }

    // Back gesture exits selection mode before the default handler runs.
    androidx.activity.compose.BackHandler(enabled = state.inSelectionMode) {
        onClearSelection()
    }
    val displayedItems =
        remember(state.items, collapsedSectionKeys) {
            state.items.filterNot { item ->
                item is HomeListItem.NoteRow && item.groupKey in collapsedSectionKeys
            }
        }
    val selectableVisibleIds =
        remember(displayedItems) {
            displayedItems
                .mapNotNull { item ->
                    (item as? HomeListItem.NoteRow)?.note?.note?.id
                }.toSet()
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            HomeSelectionActionBar(
                visible = state.inSelectionMode,
                onClearSelection = onClearSelection,
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
                    val titleTarget =
                        when {
                            searchOpen && !state.inSelectionMode -> TopBarTitleTarget.Search
                            else -> TopBarTitleTarget.AppName
                        }
                    AnimatedContent(
                        targetState = titleTarget,
                        transitionSpec = {
                            val enteringSearch = targetState == TopBarTitleTarget.Search
                            val leavingSearch = initialState == TopBarTitleTarget.Search
                            val enter =
                                if (enteringSearch) {
                                    fadeIn(fadeInSpec) +
                                        expandHorizontally(spatialSpec, expandFrom = Alignment.End)
                                } else {
                                    fadeIn(fadeInSpec)
                                }
                            val exit =
                                if (leavingSearch) {
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
                            TopBarTitleTarget.Search ->
                                InlineSearchField(
                                    query = state.filter.text,
                                    onQueryChange = onQueryChange,
                                )
                            TopBarTitleTarget.AppName ->
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    fontWeight = FontWeight.Bold,
                                )
                        }
                    }
                },
                actions = {
                    if (state.inSelectionMode) {
                        val cdSelectAll = stringResource(R.string.home_select_all)
                        Box(modifier = Modifier.size(48.dp)) {
                            RememberFilledTonalIconButton(
                                onClick = { onSelectAllVisible(selectableVisibleIds) },
                                enabled = selectableVisibleIds.isNotEmpty(),
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "select_all",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdSelectAll },
                                )
                            }
                            Badge(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 2.dp, y = (-2).dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    text = state.selectedIds.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
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
                                    modifier =
                                        Modifier.semantics {
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
        val listContentPadding =
            remember(topInset, bottomPadding) {
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
        LaunchedEffect(showEmptyState, displayedItems.size) {
            if (!initialListLiftApplied && !showEmptyState && displayedItems.isNotEmpty()) {
                initialListLiftApplied = true
                listState.scrollToItem(0, scrollOffset = -initialListLiftPx)
            }
        }
        if (showEmptyState) {
            Column(
                modifier =
                    Modifier
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
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    NotesEmptyState(
                        filter = state.filter,
                        totalUnfilteredNotes = state.totalActive,
                        onCreateNote = onCreateNote,
                        onCreateList = onCreateList,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                }
            }
        } else {
            val itemFadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val itemFadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val itemPlacementSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        fadeInSpec = itemFadeInSpec,
                                        placementSpec = itemPlacementSpec,
                                        fadeOutSpec = itemFadeOutSpec,
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
                        is HomeListItem.Header ->
                            GroupHeader(
                                label = item.labelRes?.let { stringResource(it) } ?: item.label,
                                count = item.count,
                                collapsible = item.collapsible,
                                collapsed = item.stableKey in collapsedSectionKeys,
                                onToggle =
                                    if (item.collapsible) {
                                        {
                                            collapsedSectionKeys =
                                                if (item.stableKey in collapsedSectionKeys) {
                                                    collapsedSectionKeys - item.stableKey
                                                } else {
                                                    collapsedSectionKeys + item.stableKey
                                                }
                                        }
                                    } else {
                                        null
                                    },
                                // Bookend sections (Overdue at top, Done at bottom) stay
                                // put when the user reverses sort. The pin icon advertises
                                // that to avoid surprise.
                                pinned = item.stableKey == "OVERDUE" || item.stableKey == "DONE",
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = itemFadeInSpec,
                                        placementSpec = itemPlacementSpec,
                                        fadeOutSpec = itemFadeOutSpec,
                                    ),
                            )
                        is HomeListItem.NoteRow -> {
                            val noteId = item.note.note.id
                            val isSelected = noteId in state.selectedIds
                            SwipeableRememberNoteCard(
                                note = item.note,
                                interaction = interaction,
                                onOpenNote = { n ->
                                    if (state.inSelectionMode) {
                                        onToggleSelection(n.note.id)
                                    } else {
                                        onOpenNote(n)
                                    }
                                },
                                onSwipeAction = onSwipeAction,
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = itemFadeInSpec,
                                        placementSpec = itemPlacementSpec,
                                        fadeOutSpec = itemFadeOutSpec,
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
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = itemFadeInSpec,
                                    placementSpec = itemPlacementSpec,
                                    fadeOutSpec = itemFadeOutSpec,
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
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = itemFadeInSpec,
                                        placementSpec = itemPlacementSpec,
                                        fadeOutSpec = itemFadeOutSpec,
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
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = itemFadeInSpec,
                                    placementSpec = itemPlacementSpec,
                                    fadeOutSpec = itemFadeOutSpec,
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
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = itemFadeInSpec,
                                        placementSpec = itemPlacementSpec,
                                        fadeOutSpec = itemFadeOutSpec,
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
