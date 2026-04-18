package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.ui.common.ApplyRichEditorListIndent
import dev.bikram.remember.ui.components.TagAccentEditorStrip
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalRichTextApi::class, FlowPreview::class)
@Composable
internal fun EditNoteMarkdownSyncEffects(
    editorNoteKey: Long,
    isEditMode: Boolean,
    editNoteViewModel: EditNoteViewModel,
    richTextState: RichTextState,
    onBodyChange: (String) -> Unit,
    undoController: UndoRedoController? = null,
) {
    ApplyRichEditorListIndent(richTextState)

    val lastSyncedBody = remember(editorNoteKey) { mutableStateOf("") }
    val updatedOnBodyChange = rememberUpdatedState(onBodyChange)

    fun pushMarkdownIfChanged(markdown: String) {
        if (markdown != lastSyncedBody.value) {
            lastSyncedBody.value = markdown
            undoController?.capture(markdown)
            updatedOnBodyChange.value(markdown)
        }
    }

    val previousIsEditMode = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isEditMode, richTextState) {
        val wasEdit = previousIsEditMode.value
        previousIsEditMode.value = isEditMode
        if (wasEdit == true && !isEditMode) {
            pushMarkdownIfChanged(richTextState.toMarkdown())
        }
    }

    LaunchedEffect(editorNoteKey, isEditMode) {
        if (!isEditMode) {
            editNoteViewModel.body.collect { latestBody ->
                if (latestBody != lastSyncedBody.value) {
                    richTextState.setMarkdown(latestBody)
                    lastSyncedBody.value = latestBody
                }
            }
        } else if (lastSyncedBody.value.isEmpty()) {
            val initialBody = withTimeoutOrNull(10_000L) {
                editNoteViewModel.body.first { it.isNotBlank() }
            }
            if (initialBody != null && richTextState.annotatedString.text.isBlank()) {
                richTextState.setMarkdown(initialBody)
                lastSyncedBody.value = initialBody
            }
        }
    }

    if (isEditMode) {
        LaunchedEffect(richTextState) {
            try {
                snapshotFlow { richTextState.annotatedString }
                    .distinctUntilChanged()
                    .debounce(250)
                    .collectLatest {
                        pushMarkdownIfChanged(richTextState.toMarkdown())
                    }
            } finally {
                pushMarkdownIfChanged(richTextState.toMarkdown())
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
    DisposableEffect(lifecycleOwner, richTextState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pushMarkdownIfChanged(richTextState.toMarkdown())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pushMarkdownIfChanged(richTextState.toMarkdown())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteLargeTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    title: String,
    titlePlaceholder: String,
    existing: Boolean,
    isEditMode: Boolean,
    pinned: Boolean,
    iconKey: String?,
    onTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onTogglePin: () -> Unit,
    onTrash: () -> Unit,
) {
    LargeTopAppBar(
        colors = transparentLargeTopAppBarColors(),
        title = {
            val collapseFraction = scrollBehavior.state.collapsedFraction
            val expandedStyle = MaterialTheme.typography.headlineMedium
            val collapsedStyle = MaterialTheme.typography.titleLarge
            val titleStyle = expandedStyle.copy(
                fontSize = lerp(
                    expandedStyle.fontSize,
                    collapsedStyle.fontSize,
                    collapseFraction,
                ),
                lineHeight = lerp(
                    expandedStyle.lineHeight,
                    collapsedStyle.lineHeight,
                    collapseFraction,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val iconSize = lerp(28.dp, 22.dp, collapseFraction)
            val iconGap = lerp(12.dp, 8.dp, collapseFraction)
            val headerIcon = iconFor(iconKey)
            val headerEmoji = iconEmojiPayload(iconKey)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (headerIcon != null) {
                    Icon(
                        headerIcon,
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
                        onValueChange = onTitleChange,
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            FilledTonalIconButton(onClick = onToggleEditMode) {
                Icon(
                    if (isEditMode) Icons.Filled.Done else Icons.Filled.Edit,
                    contentDescription = if (isEditMode) "Done" else "Edit",
                )
            }
            Spacer(Modifier.width(6.dp))

            FilledTonalIconButton(onClick = onTogglePin) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                )
            }
            if (existing) {
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(onClick = onTrash) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Trash")
                }
            }
            Spacer(Modifier.width(4.dp))
        },
        scrollBehavior = scrollBehavior,
    )
}

@androidx.compose.runtime.Stable
internal class UndoRedoController {
    private val undoStack = androidx.compose.runtime.mutableStateListOf<String>()
    private val redoStack = androidx.compose.runtime.mutableStateListOf<String>()
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
        if (undoStack.size > MaxHistory) undoStack.removeAt(0)
        redoStack.clear()
        lastPushed = markdown
    }

    fun undo(current: String): String? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        if (redoStack.size > MaxHistory) redoStack.removeAt(0)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        lastPushed = prev
        suppressCapture = true
        return prev
    }

    fun redo(current: String): String? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        if (undoStack.size > MaxHistory) undoStack.removeAt(0)
        val next = redoStack.removeAt(redoStack.lastIndex)
        lastPushed = next
        suppressCapture = true
        return next
    }

    companion object { private const val MaxHistory = 50 }
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
    event: androidx.compose.ui.input.key.KeyEvent,
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
                .onPreviewKeyEvent { event ->
                    handleRichEditorKey(event, richTextState)
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteScrollableEditorColumn(
    scrollBehavior: TopAppBarScrollBehavior,
    horizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    tags: List<String>,
    pictureUri: String?,
    onPictureClear: () -> Unit,
    richTextState: RichTextState,
    bodyPlaceholder: String,
    isEditMode: Boolean,
    reminderAt: Long?,
    importance: Importance,
    iconKey: String?,
    actions: List<NoteAction>,
    attachmentCount: Int,
    onOpenReminder: () -> Unit,
    onSetImportance: (Importance) -> Unit,
    onOpenPicture: () -> Unit,
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
        Spacer(Modifier.height(topPadding))
        TagAccentEditorStrip(tags = tags)
        if (pictureUri != null) {
            Spacer(Modifier.height(16.dp))
            EditNotePictureHero(uri = pictureUri, onClear = onPictureClear)
        }
        Spacer(Modifier.height(16.dp))

        EditNoteRichEditorSection(
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
        )
        Spacer(Modifier.height(24.dp))
        OptionsPanel(
            reminderAt = reminderAt,
            importance = importance,
            pictureUri = pictureUri,
            iconKey = iconKey,
            actions = actions,
            tags = tags,
            attachmentCount = attachmentCount,
            onOpenReminder = onOpenReminder,
            onSetImportance = onSetImportance,
            onOpenPicture = onOpenPicture,
            onOpenIcon = onOpenIcon,
            onOpenActions = onOpenActions,
            onOpenTags = onOpenTags,
            onOpenAttachments = onOpenAttachments,
        )
        Spacer(Modifier.height(40.dp + bottomPadding))
    }
}

@Composable
internal fun EditNoteOverlaySheets(
    repository: NoteRepository,
    reminderPickerOpen: Boolean,
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    onReminderChange: (Long?, RecurrenceRule?) -> Unit,
    onReminderPickerDismiss: () -> Unit,
    iconPickerOpen: Boolean,
    iconKey: String?,
    onIconKeyChange: (String?) -> Unit,
    onIconPickerDismiss: () -> Unit,
    actionsPickerOpen: Boolean,
    actions: List<NoteAction>,
    onActionsChange: (List<NoteAction>) -> Unit,
    onActionsPickerDismiss: () -> Unit,
    tagsPickerOpen: Boolean,
    tags: List<String>,
    onTagsWithColorsChange: (List<String>, Map<String, String>) -> Unit,
    onTagsPickerDismiss: () -> Unit,
    attachmentsPickerOpen: Boolean,
    attachments: List<NoteAttachmentEntity>,
    onAddAttachment: (Uri, String, String?) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onAttachmentsDismiss: () -> Unit,
) {
    if (reminderPickerOpen) {
        ReminderPickerDialog(
            initialMillis = reminderAt,
            initialRule = recurrence,
            onConfirm = { at, rule ->
                onReminderChange(at, rule)
                onReminderPickerDismiss()
            },
            onDismiss = onReminderPickerDismiss,
        )
    }
    if (iconPickerOpen) {
        IconPicker(
            current = iconKey,
            onPick = {
                onIconKeyChange(it)
                onIconPickerDismiss()
            },
            onDismiss = onIconPickerDismiss,
        )
    }
    if (actionsPickerOpen) {
        ActionPicker(
            current = actions,
            onConfirm = {
                onActionsChange(it)
                onActionsPickerDismiss()
            },
            onDismiss = onActionsPickerDismiss,
        )
    }
    if (tagsPickerOpen) {
        TagEditorSheet(
            initial = tags,
            repository = repository,
            onConfirm = { newTags, newColors ->
                onTagsWithColorsChange(newTags, newColors)
                onTagsPickerDismiss()
            },
            onDismiss = onTagsPickerDismiss,
        )
    }
    if (attachmentsPickerOpen) {
        AttachmentsSheet(
            attachments = attachments,
            onDismiss = onAttachmentsDismiss,
            onAdd = onAddAttachment,
            onRemove = onRemoveAttachment,
        )
    }
}

@Composable
private fun EditNotePictureHero(uri: String, onClear: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxSidePx = remember(density) {
        with(density) { (440.dp * 3f).toPx().toInt().coerceIn(480, 2048) }
    }
    val imageRequest = remember(uri, maxSidePx) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(maxSidePx, maxSidePx))
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalIconButton(
            onClick = onClear,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove picture")
        }
    }
}
