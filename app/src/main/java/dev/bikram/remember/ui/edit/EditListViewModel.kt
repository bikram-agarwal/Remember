package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.Visibility as NoteVisibility
import dev.bikram.remember.ui.common.HeroFraming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * View-model representation of one checklist row during editing. Carries:
 *  * [localId]: stable id for the lifetime of the editor. Persisted rows re-use their Room id;
 *    drafts use monotonically decreasing negative values.
 *  * [sortOrder]: weighted position. Two sublists (active / completed) are each sorted ascending
 *    on this field; insertions pick midpoints between neighbours so reorders never rewrite
 *    every row.
 *  * [parentLocalId] / [depth]: one-level nesting. `null` parent means the row is a top-level
 *    parent (depth 0); a non-null parent marks the row as a child (depth 1). No deeper nesting
 *    is supported.
 */
data class EditableItem(
    val localId: Long,
    val text: String,
    val checked: Boolean,
    val sortOrder: Double,
    val parentLocalId: Long? = null,
    val depth: Int = 0,
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

    private val _visibility = MutableStateFlow(NoteVisibility.PRIVATE)
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

    private var loadedId: Long? = noteId
    private var dirty: Boolean = false
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
                _pinned.value = n.pinned || n.tags.contains(RememberReservedTags.FAVORITE)
                _reminderAt.value = n.reminderAt
                _recurrence.value = n.recurrence?.sanitized()
                _importance.value = n.importance
                _visibility.value = n.visibility
                _locked.value = n.locked
                _pictureUri.value = n.pictureUri
                _pictureHeroFraming.value = n.pictureHeroFraming
                _iconKey.value = n.iconKey
                _actions.value = n.actions
                _tags.value = n.tags.filterNot { it == RememberReservedTags.FAVORITE }
                _attachments.value = existing.attachments
                _archived.value = n.archived
                _trashed.value = n.trashed
                _completed.value = n.completedAt != null
                _items.value = existing.items.map {
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

    fun setTitle(v: String)                  { _title.value = v; dirty = true }
    fun togglePin()                          { _pinned.value = !_pinned.value; dirty = true }
    fun setReminder(at: Long?, rule: RecurrenceRule?) {
        _reminderAt.value = at
        _recurrence.value = rule?.sanitized()
        dirty = true
    }
    fun setImportance(v: Importance)         { _importance.value = v; dirty = true }
    fun setVisibility(v: NoteVisibility)     { _visibility.value = v; dirty = true }
    fun toggleLock()                         { _locked.value = !_locked.value; dirty = true }
    fun setPictureUri(v: String?) {
        _pictureUri.value = v
        if (v == null) {
            _pictureHeroFraming.value = null
        }
        _pictureRevision.value = _pictureRevision.value + 1L
        dirty = true
    }

    fun setHeroWithFraming(pictureUri: String, framing: HeroFraming) {
        _pictureUri.value = pictureUri
        _pictureHeroFraming.value = framing.toJsonString()
        _pictureRevision.value = _pictureRevision.value + 1L
        dirty = true
    }

    fun setIconKey(v: String?)               { _iconKey.value = v; dirty = true }
    fun setActions(v: List<NoteAction>)      { _actions.value = v; dirty = true }
    fun setTags(v: List<String>)             { _tags.value = v.filterNot { it == RememberReservedTags.FAVORITE }; dirty = true }

    fun saveTagsWithColors(tags: List<String>, newColors: Map<String, String>) {
        _tags.value = tags.filterNot { it == RememberReservedTags.FAVORITE }
        dirty = true
        if (newColors.isNotEmpty()) {
            viewModelScope.launch {
                newColors.forEach { (name, hex) -> themePrefs.setTagColor(name, hex) }
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
        _items.value = _items.value + EditableItem(
            localId = nextLocalId--,
            text = "",
            checked = false,
            sortOrder = max + 1.0,
            parentLocalId = null,
            depth = 0,
        )
        dirty = true
    }

    fun updateItemText(localId: Long, text: String) {
        _items.value = _items.value.map {
            if (it.localId == localId) it.copy(text = text) else it
        }
        dirty = true
    }

    /**
     * Parent-context toggle:
     *
     *  * Checking a PARENT cascades: the parent and all its children flip to checked. Their
     *    relative [sortOrder] is preserved so the whole block keeps its internal arrangement
     *    when it lands in the completed section.
     *  * Checking a CHILD only flips that child. The parent stays where it is. A "ghost parent"
     *    header is synthesised by the UI when rendering the completed section.
     *  * Unchecking mirrors the above. Children keep their original [sortOrder] so they slot
     *    back into the same relative place in active.
     */
    fun toggleChecked(localId: Long) {
        val list = _items.value
        val target = list.firstOrNull { it.localId == localId } ?: return
        val newChecked = !target.checked
        val affectedIds: Set<Long> = if (target.depth == 0) {
            // Parent toggle: cascade DOWN to every child that points at this parent.
            buildSet {
                add(target.localId)
                list.forEach { if (it.parentLocalId == target.localId) add(it.localId) }
            }
        } else {
            // Child toggle: flip just this child, then cascade UP to the parent if the parent's
            // state should follow.
            //
            //   * Checking the LAST remaining unchecked child: the parent has nothing left
            //     unchecked, so the whole branch is now complete and the parent auto-checks.
            //   * Unchecking any child of a parent that was itself checked: the parent was only
            //     checked because its cascade-down flipped it earlier (or because of a previous
            //     "all children checked" cascade-up); either way, the branch is no longer fully
            //     complete, so the parent comes back to unchecked alongside the child. This is
            //     what lets the user uncheck a single child and see both parent and child return
            //     to the active section together instead of leaving an orphan ghost behind.
            buildSet {
                add(target.localId)
                val parentId = target.parentLocalId
                val parent = parentId?.let { pid -> list.firstOrNull { it.localId == pid } }
                if (parent != null) {
                    if (newChecked) {
                        val siblings = list.filter { it.parentLocalId == parentId }
                        val allWillBeChecked = siblings.all { sib ->
                            sib.localId == target.localId || sib.checked
                        }
                        if (allWillBeChecked && !parent.checked) add(parent.localId)
                    } else {
                        if (parent.checked) add(parent.localId)
                    }
                }
            }
        }
        _items.value = list.map {
            if (it.localId in affectedIds) it.copy(checked = newChecked) else it
        }
        dirty = true
    }

    /**
     * Drag-handle reorder within a single sublist (active or completed). [fromIndex]/[toIndex]
     * are positions in the *filtered* list whose [EditableItem]s we receive as [visibleIds]. We
     * compute a midpoint [sortOrder] between the new neighbours so the other rows never move.
     *
     * Children being dragged stay as children -- their [parentLocalId] is unchanged. To re-parent
     * use [setParent] (wired up to the horizontal drag gesture).
     */
    fun reorderWithin(visibleIds: List<Long>, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in visibleIds.indices || toIndex !in visibleIds.indices) return
        val movingId = visibleIds[fromIndex]
        val list = _items.value
        val movingItem = list.firstOrNull { it.localId == movingId } ?: return

        // Work out the neighbours that will surround the moving row at its target position in the
        // filtered list. Prev/next are taken from the *target arrangement* after removal/insert.
        val rearranged = visibleIds.toMutableList().apply {
            removeAt(fromIndex)
            add(toIndex, movingId)
        }
        val prevId = rearranged.getOrNull(toIndex - 1)
        val nextId = rearranged.getOrNull(toIndex + 1)
        val prevOrder = prevId?.let { id -> list.first { it.localId == id }.sortOrder }
        val nextOrder = nextId?.let { id -> list.first { it.localId == id }.sortOrder }

        val newSort = when {
            prevOrder != null && nextOrder != null -> (prevOrder + nextOrder) / 2.0
            prevOrder != null -> prevOrder + 1.0
            nextOrder != null -> nextOrder - 1.0
            else -> movingItem.sortOrder
        }
        _items.value = list.map {
            if (it.localId == movingId) it.copy(sortOrder = newSort) else it
        }
        dirty = true
    }

    /**
     * Horizontal drag re-parents a row. `newParentLocalId = null` promotes a child back to
     * top-level (depth 0); a non-null value nests the row under that parent (depth 1). Nesting
     * is capped at one level: if the requested parent is itself a child, we climb to its own
     * parent instead. Children of the targeted row lose their children (can't happen here since
     * depth is 1, but defensive).
     */
    fun setParent(localId: Long, newParentLocalId: Long?) {
        val list = _items.value
        val target = list.firstOrNull { it.localId == localId } ?: return
        // Resolve one-level cap: if the proposed parent is itself a child, fall back to its parent.
        val resolvedParent = newParentLocalId?.let { requested ->
            val candidate = list.firstOrNull { it.localId == requested } ?: return@let null
            if (candidate.depth == 1) candidate.parentLocalId else candidate.localId
        }
        if (target.parentLocalId == resolvedParent) return
        _items.value = list.map {
            if (it.localId == localId) {
                it.copy(
                    parentLocalId = resolvedParent,
                    depth = if (resolvedParent != null) 1 else 0,
                )
            } else it
        }
        dirty = true
    }

    /**
     * Indent [localId] under the nearest prior top-level sibling in the CURRENT unchecked list.
     *
     * The anchor lookup has to run on the freshest copy of [_items] because the UI's horizontal
     * drag gesture lives inside a `pointerInput(item.localId, item.depth)` block whose captured
     * closure does NOT re-launch when a sibling is reordered. If we did the lookup in the UI,
     * we'd see a stale `activeList` snapshot from the time the row was first composed, and the
     * "item just above" could point to the wrong row. Keeping the lookup here means every
     * invocation sees the current sortOrder and current depths. No-op if the item is already a
     * child or there is no unchecked top-level row preceding it.
     */
    fun indent(localId: Long) {
        val list = _items.value
        val target = list.firstOrNull { it.localId == localId } ?: return
        if (target.depth != 0) return
        val orderedActive = list
            .filter { !it.checked }
            .sortedBy { it.sortOrder }
        val idx = orderedActive.indexOfFirst { it.localId == localId }
        if (idx <= 0) return
        val anchor = (idx - 1 downTo 0)
            .map { orderedActive[it] }
            .firstOrNull { it.depth == 0 }
            ?: return
        setParent(localId, anchor.localId)
    }

    /** Promote [localId] back to depth 0. Mirror of [indent]. No-op if already at depth 0. */
    fun outdent(localId: Long) {
        val target = _items.value.firstOrNull { it.localId == localId } ?: return
        if (target.depth != 1) return
        setParent(localId, null)
    }

    fun removeItem(localId: Long) {
        // Removing a parent also orphans its children (promote to top-level rather than delete).
        val list = _items.value
        val target = list.firstOrNull { it.localId == localId } ?: return
        _items.value = list
            .filterNot { it.localId == localId }
            .map {
                if (target.depth == 0 && it.parentLocalId == localId) {
                    it.copy(parentLocalId = null, depth = 0)
                } else it
            }
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
                syncHasPersistedRow()
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

    private fun tagsForPersistence(): List<String> {
        val base = _tags.value.filterNot { it == RememberReservedTags.FAVORITE }
        return if (_pinned.value) (base + RememberReservedTags.FAVORITE).distinct()
        else base
    }

    private fun currentOptions() = NoteOptions(
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
    private fun currentPersistable(): List<dev.bikram.remember.data.PersistableChecklistItem> {
        return _items.value
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
    }

    private fun hasNetChanges(): Boolean {
        val id = loadedId
        val t = _title.value
        val nonEmpty = _items.value.filter { it.text.isNotBlank() }
        val opts = currentOptions()
        val pinned = _pinned.value
        if (id == null) {
            return t.isNotBlank() || nonEmpty.isNotEmpty() || _attachments.value.isNotEmpty() || opts.pictureUri != null || opts.tags.isNotEmpty() || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null
        } else {
            val old = originalNote ?: return true
            val oldItems = originalItems
            if (t != old.title) return true
            if (pinned != old.pinned) return true
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
                if (_pinned.value) repository.setPinned(newId, true)
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
                if (cur != null && cur.pinned != _pinned.value) {
                    repository.setPinned(id, _pinned.value)
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
                            items = oldItems.map {
                                dev.bikram.remember.data.PersistableChecklistItem(
                                    localKey = it.id,
                                    text = it.text,
                                    checked = it.checked,
                                    sortOrder = it.sortOrder,
                                    parentLocalKey = it.parentId,
                                    depth = it.depth,
                                )
                            },
                            options = NoteOptions(
                                reminderAt = old.reminderAt,
                                importance = old.importance,
                                visibility = old.visibility,
                                pictureUri = old.pictureUri,
                                pictureHeroFraming = old.pictureHeroFraming,
                                locked = old.locked,
                                iconKey = old.iconKey,
                                actions = old.actions,
                                tags = old.tags,
                                recurrence = old.recurrence
                            )
                        )
                        repository.setPinned(id, old.pinned)
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
            dirty = false
        }
    }

    suspend fun unarchiveCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.unarchiveNote(id)
            _archived.value = false
            dirty = false
        }
    }

    suspend fun restoreFromTrashCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.restoreFromTrash(id)
            _trashed.value = false
            dirty = false
        }
    }

    suspend fun fireNotification(context: android.content.Context, untitledName: String) {
        saveIfNeeded(untitledName)
        val id = loadedId ?: return
        val note = repository.get(id)?.note ?: return
        dev.bikram.remember.reminders.ReminderReceiver.showNotification(context, note)
        android.widget.Toast.makeText(context, "Notification created", android.widget.Toast.LENGTH_SHORT).show()
    }

    suspend fun deleteForeverCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.deleteForever(id)
            dirty = false
        }
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
