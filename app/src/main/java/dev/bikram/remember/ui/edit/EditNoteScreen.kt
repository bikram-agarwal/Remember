package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteRepository
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
    repository: NoteRepository,
    appScope: CoroutineScope,
    noteId: Long?,
    forceEdit: Boolean = false,
    onBack: () -> Unit,
) {
    val vm: EditNoteViewModel = hiltViewModel()
    val hasPersistedRow by vm.hasPersistedRow.collectAsStateWithLifecycle()

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedModifier =
        if (sharedScope != null && navScope != null && noteId != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-card-$noteId"),
                    animatedVisibilityScope = navScope,
                )
            }
        } else {
            Modifier
        }

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditNoteScreen(
            vm = vm,
            repository = repository,
            appScope = appScope,
            editorNoteKey = noteId ?: 0L,
            existing = noteId != null,
            persistedForToolbar = hasPersistedRow,
            forceEdit = forceEdit,
            onBack = onBack,
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalRichTextApi::class,
)
@Composable
fun EditNoteScreen(
    vm: EditNoteViewModel,
    repository: NoteRepository,
    appScope: CoroutineScope,
    editorNoteKey: Long,
    existing: Boolean,
    persistedForToolbar: Boolean,
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
    // Force view mode on read-only shelves so pickers and the rich editor don't accept edits.
    LaunchedEffect(readOnly) {
        if (readOnly && isEditMode) isEditMode = false
    }

    val richTextState = rememberRichTextState()
    val undoController = remember(editorNoteKey) { UndoRedoController() }

    val bridge =
        rememberEditorBodyBridge(
            vm = vm,
            richTextState = richTextState,
            undoController = undoController,
            isEditMode = isEditMode,
            appScope = appScope,
        )

    val snackbarHostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current
    val changesSavedMsg = stringResource(R.string.changes_saved)
    val untitledName = stringResource(R.string.edit_note_title_new)

    val undoMsg = stringResource(R.string.common_undo)

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
                isEditMode = isEditMode,
                readOnly = readOnly,
                onBack = handleBack,
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
            // Stable callbacks - the RichTextToolbar / action item rows are lambda-heavy
            // and re-allocating on every recomposition defeats their skippable-composable
            // optimization.
            val onUndo =
                remember(richTextState, undoController, bridge) {
                    {
                        undoController.undo(richTextState.toMarkdown())?.let { previous ->
                            richTextState.setMarkdown(previous)
                            bridge.reset(previous)
                        }
                        Unit
                    }
                }
            val onRedo =
                remember(richTextState, undoController, bridge) {
                    {
                        undoController.redo(richTextState.toMarkdown())?.let { next ->
                            richTextState.setMarkdown(next)
                            bridge.reset(next)
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
                            richTextState = richTextState,
                            undoController = undoController,
                            onUndo = onUndo,
                            onRedo = onRedo,
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
                                appScope.launch { vm.archiveCurrent(untitledName) }
                            },
                            onNotification = {
                                appScope.launch { vm.fireNotification(context, untitledName) }
                            },
                            onUnarchive = {
                                appScope.launch { vm.unarchiveCurrent() }
                            },
                            onTrash = {
                                appScope.launch { vm.trashCurrent() }
                                onBack()
                            },
                            onRestore = {
                                appScope.launch { vm.restoreFromTrashCurrent() }
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
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
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
                repository = repository,
                onConfirm = { newTags, newColors ->
                    vm.saveTagsWithColors(newTags, newColors)
                    tagsPickerOpen = false
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
