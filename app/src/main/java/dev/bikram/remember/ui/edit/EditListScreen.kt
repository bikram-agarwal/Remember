package dev.bikram.remember.ui.edit

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.domain.checklist.EditableItem
import dev.bikram.remember.ui.common.NoteAdaptiveTheme
import dev.bikram.remember.ui.common.NotePageBackground
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.rememberImageDerivedColorScheme
import dev.bikram.remember.ui.common.rememberImageDerivedColors
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.components.NoteActionBottomBarContent
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import java.io.File

@Composable
fun EditListRoute(
    appScope: CoroutineScope,
    noteId: Long?,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit = onBack,
) {
    val vm: EditListViewModel = hiltViewModel()
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()
    val activeTagSuggestions by vm.activeTagSuggestions.collectAsStateWithLifecycle()
    val sharedModifier = rememberEditorSharedBoundsModifier(noteId)

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditListScreen(
            vm = vm,
            appScope = appScope,
            existing = noteId != null,
            persistedForToolbar = hasPersistedRow,
            activeTagSuggestions = activeTagSuggestions,
            forceEdit = forceEdit,
            onBack = onBack,
            onNavigateUp = onNavigateUp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun EditListScreen(
    vm: EditListViewModel,
    appScope: CoroutineScope,
    existing: Boolean,
    persistedForToolbar: Boolean,
    activeTagSuggestions: List<String>,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit = onBack,
) {
    val title by vm.title.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val recurrence by vm.recurrence.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val visibility by vm.visibility.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val pictureRevision by vm.pictureRevision.collectAsStateWithLifecycle()
    val pictureHeroFraming by vm.pictureHeroFraming.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    var actionsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionSheetOpen by rememberSaveable { mutableStateOf(false) }
    var deleteForeverConfirmOpen by rememberSaveable { mutableStateOf(false) }
    val notificationsAllowed = rememberNotificationsAllowed()

    var pendingHeroSession by remember { mutableStateOf<Pair<String, File?>?>(null) }
    val heroImagePicker =
        rememberHeroImagePickThenCopy { uriString, copiedFile ->
            pendingHeroSession = uriString to copiedFile
        }
    val launchAttachmentPicker = rememberAttachmentPicker(onAdd = vm::addAttachment)
    var pictureViewer by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val titlePlaceholder = stringResource(R.string.common_title)
    val blurStyle =
        rememberProgressiveBlurStyle(
            bottomExtra = 0.dp,
            topExtra = 68.dp,
            topBlurProgressPower = 1.1f,
        )

    val shelfState =
        when {
            trashed -> NoteShelfState.TRASHED
            archived -> NoteShelfState.ARCHIVED
            else -> NoteShelfState.ACTIVE
        }
    val readOnly = shelfState != NoteShelfState.ACTIVE

    var isEditMode by remember(existing, forceEdit) { mutableStateOf(!existing || forceEdit) }
    var pendingFocusItemId by remember { mutableStateOf<Long?>(null) }
    var pendingTitleFocusOffset by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            pendingTitleFocusOffset = null
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val lazyListStateForVisibility =
        androidx.compose.foundation.lazy
            .rememberLazyListState()
    var bottomBarVisible by remember { mutableStateOf(true) }

    // Force-show the bar whenever the list is scrolled to the very top. derivedStateOf is
    // cheaper than an observer because it only recomputes when the boolean transitions.
    val atTopOfContent by remember {
        derivedStateOf {
            lazyListStateForVisibility.firstVisibleItemIndex == 0 &&
                lazyListStateForVisibility.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(atTopOfContent) {
        if (atTopOfContent) bottomBarVisible = true
    }

    // The previous snapshotFlow-on-firstVisibleItem observer conflated "user dragged up"
    // with "overscroll spring releasing downward". Both looked like an upward index delta,
    // so the bar flashed back during the bounce. A NestedScrollConnection that filters the
    // SHOW direction on NestedScrollSource.UserInput means the spring-back phase never
    // re-reveals the bar, giving a clean M3E overscroll feel.
    val barVisibilityNestedScroll =
        remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    val dy = available.y
                    when {
                        dy < -1f -> bottomBarVisible = false
                        dy > 1f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput ->
                            bottomBarVisible = true
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val untitledName = stringResource(R.string.edit_list_title_new)
    val editorActions =
        rememberEditorActionHandlers(
            appScope = appScope,
            archived = archived,
            trashed = trashed,
            untitledName = untitledName,
            onBack = onBack,
            onNavigateUp = onNavigateUp,
            onNotificationPermissionRequired = { notificationPermissionSheetOpen = true },
            saveIfNeeded = vm::saveIfNeeded,
            archiveCurrent = vm::archiveCurrent,
            trashCurrent = vm::trashCurrent,
            unarchiveCurrent = vm::unarchiveCurrent,
            restoreFromTrashCurrent = vm::restoreFromTrashCurrent,
            deleteForeverCurrent = vm::deleteForeverCurrent,
            fireNotification = vm::fireNotification,
        )
    val titleCollapseProgress by remember(lazyListStateForVisibility) {
        derivedStateOf {
            if (lazyListStateForVisibility.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = lazyListStateForVisibility.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 72.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    // Edit mode replaces the action bar: the list's inline "Add item" and the top-bar Save
    // action take over, and the keyboard toolbar stays on the bottom unobstructed. Stacking
    // the shelf action bar under the keyboard would look like a layout bug. IME visibility
    // still gates it so the bar slides out when the keyboard appears.
    val actionBarVisible = bottomBarVisible && !isEditMode && !imeVisible

    // Save path for the top-bar Save icon (only visible while in edit mode): flips edit
    // mode off AND asks the route-level callback to flush the VM and flash the toast so
    // users get feedback that their Save tap did something beyond dismissing the toolbar.
    val saveAndExitEditMode: () -> Unit = {
        isEditMode = false
        editorActions.saveAndShowToast()
    }
    androidx.activity.compose.BackHandler(onBack = editorActions.saveAndBack)

    val adaptiveNoteThemes = LocalThemeState.current.adaptiveNoteThemes
    val imageDerivedColors =
        rememberImageDerivedColors(
            imageUri = if (adaptiveNoteThemes) pictureUri else null,
            cacheRevision = pictureRevision,
        )
    val imageResolution = rememberImageDerivedColorScheme(imageDerivedColors)
    NoteAdaptiveTheme(imageResolution) {
        Box(Modifier.fillMaxSize()) {
            if (imageResolution != null) NotePageBackground(colorScheme = imageResolution.backgroundScheme)
            Scaffold(
                modifier =
                    Modifier
                        .nestedScroll(barVisibilityNestedScroll),
                containerColor = Color.Transparent,
                topBar = {
                    EditorTitleTopBar(
                        contentKind = NoteKind.LIST,
                        title = title,
                        titlePlaceholder = titlePlaceholder,
                        iconKey = iconKey,
                        existing = existing,
                        isEditMode = isEditMode,
                        readOnly = readOnly,
                        hasUnsavedChanges = hasUnsavedChanges,
                        titleFocusOffset = pendingTitleFocusOffset,
                        onTitleChange = vm::setTitle,
                        onBack = editorActions.saveAndNavigateUp,
                        onTitleTappedInViewMode = { titleOffset ->
                            pendingTitleFocusOffset = titleOffset
                            isEditMode = true
                        },
                        onTitleFocusOffsetConsumed = {
                            pendingTitleFocusOffset = null
                        },
                        onOpenIcon = { iconPickerOpen = true },
                        onSave = saveAndExitEditMode,
                        showEditableWhenTitleEmpty = true,
                        titleCollapseProgress = titleCollapseProgress,
                    )
                },
                bottomBar = {
                    EditorBottomBarSlot(
                        isEditMode = isEditMode,
                        actionBarVisible = actionBarVisible,
                        actionContent = {
                            NoteActionBottomBarContent(
                                shelfState = shelfState,
                                existing = persistedForToolbar,
                                isEditMode = isEditMode,
                                starred = starred,
                                completed = completed,
                                // Action bar is hidden while isEditMode, so this callback only fires from
                                // view mode - always turning edit mode ON. Save is owned by the top bar.
                                onToggleEdit = { if (!isEditMode) isEditMode = true else saveAndExitEditMode() },
                                onToggleStar = { vm.toggleStar() },
                                onToggleCompleted = {
                                    appScope.launch { vm.toggleCompleted() }
                                },
                                onArchive = editorActions.archiveAndBack,
                                onNotification = editorActions.notifyOrRequestPermission,
                                onUnarchive = editorActions.unarchive,
                                onTrash = editorActions.trashAndBack,
                                onRestore = editorActions.restore,
                                onDeleteForever = { deleteForeverConfirmOpen = true },
                                showEditAction = false,
                            )
                        },
                    )
                },
            ) { padding ->
                val topAlphaMultiplier by remember(lazyListStateForVisibility) {
                    derivedStateOf {
                        if (lazyListStateForVisibility.firstVisibleItemIndex > 0) {
                            1f
                        } else {
                            val offsetPx = lazyListStateForVisibility.firstVisibleItemScrollOffset.toFloat()
                            val thresholdPx = with(density) { 24.dp.toPx() }
                            (offsetPx / thresholdPx).coerceIn(0f, 1f)
                        }
                    }
                }
                val blurMod =
                    blurStyle?.applyToFullBleedLayer(topAlphaMultiplier = topAlphaMultiplier)
                        ?: Modifier
                val focusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
                var draggingParentLocalId by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(isEditMode, pendingFocusItemId, readOnly) {
                    val itemId = pendingFocusItemId
                    if (isEditMode && itemId != null && !readOnly) {
                        delay(80)
                        focusRequesters[itemId]?.requestFocus()
                        keyboardController?.show()
                        pendingFocusItemId = null
                    }
                }

                // ---------------------------------------------------------------------------------
                // Compose the two weighted sublists per the spec:
                //   activeList    = items.filter { !it.isChecked }.sortedBy { it.sortOrder }
                //   completedList = items.filter {  it.isChecked }.sortedBy { it.sortOrder }
                //
                // The completed list is additionally augmented with "ghost parent" headers: when a
                // checked child's real parent is still in the active section we synthesise a read-only
                // header row above that child's group so the context isn't lost.
                // ---------------------------------------------------------------------------------
                val activeList =
                    remember(items) {
                        items.filter { !it.checked }.sortedBy { it.sortOrder }
                    }
                val completedItems =
                    remember(items) {
                        items.filter { it.checked }.sortedBy { it.sortOrder }
                    }
                val activeParentLookup =
                    remember(activeList) {
                        activeList.filter { it.depth == 0 }.associateBy { it.localId }
                    }
                val checkedParentLookup =
                    remember(completedItems) {
                        completedItems.filter { it.depth == 0 }.associateBy { it.localId }
                    }
                val completedEntries: List<CompletedEntry> =
                    remember(completedItems, activeParentLookup, checkedParentLookup) {
                        buildCompletedEntries(
                            completedItems = completedItems,
                            activeParents = activeParentLookup,
                            checkedParents = checkedParentLookup,
                        )
                    }
                // Mirror of completedEntries for the active half: synthesises a ghost parent header when a
                // child is unchecked but its real parent is still in the completed section. This keeps the
                // parent context visible when the user unchecks a single child out of a cascade-checked
                // group (the user's bug report: "tap B2 to uncheck it, only B2 moves back to unchecked
                // section, without a parent above it").
                val activeEntries: List<ActiveEntry> =
                    remember(activeList, activeParentLookup, checkedParentLookup) {
                        buildActiveEntries(
                            activeItems = activeList,
                            activeParents = activeParentLookup,
                            checkedParents = checkedParentLookup,
                        )
                    }
                val visibleActiveEntries =
                    remember(activeEntries, draggingParentLocalId) {
                        val parentLocalId = draggingParentLocalId
                        if (parentLocalId == null) {
                            activeEntries
                        } else {
                            activeEntries.filterNot { entry ->
                                when (entry) {
                                    is ActiveEntry.Ghost -> true
                                    is ActiveEntry.Row -> entry.item.depth == 1
                                }
                            }
                        }
                    }
                val visibleCompletedEntries =
                    remember(completedEntries, draggingParentLocalId) {
                        val parentLocalId = draggingParentLocalId
                        if (parentLocalId == null) {
                            completedEntries
                        } else {
                            completedEntries.filterNot { entry ->
                                when (entry) {
                                    is CompletedEntry.Ghost -> entry.header.realParentLocalId == parentLocalId
                                    is CompletedEntry.Row -> entry.item.parentLocalId == parentLocalId
                                }
                            }
                        }
                    }
                val activeIds =
                    remember(activeList, draggingParentLocalId) {
                        val parentLocalId = draggingParentLocalId
                        if (parentLocalId == null) {
                            activeList.map { item -> item.localId }
                        } else {
                            activeList
                                .filter { item -> item.depth == 0 }
                                .map { item -> item.localId }
                        }
                    }
                val completedRowIds = remember(completedItems) { completedItems.map { it.localId } }

                var showChecked by rememberSaveable { mutableStateOf(true) }

                val lazyListState = lazyListStateForVisibility
                val reorderState =
                    sh.calvin.reorderable.rememberReorderableLazyListState(lazyListState) { from, to ->
                        // Keys are the EditableItem.localId for both active rows and completed rows. Ghost
                        // headers are keyed with a synthetic "ghost-<parentId>" string so their drags are
                        // ignored here. We only reorder within the matching sublist (no cross-section drags).
                        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
                        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
                        val (list, fromIdx, toIdx) =
                            when {
                                fromId in activeIds && toId in activeIds ->
                                    Triple(activeIds, activeIds.indexOf(fromId), activeIds.indexOf(toId))
                                fromId in completedRowIds && toId in completedRowIds ->
                                    Triple(completedRowIds, completedRowIds.indexOf(fromId), completedRowIds.indexOf(toId))
                                else -> return@rememberReorderableLazyListState
                            }
                        if (fromIdx >= 0 && toIdx >= 0) vm.reorderWithin(list, fromIdx, toIdx)
                    }

                androidx.compose.foundation.lazy.LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(blurMod)
                            .padding(horizontal = EditorContentBodyDefaults.HorizontalPadding),
                ) {
                    // Order: hero image -> shelf banner -> list items. The banner sits right above the
                    // list body so the "why is this read-only" hint is adjacent to the items it gates.
                    editorContentHeaderItems(
                        padding = padding,
                        shelfState = shelfState,
                        heroContent =
                            pictureUri?.let { uri ->
                                {
                                    EditorContentPictureHero(
                                        uri = uri,
                                        pictureRevision = pictureRevision,
                                        pictureHeroFraming = pictureHeroFraming,
                                        viewerOpen = pictureViewer != null,
                                        onOpenFull = { pictureViewer = uri to pictureRevision },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                    )
                                }
                            },
                        bodyTopSpacing = 12.dp,
                    )

                    if (existing && !isEditMode && activeEntries.isEmpty() && completedEntries.isEmpty()) {
                        item(key = "empty_list_view_placeholder") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .tapSoundClickable(
                                            enabled = !readOnly,
                                            onClick = {
                                                isEditMode = true
                                            },
                                        ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_list_empty_view_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.edit_list_empty_view_hint),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = FontStyle.Italic,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleActiveEntries,
                        key = { entry ->
                            when (entry) {
                                is ActiveEntry.Ghost -> "ghost-active-${entry.header.realParentLocalId}"
                                is ActiveEntry.Row -> entry.item.localId
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is ActiveEntry.Ghost ->
                                GhostParentHeaderRow(
                                    header = entry.header,
                                    isParentChecked = entry.header.parentChecked,
                                    // Active rows draw a drag-handle gutter while in edit mode; the ghost
                                    // has to mirror that so its checkbox lines up with the rows below.
                                    showDragHandleGutter = isEditMode,
                                    modifier =
                                        Modifier.animateItem(
                                            placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                        ),
                                )
                            is ActiveEntry.Row -> {
                                val item = entry.item
                                ReorderableItem(
                                    state = reorderState,
                                    key = item.localId,
                                    modifier =
                                        Modifier.animateItem(
                                            placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                        ),
                                ) { isDragging ->
                                    DisposableEffect(isDragging, item.localId) {
                                        val wasDragging = isDragging
                                        if (wasDragging) {
                                            vm.startDragging(item.localId)
                                            if (item.depth == 0) {
                                                draggingParentLocalId = item.localId
                                            }
                                        }
                                        onDispose {
                                            if (wasDragging) {
                                                vm.stopDragging(item.localId)
                                                if (draggingParentLocalId == item.localId) {
                                                    draggingParentLocalId = null
                                                }
                                            }
                                        }
                                    }
                                    val focusRequester = remember(item.localId) { FocusRequester() }
                                    DisposableEffect(item.localId, focusRequester) {
                                        focusRequesters[item.localId] = focusRequester
                                        onDispose {
                                            if (focusRequesters[item.localId] === focusRequester) {
                                                focusRequesters.remove(item.localId)
                                            }
                                        }
                                    }
                                    ChecklistRow(
                                        item = item,
                                        isEditMode = isEditMode && !readOnly,
                                        focusRequester = focusRequester,
                                        isDragging = isDragging,
                                        dragHandleModifier = if (readOnly) Modifier else Modifier.draggableHandle(),
                                        onTextChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemText(item.localId, it) }),
                                        onToggle = if (readOnly) ({}) else ({ vm.toggleChecked(item.localId) }),
                                        onRemove = if (readOnly) ({}) else ({ vm.removeItem(item.localId) }),
                                        onNext =
                                            if (readOnly) {
                                                ({})
                                            } else {
                                                (
                                                    {
                                                        pendingFocusItemId = vm.addItemAfter(item.localId)
                                                        isEditMode = true
                                                    }
                                                )
                                            },
                                        onTextTap =
                                            if (readOnly) {
                                                null
                                            } else {
                                                {
                                                    pendingFocusItemId = item.localId
                                                    isEditMode = true
                                                }
                                            },
                                        onIndentChange =
                                            if (readOnly) {
                                                null
                                            } else {
                                                (
                                                    { deltaDepth ->
                                                        // deltaDepth = +1 means user dragged right (indent); -1 means
                                                        // user dragged left (outdent). We delegate both to the
                                                        // ViewModel so the anchor lookup runs on the freshest
                                                        // in-memory list. Doing the lookup here was buggy: the
                                                        // gesture lives inside a pointerInput(item.localId, depth)
                                                        // block whose captured `activeList` does NOT refresh when
                                                        // siblings are reordered, so swiping right after a drag
                                                        // picked the wrong prior top-level row as the anchor.
                                                        if (deltaDepth > 0) {
                                                            vm.indent(item.localId)
                                                        } else if (deltaDepth < 0) {
                                                            vm.outdent(item.localId)
                                                        }
                                                    }
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }

                    if (isEditMode && !readOnly) {
                        item(key = "add_item_btn") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                        ).tapSoundClickable {
                                            pendingFocusItemId = vm.addItem()
                                            isEditMode = true
                                        }.padding(vertical = 14.dp),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "add",
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.edit_list_add_item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (completedItems.isNotEmpty()) {
                        item(key = "checked_header") {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                        ).tapSoundClickable { showChecked = !showChecked }
                                        .padding(vertical = 8.dp),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = if (showChecked) "expand_more" else "chevron_right",
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    pluralStringResource(R.plurals.checked_items_count, completedItems.size, completedItems.size),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (showChecked) {
                            items(
                                items = visibleCompletedEntries,
                                key = { entry ->
                                    when (entry) {
                                        is CompletedEntry.Ghost -> "ghost-${entry.header.realParentLocalId}"
                                        is CompletedEntry.Row -> entry.item.localId
                                    }
                                },
                            ) { entry ->
                                when (entry) {
                                    is CompletedEntry.Ghost ->
                                        GhostParentHeaderRow(
                                            header = entry.header,
                                            isParentChecked = entry.header.parentChecked,
                                            // Completed rows never render a drag handle, so the ghost in
                                            // the checked section never reserves a gutter either.
                                            showDragHandleGutter = false,
                                            modifier =
                                                Modifier.animateItem(
                                                    placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                                ),
                                        )
                                    is CompletedEntry.Row -> {
                                        // Checked rows are NOT wrapped in ReorderableItem: Google Keep
                                        // freezes the order of checked items and we mirror that. The
                                        // item sort order in completed is implicit (most-recently
                                        // checked last, siblings grouped under their ghost parent).
                                        val item = entry.item
                                        val focusRequester = remember(item.localId) { FocusRequester() }
                                        DisposableEffect(item.localId, focusRequester) {
                                            focusRequesters[item.localId] = focusRequester
                                            onDispose {
                                                if (focusRequesters[item.localId] === focusRequester) {
                                                    focusRequesters.remove(item.localId)
                                                }
                                            }
                                        }
                                        ChecklistRow(
                                            item = item,
                                            isEditMode = isEditMode && !readOnly,
                                            focusRequester = focusRequester,
                                            isDragging = false,
                                            dragHandleModifier = Modifier,
                                            showDragHandle = false,
                                            onTextChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemText(item.localId, it) }),
                                            onToggle = if (readOnly) ({}) else ({ vm.toggleChecked(item.localId) }),
                                            onRemove = if (readOnly) ({}) else ({ vm.removeItem(item.localId) }),
                                            onNext =
                                                if (readOnly) {
                                                    ({})
                                                } else {
                                                    (
                                                        {
                                                            pendingFocusItemId = vm.addItemAfter(item.localId)
                                                            isEditMode = true
                                                        }
                                                    )
                                                },
                                            onTextTap =
                                                if (readOnly) {
                                                    null
                                                } else {
                                                    {
                                                        pendingFocusItemId = item.localId
                                                        isEditMode = true
                                                    }
                                                },
                                            onIndentChange = null,
                                            modifier =
                                                Modifier.animateItem(
                                                    placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    editorContentOptionsItem(padding = padding) {
                        Spacer(Modifier.height(20.dp))
                        EditorOptionsPanel(
                            reminderAt = reminderAt,
                            recurrence = recurrence,
                            importance = importance,
                            visibility = visibility,
                            pictureUri = pictureUri,
                            actions = actions,
                            tags = tags,
                            attachments = attachments,
                            notificationsAllowed = notificationsAllowed,
                            readOnly = readOnly,
                            starred = starred,
                            onOpenReminder = { reminderPickerOpen = true },
                            onImportanceChange = vm::setImportance,
                            onVisibilityChange = vm::setVisibility,
                            onOpenPicture = heroImagePicker.pickWithPhotoPicker,
                            onBrowsePictureWithApp = heroImagePicker.browseWithApp,
                            onOpenActions = { actionsPickerOpen = true },
                            onOpenTags = { tagsPickerOpen = true },
                            onOpenAttachmentsSheet = { attachmentsPickerOpen = true },
                            onPickAttachment = launchAttachmentPicker,
                        )
                    }
                }
            }

            EditorOptionSheets(
                contentKind = NoteKind.LIST,
                reminderPickerOpen = reminderPickerOpen,
                iconPickerOpen = iconPickerOpen,
                actionsPickerOpen = actionsPickerOpen,
                tagsPickerOpen = tagsPickerOpen,
                attachmentsPickerOpen = attachmentsPickerOpen,
                notificationPermissionSheetOpen = notificationPermissionSheetOpen,
                deleteForeverConfirmOpen = deleteForeverConfirmOpen,
                heroImagePicker = heroImagePicker,
                pendingHeroSession = pendingHeroSession,
                pictureViewer = pictureViewer,
                currentPictureHeroFraming = pictureHeroFraming,
                readOnly = readOnly,
                activeTagSuggestions = activeTagSuggestions,
                attachments = attachments,
                currentReminderAt = reminderAt,
                currentRecurrence = recurrence,
                currentIconKey = iconKey,
                currentActions = actions,
                currentTags = tags,
                heroImageContentDescription = stringResource(R.string.viewer_cover_image_cd),
                onReminderChange = vm::setReminder,
                onIconKeyChange = vm::setIconKey,
                onActionsChange = vm::setActions,
                onTagsWithColorsChange = vm::saveTagsWithColors,
                onEditExistingTag = vm::editExistingTag,
                onAddAttachment = vm::addAttachment,
                onRemoveAttachment = vm::removeAttachment,
                onHeroCommitted = vm::setHeroWithFraming,
                onPictureChange = vm::setPictureUri,
                onDeleteForever = editorActions.deleteForeverAndBack,
                onDismissReminder = { reminderPickerOpen = false },
                onDismissIcon = { iconPickerOpen = false },
                onDismissActions = { actionsPickerOpen = false },
                onDismissTags = { tagsPickerOpen = false },
                onDismissAttachments = { attachmentsPickerOpen = false },
                onDismissNotificationPermission = { notificationPermissionSheetOpen = false },
                onDismissPendingHero = { pendingHeroSession = null },
                onDismissDeleteForever = { deleteForeverConfirmOpen = false },
                onDismissPictureViewer = { pictureViewer = null },
            )
        }
    }
}
