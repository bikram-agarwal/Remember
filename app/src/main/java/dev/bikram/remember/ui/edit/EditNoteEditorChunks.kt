package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import dev.bikram.remember.ui.components.TagAccentEditorStrip
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    DisposableEffect(lifecycleOwner, bridge, vm, appScope) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                bridge.flush()
                appScope.launch { vm.saveIfNeeded() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bridge.flush()
            appScope.launch { vm.saveIfNeeded() }
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
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onTrash: () -> Unit,
) {
    val title by vm.title.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()

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
                }
                if (isEditMode || title.isEmpty()) {
                    BasicTextField(
                        value = title,
                        onValueChange = vm::setTitle,
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
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
            RememberFilledTonalIconButton(onClick = onToggleEditMode) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isEditMode,
                    label = "editModeIcon",
                    transitionSpec = {
                        androidx.compose.animation.scaleIn() togetherWith androidx.compose.animation.scaleOut()
                    }
                ) { editing ->
                    RememberMaterialRoundedSymbol(
                        name = if (editing) "done" else "edit",
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))

            RememberFilledTonalIconButton(onClick = vm::togglePin) {
                // FILL=1 font: outline-style ligatures resolve to the same filled heart; use alpha when unpinned.
                val pinHeartTint =
                    if (pinned) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                RememberMaterialRoundedSymbol(
                    name = "favorite",
                    size = 24.dp,
                    tint = pinHeartTint,
                    weight = FontWeight.Medium,
                    opticalCenterYOffset = 1.5.dp,
                )
            }
            if (existing) {
                Spacer(Modifier.width(6.dp))
                RememberFilledTonalIconButton(onClick = onTrash) {
                    RememberMaterialRoundedSymbol(
                        name = "delete_outline",
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
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
) {
    if (isEditMode || richTextState.annotatedString.isEmpty()) {
        BasicRichTextEditor(
            state = richTextState,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteScrollableContent(
    vm: EditNoteViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    horizontalPadding: Dp,
    padding: PaddingValues,
    richTextState: RichTextState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    onOpenReminder: () -> Unit,
    onOpenPicture: () -> Unit,
    onViewPictureFull: (String, Long) -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    blurModifier: Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(blurModifier)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        TagAccentSection(vm)
        PictureHeroSection(vm, onViewPictureFull = onViewPictureFull)
        Spacer(Modifier.height(16.dp))

        EditNoteRichEditorSection(
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
        )
        Spacer(Modifier.height(24.dp))
        OptionsPanelSection(
            vm = vm,
            onOpenReminder = onOpenReminder,
            onOpenPicture = onOpenPicture,
            onOpenIcon = onOpenIcon,
            onOpenActions = onOpenActions,
            onOpenTags = onOpenTags,
            onOpenAttachments = onOpenAttachments,
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
        onClear = { vm.setPictureUri(null) },
        onOpenFull = { onViewPictureFull(uri, pictureRevision) },
    )
}

@Composable
private fun OptionsPanelSection(
    vm: EditNoteViewModel,
    onOpenReminder: () -> Unit,
    onOpenPicture: () -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()

    OptionsPanel(
        reminderAt = reminderAt,
        importance = importance,
        pictureUri = pictureUri,
        iconKey = iconKey,
        actions = actions,
        tags = tags,
        attachmentCount = attachments.size,
        onOpenReminder = onOpenReminder,
        onSetImportance = vm::setImportance,
        onOpenPicture = onOpenPicture,
        onOpenIcon = onOpenIcon,
        onOpenActions = onOpenActions,
        onOpenTags = onOpenTags,
        onOpenAttachments = onOpenAttachments,
    )
}

@Composable
private fun EditNotePictureHero(
    uri: String,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    onClear: () -> Unit,
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
        RememberFilledTonalIconButton(
            onClick = onClear,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd),
        ) {
            RememberMaterialRoundedSymbol(
                name = "delete_outline",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
    }
}
