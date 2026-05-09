package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.domain.checklist.ChecklistEditResult
import dev.bikram.remember.domain.checklist.ChecklistEditor
import dev.bikram.remember.domain.checklist.EditableItem
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import dev.bikram.remember.data.Visibility as NoteVisibility

@HiltViewModel
class EditListViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
        private val appMediaStorage: AppMediaStorage? = null,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val noteId: Long? =
            savedStateHandle
                .get<Long>(Routes.ARG_ID)
                ?.takeIf { it > 0L }

        private val _title = MutableStateFlow("")
        val title: StateFlow<String> = _title.asStateFlow()

        private val _starred = MutableStateFlow(false)
        val starred: StateFlow<Boolean> = _starred.asStateFlow()

        /** Bool projection of NoteEntity.completedAt; same semantics as EditNoteViewModel.completed. */
        private val _completed = MutableStateFlow(false)
        val completed: StateFlow<Boolean> = _completed.asStateFlow()

        private val _items = MutableStateFlow<List<EditableItem>>(emptyList())
        val items: StateFlow<List<EditableItem>> = _items.asStateFlow()

        private val _reminderAt = MutableStateFlow<Long?>(null)
        val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()

        private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
        val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

        private val _importance = MutableStateFlow(Importance.DEFAULT)
        val importance: StateFlow<Importance> = _importance.asStateFlow()

        private val _visibility = MutableStateFlow(NoteVisibility.DEFAULT)
        val visibility: StateFlow<NoteVisibility> = _visibility.asStateFlow()

        private val _locked = MutableStateFlow(false)
        val locked: StateFlow<Boolean> = _locked.asStateFlow()

        private val _pictureUri = MutableStateFlow<String?>(null)
        val pictureUri: StateFlow<String?> = _pictureUri.asStateFlow()

        private val _pictureRevision = MutableStateFlow(0L)
        val pictureRevision: StateFlow<Long> = _pictureRevision.asStateFlow()

        private val _pictureHeroFraming = MutableStateFlow<String?>(null)
        val pictureHeroFraming: StateFlow<String?> = _pictureHeroFraming.asStateFlow()

        private val _iconKey = MutableStateFlow<String?>(null)
        val iconKey: StateFlow<String?> = _iconKey.asStateFlow()

        private val _actions = MutableStateFlow<List<NoteAction>>(emptyList())
        val actions: StateFlow<List<NoteAction>> = _actions.asStateFlow()

        private val _tags = MutableStateFlow<List<String>>(emptyList())
        val tags: StateFlow<List<String>> = _tags.asStateFlow()
        val activeTagSuggestions: StateFlow<List<String>> =
            repository
                .observeActiveTagSuggestions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
        val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

        /**
         * Mirrors the underlying note's archived / trashed shelf. The edit screen uses these to
         * flip into read-only mode and swap the bottom-bar action set. New lists always start on
         * the active shelf.
         */
        private val _archived = MutableStateFlow(false)
        val archived: StateFlow<Boolean> = _archived.asStateFlow()

        private val _trashed = MutableStateFlow(false)
        val trashed: StateFlow<Boolean> = _trashed.asStateFlow()

        /**
         * True once this session is backed by a database row ([loadedId] non-null). Drives archive /
         * trash on the bottom bar after the first save without using the navigation [noteId] alone.
         */
        private val _hasPersistedRow = MutableStateFlow(noteId != null)
        val hasPersistedRow: StateFlow<Boolean> = _hasPersistedRow.asStateFlow()

        private val _hasUnsavedChanges = MutableStateFlow(false)
        val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

        private var loadedId: Long? = noteId
        private var dirty: Boolean = false
            set(value) {
                field = value
                _hasUnsavedChanges.value = value
            }
        private var nextLocalId: Long = -1L
        private val persistMutex = Mutex()
        private var originalNote: dev.bikram.remember.data.NoteEntity? = null
        private var originalItems: List<dev.bikram.remember.data.ChecklistItemEntity> = emptyList()

        private fun syncHasPersistedRow() {
            _hasPersistedRow.value = loadedId != null
        }

        init {
            if (noteId != null) {
                viewModelScope.launch {
                    val existing = repository.get(noteId) ?: return@launch
                    val n = existing.note
                    originalNote = n
                    originalItems = existing.items
                    _title.value = n.title
                    _starred.value = n.starred || n.tags.contains(RememberReservedTags.STARRED)
                    _reminderAt.value = n.reminderAt
                    _recurrence.value = n.recurrence?.sanitized()
                    _importance.value = n.importance
                    _visibility.value = n.visibility
                    _locked.value = n.locked
                    _pictureUri.value = n.pictureUri
                    _pictureHeroFraming.value = n.pictureHeroFraming
                    _iconKey.value = n.iconKey
                    _actions.value = n.actions
                    _tags.value = n.tags.filterNot { it == RememberReservedTags.STARRED }
                    _attachments.value = existing.attachments
                    _archived.value = n.archived
                    _trashed.value = n.trashed
                    _completed.value = n.completedAt != null
                    _items.value =
                        existing.items
                            .map {
                                EditableItem(
                                    localId = it.id,
                                    text = it.text,
                                    checked = it.checked,
                                    sortOrder = it.sortOrder,
                                    parentLocalId = it.parentId,
                                    depth = it.depth,
                                )
                            }.sortedBy { it.sortOrder }
                }
                // Same rationale as EditNoteViewModel: mirror only fields that get
                // written from outside this VM (snooze action, recurrence advance,
                // mark-as-done) so the open list reflects them live.
                viewModelScope.launch {
                    repository.observe(noteId).collect { row ->
                        val n = row?.note ?: return@collect
                        if (_reminderAt.value != n.reminderAt) _reminderAt.value = n.reminderAt
                        val sanitized = n.recurrence?.sanitized()
                        if (_recurrence.value != sanitized) _recurrence.value = sanitized
                        if (_trashed.value != n.trashed) _trashed.value = n.trashed
                        val isCompleted = n.completedAt != null
                        if (_completed.value != isCompleted) _completed.value = isCompleted
                    }
                }
            }
        }

        /**
         * Mark this list done / not done. Routes to the recurrence-aware repository methods
         * - completing a recurring list rolls its reminder forward instead of moving it
         * to Done. The live observer in [init] will reflect the new value back into
         * [completed] automatically.
         */
        suspend fun toggleCompleted() {
            val id = loadedId ?: return
            if (_completed.value) {
                repository.markIncomplete(id)
            } else {
                repository.markCompleted(id)
            }
        }

        fun setTitle(v: String) {
            _title.value = v
            dirty = true
        }

        fun toggleStar() {
            _starred.value = !_starred.value
            dirty = true
        }

        fun setReminder(
            at: Long?,
            rule: RecurrenceRule?,
        ) {
            _reminderAt.value = at
            _recurrence.value = rule?.sanitized()
            dirty = true
        }

        fun setImportance(v: Importance) {
            _importance.value = v
            dirty = true
        }

        fun setVisibility(v: NoteVisibility) {
            if (_visibility.value == v) return
            _visibility.value = v
            dirty = true
            refreshActiveNotificationVisibility(v)
        }

        private fun refreshActiveNotificationVisibility(value: NoteVisibility) {
            val id = loadedId ?: return
            viewModelScope.launch {
                repository.refreshNotificationVisibilityPreview(id, value)
            }
        }

        fun toggleLock() {
            _locked.value = !_locked.value
            dirty = true
        }

        fun setPictureUri(v: String?) {
            _pictureUri.value = v
            if (v == null) {
                _pictureHeroFraming.value = null
            }
            _pictureRevision.value = _pictureRevision.value + 1L
            dirty = true
        }

        fun setHeroWithFraming(
            pictureUri: String,
            framing: HeroFraming,
        ) {
            _pictureUri.value = pictureUri
            _pictureHeroFraming.value = framing.toJsonString()
            _pictureRevision.value = _pictureRevision.value + 1L
            dirty = true
        }

        fun setIconKey(v: String?) {
            _iconKey.value = v
            dirty = true
        }

        fun setActions(v: List<NoteAction>) {
            _actions.value = v
            dirty = true
        }

        fun setTags(v: List<String>) {
            _tags.value = v.filterNot { it == RememberReservedTags.STARRED }
            dirty = true
        }

        fun saveTagsWithColors(
            tags: List<String>,
            newColors: Map<String, String>,
        ) {
            _tags.value = tags.filterNot { it == RememberReservedTags.STARRED }
            dirty = true
            if (newColors.isNotEmpty()) {
                viewModelScope.launch {
                    newColors.forEach { (name, hex) ->
                        repository.tagRepository?.setTagColor(name, hex)
                    }
                }
            }
        }

        fun editExistingTag(
            oldName: String,
            newName: String,
            colorHex: String?,
            resetColor: Boolean,
        ) {
            viewModelScope.launch {
                val result =
                    repository.tagRepository?.editTag(
                        oldName = oldName,
                        newName = newName,
                        colorHex = colorHex,
                        resetColor = resetColor,
                    ) ?: return@launch
                val updatedTags =
                    _tags.value
                        .map { tagName ->
                            if (tagName.equals(result.oldName, ignoreCase = true)) result.newName else tagName
                        }.distinctBy { tagName -> tagName.lowercase() }
                if (_tags.value != updatedTags) {
                    _tags.value = updatedTags
                    dirty = true
                }
            }
        }

        /**
         * Appends a new top-level row at the end of the active section.
         * [max(sortOrder) + 1.0] per the weighted-position spec. Uses fresh negative [localId] so
         * drafts have stable identity until the row is persisted.
         */
        fun addItem() {
            val max = _items.value.maxOfOrNull { it.sortOrder } ?: 0.0
            _items.value = _items.value +
                EditableItem(
                    localId = nextLocalId--,
                    text = "",
                    checked = false,
                    sortOrder = max + 1.0,
                    parentLocalId = null,
                    depth = 0,
                )
            dirty = true
        }

        fun updateItemText(
            localId: Long,
            text: String,
        ) {
            _items.value =
                _items.value.map {
                    if (it.localId == localId) it.copy(text = text) else it
                }
            dirty = true
        }

        fun toggleChecked(localId: Long) {
            applyChecklistEdit(
                ChecklistEditor.toggleChecked(_items.value, localId),
            )
        }

        fun reorderWithin(
            visibleIds: List<Long>,
            fromIndex: Int,
            toIndex: Int,
        ) {
            applyChecklistEdit(
                ChecklistEditor.reorderWithin(_items.value, visibleIds, fromIndex, toIndex),
            )
        }

        fun setParent(
            localId: Long,
            newParentLocalId: Long?,
        ) {
            applyChecklistEdit(
                ChecklistEditor.setParent(_items.value, localId, newParentLocalId),
            )
        }

        fun indent(localId: Long) {
            applyChecklistEdit(
                ChecklistEditor.indent(_items.value, localId),
            )
        }

        fun outdent(localId: Long) {
            applyChecklistEdit(
                ChecklistEditor.outdent(_items.value, localId),
            )
        }

        private fun applyChecklistEdit(result: ChecklistEditResult) {
            if (!result.changed) {
                return
            }
            _items.value = result.items
            dirty = true
        }

        fun removeItem(localId: Long) {
            // Removing a parent also orphans its children (promote to top-level rather than delete).
            val list = _items.value
            val target = list.firstOrNull { it.localId == localId } ?: return
            _items.value =
                list
                    .filterNot { it.localId == localId }
                    .map {
                        if (target.depth == 0 && it.parentLocalId == localId) {
                            it.copy(parentLocalId = null, depth = 0)
                        } else {
                            it
                        }
                    }
            dirty = true
        }

        fun addAttachment(
            uri: Uri,
            name: String,
            mime: String?,
        ) {
            viewModelScope.launch {
                val id =
                    loadedId ?: run {
                        val entities = currentItems()
                        val newId =
                            repository.createList(
                                title = _title.value,
                                colorIndex = 0,
                                items = entities.map { it.text },
                                options = currentOptions(),
                            )
                        loadedId = newId
                        syncHasPersistedRow()
                        newId
                    }
                val attachmentUri =
                    appMediaStorage
                        ?.copyAttachmentToPrivateStorage(
                            noteId = id,
                            sourceUri = uri,
                            displayName = name,
                            mimeType = mime,
                        )?.uriString ?: uri.toString()
                repository.addAttachment(id, attachmentUri, name, mime)
                _attachments.value = repository.get(id)?.attachments ?: emptyList()
                dirty = true
            }
        }

        fun removeAttachment(attachmentId: Long) {
            viewModelScope.launch {
                repository.removeAttachment(attachmentId)
                val id = loadedId
                _attachments.value =
                    if (id != null) {
                        repository.get(id)?.attachments ?: emptyList()
                    } else {
                        _attachments.value.filterNot { it.id == attachmentId }
                    }
                dirty = true
            }
        }

        private fun tagsForPersistence(): List<String> {
            val base = _tags.value.filterNot { it == RememberReservedTags.STARRED }
            return if (_starred.value) {
                (base + RememberReservedTags.STARRED).distinct()
            } else {
                base
            }
        }

        private fun currentOptions() =
            NoteOptions(
                reminderAt = _reminderAt.value,
                recurrence = _recurrence.value,
                importance = _importance.value,
                visibility = _visibility.value,
                pictureUri = _pictureUri.value,
                pictureHeroFraming = _pictureHeroFraming.value,
                locked = _locked.value,
                iconKey = _iconKey.value,
                actions = _actions.value,
                tags = tagsForPersistence(),
            )

        private fun currentItems(): List<ChecklistItemEntity> {
            val id = loadedId ?: 0L
            val nonEmpty = _items.value.filter { it.text.isNotBlank() }
            return nonEmpty.map { item ->
                ChecklistItemEntity(
                    id = 0,
                    noteId = id,
                    text = item.text,
                    checked = item.checked,
                    sortOrder = item.sortOrder,
                    parentId = null,
                    depth = item.depth.coerceIn(0, 1),
                )
            }
        }

        /**
         * Translates the current in-memory hierarchy into the save-side shape. [EditableItem.localId]
         * doubles as [PersistableChecklistItem.localKey] so the repository can remap parent pointers
         * even for drafts that have never been written.
         */
        private fun currentPersistable(): List<dev.bikram.remember.data.PersistableChecklistItem> =
            _items.value
                .filter { it.text.isNotBlank() }
                .map { item ->
                    dev.bikram.remember.data.PersistableChecklistItem(
                        localKey = item.localId,
                        text = item.text,
                        checked = item.checked,
                        sortOrder = item.sortOrder,
                        parentLocalKey = item.parentLocalId,
                        depth = item.depth.coerceIn(0, 1),
                    )
                }

        private fun hasNetChanges(): Boolean {
            val id = loadedId
            val t = _title.value
            val nonEmpty = _items.value.filter { it.text.isNotBlank() }
            val opts = currentOptions()
            val starred = _starred.value
            if (id == null) {
                return t.isNotBlank() || nonEmpty.isNotEmpty() || _attachments.value.isNotEmpty() || opts.pictureUri != null || opts.tags.isNotEmpty() || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null
            } else {
                val old = originalNote ?: return true
                val oldItems = originalItems
                if (t != old.title) return true
                if (starred != old.starred) return true
                if (opts.reminderAt != old.reminderAt) return true
                if (opts.importance != old.importance) return true
                if (opts.visibility != old.visibility) return true
                if (opts.pictureUri != old.pictureUri) return true
                if (opts.pictureHeroFraming != old.pictureHeroFraming) return true
                if (opts.locked != old.locked) return true
                if (opts.iconKey != old.iconKey) return true
                if (opts.actions != old.actions) return true
                if (opts.tags != old.tags) return true
                if (opts.recurrence != old.recurrence) return true
                if (nonEmpty.size != oldItems.size) return true
                // Compare by sortOrder-sorted traversal rather than raw list index, since the editor
                // reorders items by sortOrder while the persisted list also comes back ordered by
                // sortOrder from the DAO query.
                val nowSorted = nonEmpty.sortedBy { it.sortOrder }
                val oldSorted = oldItems.sortedBy { it.sortOrder }
                for (i in nowSorted.indices) {
                    val c = nowSorted[i]
                    val o = oldSorted[i]
                    if (c.text != o.text) return true
                    if (c.checked != o.checked) return true
                    if (c.sortOrder != o.sortOrder) return true
                    if (c.depth != o.depth) return true
                    // Parent pointer equality: both sides refer to rows by Room id once saved, and by
                    // the stable editor localId while editing. Only flag a change when both sides have
                    // a parent and the ids disagree (or one side has a parent and the other does not).
                    val cParent = c.parentLocalId
                    val oParent = o.parentId
                    if ((cParent == null) != (oParent == null)) return true
                }
                return false
            }
        }

        suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)? {
            return persistMutex.withLock {
                if (!hasNetChanges()) {
                    dirty = false
                    return@withLock null
                }
                val t = _title.value
                val id = loadedId
                val finalTitle = t.ifBlank { untitledName }
                val nonEmpty = _items.value.filter { it.text.isNotBlank() }
                val persistable = currentPersistable()
                if (id == null) {
                    if (!dirty) return@withLock null
                    // Creation still goes through createList which assigns sortOrder itself. If the
                    // user pre-composed hierarchy/checked state in the draft, we re-run updateList
                    // immediately afterwards with the real persistable payload.
                    val newId = repository.createList(finalTitle, 0, nonEmpty.map { it.text }, currentOptions())
                    loadedId = newId
                    syncHasPersistedRow()
                    if (t.isBlank()) _title.value = finalTitle
                    if (persistable.any { it.checked || it.parentLocalKey != null || it.depth > 0 }) {
                        repository.updateList(newId, finalTitle, 0, persistable, currentOptions())
                    }
                    if (_starred.value) repository.setStarred(newId, true)
                    dirty = false

                    val savedList = repository.get(newId)
                    originalNote = savedList?.note
                    originalItems = savedList?.items ?: emptyList()

                    return@withLock {
                        repository.moveToTrash(newId)
                    }
                } else {
                    if (!dirty) return@withLock null
                    repository.updateList(id, finalTitle, 0, persistable, currentOptions())
                    if (t.isBlank()) _title.value = finalTitle
                    val cur = repository.get(id)?.note
                    if (cur != null && cur.starred != _starred.value) {
                        repository.setStarred(id, _starred.value)
                    }
                    dirty = false

                    val old = originalNote
                    val oldItems = originalItems

                    val savedList = repository.get(id)
                    originalNote = savedList?.note
                    originalItems = savedList?.items ?: emptyList()

                    if (old != null) {
                        return@withLock {
                            repository.updateList(
                                id = id,
                                title = old.title,
                                colorIndex = old.colorIndex,
                                items =
                                    oldItems.map {
                                        dev.bikram.remember.data.PersistableChecklistItem(
                                            localKey = it.id,
                                            text = it.text,
                                            checked = it.checked,
                                            sortOrder = it.sortOrder,
                                            parentLocalKey = it.parentId,
                                            depth = it.depth,
                                        )
                                    },
                                options =
                                    NoteOptions(
                                        reminderAt = old.reminderAt,
                                        importance = old.importance,
                                        visibility = old.visibility,
                                        pictureUri = old.pictureUri,
                                        pictureHeroFraming = old.pictureHeroFraming,
                                        locked = old.locked,
                                        iconKey = old.iconKey,
                                        actions = old.actions,
                                        tags = old.tags,
                                        recurrence = old.recurrence,
                                    ),
                            )
                            repository.setStarred(id, old.starred)
                        }
                    } else {
                        return@withLock null
                    }
                }
            }
        }

        suspend fun trashCurrent() {
            persistMutex.withLock {
                val id = loadedId ?: return@withLock
                repository.moveToTrash(id)
                dirty = false
                _trashed.value = true
                _archived.value = false
            }
        }

        /** Flip the list onto the archive shelf. Saves any in-flight edits first. */
        suspend fun archiveCurrent(untitledName: String) {
            saveIfNeeded(untitledName)
            persistMutex.withLock {
                val id = loadedId ?: return@withLock
                repository.archiveNote(id)
                _archived.value = true
                _trashed.value = false
                dirty = false
            }
        }

        suspend fun unarchiveCurrent() {
            persistMutex.withLock {
                val id = loadedId ?: return@withLock
                repository.unarchiveNote(id)
                _archived.value = false
                _trashed.value = false
                dirty = false
            }
        }

        suspend fun restoreFromTrashCurrent() {
            persistMutex.withLock {
                val id = loadedId ?: return@withLock
                repository.restoreFromTrash(id)
                _trashed.value = false
                _archived.value = false
                dirty = false
            }
        }

        suspend fun fireNotification(
            context: android.content.Context,
            untitledName: String,
        ) {
            saveIfNeeded(untitledName)
            val id = loadedId ?: return
            val noteWithItems = repository.get(id) ?: return
            dev.bikram.remember.reminders.ReminderReceiver.showNotification(
                context,
                noteWithItems.note,
                noteWithItems.items,
            )
            android.widget.Toast
                .makeText(
                    context,
                    context.getString(dev.bikram.remember.R.string.notification_created),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
        }

        suspend fun deleteForeverCurrent() {
            persistMutex.withLock {
                val id = loadedId ?: return@withLock
                repository.deleteForever(id)
                dirty = false
            }
        }
    }
