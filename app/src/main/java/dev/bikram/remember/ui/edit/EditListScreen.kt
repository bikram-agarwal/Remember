package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil3.compose.AsyncImage
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.Visibility as NoteVisibility
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.components.TagAccentEditorStrip
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditableItem(
    val localId: Long,
    val text: String,
    val checked: Boolean,
)

class EditListViewModel(
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val noteId: Long?,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _items = MutableStateFlow<List<EditableItem>>(emptyList())
    val items: StateFlow<List<EditableItem>> = _items.asStateFlow()

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
    private var nextLocalId: Long = -1L

    init {
        if (noteId != null) {
            viewModelScope.launch {
                val existing = repository.get(noteId) ?: return@launch
                val n = existing.note
                _title.value = n.title
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
                _items.value = existing.items.map {
                    EditableItem(localId = it.id, text = it.text, checked = it.checked)
                }
            }
        }
    }

    fun setTitle(v: String)                  { _title.value = v; dirty = true }
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

    fun addItem() {
        _items.value = _items.value + EditableItem(nextLocalId--, "", false)
        dirty = true
    }

    fun updateItemText(localId: Long, text: String) {
        _items.value = _items.value.map {
            if (it.localId == localId) it.copy(text = text) else it
        }
        dirty = true
    }

    fun toggleChecked(localId: Long) {
        _items.value = _items.value.map {
            if (it.localId == localId) it.copy(checked = !it.checked) else it
        }
        dirty = true
    }

    fun removeItem(localId: Long) {
        _items.value = _items.value.filterNot { it.localId == localId }
        dirty = true
    }

    fun addAttachment(uri: Uri, name: String, mime: String?) {
        viewModelScope.launch {
            val id = loadedId ?: run {
                val entities = currentItems()
                val newId = repository.createList(
                    title = _title.value,
                    colorIndex = 0,
                    items = entities.map { it.text },
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
            _attachments.value = if (id != null) {
                repository.get(id)?.attachments ?: emptyList()
            } else {
                _attachments.value.filterNot { it.id == attachmentId }
            }
            dirty = true
        }
    }

    private fun currentOptions() = NoteOptions(
        reminderAt = _reminderAt.value,
        recurrence = _recurrence.value,
        importance = _importance.value,
        visibility = _visibility.value,
        pictureUri = _pictureUri.value,
        locked = _locked.value,
        iconKey = _iconKey.value,
        actions = _actions.value,
        tags = _tags.value,
    )

    private fun currentItems(): List<ChecklistItemEntity> {
        val id = loadedId ?: 0L
        val nonEmpty = _items.value.filter { it.text.isNotBlank() }
        return nonEmpty.mapIndexed { idx, item ->
            ChecklistItemEntity(
                id = 0,
                noteId = id,
                text = item.text,
                checked = item.checked,
                position = idx,
            )
        }
    }

    suspend fun saveIfNeeded() {
        val t = _title.value
        val id = loadedId
        val nonEmpty = _items.value.filter { it.text.isNotBlank() }
        val entities = nonEmpty.mapIndexed { idx, item ->
            ChecklistItemEntity(
                id = 0,
                noteId = id ?: 0L,
                text = item.text,
                checked = item.checked,
                position = idx,
            )
        }
        val empty = t.isBlank() && entities.isEmpty()
        if (id == null) {
            if (empty) return
            val newId = repository.createList(t, 0, entities.map { it.text }, currentOptions())
            if (nonEmpty.any { it.checked }) {
                val saved = repository.get(newId)
                saved?.items?.forEachIndexed { idx, it ->
                    val want = nonEmpty.getOrNull(idx)?.checked ?: false
                    if (want && !it.checked) repository.toggleItemChecked(it)
                }
            }
            if (_pinned.value) repository.setPinned(newId, true)
        } else {
            if (!dirty) return
            repository.updateList(id, t, 0, entities, currentOptions())
            val cur = repository.get(id)?.note
            if (cur != null && cur.pinned != _pinned.value) {
                repository.setPinned(id, _pinned.value)
            }
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
                EditListViewModel(repository, themePrefs, noteId) as T
        }
    }
}

@Composable
fun EditListRoute(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    appScope: CoroutineScope,
    noteId: Long?,
    onBack: () -> Unit,
) {
    val vm: EditListViewModel = viewModel(
        key = "editList-${noteId ?: 0L}",
        factory = EditListViewModel.factory(repository, themePrefs, noteId),
    )
    val title by vm.title.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
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

    EditListScreen(
        repository = repository,
        title = title,
        pinned = pinned,
        items = items,
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
        onTogglePin = vm::togglePin,
        onAddItem = vm::addItem,
        onItemTextChange = vm::updateItemText,
        onItemToggle = vm::toggleChecked,
        onItemRemove = vm::removeItem,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditListScreen(
    repository: NoteRepository,
    title: String,
    pinned: Boolean,
    items: List<EditableItem>,
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
    onTogglePin: () -> Unit,
    onAddItem: () -> Unit,
    onItemTextChange: (Long, String) -> Unit,
    onItemToggle: (Long) -> Unit,
    onItemRemove: (Long) -> Unit,
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

    val titlePlaceholder = if (existing) "Untitled" else "New list"
    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = 0.dp)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
        },
    ) { padding ->
        val blurMod = blurStyle?.applyToFullBleedLayer() ?: Modifier
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            TagAccentEditorStrip(tags = tags)
            if (pictureUri != null) {
                Spacer(Modifier.height(16.dp))
                PictureHero(uri = pictureUri, onClear = { onPictureChange(null) })
            }
            Spacer(Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { item ->
                    ChecklistRow(
                        item = item,
                        onTextChange = { onItemTextChange(item.localId, it) },
                        onToggle = { onItemToggle(item.localId) },
                        onRemove = { onItemRemove(item.localId) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddItem() }
                        .padding(vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Add item",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OptionsPanel(
                reminderAt = reminderAt,
                importance = importance,
                pictureUri = pictureUri,
                iconKey = iconKey,
                actions = actions,
                tags = tags,
                attachmentCount = attachments.size,
                onOpenReminder = { reminderPickerOpen = true },
                onSetImportance = onImportanceChange,
                onOpenPicture = {
                    imagePicker.launch(
                        PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenIcon = { iconPickerOpen = true },
                onOpenActions = { actionsPickerOpen = true },
                onOpenTags = { tagsPickerOpen = true },
                onOpenAttachments = { attachmentsPickerOpen = true },
            )
            Spacer(Modifier.height(40.dp + padding.calculateBottomPadding()))
        }

        if (reminderPickerOpen) {
            ReminderPickerDialog(
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
    }
}

@Composable
private fun PictureHero(uri: String, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AsyncImage(
            model = uri,
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

@Composable
private fun ChecklistRow(
    item: EditableItem,
    onTextChange: (String) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (item.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = item.text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (item.text.isEmpty()) {
                    Text(
                        "New item",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
                inner()
            },
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

