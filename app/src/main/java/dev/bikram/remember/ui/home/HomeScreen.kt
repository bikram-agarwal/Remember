package dev.bikram.remember.ui.home

import android.annotation.SuppressLint
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.bulkActionSnackbarMessage
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.NoteCardUiModel
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.components.toNoteCardUiModel
import dev.bikram.remember.ui.edit.NoteIcon
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope
import dev.bikram.remember.ui.nav.LocalSharedTransitionScope
import dev.bikram.remember.ui.theme.LocalSnackbarHostState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

private object HomeScreenSessionState {
    var archiveSectionExpanded: Boolean = false
    var trashSectionExpanded: Boolean = false
    var collapsedSectionKeys: Set<String> = setOf("DONE")
    var listFirstVisibleItemIndex: Int = 0
    var listFirstVisibleItemScrollOffset: Int = 0
    var initialListLiftApplied: Boolean = false
}

@Composable
private fun PendingNewNoteCard(
    kind: NoteKind,
    modifier: Modifier = Modifier,
) {
    val model =
        remember(kind) {
            NoteCardUiModel(
                id = -1L,
                kind = kind,
                title = "",
                body = "",
                starred = false,
                completed = false,
                icon =
                    when (kind) {
                        NoteKind.NOTE -> NoteIcon.NotePlaceholder
                        NoteKind.LIST -> NoteIcon.ListPlaceholder
                    },
                pictureUri = null,
                pictureHeroFraming = null,
                pictureCacheRevision = 0L,
                reminderAt = null,
                recurring = false,
                hasAttachment = false,
                visibleTags = persistentListOf(),
                checklistPreviewItems = persistentListOf(),
                checklistHiddenItemCount = 0,
            )
        }
    NoteCard(
        model = model,
        onClick = {},
        modifier = modifier,
        activeInDetailPane = true,
    )
}

@Composable
fun HomeRoute(
    interactionPrefs: InteractionPrefs,
    closeRevealRequest: Int,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    activeNoteId: Long? = null,
    pendingNewNoteKind: NoteKind? = null,
    showSelectionActionBar: Boolean = true,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val interaction by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.bulk_action_undo)
    LaunchedEffect(vm, onOpenNote, snackbarHostState, context, undoLabel) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.OpenNote -> onOpenNote(event.note, event.forceEdit)
                is HomeEvent.BulkActionPerformed -> {
                    launch {
                        // Do not block collection of OpenNote events while the snackbar is visible.
                        val message = bulkActionSnackbarMessage(context, event.action)
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = undoLabel,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            vm.undoLastBulkAction()
                        }
                    }
                }
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
        onPruneSelection = vm::pruneSelection,
        onClearSelection = vm::clearSelection,
        onMarkSelectedDone = vm::markSelectedDone,
        onArchiveSelected = vm::archiveSelected,
        onTrashSelected = vm::trashSelected,
        onApplyTagsToSelection = vm::applyTagsToSelection,
        closeRevealRequest = closeRevealRequest,
        onCreateNote = onCreateNote,
        onCreateList = onCreateList,
        activeNoteId = activeNoteId,
        pendingNewNoteKind = pendingNewNoteKind,
        showSelectionActionBar = showSelectionActionBar,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
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
    onPruneSelection: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onMarkSelectedDone: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onApplyTagsToSelection: (Set<String>, Set<String>, Map<String, String>) -> Unit,
    closeRevealRequest: Int,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    activeNoteId: Long? = null,
    pendingNewNoteKind: NoteKind? = null,
    showSelectionActionBar: Boolean = true,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchShouldRequestFocus by rememberSaveable { mutableStateOf(false) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Collapsed by default so the search results feel focused on active notes. Each section
    // remembers its own expansion state when the user switches away and comes back.
    var archiveSectionExpanded by rememberSaveable { mutableStateOf(HomeScreenSessionState.archiveSectionExpanded) }
    var trashSectionExpanded by rememberSaveable { mutableStateOf(HomeScreenSessionState.trashSectionExpanded) }
    var expandedFilterDropdown by rememberSaveable { mutableStateOf<ActiveFilterDropdown?>(null) }
    var initialListLiftApplied by rememberSaveable {
        mutableStateOf(
            HomeScreenSessionState.initialListLiftApplied ||
                HomeScreenSessionState.listFirstVisibleItemIndex > 0 ||
                HomeScreenSessionState.listFirstVisibleItemScrollOffset > 0,
        )
    }
    var revealedNoteCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var revealedNoteCardBounds by remember { mutableStateOf<Rect?>(null) }
    var homeScreenBounds by remember { mutableStateOf<Rect?>(null) }
    // Every section header is collapsible. Done starts collapsed by default because those
    // tasks are already finished; every other section starts expanded.
    var collapsedSectionKeys by rememberSaveable { mutableStateOf(HomeScreenSessionState.collapsedSectionKeys) }
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = HomeScreenSessionState.listFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = HomeScreenSessionState.listFirstVisibleItemScrollOffset,
        )
    val listScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = listState,
            additionalScrollEnabled = true,
        )
    val filterControlScrollState = rememberScrollState()
    val blurStyle = rememberProgressiveBlurStyle(blurTop = false)
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationsAllowed = rememberNotificationsAllowed()
    val initialListLiftPx =
        with(LocalDensity.current) {
            16.dp.roundToPx()
        }

    DisposableEffect(listState) {
        onDispose {
            HomeScreenSessionState.archiveSectionExpanded = archiveSectionExpanded
            HomeScreenSessionState.trashSectionExpanded = trashSectionExpanded
            HomeScreenSessionState.collapsedSectionKeys = collapsedSectionKeys
            HomeScreenSessionState.listFirstVisibleItemIndex = listState.firstVisibleItemIndex
            HomeScreenSessionState.listFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            HomeScreenSessionState.initialListLiftApplied = initialListLiftApplied
        }
    }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back gesture exits selection mode before the default handler runs.
    androidx.activity.compose.BackHandler(enabled = state.inSelectionMode) {
        onClearSelection()
    }
    LaunchedEffect(state.inSelectionMode) {
        if (state.inSelectionMode) {
            revealedNoteCardId = null
        }
    }
    LaunchedEffect(closeRevealRequest) {
        if (closeRevealRequest > 0) {
            revealedNoteCardId = null
        }
    }
    LaunchedEffect(pendingNewNoteKind) {
        if (pendingNewNoteKind != null) {
            revealedNoteCardId = null
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(revealedNoteCardId) {
        if (revealedNoteCardId == null) {
            revealedNoteCardBounds = null
        }
    }
    val displayedItems = state.items
    val visibleDisplayedItems by
        remember(displayedItems, collapsedSectionKeys) {
            derivedStateOf {
                displayedItems.filterNot { item ->
                    item is HomeListItem.NoteRow && item.groupKey in collapsedSectionKeys
                }
            }
        }
    val selectableVisibleIds by
        remember(visibleDisplayedItems) {
            derivedStateOf {
                visibleDisplayedItems
                    .mapNotNull { item ->
                        (item as? HomeListItem.NoteRow)?.card?.id
                    }.toSet()
            }
        }
    LaunchedEffect(selectableVisibleIds) {
        onPruneSelection(selectableVisibleIds)
    }
    val bulkTagCoverage by
        remember(visibleDisplayedItems, state.availableTags, state.selectedIds) {
            derivedStateOf {
                val selectedTagsByNoteId = LinkedHashMap<Long, List<String>>()
                visibleDisplayedItems.forEach { item ->
                    if (item is HomeListItem.NoteRow && item.card.id in state.selectedIds) {
                        selectedTagsByNoteId.putIfAbsent(item.card.id, item.note.note.tags)
                    }
                }
                buildBulkTagCoverage(
                    availableTags = state.availableTags,
                    selectedNoteTags = selectedTagsByNoteId.values.toList(),
                )
            }
        }
    // Note ids that appear in more than one row of [displayedItems]. Only multi-tag
    // notes under the by-tag grouping land here; everything else is mutually exclusive
    // (a note is either active or done, either overdue or upcoming). For unique ids we
    // key the LazyColumn row by [note.id] alone so transitions across sections (e.g.
    // active -> Done bucket) stay the same key and animateItem can slide the card
    // instead of fading it out and back in.
    val duplicatedRowNoteIds by
        remember(displayedItems) {
            derivedStateOf {
                val counts = HashMap<Long, Int>()
                displayedItems.forEach { item ->
                    if (item is HomeListItem.NoteRow) {
                        counts[item.card.id] = (counts[item.card.id] ?: 0) + 1
                    }
                }
                counts
                    .asSequence()
                    .filter { it.value > 1 }
                    .map { it.key }
                    .toSet()
            }
        }
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val homeRouteVisible =
        navAnimatedVisibilityScope == null ||
            navAnimatedVisibilityScope.transition.targetState == EnterExitState.Visible
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val topBarOverlayModifier =
        if (homeRouteVisible && sharedTransitionScope != null) {
            with(sharedTransitionScope) {
                Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 20f)
            }
        } else {
            Modifier
        }

    Scaffold(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates ->
                    homeScreenBounds = coordinates.boundsInRoot()
                }.pointerInput(revealedNoteCardId, revealedNoteCardBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val downChange =
                                event.changes.firstOrNull { change ->
                                    change.changedToDownIgnoreConsumed()
                                } ?: continue
                            val activeCardBounds = revealedNoteCardBounds
                            val screenBounds = homeScreenBounds
                            val tapPositionInRoot =
                                if (screenBounds != null) {
                                    Offset(
                                        x = screenBounds.left + downChange.position.x,
                                        y = screenBounds.top + downChange.position.y,
                                    )
                                } else {
                                    downChange.position
                                }
                            if (
                                revealedNoteCardId != null &&
                                activeCardBounds != null &&
                                !activeCardBounds.contains(tapPositionInRoot)
                            ) {
                                revealedNoteCardId = null
                            }
                        }
                    }
                },
        containerColor = Color.Transparent,
        bottomBar = {
            if (showSelectionActionBar) {
                HomeSelectionActionBar(
                    visible = state.inSelectionMode,
                    onClearSelection = onClearSelection,
                    onTagSelected = { tagSheetOpen = true },
                    onMarkDoneSelected = onMarkSelectedDone,
                    onArchiveSelected = onArchiveSelected,
                    onTrashSelected = onTrashSelected,
                    bottomPadding = navBarInset + PillBottomBarHeight + PillBottomScrimExtra + 24.dp,
                )
            }
        },
    ) { _ ->
        // Pane mode (showSelectionActionBar = false) has no floating pill over this list,
        // so the pill-sized bottom blur band is dropped.
        val blurMod =
            remember(blurStyle, showSelectionActionBar) {
                blurStyle
                    ?.applyToScrollableList(bottomAlphaMultiplier = if (showSelectionActionBar) 1f else 0f)
                    ?: Modifier
            }
        val topInset = statusBarInset + 68.dp
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
        val itemFadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val itemFadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val itemPlacementSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Height of the area actually visible between the list's top inset and the
            // bottom chrome padding. The empty state centers within THIS, not the raw
            // viewport — otherwise its bottom (subtitle) starts below the fold on short
            // landscape panes. If the content is taller it simply grows and the list
            // scrolls; nothing is ever clipped.
            val emptyStateMinHeight = (maxHeight - topInset - bottomPadding).coerceAtLeast(0.dp)
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (showEmptyState) {
                                Modifier
                            } else {
                                blurMod
                            },
                        ),
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
                            expandedDropdown = expandedFilterDropdown,
                            onExpandedDropdownChange = { dropdown -> expandedFilterDropdown = dropdown },
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
                // After the chips: the placeholder for a pane-mode new note belongs under
                // the filter row, like any other list entry.
                if (pendingNewNoteKind != null) {
                    item(key = "__pending_new_note__", contentType = "pendingNewNote") {
                        PendingNewNoteCard(
                            kind = pendingNewNoteKind,
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
                if (showEmptyState) {
                    item(key = "__empty_state__", contentType = "emptyState") {
                        Box(
                            modifier =
                                Modifier
                                    .heightIn(min = emptyStateMinHeight)
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NotesEmptyState(
                                filter = state.filter,
                                totalUnfilteredNotes = state.totalActive,
                                onCreateNote = onCreateNote,
                                onCreateList = onCreateList,
                                showCreateActions = showSelectionActionBar,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 24.dp),
                            )
                        }
                    }
                } else {
                    items(
                        items = visibleDisplayedItems,
                        key = { item ->
                            when (item) {
                                is HomeListItem.Header -> item.stableKey.hashCode()
                                is HomeListItem.NoteRow ->
                                    if (item.card.id in duplicatedRowNoteIds) {
                                        // Multi-tag note appearing under more than one tag
                                        // group: the prefix disambiguates so LazyColumn does
                                        // not see a duplicate-key crash.
                                        "n:${item.groupKey}:${item.card.id}"
                                    } else {
                                        // Stable across cross-section transitions so
                                        // animateItem slides the card to its new spot.
                                        item.card.id
                                    }
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
                                val noteId = item.card.id
                                val isSelected = noteId in state.selectedIds
                                val isActiveInDetailPane = !state.inSelectionMode && activeNoteId == noteId
                                var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                SwipeableRememberNoteCard(
                                    note = item.note,
                                    model = item.card,
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
                                        Modifier
                                            .onGloballyPositioned { coordinates ->
                                                cardBounds = coordinates.boundsInRoot()
                                                if (revealedNoteCardId == noteId) {
                                                    revealedNoteCardBounds = cardBounds
                                                }
                                            }.animateItem(
                                                fadeInSpec = itemFadeInSpec,
                                                placementSpec = itemPlacementSpec,
                                                fadeOutSpec = itemFadeOutSpec,
                                            ),
                                    selected = isSelected,
                                    activeInDetailPane = isActiveInDetailPane,
                                    onLongClick = { onToggleSelection(noteId) },
                                    activeRevealKey = revealedNoteCardId,
                                    onRevealStarted = { revealedNoteId ->
                                        revealedNoteCardId = revealedNoteId
                                        revealedNoteCardBounds = cardBounds
                                    },
                                    onRevealClosed = { revealedNoteId ->
                                        if (revealedNoteCardId == revealedNoteId) {
                                            revealedNoteCardId = null
                                        }
                                    },
                                    // Swipes are meaningless during bulk-select and would visually
                                    // fight the tap-to-toggle gesture.
                                    swipeEnabled = !state.inSelectionMode,
                                    reminderNotificationsAllowed = notificationsAllowed,
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
                                    model = remember(note) { note.toNoteCardUiModel() },
                                    interaction = interaction,
                                    onOpen = onOpenNote,
                                    onSwipeAction = onSwipeAction,
                                    badgeText = stringResource(R.string.home_search_section_badge_archive),
                                    badgeStyle = SectionBadgeStyle.ARCHIVE,
                                    reminderNotificationsAllowed = notificationsAllowed,
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
                                    model = remember(note) { note.toNoteCardUiModel() },
                                    interaction = interaction,
                                    onOpen = onOpenNote,
                                    onSwipeAction = onSwipeAction,
                                    badgeText = stringResource(R.string.home_search_section_badge_trash),
                                    badgeStyle = SectionBadgeStyle.TRASH,
                                    reminderNotificationsAllowed = notificationsAllowed,
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
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                        .then(topBarOverlayModifier),
                contentAlignment = Alignment.TopEnd,
            ) {
                // In pane mode (showSelectionActionBar = false) the detail pane owns
                // Select all / Cancel selection, so the top-bar swap stays out entirely.
                if (!state.inSelectionMode) {
                    SearchableTopBarTitle(
                        searchOpen = searchOpen,
                        requestSearchFocus = searchShouldRequestFocus,
                        query = state.filter.text,
                        onQueryChange = onQueryChange,
                        onSearchFocusRequested = { searchShouldRequestFocus = false },
                        onToggleSearch = {
                            if (searchOpen && state.filter.text.isNotEmpty()) {
                                onQueryChange("")
                            }
                            val nextSearchOpen = !searchOpen
                            searchOpen = nextSearchOpen
                            if (nextSearchOpen) {
                                searchShouldRequestFocus = true
                            }
                        },
                    )
                } else if (showSelectionActionBar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val actionButtonSize = rememberResponsiveActionButtonSize()
                        val cdSelectAll = stringResource(R.string.home_select_all)
                        Box(modifier = Modifier.size(actionButtonSize)) {
                            RememberFilledTonalIconButton(
                                onClick = { onSelectAllVisible(selectableVisibleIds) },
                                enabled = selectableVisibleIds.isNotEmpty(),
                                modifier = Modifier.align(Alignment.Center).size(actionButtonSize),
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
                        RememberFilledTonalIconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(actionButtonSize),
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "deselect",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdUnselectAll },
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }

    if (tagSheetOpen) {
        BulkTagSheet(
            tagCoverage = bulkTagCoverage,
            selectedNoteCount = state.selectedIds.size,
            onApply = onApplyTagsToSelection,
            onDismiss = { tagSheetOpen = false },
        )
    }
}
