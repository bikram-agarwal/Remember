package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.ui.common.HERO_MASK_ASPECT_RATIO
import dev.bikram.remember.ui.common.HeroFramedImage
import dev.bikram.remember.ui.common.HeroFraming
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.ApplyRichEditorListIndent
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.RichTextToolbar
import dev.bikram.remember.ui.components.ArchivedBanner
import dev.bikram.remember.ui.components.ArchivedBannerState
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.components.TagAccentEditorStrip
import dev.bikram.remember.ui.modifiers.rememberExpressiveOverscrollEffect
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

private const val UndoMaxHistory = 50

// snapshotFlow on the full annotated string forces a deep equals check (paragraph + span lists)
// per emission, which compounds with toMarkdown work on every keystroke. Reading length+hashCode
// of the plain text instead is O(text length) and avoids the per-emit allocation entirely.
private data class BodyFingerprint(val length: Int, val textHash: Int)

/**
 * Bridges the editor's [RichTextState] with [EditNoteViewModel.body]. Owns the "last
 * synced markdown" book-keeping so flushes from the debounced typing pipeline, lifecycle
 * ON_STOP, and onDispose all share state.
 *
 * Use [rememberEditorBodyBridge] from a composable to wire it up correctly.
 */
@Stable
internal class EditorBodyBridge(
    private val richTextState: RichTextState,
    private val undoController: UndoRedoController?,
    private val onMarkdownChanged: (String) -> Unit,
) {
    @Volatile var lastSyncedBody: String = ""
        private set

    fun reset(initial: String) {
        lastSyncedBody = initial
    }

    fun pushIfChanged(markdown: String) {
        if (markdown == lastSyncedBody) return
        lastSyncedBody = markdown
        undoController?.capture(markdown)
        onMarkdownChanged(markdown)
    }

    /** Snapshot the editor and immediately flush the markdown to the VM, bypassing the debounce. */
    fun flush() {
        pushIfChanged(richTextState.toMarkdown())
    }
}

/**
 * Wires the [richTextState] to the [vm] body flow and persists changes through [appScope]
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
@OptIn(ExperimentalRichTextApi::class, FlowPreview::class)
@Composable
internal fun rememberEditorBodyBridge(
    vm: EditNoteViewModel,
    richTextState: RichTextState,
    undoController: UndoRedoController,
    isEditMode: Boolean,
    appScope: CoroutineScope,
): EditorBodyBridge {
    ApplyRichEditorListIndent(richTextState)

    val bridge = remember(vm, richTextState, undoController) {
        EditorBodyBridge(
            richTextState = richTextState,
            undoController = undoController,
            onMarkdownChanged = vm::setBody,
        )
    }
    val isEditModeState = rememberUpdatedState(isEditMode)

    // Seed the editor and undo baseline once the VM finishes loading from disk. Resetting on
    // (loaded -> true) instead of immediately at remember{} time fixes the historical bug where
    // an existing note's first edit went into an empty undo stack (so undoing erased it).
    LaunchedEffect(vm, richTextState, bridge) {
        vm.loaded.first { it }
        val initialBody = vm.body.value
        if (initialBody.isNotEmpty() && richTextState.annotatedString.text.isEmpty()) {
            richTextState.setMarkdown(initialBody)
        }
        bridge.reset(initialBody)
        undoController.reset(initialBody)
    }

    // In view mode: mirror VM body changes back into the editor so external edits (e.g. the
    // share-text prefill) show up. In edit mode: do nothing here; the snapshotFlow loop below
    // is the source of truth.
    LaunchedEffect(vm, richTextState, bridge) {
        vm.body.collect { latestBody ->
            if (!isEditModeState.value && latestBody != bridge.lastSyncedBody) {
                richTextState.setMarkdown(latestBody)
                bridge.reset(latestBody)
            }
        }
    }

    // Flush pending edits whenever we leave edit mode so view-mode rendering uses fresh markdown.
    LaunchedEffect(isEditMode, bridge) {
        if (!isEditMode) bridge.flush()
    }

    if (isEditMode) {
        LaunchedEffect(richTextState, bridge) {
            try {
                snapshotFlow {
                    val text = richTextState.annotatedString.text
                    BodyFingerprint(text.length, text.hashCode())
                }
                    .distinctUntilChanged()
                    .debounce(250)
                    .collectLatest { bridge.flush() }
            } finally {
                bridge.flush()
            }
        }

        // When the buffer empties out (user deleted everything), the library keeps the last span
        // style as the "pending" style for new characters. That makes formatting sticky across a
        // full erase-and-retype. Reset any active styles so fresh typing is unformatted.
        LaunchedEffect(richTextState) {
            snapshotFlow { richTextState.annotatedString.text.isEmpty() }
                .distinctUntilChanged()
                .collectLatest { isEmpty ->
                    if (isEmpty) clearPendingSpanStyles(richTextState)
                }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current
    val changesSavedMsg = androidx.compose.ui.res.stringResource(dev.bikram.remember.R.string.changes_saved)
    val undoMsg = stringResource(R.string.common_undo)
    
    val untitledName = stringResource(R.string.edit_note_title_new)
    
    DisposableEffect(lifecycleOwner, bridge, vm, appScope) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                bridge.flush()
                appScope.launch { vm.saveIfNeeded(untitledName) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bridge.flush()
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

    return bridge
}

/**
 * Top app bar that reads its mutable state slices ([title], [pinned], [iconKey]) directly
 * from the VM. The rest of the screen does not collect these flows so title typing only
 * recomposes the title field area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteTopBarSection(
    vm: EditNoteViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    titlePlaceholder: String,
    existing: Boolean,
    isEditMode: Boolean,
    readOnly: Boolean,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    val title by vm.title.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()

    val newNoteTitleFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(existing, isEditMode, readOnly) {
        if (!existing && isEditMode && !readOnly) {
            delay(80)
            newNoteTitleFocus.requestFocus()
            keyboardController?.show()
        }
    }

    LargeTopAppBar(
        colors = transparentLargeTopAppBarColors(),
        title = {
            val collapseFraction = scrollBehavior.state.collapsedFraction
            val expandedStyle = MaterialTheme.typography.headlineMedium
            val collapsedStyle = MaterialTheme.typography.titleLarge
            val titleStyle = expandedStyle.copy(
                fontSize = lerp(expandedStyle.fontSize, collapsedStyle.fontSize, collapseFraction),
                lineHeight = lerp(expandedStyle.lineHeight, collapsedStyle.lineHeight, collapseFraction),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val iconSize = lerp(28.dp, 22.dp, collapseFraction)
            val iconGap = lerp(12.dp, 8.dp, collapseFraction)
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
                        name = DEFAULT_NOTE_HEADER_SYMBOL,
                        size = iconSize,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(iconGap))
                }
                if ((isEditMode && !readOnly) || title.isEmpty()) {
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 80) vm.setTitle(it) },
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
                            .focusRequester(newNoteTitleFocus),
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
                    SelectionContainer {
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
            // In edit mode the NoteActionBottomBar slides out (the rich-text toolbar owns the
            // bottom slot), so we surface Save here instead. Outside edit mode this slot is
            // empty - the action bar handles Edit / Favorite / Archive / Trash.
            if (isEditMode && !readOnly && onSave != null) {
                val saveCd = stringResource(R.string.edit_save_cd)
                RememberIconButton(
                    onClick = onSave,
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
}

/**
 * Bottom toolbar wrapping [RichTextToolbar]. Stable lambdas (built once via [remember]) keep
 * the toolbar from rebuilding undo/redo callbacks on every parent recomposition.
 */
@OptIn(ExperimentalRichTextApi::class)
@Composable
internal fun EditNoteBottomBarSection(
    richTextState: RichTextState,
    undoController: UndoRedoController,
    bridge: EditorBodyBridge,
    isEditMode: Boolean,
) {
    val onUndo = remember(richTextState, undoController, bridge) {
        {
            undoController.undo(richTextState.toMarkdown())?.let { previous ->
                richTextState.setMarkdown(previous)
                bridge.reset(previous)
            }
            Unit
        }
    }
    val onRedo = remember(richTextState, undoController, bridge) {
        {
            undoController.redo(richTextState.toMarkdown())?.let { next ->
                richTextState.setMarkdown(next)
                bridge.reset(next)
            }
            Unit
        }
    }
    AnimatedVisibility(visible = isEditMode) {
        EditNoteFormatBarContent(
            richTextState = richTextState,
            undoController = undoController,
            onUndo = onUndo,
            onRedo = onRedo,
        )
    }
}

/**
 * Static content of the rich-text format bar (no visibility animation wrapping).
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
@OptIn(ExperimentalRichTextApi::class)
@Composable
internal fun EditNoteFormatBarContent(
    richTextState: RichTextState,
    undoController: UndoRedoController,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        RichTextToolbar(
            state = richTextState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            canUndo = undoController.canUndo,
            canRedo = undoController.canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
        )
    }
}

/**
 * Undo / redo controller backed by Compose state lists so toolbar enabled-state recomposes
 * automatically. Capacity is capped at [UndoMaxHistory] entries so a long editing session
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
        if (undoStack.size > UndoMaxHistory) undoStack.removeAt(0)
        redoStack.clear()
        lastPushed = markdown
    }

    fun undo(current: String): String? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        if (redoStack.size > UndoMaxHistory) redoStack.removeAt(0)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        lastPushed = prev
        suppressCapture = true
        return prev
    }

    fun redo(current: String): String? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        if (undoStack.size > UndoMaxHistory) undoStack.removeAt(0)
        val next = redoStack.removeAt(redoStack.lastIndex)
        lastPushed = next
        suppressCapture = true
        return next
    }
}

@OptIn(ExperimentalRichTextApi::class)
private fun clearPendingSpanStyles(state: RichTextState) {
    val current = state.currentSpanStyle
    if (current.fontWeight == FontWeight.Bold) {
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
    }
    if (current.fontStyle == FontStyle.Italic) {
        state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
    }
    current.textDecoration?.let { deco ->
        if (TextDecoration.Underline in deco) {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }
        if (TextDecoration.LineThrough in deco) {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }
    }
    val size = current.fontSize
    if (size.isSpecified) {
        state.toggleSpanStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold))
    }
}

@OptIn(ExperimentalRichTextApi::class)
private fun handleRichEditorKey(
    event: KeyEvent,
    state: RichTextState,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Backspace -> {
            val sel = state.selection
            if (!sel.collapsed || sel.start == 0) return false
            val text = state.annotatedString.text
            val atLineStart = text.getOrNull(sel.start - 1) == '\n'
            val inList = state.isUnorderedList || state.isOrderedList
            if (atLineStart && inList) {
                if (state.isUnorderedList) state.removeUnorderedList() else state.removeOrderedList()
            }
            false
        }
        Key.Tab -> {
            if (!(state.isUnorderedList || state.isOrderedList)) return false
            if (event.isShiftPressed) {
                if (state.canDecreaseListLevel) state.decreaseListLevel()
            } else {
                if (state.canIncreaseListLevel) state.increaseListLevel()
            }
            true
        }
        else -> false
    }
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
internal fun EditNoteRichEditorSection(
    richTextState: RichTextState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    existing: Boolean,
    onRequestEditMode: () -> Unit,
) {
    val bodyEmpty = richTextState.annotatedString.isEmpty()
    // Only the "new note" flow may show the real editor while not in edit mode (empty draft).
    // Existing notes in view mode must use read-only body UI so typing does not fight the bridge.
    if (isEditMode || (!existing && bodyEmpty)) {
        BasicRichTextEditor(
            state = richTextState,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .onPreviewKeyEvent { event -> handleRichEditorKey(event, richTextState) },
            decorationBox = { inner ->
                if (richTextState.annotatedString.isEmpty()) {
                    Text(
                        bodyPlaceholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
                inner()
            },
        )
    } else if (existing && bodyEmpty) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .tapSoundClickable { onRequestEditMode() },
        ) {
            Text(
                text = bodyPlaceholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }
    } else {
        SelectionContainer {
            RichText(
                state = richTextState,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            )
        }
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
    horizontalPadding: Dp,
    padding: PaddingValues,
    richTextState: RichTextState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    existing: Boolean,
    shelfState: NoteShelfState,
    onRequestEditMode: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenPicture: () -> Unit,
    onViewPictureFull: (String, Long) -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    blurModifier: Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val readOnly = shelfState != NoteShelfState.ACTIVE
    // NOTE: do NOT attach scrollBehavior.nestedScrollConnection here. It is already attached on
    // the Scaffold in EditNoteScreen. Double-attachment causes the top bar to consume each
    // scroll delta twice and produces a glitchy overscroll bounce.
    //
    // We replace the platform stretch-overscroll with [rememberExpressiveOverscrollEffect] -
    // a translation-based overscroll that releases on the Material 3 Expressive slow spatial
    // spring. The OEM stretch snaps back with a stiff hard-coded spring that reads as a sudden
    // "crack" on the editor; the Expressive spring gives a soft, rounded settle that matches
    // the rest of the motion language in the app.
    val overscrollEffect = rememberExpressiveOverscrollEffect()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(blurModifier)
            // Clip BEFORE the overscroll translation so the translated content doesn't bleed
            // into the top-app-bar / bottom-bar zones during the bounce. Foundation 1.8+
            // deprecated `OverscrollEffect.effectModifier`; the replacement is the
            // `Modifier.overscroll(effect)` extension, which attaches the effect's
            // DelegatableNode to the chain.
            .clipToBounds()
            .overscroll(overscrollEffect)
            .verticalScroll(state = scrollState, overscrollEffect = overscrollEffect)
            .padding(horizontal = horizontalPadding),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        // Order: tag color strip -> hero image -> shelf banner -> body. The banner sits right
        // above the note body so the "why is this disabled" hint is adjacent to the content it
        // gates, not buried at the top of the scroll above decorative chrome.
        TagAccentSection(vm)
        PictureHeroSection(vm, onViewPictureFull = onViewPictureFull)
        when (shelfState) {
            NoteShelfState.ARCHIVED -> {
                Spacer(Modifier.height(16.dp))
                ArchivedBanner(
                    state = ArchivedBannerState.ARCHIVED,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NoteShelfState.TRASHED -> {
                Spacer(Modifier.height(16.dp))
                ArchivedBanner(
                    state = ArchivedBannerState.TRASHED,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NoteShelfState.ACTIVE -> {}
        }
        Spacer(Modifier.height(16.dp))

        EditNoteRichEditorSection(
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode && !readOnly,
            existing = existing,
            onRequestEditMode = onRequestEditMode,
        )
        Spacer(Modifier.height(24.dp))
        OptionsPanelSection(
            vm = vm,
            readOnly = readOnly,
            onOpenReminder = if (readOnly) ({}) else onOpenReminder,
            onOpenPicture = if (readOnly) ({}) else onOpenPicture,
            onOpenIcon = if (readOnly) ({}) else onOpenIcon,
            onOpenActions = if (readOnly) ({}) else onOpenActions,
            onOpenTags = if (readOnly) ({}) else onOpenTags,
            onOpenAttachments = if (readOnly) ({}) else onOpenAttachments,
        )
        Spacer(Modifier.height(40.dp + padding.calculateBottomPadding()))
    }
}

@Composable
private fun TagAccentSection(vm: EditNoteViewModel) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    TagAccentEditorStrip(tags = tags)
}

@Composable
private fun PictureHeroSection(vm: EditNoteViewModel, onViewPictureFull: (String, Long) -> Unit) {
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val pictureRevision by vm.pictureRevision.collectAsStateWithLifecycle()
    val pictureHeroFraming by vm.pictureHeroFraming.collectAsStateWithLifecycle()
    val uri = pictureUri ?: return
    Spacer(Modifier.height(16.dp))
    EditNotePictureHero(
        uri = uri,
        pictureRevision = pictureRevision,
        pictureHeroFraming = pictureHeroFraming,
        onOpenFull = { onViewPictureFull(uri, pictureRevision) },
    )
}

@Composable
private fun OptionsPanelSection(
    vm: EditNoteViewModel,
    readOnly: Boolean,
    onOpenReminder: () -> Unit,
    onOpenPicture: () -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val recurrence by vm.recurrence.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()

    OptionsPanel(
        reminderAt = reminderAt,
        recurrence = recurrence,
        importance = importance,
        pictureUri = pictureUri,
        iconKey = iconKey,
        isChecklist = false,
        actions = actions,
        tags = tags,
        attachmentCount = attachments.size,
        onOpenReminder = onOpenReminder,
        onSetImportance = if (readOnly) ({ _ -> }) else vm::setImportance,
        onOpenPicture = onOpenPicture,
        onOpenIcon = onOpenIcon,
        onOpenActions = onOpenActions,
        onOpenTags = onOpenTags,
        onOpenAttachments = onOpenAttachments,
        readOnly = readOnly,
    )
}

@Composable
private fun EditNotePictureHero(
    uri: String,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    onOpenFull: () -> Unit,
) {
    val framing = remember(pictureHeroFraming) { HeroFraming.fromJsonString(pictureHeroFraming) }

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedModifier = if (sharedScope != null && navScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "hero-image-${uri}"),
                animatedVisibilityScope = navScope
            )
        }
    } else Modifier

    // No delete overlay on the inline hero: it competes visually with the hero image and
    // invites accidental taps. Delete lives in the full-screen viewer (see EditNoteScreen).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_MASK_ASPECT_RATIO)
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
