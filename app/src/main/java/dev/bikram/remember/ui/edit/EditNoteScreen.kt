package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import dev.bikram.remember.R
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.Visibility as NoteVisibility
import dev.bikram.remember.ui.common.RichTextToolbar
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditNoteViewModel(
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val noteId: Long?,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _reminderAt = MutableStateFlow<Long?>(null)
    val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()

    private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
    val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

    private val _importance = MutableStateFlow(Importance.DEFAULT)
    val importance: StateFlow<Importance> = _importance.asStateFlow()

    private val _visibility = MutableStateFlow(NoteVisibility.PRIVATE)
    val visibility: StateFlow<NoteVisibility> = _visibility.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _pictureUri = MutableStateFlow<String?>(null)
    val pictureUri: StateFlow<String?> = _pictureUri.asStateFlow()

    private val _iconKey = MutableStateFlow<String?>(null)
    val iconKey: StateFlow<String?> = _iconKey.asStateFlow()

    private val _actions = MutableStateFlow<List<NoteAction>>(emptyList())
    val actions: StateFlow<List<NoteAction>> = _actions.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

    private var loadedId: Long? = noteId
    private var dirty: Boolean = false

    init {
        if (noteId != null) {
            viewModelScope.launch {
                val existing = repository.get(noteId) ?: return@launch
                val n = existing.note
                _title.value = n.title
                _body.value = n.body
                _pinned.value = n.pinned
                _reminderAt.value = n.reminderAt
                _recurrence.value = n.recurrence
                _importance.value = n.importance
                _visibility.value = n.visibility
                _locked.value = n.locked
                _pictureUri.value = n.pictureUri
                _iconKey.value = n.iconKey
                _actions.value = n.actions
                _tags.value = n.tags
                _attachments.value = existing.attachments
            }
        }
    }

    fun setTitle(v: String)                  { _title.value = v; dirty = true }
    fun setBody(v: String)                   { _body.value  = v; dirty = true }
    fun togglePin()                          { _pinned.value = !_pinned.value; dirty = true }
    fun setReminder(at: Long?, rule: RecurrenceRule?) {
        _reminderAt.value = at
        _recurrence.value = rule
        dirty = true
    }
    fun setImportance(v: Importance)         { _importance.value = v; dirty = true }
    fun setVisibility(v: NoteVisibility)     { _visibility.value = v; dirty = true }
    fun toggleLock()                         { _locked.value = !_locked.value; dirty = true }
    fun setPictureUri(v: String?)            { _pictureUri.value = v; dirty = true }
    fun setIconKey(v: String?)               { _iconKey.value = v; dirty = true }
    fun setActions(v: List<NoteAction>)      { _actions.value = v; dirty = true }
    fun setTags(v: List<String>)             { _tags.value = v; dirty = true }

    fun saveTagsWithColors(tags: List<String>, newColors: Map<String, String>) {
        _tags.value = tags
        dirty = true
        if (newColors.isNotEmpty()) {
            viewModelScope.launch {
                newColors.forEach { (name, hex) -> themePrefs.setTagColor(name, hex) }
            }
        }
    }

    fun addAttachment(uri: Uri, name: String, mime: String?) {
        viewModelScope.launch {
            val id = loadedId ?: run {
                val newId = repository.createNote(
                    title = _title.value,
                    body = _body.value,
                    colorIndex = 0,
                    options = currentOptions(),
                )
                loadedId = newId
                newId
            }
            repository.addAttachment(id, uri.toString(), name, mime)
            _attachments.value = repository.get(id)?.attachments ?: emptyList()
            dirty = true
        }
    }

    fun removeAttachment(attachmentId: Long) {
        viewModelScope.launch {
            repository.removeAttachment(attachmentId)
            val id = loadedId
            if (id != null) {
                _attachments.value = repository.get(id)?.attachments ?: emptyList()
            } else {
                _attachments.value = _attachments.value.filterNot { it.id == attachmentId }
            }
            dirty = true
        }
    }

    private fun currentOptions() = NoteOptions(
        reminderAt = _reminderAt.value,
        importance = _importance.value,
        visibility = _visibility.value,
        pictureUri = _pictureUri.value,
        locked = _locked.value,
        iconKey = _iconKey.value,
        actions = _actions.value,
        tags = _tags.value,
        recurrence = _recurrence.value,
    )

    suspend fun saveIfNeeded() {
        val titleValue = _title.value
        val bodyValue = _body.value
        val id = loadedId
        val empty = titleValue.isBlank() && bodyValue.isBlank()
        if (id == null) {
            if (empty) return
            val newId = repository.createNote(titleValue, bodyValue, 0, currentOptions())
            loadedId = newId
            if (_pinned.value) repository.setPinned(newId, true)
            dirty = false
        } else {
            if (!dirty) return
            repository.updateNote(id, titleValue, bodyValue, 0, currentOptions())
            val cur = repository.get(id)?.note
            if (cur != null && cur.pinned != _pinned.value) {
                repository.setPinned(id, _pinned.value)
            }
            dirty = false
        }
    }

    suspend fun trashCurrent() {
        val id = loadedId ?: return
        repository.moveToTrash(id)
    }

    companion object {
        fun factory(
            repository: NoteRepository,
            themePrefs: ThemePrefs,
            noteId: Long?,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditNoteViewModel(repository, themePrefs, noteId) as T
        }
    }
}

@Composable
fun EditNoteRoute(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    appScope: CoroutineScope,
    noteId: Long?,
    prefillBody: String = "",
    onBack: () -> Unit,
) {
    val vm: EditNoteViewModel = viewModel(
        key = "editNote-${noteId ?: 0L}",
        factory = EditNoteViewModel.factory(repository, themePrefs, noteId),
    )
    LaunchedEffect(vm, prefillBody) {
        if (noteId == null && prefillBody.isNotBlank()) {
            vm.setBody(prefillBody)
        }
    }
    val title by vm.title.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val reminderAt by vm.reminderAt.collectAsStateWithLifecycle()
    val recurrence by vm.recurrence.collectAsStateWithLifecycle()
    val importance by vm.importance.collectAsStateWithLifecycle()
    val visibility by vm.visibility.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val pictureUri by vm.pictureUri.collectAsStateWithLifecycle()
    val iconKey by vm.iconKey.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()

    DisposableEffect(vm) {
        onDispose { appScope.launch { vm.saveIfNeeded() } }
    }

    EditNoteScreen(
        repository = repository,
        editorNoteKey = noteId ?: 0L,
        title = title,
        editNoteViewModel = vm,
        pinned = pinned,
        reminderAt = reminderAt,
        recurrence = recurrence,
        importance = importance,
        visibility = visibility,
        locked = locked,
        pictureUri = pictureUri,
        iconKey = iconKey,
        actions = actions,
        tags = tags,
        attachments = attachments,
        existing = noteId != null,
        onTitleChange = vm::setTitle,
        onBodyChange = vm::setBody,
        onTogglePin = vm::togglePin,
        onReminderChange = vm::setReminder,
        onImportanceChange = vm::setImportance,
        onVisibilityChange = vm::setVisibility,
        onToggleLock = vm::toggleLock,
        onPictureChange = vm::setPictureUri,
        onIconKeyChange = vm::setIconKey,
        onActionsChange = vm::setActions,
        onTagsChange = vm::setTags,
        onTagsWithColorsChange = vm::saveTagsWithColors,
        onAddAttachment = vm::addAttachment,
        onRemoveAttachment = vm::removeAttachment,
        onTrash = {
            appScope.launch { vm.trashCurrent() }
            onBack()
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalRichTextApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun EditNoteScreen(
    repository: NoteRepository,
    editorNoteKey: Long,
    title: String,
    editNoteViewModel: EditNoteViewModel,
    pinned: Boolean,
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    importance: Importance,
    visibility: NoteVisibility,
    locked: Boolean,
    pictureUri: String?,
    iconKey: String?,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    existing: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onReminderChange: (Long?, RecurrenceRule?) -> Unit,
    onImportanceChange: (Importance) -> Unit,
    onVisibilityChange: (NoteVisibility) -> Unit,
    onToggleLock: () -> Unit,
    onPictureChange: (String?) -> Unit,
    onIconKeyChange: (String?) -> Unit,
    onActionsChange: (List<NoteAction>) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onTagsWithColorsChange: (List<String>, Map<String, String>) -> Unit,
    onAddAttachment: (Uri, String, String?) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onTrash: () -> Unit,
    onBack: () -> Unit,
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    var actionsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentsPickerOpen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePicker = rememberImagePicker { uri ->
        persistReadPermission(context, uri)
        onPictureChange(uri.toString())
    }

    val titlePlaceholder = if (existing) {
        stringResource(R.string.edit_note_title_existing)
    } else {
        stringResource(R.string.common_title)
    }
    val bodyPlaceholder = stringResource(R.string.edit_note_body_placeholder)
    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = PillBottomBarHeight * 0)

    var isEditMode by remember(existing) { mutableStateOf(!existing) }
    val richTextState = rememberRichTextState()
    val undoController = remember(editorNoteKey) { UndoRedoController() }
    LaunchedEffect(editorNoteKey) {
        undoController.reset(editNoteViewModel.body.value)
    }
    EditNoteMarkdownSyncEffects(
        editorNoteKey = editorNoteKey,
        isEditMode = isEditMode,
        editNoteViewModel = editNoteViewModel,
        richTextState = richTextState,
        onBodyChange = onBodyChange,
        undoController = undoController,
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(visible = isEditMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().imePadding(),
                ) {
                    RichTextToolbar(
                        state = richTextState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        canUndo = undoController.canUndo,
                        canRedo = undoController.canRedo,
                        onUndo = {
                            undoController.undo(richTextState.toMarkdown())?.let { richTextState.setMarkdown(it) }
                        },
                        onRedo = {
                            undoController.redo(richTextState.toMarkdown())?.let { richTextState.setMarkdown(it) }
                        },
                    )
                }
            }
        },
        topBar = {
            EditNoteLargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = title,
                titlePlaceholder = titlePlaceholder,
                existing = existing,
                isEditMode = isEditMode,
                pinned = pinned,
                iconKey = iconKey,
                onTitleChange = onTitleChange,
                onBack = onBack,
                onToggleEditMode = { isEditMode = !isEditMode },
                onTogglePin = onTogglePin,
                onTrash = onTrash,
            )
        },
    ) { padding ->
        val blurMod = blurStyle?.applyToFullBleedLayer() ?: Modifier
        EditNoteScrollableEditorColumn(
            scrollBehavior = scrollBehavior,
            horizontalPadding = 20.dp,
            topPadding = padding.calculateTopPadding(),
            bottomPadding = padding.calculateBottomPadding(),
            tags = tags,
            pictureUri = pictureUri,
            onPictureClear = { onPictureChange(null) },
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
            reminderAt = reminderAt,
            importance = importance,
            iconKey = iconKey,
            actions = actions,
            attachmentCount = attachments.size,
            onOpenReminder = { reminderPickerOpen = true },
            onSetImportance = onImportanceChange,
            onOpenPicture = {
                imagePicker.launch(
                    PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onOpenIcon = { iconPickerOpen = true },
            onOpenActions = { actionsPickerOpen = true },
            onOpenTags = { tagsPickerOpen = true },
            onOpenAttachments = { attachmentsPickerOpen = true },
            blurModifier = blurMod,
        )

        EditNoteOverlaySheets(
            repository = repository,
            reminderPickerOpen = reminderPickerOpen,
            reminderAt = reminderAt,
            recurrence = recurrence,
            onReminderChange = onReminderChange,
            onReminderPickerDismiss = { reminderPickerOpen = false },
            iconPickerOpen = iconPickerOpen,
            iconKey = iconKey,
            onIconKeyChange = onIconKeyChange,
            onIconPickerDismiss = { iconPickerOpen = false },
            actionsPickerOpen = actionsPickerOpen,
            actions = actions,
            onActionsChange = onActionsChange,
            onActionsPickerDismiss = { actionsPickerOpen = false },
            tagsPickerOpen = tagsPickerOpen,
            tags = tags,
            onTagsWithColorsChange = onTagsWithColorsChange,
            onTagsPickerDismiss = { tagsPickerOpen = false },
            attachmentsPickerOpen = attachmentsPickerOpen,
            attachments = attachments,
            onAddAttachment = onAddAttachment,
            onRemoveAttachment = onRemoveAttachment,
            onAttachmentsDismiss = { attachmentsPickerOpen = false },
        )
    }
}
