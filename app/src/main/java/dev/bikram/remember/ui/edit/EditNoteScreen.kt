package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.FullScreenHeroImageOverlay
import dev.bikram.remember.ui.common.HeroFramingEditorDialog
import dev.bikram.remember.ui.components.NoteActionBottomBarContent
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Target slot in the editor's bottom bar. Exactly one of these is ever mounted at a
 * time, driven by [AnimatedContent] so the transition between view-mode action bar
 * and edit-mode format bar is a single synchronized M3E spatial-spring swap rather
 * than two overlapping visibility animations.
 */
private enum class EditorBottomSlot { Format, Action, None }

@Composable
fun EditNoteRoute(
    appScope: CoroutineScope,
    noteId: Long?,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
) {
    val vm: EditNoteViewModel = hiltViewModel()
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()
    val activeTagSuggestions by vm.activeTagSuggestions.collectAsStateWithLifecycle()

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val sharedModifier =
        if (sharedScope != null && navScope != null && noteId != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-card-$noteId"),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditNoteScreen(
            vm = vm,
            appScope = appScope,
            editorNoteKey = noteId ?: 0L,
            existing = noteId != null,
            persistedForToolbar = hasPersistedRow,
            activeTagSuggestions = activeTagSuggestions,
            sharedNoteId = noteId,
            forceEdit = forceEdit,
            onBack = onBack,
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun EditNoteScreen(
    vm: EditNoteViewModel,
    appScope: CoroutineScope,
    editorNoteKey: Long,
    existing: Boolean,
    persistedForToolbar: Boolean,
    activeTagSuggestions: List<String>,
    sharedNoteId: Long?,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    var actionsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionSheetOpen by rememberSaveable { mutableStateOf(false) }
    var deleteForeverConfirmOpen by rememberSaveable { mutableStateOf(false) }

    var pendingHeroSession by remember { mutableStateOf<Pair<String, File?>?>(null) }
    val launchHeroImagePick =
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

    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = PillBottomBarHeight * 0)
    val blurMod = remember(blurStyle) { blurStyle?.applyToFullBleedLayer() ?: Modifier }

    val archived by vm.archived.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val shelfState =
        when {
            trashed -> NoteShelfState.TRASHED
            archived -> NoteShelfState.ARCHIVED
            else -> NoteShelfState.ACTIVE
        }
    val readOnly = shelfState != NoteShelfState.ACTIVE

    var isEditMode by remember(existing, forceEdit) { mutableStateOf(!existing || forceEdit) }
    var markdownDisplayMode by rememberSaveable { mutableStateOf(MarkdownEditorDisplayMode.LivePreview) }
    // Force view mode on read-only shelves so pickers and the markdown editor don't accept edits.
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }

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

    val snackbarHostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current
    val changesSavedMsg = stringResource(R.string.changes_saved)
    val untitledName = stringResource(R.string.edit_note_title_new)

    val undoMsg = stringResource(R.string.common_undo)
    // Snackbar templates for the bottom-bar actions. Reused from the bulk-action
    // strings since the count placeholder ("%1$d archived") reads naturally with 1.
    val msgArchived = stringResource(R.string.bulk_action_archived, 1)
    val msgTrashed = stringResource(R.string.bulk_action_trashed, 1)
    val msgUnarchived = stringResource(R.string.bulk_action_unarchived, 1)
    val msgRestored = stringResource(R.string.bulk_action_restored, 1)

    val handleBack = {
        appScope.launch {
            val undoAction = vm.saveIfNeeded(untitledName)
            if (undoAction != null) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = changesSavedMsg,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    undoAction()
                }
            }
        }
        onBack()
    }
    // Use the regular BackHandler instead of PredictiveBackHandler. The predictive
    // version suspends on `progress.collect { }` until the gesture's flow completes,
    // so popBackStack does not fire until after the system's predictive preview has
    // already finished animating - that gap reads as a "moment of nothing" before
    // the back transition starts. BackHandler fires immediately on commit, letting
    // the navigation reverse animation pick up where the predictive preview ends.
    androidx.activity.compose.BackHandler(onBack = handleBack)

    // Hoisted scroll state so the bottom action bar can hide on scroll-down and re-show on
    // scroll-up. Also keyed off IME visibility so the rich-text toolbar has the stage alone
    // when the keyboard is open.
    val contentScrollState = rememberScrollState()
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

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // Edit mode replaces the action bar: the rich-text formatting toolbar takes the bottom
    // slot and a Save button moves to the top bar. Stacking two bars at the bottom would
    // look like a layout bug. IME visibility also gates the action bar so the keyboard has
    // the stage alone when it's up.
    val actionBarVisible = bottomBarVisible && !isEditMode && !imeVisible

    // Shared save + exit-edit-mode path. Used by both the Done action on NoteActionBottomBar
    // (outside edit mode) and the Save icon in the top bar (inside edit mode) so both code
    // paths show the same "Changes saved" toast.
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveAndExitEditMode: () -> Unit = {
        isEditMode = false
        appScope.launch {
            if (vm.saveIfNeeded(untitledName) != null) {
                android.widget.Toast
                    .makeText(
                        context,
                        changesSavedMsg,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    Scaffold(
        // Chain two nestedScroll connections: scrollBehavior drives the TopAppBar collapse,
        // barVisibilityNestedScroll drives the bottom action bar hide/show with a source
        // filter so overscroll spring-back doesn't flash the bar back in.
        modifier =
            Modifier
                .nestedScroll(barVisibilityNestedScroll)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            EditNoteTopBarSection(
                vm = vm,
                scrollBehavior = scrollBehavior,
                titlePlaceholder = titlePlaceholder,
                existing = existing,
                sharedNoteId = sharedNoteId,
                isEditMode = isEditMode,
                readOnly = readOnly,
                markdownDisplayMode = markdownDisplayMode,
                onBack = handleBack,
                onToggleMarkdownDisplayMode = {
                    markdownDisplayMode =
                        if (markdownDisplayMode == MarkdownEditorDisplayMode.MarkdownCode) {
                            MarkdownEditorDisplayMode.LivePreview
                        } else {
                            MarkdownEditorDisplayMode.MarkdownCode
                        }
                },
                onSave = saveAndExitEditMode,
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
            val bottomSlot: EditorBottomSlot =
                when {
                    isEditMode -> EditorBottomSlot.Format
                    actionBarVisible -> EditorBottomSlot.Action
                    else -> EditorBottomSlot.None
                }
            val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
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
            AnimatedContent(
                targetState = bottomSlot,
                label = "EditNoteBottomSlot",
                transitionSpec = {
                    (
                        slideInVertically(animationSpec = spatialSpec) { it } +
                            fadeIn(animationSpec = effectsSpec)
                    ) togetherWith (
                        slideOutVertically(animationSpec = spatialSpec) { it } +
                            fadeOut(animationSpec = effectsSpec)
                    )
                },
            ) { currentSlot ->
                when (currentSlot) {
                    EditorBottomSlot.Format ->
                        EditNoteFormatBarContent(
                            markdownEditorState = markdownEditorState,
                            undoController = undoController,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            imeVisible = imeVisible,
                        )
                    EditorBottomSlot.Action ->
                        NoteActionBottomBarContent(
                            shelfState = shelfState,
                            existing = persistedForToolbar,
                            isEditMode = isEditMode,
                            favorite = favorite,
                            completed = completed,
                            onToggleEdit = {
                                // Outside edit mode this turns edit mode ON. The SAVE path is
                                // owned by the top-bar Save icon (edit mode) or by
                                // back/lifecycle (view mode flush), so there's no save
                                // side-effect to run here.
                                if (!isEditMode) isEditMode = true else saveAndExitEditMode()
                            },
                            onToggleFavorite = { vm.toggleFavorite() },
                            onToggleCompleted = {
                                appScope.launch { vm.toggleCompleted() }
                            },
                            onArchive = {
                                // Archive follows the same leave-editor flow as Trash: pop
                                // back immediately, then let the root snackbar host offer Undo.
                                val archiveStartedFromTrash = trashed
                                appScope.launch {
                                    vm.archiveCurrent(untitledName)
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            message = msgArchived,
                                            actionLabel = undoMsg,
                                            withDismissAction = true,
                                            duration = androidx.compose.material3.SnackbarDuration.Short,
                                        )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        if (archiveStartedFromTrash) {
                                            vm.trashCurrent()
                                        } else {
                                            vm.unarchiveCurrent()
                                        }
                                    }
                                }
                                onBack()
                            },
                            onNotification = {
                                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                    appScope.launch { vm.fireNotification(context, untitledName) }
                                } else {
                                    notificationPermissionSheetOpen = true
                                }
                            },
                            onUnarchive = {
                                appScope.launch {
                                    vm.unarchiveCurrent()
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            message = msgUnarchived,
                                            actionLabel = undoMsg,
                                            withDismissAction = true,
                                            duration = androidx.compose.material3.SnackbarDuration.Short,
                                        )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        vm.archiveCurrent(untitledName)
                                    }
                                }
                            },
                            onTrash = {
                                // Trash + back navigation. The snackbar host is at the
                                // scaffold root so it survives the screen pop and shows
                                // up on Home. Undo route hits vm.restoreFromTrashCurrent
                                // even after the screen is gone - the suspend doesn't
                                // depend on viewModelScope and the VM's loadedId field
                                // is still in memory long enough to complete the call.
                                val trashStartedFromArchive = archived
                                appScope.launch {
                                    vm.trashCurrent()
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            message = msgTrashed,
                                            actionLabel = undoMsg,
                                            withDismissAction = true,
                                            duration = androidx.compose.material3.SnackbarDuration.Short,
                                        )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        if (trashStartedFromArchive) {
                                            vm.archiveCurrent(untitledName)
                                        } else {
                                            vm.restoreFromTrashCurrent()
                                        }
                                    }
                                }
                                onBack()
                            },
                            onRestore = {
                                appScope.launch {
                                    vm.restoreFromTrashCurrent()
                                    val result =
                                        snackbarHostState.showSnackbar(
                                            message = msgRestored,
                                            actionLabel = undoMsg,
                                            withDismissAction = true,
                                            duration = androidx.compose.material3.SnackbarDuration.Short,
                                        )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        vm.trashCurrent()
                                    }
                                }
                            },
                            onDeleteForever = { deleteForeverConfirmOpen = true },
                        )
                    // Empty slot keeps Scaffold's bottomBar measure stable during the
                    // exit animation of whichever bar was previously visible.
                    EditorBottomSlot.None -> Box(Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->

        EditNoteScrollableContent(
            vm = vm,
            horizontalPadding = 20.dp,
            padding = padding,
            markdownEditorState = markdownEditorState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
            markdownDisplayMode = markdownDisplayMode,
            existing = existing,
            shelfState = shelfState,
            pictureViewerOpen = pictureViewer != null,
            onOpenReminder = { reminderPickerOpen = true },
            onOpenPicture = launchHeroImagePick,
            onViewPictureFull = { uri, revision ->
                pictureViewer = uri to revision
            },
            onOpenIcon = { iconPickerOpen = true },
            onOpenActions = { actionsPickerOpen = true },
            onOpenTags = { tagsPickerOpen = true },
            onOpenAttachments = { attachmentsPickerOpen = true },
            blurModifier = blurMod,
            scrollState = contentScrollState,
            scrollEnabled = !markdownSelectionActive,
        )

        // Pickers each collect only the slice they need, lazily, so they impose no overhead
        // when closed. Inlined here (instead of dispatched from a giant 21-parameter function)
        // because the dispatch wrapper offered no reuse and made every open/dismiss callback
        // travel three layers down.
        if (reminderPickerOpen) {
            val reminderAt by vm.reminderAt.collectAsState()
            val recurrence by vm.recurrence.collectAsState()
            ReminderPickerSheet(
                initialMillis = reminderAt,
                initialRule = recurrence,
                onConfirm = { at, rule ->
                    vm.setReminder(at, rule)
                    reminderPickerOpen = false
                },
                onDismiss = { reminderPickerOpen = false },
            )
        }
        if (iconPickerOpen) {
            val iconKey by vm.iconKey.collectAsState()
            IconPicker(
                current = iconKey,
                onPick = {
                    vm.setIconKey(it)
                    iconPickerOpen = false
                },
                onDismiss = { iconPickerOpen = false },
            )
        }
        if (actionsPickerOpen) {
            val actions by vm.actions.collectAsState()
            ActionPicker(
                current = actions,
                onConfirm = {
                    vm.setActions(it)
                    actionsPickerOpen = false
                },
                onDismiss = { actionsPickerOpen = false },
            )
        }
        if (tagsPickerOpen) {
            val tags by vm.tags.collectAsState()
            TagEditorSheet(
                initial = tags,
                availableTags = activeTagSuggestions,
                onConfirm = { newTags, newColors ->
                    vm.saveTagsWithColors(newTags, newColors)
                },
                onEditExistingTag = vm::editExistingTag,
                onDismiss = { tagsPickerOpen = false },
            )
        }
        if (attachmentsPickerOpen) {
            val attachments by vm.attachments.collectAsState()
            AttachmentsSheet(
                attachments = attachments,
                onDismiss = { attachmentsPickerOpen = false },
                onAdd = vm::addAttachment,
                onRemove = vm::removeAttachment,
            )
        }
        if (notificationPermissionSheetOpen) {
            NotificationPermissionRequiredSheet(
                onDismiss = { notificationPermissionSheetOpen = false },
                titleRes = R.string.notification_permission_required_title,
                bodyRes = R.string.notification_permission_required_body,
            )
        }
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
                    vm.setHeroWithFraming(pickedUri, framing)
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
                        appScope.launch { vm.deleteForeverCurrent() }
                        onBack()
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

    val viewerForOverlay = pictureViewer
    FullScreenHeroImageOverlay(
        visible = viewerForOverlay != null,
        imageUri = viewerForOverlay?.first,
        imageCacheRevision = viewerForOverlay?.second ?: 0L,
        imageContentDescription = stringResource(R.string.cd_note_hero_image),
        sharedElementKey = viewerForOverlay?.first?.let { uri -> "hero-image-$uri" },
        onDismiss = { pictureViewer = null },
        onDelete =
            if (readOnly) {
                null
            } else {
                (
                    {
                        vm.setPictureUri(null)
                        pictureViewer = null
                    }
                )
            },
    )
}
