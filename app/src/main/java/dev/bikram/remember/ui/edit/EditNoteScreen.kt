package dev.bikram.remember.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.ui.common.NoteAdaptiveTheme
import dev.bikram.remember.ui.common.NotePageBackground
import dev.bikram.remember.ui.common.rememberImageDerivedColorScheme
import dev.bikram.remember.ui.common.rememberImageDerivedColors
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.components.NoteActionBottomBarContent
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EditNoteRoute(
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
    val vm: EditNoteViewModel = hiltViewModel()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val missingNote by vm.missingNote.collectAsStateWithLifecycle()
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()
    val currentNoteId by vm.currentNoteId.collectAsStateWithLifecycle()
    val activeTagSuggestions by vm.activeTagSuggestions.collectAsStateWithLifecycle()
    val sharedModifier = Modifier.rememberEditorSharedBoundsModifier(noteId)
    LaunchedEffect(currentNoteId) {
        currentNoteId?.let(onPersistedNoteIdChanged)
    }
    LaunchedEffect(missingNote) {
        if (missingNote) onBack()
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

    // The list card this screen shares bounds with is clipped to shapes.medium; without a
    // matching clip here, the overlay renders rounded corners throughout the shared-bounds
    // animation and then pops to square corners the instant it hands off to this (unclipped)
    // Box, flickering right at the tail of the transition.
    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize().clip(MaterialTheme.shapes.medium)) {
        if (loaded && !missingNote) {
            EditNoteScreen(
                vm = vm,
                appScope = appScope,
                editorNoteKey = noteId ?: 0L,
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
        } else {
            LoadingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    vm: EditNoteViewModel,
    appScope: CoroutineScope,
    editorNoteKey: Long,
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
    val contentScrollState = rememberScrollState()
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(contentScrollState) {
        derivedStateOf {
            if (contentScrollState.value <= 0) {
                0f
            } else {
                val offsetPx = contentScrollState.value.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val titleCollapseProgress by remember(contentScrollState) {
        derivedStateOf {
            if (contentScrollState.value <= 0) {
                0f
            } else {
                val offsetPx = contentScrollState.value.toFloat()
                val thresholdPx = with(density) { 72.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

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
    var pictureViewer by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val titlePlaceholder =
        if (existing) {
            stringResource(R.string.edit_note_title_existing)
        } else {
            stringResource(R.string.common_title)
        }
    val bodyPlaceholder = stringResource(R.string.edit_note_body_placeholder)

    val blurStyle =
        rememberProgressiveBlurStyle(
            bottomExtra = 0.dp,
            topExtra = 68.dp,
            topBlurProgressPower = 1.1f,
        )
    val blurMod =
        blurStyle?.applyToFullBleedLayer(topAlphaMultiplier = topAlphaMultiplier)
            ?: Modifier

    val archived by vm.archived.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()
    val shelfState =
        when {
            trashed -> NoteShelfState.TRASHED
            archived -> NoteShelfState.ARCHIVED
            else -> NoteShelfState.ACTIVE
        }
    val readOnly = shelfState != NoteShelfState.ACTIVE

    var isEditMode by remember(existing, forceEdit) { mutableStateOf(!existing || forceEdit) }
    var suppressBodyAutoFocusOnEdit by remember { mutableStateOf(false) }
    var pendingTitleFocusOffset by remember { mutableStateOf<Int?>(null) }
    var titleFocused by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    var markdownDisplayMode by rememberSaveable { mutableStateOf(MarkdownEditorDisplayMode.LivePreview) }
    // Force view mode on read-only shelves so pickers and the markdown editor don't accept edits.
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            suppressBodyAutoFocusOnEdit = false
            pendingTitleFocusOffset = null
            titleFocused = false
            bodyFocused = false
        }
    }
    val bodyEditorFocused = isEditMode && bodyFocused && !titleFocused

    val markdownEditorState = remember(editorNoteKey) { MarkdownEditorState() }
    val undoController = remember(editorNoteKey) { UndoRedoController() }
    val markdownSelectionActive by remember {
        derivedStateOf {
            markdownEditorState.selectionRevision
            isEditMode && markdownEditorState.hasSelection
        }
    }

    val bridge =
        rememberEditorBodyBridge(
            vm = vm,
            markdownEditorState = markdownEditorState,
            undoController = undoController,
            isEditMode = isEditMode,
            appScope = appScope,
        )

    val untitledName = stringResource(R.string.edit_note_title_new)
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
            flushPendingEdits = bridge::flush,
        )

    // Backstop for the IME teardown the editor actions already do on every explicit exit:
    // pane hosts dispose this editor without routing through those actions, and a text
    // field that still holds focus at that point strands an input connection in the
    // InputMethodManager. The next home-screen tap then gets consumed reviving it instead
    // of opening the note. Mirrors HomeScreen's observer.
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    // Use the regular BackHandler instead of PredictiveBackHandler. The predictive
    // version suspends on `progress.collect { }` until the gesture's flow completes,
    // so popBackStack does not fire until after the system's predictive preview has
    // already finished animating - that gap reads as a "moment of nothing" before
    // the back transition starts. BackHandler fires immediately on commit, letting
    // the navigation reverse animation pick up where the predictive preview ends.
    // Pane hosts disable interception (except for pending new notes) so system back is
    // not swallowed while the editor lives permanently in the detail pane; autosave on
    // ON_STOP/dispose covers persistence there.
    androidx.activity.compose.BackHandler(enabled = interceptBack, onBack = editorActions.saveAndBack)

    // Hoisted scroll state so the bottom action bar can hide on scroll-down and re-show on
    // scroll-up. Also keyed off IME visibility so the rich-text toolbar has the stage alone
    // when the keyboard is open.
    var bottomBarVisible by remember { mutableStateOf(true) }

    // Force-show the bar whenever content is scrolled to the very top, regardless of any
    // prior hide/show state. derivedStateOf is cheaper than an observer because it only
    // recomputes when scrollState.value transitions across 0.
    val atTopOfContent by remember { derivedStateOf { contentScrollState.value <= 0 } }
    LaunchedEffect(atTopOfContent) {
        if (atTopOfContent) bottomBarVisible = true
    }

    // The previous snapshotFlow-on-scrollState.value observer conflated "user dragged up"
    // with "overscroll spring releasing downward" - both looked like a negative delta, so
    // the bar briefly flashed back during the bounce. Switching to a NestedScrollConnection
    // that filters on NestedScrollSource.UserInput for the SHOW direction means the spring
    // phase (reported as SideEffect) never re-reveals the bar - the overscroll stretch and
    // release land cleanly without the bar flickering.
    val barVisibilityNestedScroll =
        remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    val dy = available.y
                    when {
                        // Content moves up -> user is scrolling DOWN -> hide the bar. Accept
                        // this from any source so a fling that carries scroll-down still hides
                        // the bar.
                        dy < -1f -> bottomBarVisible = false
                        // Content moves down -> user is scrolling UP -> show the bar. Only
                        // honour this for direct user drags; the spring-back and fling
                        // deceleration both come through as SideEffect and would otherwise
                        // flip the bar on during the bounce the user is scrolling away from.
                        dy > 1f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput ->
                            bottomBarVisible = true
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }

    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // Edit mode replaces the action bar: the rich-text formatting toolbar takes the bottom
    // slot and a Save button moves to the top bar. Stacking two bars at the bottom would
    // look like a layout bug. IME visibility also gates the action bar so the keyboard has
    // the stage alone when it's up.
    val actionBarVisible = bottomBarVisible && !isEditMode && !imeVisible

    val launchAttachmentPicker = rememberAttachmentPicker(onAdd = vm::addAttachment)
    val hasPersistedEditorRow = existing || persistedForToolbar
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val pictureRevision by vm.pictureRevision.collectAsStateWithLifecycle()
    val pictureHeroFraming by vm.pictureHeroFraming.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
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
    NoteAdaptiveTheme(imageResolution) {
        Box(Modifier.fillMaxSize()) {
            if (imageResolution != null) NotePageBackground(colorScheme = imageResolution.backgroundScheme)
            Scaffold(
                // This nested scroll hides/shows the bottom action bar with a source filter so
                // overscroll spring-back doesn't flash it back in.
                modifier =
                    Modifier
                        .nestedScroll(barVisibilityNestedScroll),
                containerColor = Color.Transparent,
                topBar = {
                    val title by vm.title.collectAsStateWithLifecycle()
                    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
                    EditorTitleTopBar(
                        contentKind = NoteKind.NOTE,
                        title = title,
                        titlePlaceholder = titlePlaceholder,
                        iconKey = iconKey,
                        existing = hasPersistedEditorRow,
                        isEditMode = isEditMode,
                        readOnly = readOnly,
                        hasUnsavedChanges = hasUnsavedChanges,
                        titleFocusOffset = pendingTitleFocusOffset,
                        onTitleChange = vm::setTitle,
                        onBack = editorActions.saveAndNavigateUp,
                        onTitleTappedInViewMode = { titleOffset ->
                            suppressBodyAutoFocusOnEdit = true
                            pendingTitleFocusOffset = titleOffset
                            isEditMode = true
                        },
                        onTitleFocusOffsetConsumed = {
                            pendingTitleFocusOffset = null
                        },
                        onTitleFocusChanged = { focused ->
                            titleFocused = focused
                            if (focused) bodyFocused = false
                        },
                        showNavigateBack = showNavigateBack,
                        allowInitialTitleFocus = allowInitialTitleFocus,
                        titleCollapseProgress = titleCollapseProgress,
                        onSave = saveAndExitEditMode,
                        onOpenIcon = { iconPickerOpen = true },
                        markdownDisplayMode = if (bodyEditorFocused) markdownDisplayMode else null,
                        onToggleMarkdownDisplayMode = {
                            markdownDisplayMode =
                                if (markdownDisplayMode == MarkdownEditorDisplayMode.MarkdownCode) {
                                    MarkdownEditorDisplayMode.LivePreview
                                } else {
                                    MarkdownEditorDisplayMode.MarkdownCode
                                }
                        },
                    )
                },
                bottomBar = {
                    // Previously this slot rendered as:
                    //     Column {
                    //         EditNoteBottomBarSection(... AnimatedVisibility(isEditMode) ...)
                    //         NoteActionBottomBar(... AnimatedVisibility(actionBarVisible) ...)
                    //     }
                    // which meant when the user tapped Edit, BOTH bars' visibility animations ran
                    // at the same time. The format bar expanded in above the (still visible) action
                    // bar, which then collapsed, leaving the format bar to drop into the now-empty
                    // space - a multi-phase transition that reads as a glitch.
                    //
                    // AnimatedContent gives us a single-slot swap: whichever of Format / Action /
                    // None is the target, only that one is mounted. The enter/exit animations run
                    // against one another on the same surface, driven by the M3E default spatial
                    // spring, so the swap is one smooth vertical cross-fade instead of two
                    // overlapping vertical expand/collapse passes.
                    // Stable callbacks - the MarkdownToolbar / action item rows are lambda-heavy
                    // and re-allocating on every recomposition defeats their skippable-composable
                    // optimization.
                    val onUndo =
                        remember(markdownEditorState, undoController, bridge) {
                            {
                                undoController.undo(markdownEditorState.markdown)?.let { previous ->
                                    markdownEditorState.setMarkdown(previous)
                                    bridge.replaceFromHistory(previous)
                                }
                                Unit
                            }
                        }
                    val onRedo =
                        remember(markdownEditorState, undoController, bridge) {
                            {
                                undoController.redo(markdownEditorState.markdown)?.let { next ->
                                    markdownEditorState.setMarkdown(next)
                                    bridge.replaceFromHistory(next)
                                }
                                Unit
                            }
                        }
                    EditorBottomBarSlot(
                        isEditMode = bodyEditorFocused,
                        actionBarVisible = actionBarVisible,
                        formatContent = {
                            EditNoteFormatBarContent(
                                markdownEditorState = markdownEditorState,
                                undoController = undoController,
                                onUndo = onUndo,
                                onRedo = onRedo,
                                imeVisible = imeVisible,
                            )
                        },
                        actionContent = {
                            NoteActionBottomBarContent(
                                shelfState = shelfState,
                                existing = persistedForToolbar,
                                isEditMode = isEditMode,
                                starred = starred,
                                pinned = pinned,
                                completed = completed,
                                onToggleEdit = {
                                    // Outside edit mode this turns edit mode ON. The SAVE path is
                                    // owned by the top-bar Save icon (edit mode) or by
                                    // back/lifecycle (view mode flush), so there's no save
                                    // side-effect to run here.
                                    if (!isEditMode) isEditMode = true else saveAndExitEditMode()
                                },
                                onToggleStar = { vm.toggleStar() },
                                onTogglePin = {
                                    appScope.launch { vm.togglePinned() }
                                },
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

                EditNoteScrollableContent(
                    vm = vm,
                    modifier = blurMod,
                    horizontalPadding = EditorContentBodyDefaults.HorizontalPadding,
                    padding = padding,
                    markdownEditorState = markdownEditorState,
                    bodyPlaceholder = bodyPlaceholder,
                    isEditMode = isEditMode,
                    markdownDisplayMode = markdownDisplayMode,
                    existing = hasPersistedEditorRow,
                    autoFocusBodyOnEdit = hasPersistedEditorRow && !suppressBodyAutoFocusOnEdit,
                    shelfState = shelfState,
                    pictureViewerOpen = pictureViewer != null,
                    onOpenReminder = {
                        reminderPickerOpen = true
                    },
                    notificationsAllowed = notificationsAllowed,
                    onOpenPicture = heroImagePicker.pickWithPhotoPicker,
                    onBrowsePictureWithApp = heroImagePicker.browseWithApp,
                    onViewPictureFull = { uri, revision ->
                        pictureViewer = uri to revision
                    },
                    onOpenActions = { actionsPickerOpen = true },
                    onOpenTags = { tagsPickerOpen = true },
                    onOpenAttachments = {
                        if (attachments.isEmpty()) {
                            launchAttachmentPicker()
                        } else {
                            attachmentsPickerOpen = true
                        }
                    },
                    onEnterEditModeAtOffset = { markdownOffset ->
                        suppressBodyAutoFocusOnEdit = true
                        pendingTitleFocusOffset = null
                        markdownEditorState.focusAtOffsetAndShowKeyboard(markdownOffset)
                        isEditMode = true
                    },
                    onEnterEditModeSelectingRange = { startOffset, endOffset ->
                        suppressBodyAutoFocusOnEdit = true
                        pendingTitleFocusOffset = null
                        markdownEditorState.focusRangeAndShowKeyboard(startOffset, endOffset)
                        isEditMode = true
                    },
                    onBodyFocusChanged = { focused ->
                        bodyFocused = focused
                        if (focused) titleFocused = false
                    },
                    scrollState = contentScrollState,
                    scrollEnabled = !markdownSelectionActive,
                )
            }

            EditorOptionSheets(
                contentKind = NoteKind.NOTE,
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
