package dev.bikram.remember.ui.edit

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
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
import dev.bikram.remember.ui.theme.LocalSnackbarHostState
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

private enum class FocusField { TITLE, DETAILS }

@Composable
fun EditListRoute(
    appScope: CoroutineScope,
    noteId: Long?,
    forceEdit: Boolean = false,
    showNavigateBack: Boolean = true,
    allowInitialTitleFocus: Boolean = true,
    interceptBack: Boolean = true,
    onPersistedNoteIdChanged: (Long) -> Unit = {},
    onBack: () -> Unit,
    onNavigateUp: () -> Unit = onBack,
) {
    val vm: EditListViewModel = hiltViewModel()
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()
    val currentNoteId by vm.currentNoteId.collectAsStateWithLifecycle()
    val activeTagSuggestions by vm.activeTagSuggestions.collectAsStateWithLifecycle()
    val sharedModifier = Modifier.rememberEditorSharedBoundsModifier(noteId)
    LaunchedEffect(currentNoteId) {
        currentNoteId?.let(onPersistedNoteIdChanged)
    }
    // Report the persisted id before leaving: a save-and-back disposes pane hosts before
    // the currentNoteId LaunchedEffect gets a chance to run, so deliver it synchronously.
    val handleBack = {
        vm.currentNoteId.value?.let(onPersistedNoteIdChanged)
        onBack()
    }
    val handleNavigateUp = {
        vm.currentNoteId.value?.let(onPersistedNoteIdChanged)
        onNavigateUp()
    }

    // The list editor has no body-bridge equivalent of the note editor's ON_STOP/dispose
    // autosave, so add one here: pane hosts dispose this editor when another note is
    // selected, and without this hook those edits would be silently dropped.
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current
    val changesSavedMsg = stringResource(R.string.changes_saved)
    val undoMsg = stringResource(R.string.common_undo)
    val untitledName = stringResource(R.string.edit_list_title_new)
    DisposableEffect(lifecycleOwner, vm, appScope) {
        fun saveNow() {
            appScope.launch {
                val undoAction = vm.saveIfNeeded(untitledName) ?: return@launch
                val result =
                    snackbarHostState.showSnackbar(
                        message = changesSavedMsg,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    undoAction()
                }
            }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    saveNow()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveNow()
        }
    }

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditListScreen(
            vm = vm,
            appScope = appScope,
            existing = noteId != null,
            persistedForToolbar = hasPersistedRow,
            activeTagSuggestions = activeTagSuggestions,
            forceEdit = forceEdit,
            showNavigateBack = showNavigateBack,
            allowInitialTitleFocus = allowInitialTitleFocus,
            interceptBack = interceptBack,
            onBack = handleBack,
            onNavigateUp = handleNavigateUp,
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
    showNavigateBack: Boolean = true,
    allowInitialTitleFocus: Boolean = true,
    interceptBack: Boolean = true,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit = onBack,
) {
    val title by vm.title.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
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
    val createdAt by vm.createdAt.collectAsStateWithLifecycle()
    val updatedAt by vm.updatedAt.collectAsStateWithLifecycle()

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
    var pendingFocusField by remember { mutableStateOf(FocusField.TITLE) }
    var pendingTitleFocusOffset by remember { mutableStateOf<Int?>(null) }
    var pendingItemTitleFocusOffset by remember { mutableStateOf<Int?>(null) }
    var pendingItemDetailsFocusOffset by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            pendingTitleFocusOffset = null
            pendingItemTitleFocusOffset = null
            pendingItemDetailsFocusOffset = null
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val lazyListState = rememberLazyListState()
    var bottomBarVisible by remember { mutableStateOf(true) }

    // Force-show the bar whenever the list is scrolled to the very top. derivedStateOf is
    // cheaper than an observer because it only recomputes when the boolean transitions.
    val atTopOfContent by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
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
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = available.y
                    when {
                        dy < -1f -> bottomBarVisible = false
                        dy > 1f && source == NestedScrollSource.UserInput ->
                            bottomBarVisible = true
                    }
                    return Offset.Zero
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

    val topAlphaMultiplier by remember(lazyListState) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = lazyListState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    val titleCollapseProgress by remember(lazyListState) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = lazyListState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 72.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    val blurMod =
        blurStyle?.applyToFullBleedLayer(topAlphaMultiplier = topAlphaMultiplier)
            ?: Modifier

    // Save on back press. Pane hosts disable interception (except for pending new lists)
    // so system back is not swallowed; the route-level autosave covers persistence there.
    BackHandler(enabled = interceptBack, onBack = editorActions.saveAndBack)

    val (completedItems, activeItems) = items.partition { it.checked }
    val activeParents = activeItems.associateBy { it.localId }
    val checkedParents = completedItems.associateBy { it.localId }
    val activeEntries = remember(items) { buildActiveEntries(activeItems, activeParents, checkedParents) }
    val completedEntries = remember(items) { buildCompletedEntries(completedItems, activeParents, checkedParents) }

    var draggingParentLocalId by remember { mutableStateOf<Long?>(null) }
    val visibleActiveEntries =
        remember(activeEntries, draggingParentLocalId) {
            if (draggingParentLocalId == null) {
                activeEntries
            } else {
                activeEntries.filter { entry ->
                    when (entry) {
                        is ActiveEntry.Ghost -> entry.header.realParentLocalId != draggingParentLocalId
                        is ActiveEntry.Row -> entry.item.localId == draggingParentLocalId || entry.item.parentLocalId != draggingParentLocalId
                    }
                }
            }
        }
    val visibleCompletedEntries =
        remember(completedEntries, draggingParentLocalId) {
            if (draggingParentLocalId == null) {
                completedEntries
            } else {
                completedEntries.filter { entry ->
                    when (entry) {
                        is CompletedEntry.Ghost -> entry.header.realParentLocalId != draggingParentLocalId
                        is CompletedEntry.Row -> entry.item.parentLocalId != draggingParentLocalId
                    }
                }
            }
        }

    val titleFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val detailsFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }

    LaunchedEffect(pendingFocusItemId, isEditMode) {
        if (isEditMode && pendingFocusItemId != null) {
            delay(80)
            val requester =
                if (pendingFocusField == FocusField.DETAILS) {
                    detailsFocusRequesters[pendingFocusItemId]
                } else {
                    titleFocusRequesters[pendingFocusItemId]
                }
            requester?.requestFocus()
            pendingFocusItemId = null
        }
    }

    val saveAndExitEditMode: () -> Unit = {
        isEditMode = false
        editorActions.saveAndShowToast()
    }

    val adaptiveNoteThemes = LocalThemeState.current.adaptiveNoteThemes
    val imageDerivedColors =
        rememberImageDerivedColors(
            imageUri = if (adaptiveNoteThemes) pictureUri else null,
            cacheRevision = pictureRevision,
        )
    val imageResolution = rememberImageDerivedColorScheme(imageDerivedColors)
    var showChecked by rememberSaveable { mutableStateOf(true) }

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
                        existing = existing || persistedForToolbar,
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
                        onTitleFocusChanged = { /* unused */ },
                        showNavigateBack = showNavigateBack,
                        allowInitialTitleFocus = allowInitialTitleFocus,
                        titleCollapseProgress = titleCollapseProgress,
                        onSave = saveAndExitEditMode,
                        onOpenIcon = { iconPickerOpen = true },
                    )
                },
                bottomBar = {
                    val actionBarVisible = bottomBarVisible && !isEditMode && !imeVisible
                    EditorBottomBarSlot(
                        isEditMode = false,
                        actionBarVisible = actionBarVisible,
                        actionContent = {
                            NoteActionBottomBarContent(
                                shelfState = shelfState,
                                existing = persistedForToolbar,
                                isEditMode = isEditMode,
                                starred = starred,
                                completed = completed,
                                onToggleEdit = {
                                    if (!isEditMode) isEditMode = true else saveAndExitEditMode()
                                },
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
                                showEditAction = true,
                            )
                        },
                    )
                },
            ) { padding ->
                val activeIds = activeEntries.filterIsInstance<ActiveEntry.Row>().map { it.item.localId }
                val completedRowIds = completedEntries.filterIsInstance<CompletedEntry.Row>().map { it.item.localId }
                val reorderState =
                    rememberReorderableLazyListState(lazyListState) { from, to ->
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
                                    val titleFocusRequester = remember(item.localId) { FocusRequester() }
                                    val detailsFocusRequester = remember(item.localId) { FocusRequester() }
                                    DisposableEffect(item.localId, titleFocusRequester, detailsFocusRequester) {
                                        titleFocusRequesters[item.localId] = titleFocusRequester
                                        detailsFocusRequesters[item.localId] = detailsFocusRequester
                                        onDispose {
                                            if (titleFocusRequesters[item.localId] === titleFocusRequester) {
                                                titleFocusRequesters.remove(item.localId)
                                            }
                                            if (detailsFocusRequesters[item.localId] === detailsFocusRequester) {
                                                detailsFocusRequesters.remove(item.localId)
                                            }
                                        }
                                    }
                                    ChecklistRow(
                                        item = item,
                                        isEditMode = isEditMode && !readOnly,
                                        focusRequester = titleFocusRequester,
                                        detailsFocusRequester = detailsFocusRequester,
                                        initialTitleSelection = if (pendingFocusItemId == item.localId) pendingItemTitleFocusOffset else null,
                                        initialDetailsSelection = if (pendingFocusItemId == item.localId) pendingItemDetailsFocusOffset else null,
                                        onTitleFocusOffsetConsumed = { pendingItemTitleFocusOffset = null },
                                        onDetailsFocusOffsetConsumed = { pendingItemDetailsFocusOffset = null },
                                        isDragging = isDragging,
                                        dragHandleModifier = if (readOnly) Modifier else Modifier.draggableHandle(),
                                        onTextChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemText(item.localId, it) }),
                                        onDetailsChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemDetails(item.localId, it) }),
                                        onToggle = if (readOnly) ({}) else ({ vm.toggleChecked(item.localId) }),
                                        onRemove = if (readOnly) ({}) else ({ vm.removeItem(item.localId) }),
                                        onNext =
                                            if (readOnly) {
                                                ({})
                                            } else {
                                                (
                                                    {
                                                        pendingFocusItemId = vm.addItemAfter(item.localId)
                                                        pendingFocusField = FocusField.TITLE
                                                        isEditMode = true
                                                    }
                                                )
                                            },
                                        onTextTap =
                                            if (readOnly) {
                                                null
                                            } else {
                                                { offset ->
                                                    pendingFocusItemId = item.localId
                                                    pendingFocusField = FocusField.TITLE
                                                    pendingItemTitleFocusOffset = offset
                                                    isEditMode = true
                                                }
                                            },
                                        onDetailsTap =
                                            if (readOnly) {
                                                null
                                            } else {
                                                { offset ->
                                                    pendingFocusItemId = item.localId
                                                    pendingFocusField = FocusField.DETAILS
                                                    pendingItemDetailsFocusOffset = offset
                                                    isEditMode = true
                                                }
                                            },
                                        onIndentChange =
                                            if (readOnly) {
                                                null
                                            } else {
                                                (
                                                    { deltaDepth ->
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
                                            pendingFocusField = FocusField.TITLE
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
                                        val item = entry.item
                                        val titleFocusRequester = remember(item.localId) { FocusRequester() }
                                        val detailsFocusRequester = remember(item.localId) { FocusRequester() }
                                        DisposableEffect(item.localId, titleFocusRequester, detailsFocusRequester) {
                                            titleFocusRequesters[item.localId] = titleFocusRequester
                                            detailsFocusRequesters[item.localId] = detailsFocusRequester
                                            onDispose {
                                                if (titleFocusRequesters[item.localId] === titleFocusRequester) {
                                                    titleFocusRequesters.remove(item.localId)
                                                }
                                                if (detailsFocusRequesters[item.localId] === detailsFocusRequester) {
                                                    detailsFocusRequesters.remove(item.localId)
                                                }
                                            }
                                        }
                                        ChecklistRow(
                                            item = item,
                                            isEditMode = isEditMode && !readOnly,
                                            focusRequester = titleFocusRequester,
                                            detailsFocusRequester = detailsFocusRequester,
                                            initialTitleSelection = if (pendingFocusItemId == item.localId) pendingItemTitleFocusOffset else null,
                                            initialDetailsSelection = if (pendingFocusItemId == item.localId) pendingItemDetailsFocusOffset else null,
                                            onTitleFocusOffsetConsumed = { pendingItemTitleFocusOffset = null },
                                            onDetailsFocusOffsetConsumed = { pendingItemDetailsFocusOffset = null },
                                            isDragging = false,
                                            dragHandleModifier = Modifier,
                                            showDragHandle = false,
                                            onTextChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemText(item.localId, it) }),
                                            onDetailsChange = if (readOnly) ({ _ -> }) else ({ vm.updateItemDetails(item.localId, it) }),
                                            onToggle = if (readOnly) ({}) else ({ vm.toggleChecked(item.localId) }),
                                            onRemove = if (readOnly) ({}) else ({ vm.removeItem(item.localId) }),
                                            onNext =
                                                if (readOnly) {
                                                    ({})
                                                } else {
                                                    (
                                                        {
                                                            pendingFocusItemId = vm.addItemAfter(item.localId)
                                                            pendingFocusField = FocusField.TITLE
                                                            isEditMode = true
                                                        }
                                                    )
                                                },
                                            onTextTap =
                                                if (readOnly) {
                                                    null
                                                } else {
                                                    { offset ->
                                                        pendingFocusItemId = item.localId
                                                        pendingFocusField = FocusField.TITLE
                                                        pendingItemTitleFocusOffset = offset
                                                        isEditMode = true
                                                    }
                                                },
                                            onDetailsTap =
                                                if (readOnly) {
                                                    null
                                                } else {
                                                    { offset ->
                                                        pendingFocusItemId = item.localId
                                                        pendingFocusField = FocusField.DETAILS
                                                        pendingItemDetailsFocusOffset = offset
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
                            reminders = reminders,
                            importance = importance,
                            visibility = visibility,
                            pictureUri = pictureUri,
                            actions = actions,
                            tags = tags,
                            attachments = attachments,
                            notificationsAllowed = notificationsAllowed,
                            readOnly = readOnly,
                            starred = starred,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
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
                currentReminders = reminders,
                currentIconKey = iconKey,
                currentActions = actions,
                currentTags = tags,
                heroImageContentDescription = stringResource(R.string.viewer_cover_image_cd),
                onReminderChange = vm::setReminders,
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
