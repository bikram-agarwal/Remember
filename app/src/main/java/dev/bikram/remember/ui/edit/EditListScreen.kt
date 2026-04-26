package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bikram.remember.R
import dev.bikram.remember.domain.checklist.EditableItem
import dev.bikram.remember.ui.common.FullScreenHeroImageOverlay
import dev.bikram.remember.ui.common.HeroFramingEditorDialog
import dev.bikram.remember.ui.common.HeroFramedImage
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.Visibility as NoteVisibility
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.components.ArchivedBanner
import dev.bikram.remember.ui.components.ArchivedBannerState
import dev.bikram.remember.ui.components.NoteActionBottomBar
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.components.TagAccentEditorStrip
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable


@Composable
fun EditListRoute(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    appScope: CoroutineScope,
    noteId: Long?,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
) {
    val vm: EditListViewModel = viewModel(
        key = "editList-${noteId ?: 0L}",
        factory = EditListViewModel.factory(repository, themePrefs, noteId),
    )
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val recurrence by vm.recurrence.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val visibility by vm.visibility.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val pictureRevision by vm.pictureRevision.collectAsStateWithLifecycle()
    val pictureHeroFraming by vm.pictureHeroFraming.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()

    val snackbarHostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current
    val changesSavedMsg = androidx.compose.ui.res.stringResource(dev.bikram.remember.R.string.changes_saved)
    val undoMsg = androidx.compose.ui.res.stringResource(dev.bikram.remember.R.string.common_undo)
    val untitledName = androidx.compose.ui.res.stringResource(dev.bikram.remember.R.string.edit_list_title_new)
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedModifier = if (sharedScope != null && navScope != null && noteId != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-card-${noteId}"),
                animatedVisibilityScope = navScope,
            )
        }
    } else {
        Modifier
    }

    // Save path used by the top-bar Save icon while in edit mode. Runs saveIfNeeded
    // explicitly (vs. waiting for dispose) and flashes a toast so users get feedback
    // that their Save tap did something beyond flipping edit mode off.
    val onExplicitSave: () -> Unit = {
        appScope.launch {
            if (vm.saveIfNeeded(untitledName) != null) {
                android.widget.Toast.makeText(
                    context,
                    changesSavedMsg,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    DisposableEffect(vm) {
        onDispose { 
            appScope.launch { 
                val undoAction = vm.saveIfNeeded(untitledName)
                if (undoAction != null) {
                    val result = snackbarHostState.showSnackbar(
                        message = changesSavedMsg,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        undoAction()
                    }
                }
            } 
        }
    }

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditListScreen(
            repository = repository,
            title = title,
            favorite = favorite,
            items = items,
            reminderAt = reminderAt,
            recurrence = recurrence,
            importance = importance,
            visibility = visibility,
            locked = locked,
            pictureUri = pictureUri,
            pictureRevision = pictureRevision,
            pictureHeroFraming = pictureHeroFraming,
            iconKey = iconKey,
            actions = actions,
            tags = tags,
            attachments = attachments,
            archived = archived,
            trashed = trashed,
            existing = noteId != null,
            persistedForToolbar = hasPersistedRow,
            forceEdit = forceEdit,
            onTitleChange = vm::setTitle,
            onToggleFavorite = vm::toggleFavorite,
            completed = completed,
            onToggleCompleted = { appScope.launch { vm.toggleCompleted() } },
            onAddItem = vm::addItem,
            onItemTextChange = vm::updateItemText,
            onItemToggle = vm::toggleChecked,
            onItemRemove = vm::removeItem,
            onReorderWithin = vm::reorderWithin,
            onSetParent = vm::setParent,
            onIndent = vm::indent,
            onOutdent = vm::outdent,
            onReminderChange = vm::setReminder,
            onImportanceChange = vm::setImportance,
            onVisibilityChange = vm::setVisibility,
            onToggleLock = vm::toggleLock,
            onPictureChange = vm::setPictureUri,
            onHeroCommitted = vm::setHeroWithFraming,
            onIconKeyChange = vm::setIconKey,
            onActionsChange = vm::setActions,
            onTagsChange = vm::setTags,
            onTagsWithColorsChange = vm::saveTagsWithColors,
            onEditExistingTag = vm::editExistingTag,
            onAddAttachment = vm::addAttachment,
            onRemoveAttachment = vm::removeAttachment,
            onTrash = {
                appScope.launch { vm.trashCurrent() }
                onBack()
            },
            onArchive = { appScope.launch { vm.archiveCurrent(untitledName) } },
            onNotification = { appScope.launch { vm.fireNotification(context, untitledName) } },
            onUnarchive = { appScope.launch { vm.unarchiveCurrent() } },
            onRestore = { appScope.launch { vm.restoreFromTrashCurrent() } },
            onDeleteForever = {
                appScope.launch { vm.deleteForeverCurrent() }
                onBack()
            },
            onBack = onBack,
            onSave = onExplicitSave,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditListScreen(
    repository: NoteRepository,
    title: String,
    favorite: Boolean,
    items: List<EditableItem>,
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    importance: Importance,
    visibility: NoteVisibility,
    locked: Boolean,
    pictureUri: String?,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    iconKey: String?,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    archived: Boolean,
    trashed: Boolean,
    existing: Boolean,
    persistedForToolbar: Boolean,
    forceEdit: Boolean = false,
    completed: Boolean,
    onTitleChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleCompleted: () -> Unit,
    onAddItem: () -> Unit,
    onItemTextChange: (Long, String) -> Unit,
    onItemToggle: (Long) -> Unit,
    onItemRemove: (Long) -> Unit,
    /** Reorders within a single sublist. [visibleIds] is the filtered+sorted ordering the user
     *  actually sees (active OR completed), and from/to are indices within that list. */
    onReorderWithin: (visibleIds: List<Long>, fromIndex: Int, toIndex: Int) -> Unit,
    /** Horizontal drag re-parent. `newParentLocalId = null` promotes back to top-level. */
    onSetParent: (localId: Long, newParentLocalId: Long?) -> Unit,
    /**
     * Horizontal swipe-right indent. Picks the anchor parent from the CURRENT ViewModel state
     * so it can't see a stale `activeList` snapshot captured by a pointerInput closure. Swipe-
     * left is [onOutdent]. These replace the old "compute anchor in the UI" approach that
     * silently reparented rows under the wrong sibling after a drag reorder.
     */
    onIndent: (localId: Long) -> Unit,
    onOutdent: (localId: Long) -> Unit,
    onReminderChange: (Long?, RecurrenceRule?) -> Unit,
    onImportanceChange: (Importance) -> Unit,
    onVisibilityChange: (NoteVisibility) -> Unit,
    onToggleLock: () -> Unit,
    onPictureChange: (String?) -> Unit,
    onHeroCommitted: (String, HeroFraming) -> Unit,
    onIconKeyChange: (String?) -> Unit,
    onActionsChange: (List<NoteAction>) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onTagsWithColorsChange: (List<String>, Map<String, String>) -> Unit,
    onEditExistingTag: (String, String, String?, Boolean) -> Unit,
    onAddAttachment: (Uri, String, String?) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onTrash: () -> Unit,
    onArchive: () -> Unit,
    onNotification: () -> Unit,
    onUnarchive: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit = {},
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    var actionsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var deleteForeverConfirmOpen by rememberSaveable { mutableStateOf(false) }

    var pendingHeroSession by remember { mutableStateOf<Pair<String, File?>?>(null) }
    val launchHeroImagePick = rememberHeroImagePickThenCopy { uriString, copiedFile ->
        pendingHeroSession = uriString to copiedFile
    }
    var pictureViewer by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val titlePlaceholder = if (existing) stringResource(R.string.edit_list_title_new) else stringResource(R.string.edit_list_title_new)
    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = 0.dp)

    val shelfState = when {
        trashed -> NoteShelfState.TRASHED
        archived -> NoteShelfState.ARCHIVED
        else -> NoteShelfState.ACTIVE
    }
    val readOnly = shelfState != NoteShelfState.ACTIVE

    var isEditMode by remember(existing, forceEdit) { mutableStateOf(!existing || forceEdit) }
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }

    val newListTitleFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(existing, isEditMode, readOnly) {
        if (!existing && isEditMode && !readOnly) {
            delay(80)
            newListTitleFocus.requestFocus()
            keyboardController?.show()
        }
    }

    val lazyListStateForVisibility = androidx.compose.foundation.lazy.rememberLazyListState()
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
    val barVisibilityNestedScroll = remember {
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
        onSave()
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(barVisibilityNestedScroll)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    val collapseFraction = scrollBehavior.state.collapsedFraction
                    val expandedStyle = MaterialTheme.typography.headlineMedium
                    val collapsedStyle = MaterialTheme.typography.titleLarge
                    val titleStyle = expandedStyle.copy(
                        fontSize = androidx.compose.ui.unit.lerp(
                            expandedStyle.fontSize,
                            collapsedStyle.fontSize,
                            collapseFraction,
                        ),
                        lineHeight = androidx.compose.ui.unit.lerp(
                            expandedStyle.lineHeight,
                            collapsedStyle.lineHeight,
                            collapseFraction,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val iconSize = androidx.compose.ui.unit.lerp(28.dp, 22.dp, collapseFraction)
                    val iconGap = androidx.compose.ui.unit.lerp(12.dp, 8.dp, collapseFraction)
                    val headerSymbol = iconSymbolName(iconKey)
                    val headerBrandDrawable = iconDrawableRes(iconKey)
                    val headerEmoji = iconEmojiPayload(iconKey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (headerSymbol != null) {
                            RememberMaterialRoundedSymbol(
                                name = headerSymbol,
                                size = iconSize,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(iconGap))
                        } else if (headerBrandDrawable != null) {
                            Icon(
                                painterResource(headerBrandDrawable),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(iconSize),
                            )
                            Spacer(Modifier.width(iconGap))
                        } else if (headerEmoji != null) {
                            Text(
                                text = headerEmoji,
                                style = titleStyle.copy(fontSize = iconSize.value.sp),
                            )
                            Spacer(Modifier.width(iconGap))
                        } else {
                            RememberMaterialRoundedSymbol(
                                name = DEFAULT_LIST_HEADER_SYMBOL,
                                size = iconSize,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(iconGap))
                        }
                        if ((isEditMode && !readOnly) || title.isEmpty()) {
                            BasicTextField(
                                value = title,
                                onValueChange = { if (it.length <= 80) onTitleChange(it) },
                                textStyle = titleStyle,
                                enabled = !readOnly,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(newListTitleFocus),
                                decorationBox = { inner ->
                                    if (title.isEmpty()) {
                                        Text(
                                            text = titlePlaceholder,
                                            style = titleStyle.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                },
                            )
                        } else {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = title,
                                    style = titleStyle,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    RememberIconButton(onClick = onBack) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            weight = FontWeight.Medium,
                        )
                    }
                },
                actions = {
                    // In edit mode the NoteActionBottomBar slides out, so Save lives here.
                    // Outside edit mode this slot stays empty and the action bar handles
                    // Edit / Favorite / Archive / Trash.
                    if (isEditMode && !readOnly) {
                        val saveCd = stringResource(R.string.edit_save_cd)
                        RememberIconButton(
                            onClick = saveAndExitEditMode,
                            modifier = Modifier.semantics { contentDescription = saveCd },
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "check",
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NoteActionBottomBar(
                shelfState = shelfState,
                existing = persistedForToolbar,
                isEditMode = isEditMode,
                favorite = favorite,
                completed = completed,
                visible = actionBarVisible,
                // Action bar is hidden while isEditMode, so this callback only fires from
                // view mode - always turning edit mode ON. Save is owned by the top bar.
                onToggleEdit = { if (!isEditMode) isEditMode = true else saveAndExitEditMode() },
                onToggleFavorite = onToggleFavorite,
                onToggleCompleted = onToggleCompleted,
                onArchive = onArchive,
                onNotification = onNotification,
                onUnarchive = onUnarchive,
                onTrash = onTrash,
                onRestore = onRestore,
                onDeleteForever = { deleteForeverConfirmOpen = true },
            )
        },
    ) { padding ->
        val blurMod = blurStyle?.applyToFullBleedLayer() ?: Modifier
        val focusRequesters = remember { mutableMapOf<Long, androidx.compose.ui.focus.FocusRequester>() }
        var previousItemCount by remember { mutableStateOf(items.size) }
        var expectingNewItem by remember { mutableStateOf(false) }

        LaunchedEffect(items) {
            if (expectingNewItem && items.size > previousItemCount) {
                items.lastOrNull()?.localId?.let { id ->
                    focusRequesters[id]?.requestFocus()
                }
                expectingNewItem = false
            }
            previousItemCount = items.size
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
        val activeList = remember(items) {
            items.filter { !it.checked }.sortedBy { it.sortOrder }
        }
        val completedItems = remember(items) {
            items.filter { it.checked }.sortedBy { it.sortOrder }
        }
        val activeParentLookup = remember(activeList) {
            activeList.filter { it.depth == 0 }.associateBy { it.localId }
        }
        val checkedParentLookup = remember(completedItems) {
            completedItems.filter { it.depth == 0 }.associateBy { it.localId }
        }
        val completedEntries: List<CompletedEntry> = remember(completedItems, activeParentLookup, checkedParentLookup) {
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
        val activeEntries: List<ActiveEntry> = remember(activeList, activeParentLookup, checkedParentLookup) {
            buildActiveEntries(
                activeItems = activeList,
                activeParents = activeParentLookup,
                checkedParents = checkedParentLookup,
            )
        }
        val activeIds = remember(activeList) { activeList.map { it.localId } }
        val completedRowIds = remember(completedItems) { completedItems.map { it.localId } }

        var showChecked by rememberSaveable { mutableStateOf(true) }

        val lazyListState = lazyListStateForVisibility
        val reorderState = sh.calvin.reorderable.rememberReorderableLazyListState(lazyListState) { from, to ->
            // Keys are the EditableItem.localId for both active rows and completed rows. Ghost
            // headers are keyed with a synthetic "ghost-<parentId>" string so their drags are
            // ignored here. We only reorder within the matching sublist (no cross-section drags).
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val (list, fromIdx, toIdx) = when {
                fromId in activeIds && toId in activeIds ->
                    Triple(activeIds, activeIds.indexOf(fromId), activeIds.indexOf(toId))
                fromId in completedRowIds && toId in completedRowIds ->
                    Triple(completedRowIds, completedRowIds.indexOf(fromId), completedRowIds.indexOf(toId))
                else -> return@rememberReorderableLazyListState
            }
            if (fromIdx >= 0 && toIdx >= 0) onReorderWithin(list, fromIdx, toIdx)
        }

        androidx.compose.foundation.lazy.LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod)
                .padding(horizontal = 20.dp),
        ) {
            item(key = "top_padding") {
                Spacer(Modifier.height(padding.calculateTopPadding()))
            }
            // Order: tag color strip -> hero image -> shelf banner -> list items. The banner
            // sits right above the list body so the "why is this read-only" hint is adjacent
            // to the items it gates, not buried above decorative chrome.
            item(key = "tag_strip") {
                TagAccentEditorStrip(tags = tags)
            }
            if (pictureUri != null) {
                item(key = "picture_hero") {
                    Spacer(Modifier.height(16.dp))
                    PictureHero(
                        uri = pictureUri,
                        pictureRevision = pictureRevision,
                        pictureHeroFraming = pictureHeroFraming,
                        viewerOpen = pictureViewer != null,
                        onOpenFull = { pictureViewer = pictureUri to pictureRevision },
                    )
                }
            }
            if (shelfState != NoteShelfState.ACTIVE) {
                item(key = "archived_banner") {
                    Spacer(Modifier.height(16.dp))
                    ArchivedBanner(
                        state = if (shelfState == NoteShelfState.TRASHED) {
                            ArchivedBannerState.TRASHED
                        } else {
                            ArchivedBannerState.ARCHIVED
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "items_spacing") {
                Spacer(Modifier.height(12.dp))
            }
            
            items(
                items = activeEntries,
                key = { entry ->
                    when (entry) {
                        is ActiveEntry.Ghost -> "ghost-active-${entry.header.realParentLocalId}"
                        is ActiveEntry.Row -> entry.item.localId
                    }
                }
            ) { entry ->
                when (entry) {
                    is ActiveEntry.Ghost -> GhostParentHeaderRow(
                        header = entry.header,
                        isParentChecked = entry.header.parentChecked,
                        // Active rows draw a drag-handle gutter while in edit mode; the ghost
                        // has to mirror that so its checkbox lines up with the rows below.
                        showDragHandleGutter = isEditMode,
                        modifier = Modifier.animateItem(
                            placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                        ),
                    )
                    is ActiveEntry.Row -> {
                        val item = entry.item
                        ReorderableItem(
                            state = reorderState,
                            key = item.localId,
                            modifier = Modifier.animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            )
                        ) { isDragging ->
                            val fr = focusRequesters.getOrPut(item.localId) { androidx.compose.ui.focus.FocusRequester() }
                            ChecklistRow(
                                item = item,
                                isEditMode = isEditMode && !readOnly,
                                focusRequester = fr,
                                isDragging = isDragging,
                                dragHandleModifier = if (readOnly) Modifier else Modifier.draggableHandle(),
                                onTextChange = if (readOnly) ({ _ -> }) else ({ onItemTextChange(item.localId, it) }),
                                onToggle = if (readOnly) ({}) else ({ onItemToggle(item.localId) }),
                                onRemove = if (readOnly) ({}) else ({ onItemRemove(item.localId) }),
                                onNext = if (readOnly) ({}) else ({
                                    expectingNewItem = true
                                    onAddItem()
                                }),
                                onIndentChange = if (readOnly) null else ({ deltaDepth ->
                                    // deltaDepth = +1 means user dragged right (indent); -1 means
                                    // user dragged left (outdent). We delegate both to the
                                    // ViewModel so the anchor lookup runs on the freshest
                                    // in-memory list. Doing the lookup here was buggy: the
                                    // gesture lives inside a pointerInput(item.localId, depth)
                                    // block whose captured `activeList` does NOT refresh when
                                    // siblings are reordered, so swiping right after a drag
                                    // picked the wrong prior top-level row as the anchor.
                                    if (deltaDepth > 0) onIndent(item.localId)
                                    else if (deltaDepth < 0) onOutdent(item.localId)
                                }),
                            )
                        }
                    }
                }
            }

            if (isEditMode && !readOnly) {
                item(key = "add_item_btn") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            )
                            .tapSoundClickable {
                                expectingNewItem = true
                                onAddItem()
                            }
                            .padding(vertical = 14.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            )
                            .tapSoundClickable { showChecked = !showChecked }
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
                            stringResource(R.string.checked_items_count, completedItems.size),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (showChecked) {
                    items(
                        items = completedEntries,
                        key = { entry ->
                            when (entry) {
                                is CompletedEntry.Ghost -> "ghost-${entry.header.realParentLocalId}"
                                is CompletedEntry.Row -> entry.item.localId
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is CompletedEntry.Ghost -> GhostParentHeaderRow(
                                header = entry.header,
                                isParentChecked = entry.header.parentChecked,
                                // Completed rows never render a drag handle, so the ghost in
                                // the checked section never reserves a gutter either.
                                showDragHandleGutter = false,
                                modifier = Modifier.animateItem(
                                    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                ),
                            )
                            is CompletedEntry.Row -> {
                                // Checked rows are NOT wrapped in ReorderableItem: Google Keep
                                // freezes the order of checked items and we mirror that. The
                                // item sort order in completed is implicit (most-recently
                                // checked last, siblings grouped under their ghost parent).
                                val item = entry.item
                                val fr = focusRequesters.getOrPut(item.localId) { androidx.compose.ui.focus.FocusRequester() }
                                ChecklistRow(
                                    item = item,
                                    isEditMode = isEditMode && !readOnly,
                                    focusRequester = fr,
                                    isDragging = false,
                                    dragHandleModifier = Modifier,
                                    showDragHandle = false,
                                    onTextChange = if (readOnly) ({ _ -> }) else ({ onItemTextChange(item.localId, it) }),
                                    onToggle = if (readOnly) ({}) else ({ onItemToggle(item.localId) }),
                                    onRemove = if (readOnly) ({}) else ({ onItemRemove(item.localId) }),
                                    onNext = if (readOnly) ({}) else ({
                                        expectingNewItem = true
                                        onAddItem()
                                    }),
                                    onIndentChange = null,
                                    modifier = Modifier.animateItem(
                                        placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            
            item(key = "options_panel") {
                Spacer(Modifier.height(20.dp))
                OptionsPanel(
                    reminderAt = reminderAt,
                    recurrence = recurrence,
                    importance = importance,
                    pictureUri = pictureUri,
                    iconKey = iconKey,
                    isChecklist = true,
                    actions = actions,
                    tags = tags,
                    attachmentCount = attachments.size,
                    onOpenReminder = if (readOnly) ({}) else ({ reminderPickerOpen = true }),
                    onSetImportance = if (readOnly) ({ _ -> }) else onImportanceChange,
                    onOpenPicture = if (readOnly) ({}) else launchHeroImagePick,
                    onOpenIcon = if (readOnly) ({}) else ({ iconPickerOpen = true }),
                    onOpenActions = if (readOnly) ({}) else ({ actionsPickerOpen = true }),
                    onOpenTags = if (readOnly) ({}) else ({ tagsPickerOpen = true }),
                    onOpenAttachments = if (readOnly) ({}) else ({ attachmentsPickerOpen = true }),
                    readOnly = readOnly,
                )
                Spacer(Modifier.height(40.dp + padding.calculateBottomPadding()))
            }
        }

        if (reminderPickerOpen) {
            ReminderPickerSheet(
                initialMillis = reminderAt,
                initialRule = recurrence,
                onConfirm = { at, rule ->
                    onReminderChange(at, rule)
                    reminderPickerOpen = false
                },
                onDismiss = { reminderPickerOpen = false },
            )
        }
        if (iconPickerOpen) {
            IconPicker(
                current = iconKey,
                onPick = { onIconKeyChange(it); iconPickerOpen = false },
                onDismiss = { iconPickerOpen = false },
                isChecklist = true,
            )
        }
        if (actionsPickerOpen) {
            ActionPicker(
                current = actions,
                onConfirm = { onActionsChange(it); actionsPickerOpen = false },
                onDismiss = { actionsPickerOpen = false },
            )
        }
        if (tagsPickerOpen) {
            TagEditorSheet(
                initial = tags,
                repository = repository,
                onConfirm = { newTags, newColors ->
                    onTagsWithColorsChange(newTags, newColors)
                    tagsPickerOpen = false
                },
                onEditExistingTag = onEditExistingTag,
                onDismiss = { tagsPickerOpen = false },
            )
        }
        if (attachmentsPickerOpen) {
            AttachmentsSheet(
                attachments = attachments,
                onDismiss = { attachmentsPickerOpen = false },
                onAdd = { uri, name, mime -> onAddAttachment(uri, name, mime) },
                onRemove = onRemoveAttachment,
            )
        }
        val viewerForOverlay = pictureViewer
        FullScreenHeroImageOverlay(
            visible = viewerForOverlay != null,
            imageUri = viewerForOverlay?.first,
            imageCacheRevision = viewerForOverlay?.second ?: 0L,
            imageContentDescription = stringResource(R.string.viewer_cover_image_cd),
        sharedElementKey = viewerForOverlay?.first?.let { uri -> "hero-image-$uri" },
            onDismiss = { pictureViewer = null },
            // Delete is only reachable on the active shelf - archived/trashed lists are
            // read-only, so there's no delete affordance there.
            onDelete = if (readOnly) null else ({ onPictureChange(null) }),
        )
        pendingHeroSession?.let { (pickedUri, copiedFile) ->
            HeroFramingEditorDialog(
                imageUri = pickedUri,
                pendingCopiedFile = copiedFile,
                initialFraming = null,
                onDismiss = {
                    copiedFile?.delete()
                    pendingHeroSession = null
                },
                onConfirm = { framing ->
                    onHeroCommitted(pickedUri, framing)
                    pendingHeroSession = null
                },
            )
        }
        if (deleteForeverConfirmOpen) {
            AlertDialog(
                onDismissRequest = { deleteForeverConfirmOpen = false },
                title = { Text(stringResource(R.string.edit_delete_forever_dialog_title)) },
                text = { Text(stringResource(R.string.edit_delete_forever_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteForeverConfirmOpen = false
                        onDeleteForever()
                    }) {
                        Text(stringResource(R.string.edit_delete_forever_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteForeverConfirmOpen = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun PictureHero(
    uri: String,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    viewerOpen: Boolean,
    onOpenFull: () -> Unit,
) {
    val framing = remember(pictureHeroFraming) { HeroFraming.fromJsonString(pictureHeroFraming) }
    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current

    // Same-screen container transform requires both ends of sharedBounds to live in
    // coordinated AnimatedVisibility / AnimatedContent scopes. The destination's nav
    // scope is "always visible" while we're on this screen, so keying the inline hero
    // to it leaves both copies (inline + overlay) reporting visibility at the same
    // time and the bounds animation has no clean source-to-target driver. We wrap the
    // inline hero in its own AnimatedVisibility(visible = !viewerOpen) and use that
    // scope so opening the viewer cleanly hands the shared element off to the overlay.
    //
    // Outer Box keeps the layout slot at a constant height; only the inline content
    // toggles, so the surrounding lazy column never reflows mid-transition.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        AnimatedVisibility(
            visible = !viewerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val sharedModifier = if (sharedScope != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "hero-image-$uri"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                    )
                }
            } else {
                Modifier
            }
            // No delete overlay on the inline hero: it competes visually with the
            // hero image and invites accidental taps. Delete lives in the full-screen
            // viewer.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sharedModifier)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .tapSoundClickable(onClick = onOpenFull),
            ) {
                HeroFramedImage(
                    imageUri = uri,
                    framing = framing,
                    cacheRevision = pictureRevision,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
