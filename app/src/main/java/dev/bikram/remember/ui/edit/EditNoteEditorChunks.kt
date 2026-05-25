package dev.bikram.remember.ui.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.HERO_MASK_ASPECT_RATIO
import dev.bikram.remember.ui.common.MarkdownLinkInteraction
import dev.bikram.remember.ui.common.MarkdownText
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val UNDO_MAX_HISTORY = 50

// Snapshotting length+hash avoids collecting the whole TextFieldValue on every keystroke while
// still detecting body edits for the debounced persistence pipeline.
private data class BodyFingerprint(
    val length: Int,
    val textHash: Int,
)

/**
 * Bridges the editor's [MarkdownEditorState] with [EditNoteViewModel.body]. Owns the "last
 * synced markdown" book-keeping so flushes from the debounced typing pipeline, lifecycle
 * ON_STOP, and onDispose all share state.
 *
 * Use [rememberEditorBodyBridge] from a composable to wire it up correctly.
 */
@Stable
internal class EditorBodyBridge(
    private val currentMarkdown: () -> String,
    private val undoController: UndoRedoController?,
    private val onMarkdownChanged: (String) -> Unit,
) {
    @Volatile var lastSyncedBody: String = ""
        private set

    fun reset(initial: String) {
        lastSyncedBody = initial
    }

    fun replaceFromHistory(markdown: String) {
        lastSyncedBody = markdown
        onMarkdownChanged(markdown)
    }

    fun pushIfChanged(markdown: String) {
        if (markdown == lastSyncedBody) return
        lastSyncedBody = markdown
        undoController?.capture(markdown)
        onMarkdownChanged(markdown)
    }

    /** Snapshot the editor and immediately flush the markdown to the VM, bypassing the debounce. */
    fun flush() {
        pushIfChanged(currentMarkdown())
    }
}

/**
 * Wires [markdownEditorState] to the [vm] body flow and persists changes through [appScope]
 * on lifecycle ON_STOP and onDispose. Returns an [EditorBodyBridge] that callers can use
 * to trigger explicit flushes (e.g. before navigating).
 *
 * Owns these subtle concerns so leaf composables don't have to:
 * - Initial load: waits for [EditNoteViewModel.loaded] before seeding the editor and
 *   resetting [undoController], so existing notes start with their loaded content as the
 *   undo baseline rather than "" (which made an undo of the first edit erase the loaded body).
 * - View<->edit flips: pushes any pending edits to the VM when leaving edit mode.
 * - Background sync: when not in edit mode, mirrors VM body changes back into the editor.
 * - Lifecycle ON_STOP: synchronously flushes the markdown then triggers a save through
 *   [appScope] so backgrounded notes survive process death.
 * - onDispose: same as ON_STOP, for the navigate-away case.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun rememberEditorBodyBridge(
    vm: EditNoteViewModel,
    markdownEditorState: MarkdownEditorState,
    undoController: UndoRedoController,
    isEditMode: Boolean,
    appScope: CoroutineScope,
): EditorBodyBridge {
    val bridge =
        remember(vm, markdownEditorState, undoController) {
            EditorBodyBridge(
                currentMarkdown = { markdownEditorState.markdown },
                undoController = undoController,
                onMarkdownChanged = vm::setBody,
            )
        }
    val isEditModeState = rememberUpdatedState(isEditMode)

    // Seed the editor and undo baseline once the VM finishes loading from disk. Resetting on
    // (loaded -> true) instead of immediately at remember{} time fixes the historical bug where
    // an existing note's first edit went into an empty undo stack (so undoing erased it).
    LaunchedEffect(vm, markdownEditorState, bridge) {
        vm.loaded.first { it }
        val initialBody = vm.body.value
        markdownEditorState.setMarkdown(initialBody)
        bridge.reset(initialBody)
        undoController.reset(initialBody)
    }

    // In view mode: mirror VM body changes back into the editor so external edits (e.g. the
    // share-text prefill) show up. In edit mode: do nothing here; the snapshotFlow loop below
    // is the source of truth.
    LaunchedEffect(vm, markdownEditorState, bridge) {
        vm.body.collect { latestBody ->
            if (!isEditModeState.value && latestBody != bridge.lastSyncedBody) {
                markdownEditorState.setMarkdown(latestBody)
                bridge.reset(latestBody)
            }
        }
    }

    // Flush pending edits whenever we leave edit mode so view-mode rendering uses fresh markdown.
    LaunchedEffect(isEditMode, bridge) {
        if (!isEditMode) {
            bridge.flush()
        }
    }

    if (isEditMode) {
        LaunchedEffect(markdownEditorState, bridge) {
            try {
                snapshotFlow {
                    val markdown = markdownEditorState.markdown
                    BodyFingerprint(markdown.length, markdown.hashCode())
                }.distinctUntilChanged()
                    .debounce(250)
                    .collectLatest { bridge.flush() }
            } finally {
                bridge.flush()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current
    val changesSavedMsg =
        androidx.compose.ui.res
            .stringResource(dev.bikram.remember.R.string.changes_saved)
    val undoMsg = stringResource(R.string.common_undo)

    val untitledName = stringResource(R.string.edit_note_title_new)

    DisposableEffect(lifecycleOwner, bridge, vm, appScope) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    bridge.flush()
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
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bridge.flush()
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
        }
    }

    return bridge
}

/**
 * Bottom toolbar wrapping [MarkdownToolbar]. Stable lambdas (built once via [remember]) keep
 * the toolbar from rebuilding undo/redo callbacks on every parent recomposition.
 */
@Composable
internal fun EditNoteBottomBarSection(
    markdownEditorState: MarkdownEditorState,
    undoController: UndoRedoController,
    bridge: EditorBodyBridge,
    isEditMode: Boolean,
    imeVisible: Boolean = false,
) {
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
    val formatFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val formatFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    AnimatedVisibility(
        visible = isEditMode,
        enter = fadeIn(animationSpec = formatFadeInSpec),
        exit = fadeOut(animationSpec = formatFadeOutSpec),
    ) {
        EditNoteFormatBarContent(
            markdownEditorState = markdownEditorState,
            undoController = undoController,
            onUndo = onUndo,
            onRedo = onRedo,
            imeVisible = imeVisible,
        )
    }
}

/**
 * Static content of the markdown format bar (no visibility animation wrapping).
 *
 * Extracted from [EditNoteBottomBarSection] so the parent [androidx.compose.animation.AnimatedContent]
 * in the editor's bottomBar can drive the transition between this toolbar and the
 * [NoteActionBottomBarContent] in a single motion scope. When both bars own their
 * own `AnimatedVisibility` and live together in a Column, their enter/exit animations
 * overlap visually (the format bar slides in above the action bar, then the action bar
 * collapses, then the format bar drops down), which reads as a multi-phase glitch.
 * Driving both from a single `AnimatedContent` keeps the swap to one synchronized
 * M3E spatial-spring transition.
 */
@Composable
internal fun EditNoteFormatBarContent(
    markdownEditorState: MarkdownEditorState,
    undoController: UndoRedoController,
    modifier: Modifier = Modifier,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    imeVisible: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding(),
    ) {
        MarkdownToolbar(
            state = markdownEditorState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (imeVisible) Modifier else Modifier.navigationBarsPadding()),
            canUndo = undoController.canUndo,
            canRedo = undoController.canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
        )
    }
}

/**
 * Undo / redo controller backed by Compose state lists so toolbar enabled-state recomposes
 * automatically. Capacity is capped at [UNDO_MAX_HISTORY] entries so a long editing session
 * doesn't grow unbounded.
 */
@Stable
internal class UndoRedoController {
    private val undoStack = mutableStateListOf<String>()
    private val redoStack = mutableStateListOf<String>()
    private var lastPushed: String = ""
    private var suppressCapture: Boolean = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun reset(initial: String) {
        undoStack.clear()
        redoStack.clear()
        lastPushed = initial
        suppressCapture = false
    }

    fun capture(markdown: String) {
        if (suppressCapture) {
            suppressCapture = false
            lastPushed = markdown
            return
        }
        if (markdown == lastPushed) return
        undoStack.add(lastPushed)
        if (undoStack.size > UNDO_MAX_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
        lastPushed = markdown
    }

    fun undo(current: String): String? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        if (redoStack.size > UNDO_MAX_HISTORY) redoStack.removeAt(0)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        lastPushed = prev
        suppressCapture = true
        return prev
    }

    fun redo(current: String): String? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        if (undoStack.size > UNDO_MAX_HISTORY) undoStack.removeAt(0)
        val next = redoStack.removeAt(redoStack.lastIndex)
        lastPushed = next
        suppressCapture = true
        return next
    }
}

@Composable
internal fun EditNoteMarkdownEditorSection(
    markdownEditorState: MarkdownEditorState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    existing: Boolean,
    autoFocusBodyOnEdit: Boolean,
    scrollState: ScrollState,
    displayMode: MarkdownEditorDisplayMode,
    assignedTags: List<String>,
    onMarkdownChanged: (String) -> Unit,
    onAddTag: (String, String) -> Unit,
    onBodyFocusChanged: (Boolean) -> Unit,
    onEnterEditModeAtOffset: (Int) -> Unit,
    onEnterEditModeSelectingRange: (Int, Int) -> Unit,
) {
    val bodyEmpty = markdownEditorState.markdown.isEmpty()
    var linkActions by remember { mutableStateOf<MarkdownLinkInteraction?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(isEditMode, autoFocusBodyOnEdit, markdownEditorState) {
        if (!isEditMode || !autoFocusBodyOnEdit) return@LaunchedEffect

        delay(120)
        if (!markdownEditorState.shouldAutoFocusBodyOnEdit()) return@LaunchedEffect
        markdownEditorState.focusAtEndAndShowKeyboard()
    }

    // Only the "new note" flow may show the real editor while not in edit mode (empty draft).
    // Existing notes in view mode must use read-only body UI so typing does not fight the bridge.
    if (isEditMode || (!existing && bodyEmpty)) {
        MarkdownTextEditor(
            state = markdownEditorState,
            bodyPlaceholder = bodyPlaceholder,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            scrollState = scrollState,
            displayMode = displayMode,
            assignedTags = assignedTags,
            onAddTag = onAddTag,
            onFocusChanged = onBodyFocusChanged,
        )
    } else if (existing && bodyEmpty) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .tapSoundClickable {
                        onEnterEditModeAtOffset(markdownEditorState.markdown.length)
                    },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.common_empty_note),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.edit_note_empty_view_hint),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    } else {
        MarkdownText(
            markdown = markdownEditorState.markdown,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            onChecklistToggle = { lineIndex, checked ->
                val updatedMarkdown = markdownEditorState.markdown.withChecklistLineToggled(lineIndex, checked)
                markdownEditorState.setMarkdown(updatedMarkdown, moveCursorToEnd = false)
                onMarkdownChanged(updatedMarkdown)
            },
            onTextTap = { tap ->
                onEnterEditModeAtOffset(tap.markdownOffset)
            },
            onLinkClick = { link ->
                runCatching { uriHandler.openUri(link.url) }
            },
            onLinkLongPress = { link ->
                linkActions = link
            },
        )
    }

    linkActions?.let { link ->
        LinkActionsSheet(
            link = link,
            onDismiss = { linkActions = null },
            onOpen = {
                runCatching { uriHandler.openUri(link.url) }
                linkActions = null
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(resources.getString(R.string.clipboard_link_label), link.url),
                )
                Toast
                    .makeText(context, resources.getString(R.string.toast_about_link_copied), Toast.LENGTH_SHORT)
                    .show()
                linkActions = null
            },
            onEditText = {
                onEnterEditModeSelectingRange(link.textStartOffset, link.textEndOffset)
                linkActions = null
            },
            onShare = {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link.url)
                    }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        resources.getString(R.string.share_chooser_generic),
                    ),
                )
                linkActions = null
            },
        )
    }
}

@Composable
private fun LinkActionsSheet(
    link: MarkdownLinkInteraction,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onEditText: () -> Unit,
    onShare: () -> Unit,
) {
    AppBottomSheet(
        title = stringResource(R.string.edit_link_actions_title),
        subtitle = link.url,
        onDismiss = onDismiss,
        scrollable = false,
    ) {
        RememberDropdownMenuItem(
            text = { Text(stringResource(R.string.edit_link_action_open)) },
            onClick = onOpen,
            leadingIcon = {
                RememberMaterialRoundedSymbol(name = "open_in_new", weight = FontWeight.Medium)
            },
        )
        RememberDropdownMenuItem(
            text = { Text(stringResource(R.string.edit_link_action_copy)) },
            onClick = onCopy,
            leadingIcon = {
                RememberMaterialRoundedSymbol(name = "content_copy", weight = FontWeight.Medium)
            },
        )
        RememberDropdownMenuItem(
            text = { Text(stringResource(R.string.edit_link_action_edit_text)) },
            onClick = onEditText,
            leadingIcon = {
                RememberMaterialRoundedSymbol(name = "edit", weight = FontWeight.Medium)
            },
        )
        RememberDropdownMenuItem(
            text = { Text(stringResource(R.string.edit_link_action_share)) },
            onClick = onShare,
            leadingIcon = {
                RememberMaterialRoundedSymbol(name = "share", weight = FontWeight.Medium)
            },
        )
    }
}

/**
 * Scrollable column for the body of the edit screen. Only reads [padding] / [blurModifier]
 * directly; mutable note state is collected by leaf sections so that, e.g., title typing
 * never recomposes this column.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
internal fun EditNoteScrollableContent(
    vm: EditNoteViewModel,
    modifier: Modifier,
    horizontalPadding: Dp,
    padding: PaddingValues,
    markdownEditorState: MarkdownEditorState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    markdownDisplayMode: MarkdownEditorDisplayMode,
    existing: Boolean,
    autoFocusBodyOnEdit: Boolean,
    shelfState: NoteShelfState,
    pictureViewerOpen: Boolean,
    onOpenReminder: () -> Unit,
    notificationsAllowed: Boolean,
    onOpenPicture: () -> Unit,
    onViewPictureFull: (String, Long) -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    onEnterEditModeAtOffset: (Int) -> Unit,
    onEnterEditModeSelectingRange: (Int, Int) -> Unit,
    onBodyFocusChanged: (Boolean) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
) {
    val readOnly = shelfState != NoteShelfState.ACTIVE
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val assignedTags by vm.tags.collectAsStateWithLifecycle()
    EditorContentBodyColumn(
        modifier = modifier,
        horizontalPadding = horizontalPadding,
        padding = padding,
        scrollState = scrollState,
        scrollEnabled = scrollEnabled,
        shelfState = shelfState,
        bottomPadding = padding.calculateBottomPadding() + if (isEditMode && !readOnly) imeBottomPadding else 0.dp,
        heroContent = {
            PictureHeroSection(
                vm = vm,
                viewerOpen = pictureViewerOpen,
                onViewPictureFull = onViewPictureFull,
            )
        },
        bodyContent = {
            EditNoteMarkdownEditorSection(
                markdownEditorState = markdownEditorState,
                bodyPlaceholder = bodyPlaceholder,
                isEditMode = isEditMode && !readOnly,
                existing = existing,
                autoFocusBodyOnEdit = autoFocusBodyOnEdit,
                scrollState = scrollState,
                displayMode = markdownDisplayMode,
                assignedTags = assignedTags,
                onMarkdownChanged = vm::setBody,
                onAddTag = vm::addTag,
                onBodyFocusChanged = onBodyFocusChanged,
                onEnterEditModeAtOffset = onEnterEditModeAtOffset,
                onEnterEditModeSelectingRange = onEnterEditModeSelectingRange,
            )
        },
        optionsContent = {
            Spacer(Modifier.height(24.dp))
            OptionsPanelSection(
                vm = vm,
                readOnly = readOnly,
                onOpenReminder = if (readOnly) ({}) else onOpenReminder,
                notificationsAllowed = notificationsAllowed,
                onOpenPicture = if (readOnly) ({}) else onOpenPicture,
                onOpenActions = if (readOnly) ({}) else onOpenActions,
                onOpenTags = if (readOnly) ({}) else onOpenTags,
                onOpenAttachments = if (readOnly) ({}) else onOpenAttachments,
            )
            Spacer(Modifier.height(36.dp))
        },
    )
}

@Composable
private fun PictureHeroSection(
    vm: EditNoteViewModel,
    viewerOpen: Boolean,
    onViewPictureFull: (String, Long) -> Unit,
) {
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val pictureRevision by vm.pictureRevision.collectAsStateWithLifecycle()
    val pictureHeroFraming by vm.pictureHeroFraming.collectAsStateWithLifecycle()
    val uri = pictureUri ?: return
    Spacer(Modifier.height(EditorContentBodyDefaults.HeroTopSpacing))
    EditorContentPictureHero(
        uri = uri,
        pictureRevision = pictureRevision,
        pictureHeroFraming = pictureHeroFraming,
        viewerOpen = viewerOpen,
        onOpenFull = { onViewPictureFull(uri, pictureRevision) },
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_MASK_ASPECT_RATIO),
    )
}

@Composable
private fun OptionsPanelSection(
    vm: EditNoteViewModel,
    readOnly: Boolean,
    onOpenReminder: () -> Unit,
    notificationsAllowed: Boolean,
    onOpenPicture: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val recurrence by vm.recurrence.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val visibility by vm.visibility.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()

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
        onOpenReminder = onOpenReminder,
        onImportanceChange = vm::setImportance,
        onVisibilityChange = vm::setVisibility,
        onOpenPicture = onOpenPicture,
        onOpenActions = onOpenActions,
        onOpenTags = onOpenTags,
        onOpenAttachmentsSheet = onOpenAttachments,
        onPickAttachment = onOpenAttachments,
    )
}
