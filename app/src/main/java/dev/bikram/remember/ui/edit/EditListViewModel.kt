package dev.bikram.remember.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.domain.checklist.ChecklistEditResult
import dev.bikram.remember.domain.checklist.ChecklistEditor
import dev.bikram.remember.domain.checklist.EditableItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the checklist hierarchy for the Edit List screen on top of every persisted field shared
 * with notes in [BaseEditorViewModel]. Only the item list, its editing/reorder operations, and the
 * create/update/diff calls that depend on it live here; everything else is inherited.
 */
@HiltViewModel
class EditListViewModel
    @Inject
    constructor(
        repository: NoteRepository,
        appMediaStorage: AppMediaStorage? = null,
        savedStateHandle: SavedStateHandle,
    ) : BaseEditorViewModel(repository, appMediaStorage, savedStateHandle) {
        private val _items = MutableStateFlow<List<EditableItem>>(emptyList())
        val items: StateFlow<List<EditableItem>> = _items.asStateFlow()

        private var nextLocalId: Long = -1L
        private var originalItems: List<ChecklistItemEntity> = emptyList()

        init {
            if (noteId != null) {
                check(persistence.tryLock()) { "persistence lock must be unlocked at construction" }
                viewModelScope.launch {
                    try {
                        val existing = repository.get(noteId)
                        if (existing != null) {
                            applyLoadedCommon(existing)
                            originalItems = existing.items
                            _items.value =
                                existing.items
                                    .map {
                                        EditableItem(
                                            localId = it.id,
                                            text = it.text,
                                            details = it.details,
                                            checked = it.checked,
                                            sortOrder = it.sortOrder,
                                            parentLocalId = it.parentId,
                                            depth = it.depth,
                                        )
                                    }.sortedBy { it.sortOrder }
                        }
                    } finally {
                        finishInitialLoad()
                        persistence.unlock()
                    }
                }
                startExternalFieldMirror(noteId)
            }
        }

        /**
         * Appends a new top-level row at the end of the active section.
         * [max(sortOrder) + 1.0] per the weighted-position spec. Uses fresh negative [localId] so
         * drafts have stable identity until the row is persisted.
         */
        fun addItem(): Long {
            val localId = nextLocalId--
            val max = _items.value.maxOfOrNull { it.sortOrder } ?: 0.0
            _items.value = _items.value +
                EditableItem(
                    localId = localId,
                    text = "",
                    details = "",
                    checked = false,
                    sortOrder = max + 1.0,
                    parentLocalId = null,
                    depth = 0,
                )
            markDirty()
            return localId
        }

        fun addItemAfter(localId: Long): Long {
            val newLocalId = nextLocalId
            val result = ChecklistEditor.insertAfter(_items.value, localId, newLocalId)
            if (result.changed) {
                nextLocalId--
                applyChecklistEdit(result)
                return newLocalId
            }
            return addItem()
        }

        fun updateItemText(
            localId: Long,
            text: String,
        ) {
            _items.value =
                _items.value.map {
                    if (it.localId == localId) it.copy(text = text) else it
                }
            markDirty()
        }

        fun updateItemDetails(
            localId: Long,
            details: String,
        ) {
            _items.value =
                _items.value.map {
                    if (it.localId == localId) it.copy(details = details) else it
                }
            markDirty()
        }

        fun toggleChecked(localId: Long) {
            applyChecklistEdit(
                ChecklistEditor.toggleChecked(_items.value, localId),
            )
        }

        fun checkAll() {
            applyChecklistEdit(
                ChecklistEditor.checkAll(_items.value),
            )
        }

        fun uncheckAll() {
            applyChecklistEdit(
                ChecklistEditor.uncheckAll(_items.value),
            )
        }

        private var draggingItemId: Long? = null
        private var originalSortOrder: Double? = null

        fun startDragging(localId: Long) {
            draggingItemId = localId
            originalSortOrder = _items.value.firstOrNull { item -> item.localId == localId }?.sortOrder
        }

        fun stopDragging(localId: Long) {
            if (draggingItemId == localId) {
                draggingItemId = null
                finalizeReorder(localId)
                originalSortOrder = null
            }
        }

        fun reorderWithin(
            visibleIds: List<Long>,
            fromIndex: Int,
            toIndex: Int,
        ) {
            applyChecklistEdit(
                ChecklistEditor.reorderWithin(_items.value, visibleIds, fromIndex, toIndex, isDragging = true),
            )
        }

        private fun finalizeReorder(localId: Long) {
            val currentItems = _items.value
            val targetItem = currentItems.firstOrNull { item -> item.localId == localId } ?: return

            if (targetItem.depth == 0) {
                val sortedItems = currentItems.sortedBy { item -> item.sortOrder }
                val targetIndex = sortedItems.indexOfFirst { item -> item.localId == localId }
                if (targetIndex < 0) {
                    return
                }
                finalizeParentReorder(
                    localId = localId,
                    currentItems = currentItems,
                    sortedItems = sortedItems,
                    targetIndex = targetIndex,
                )
            } else {
                finalizeChildReorder(localId, currentItems)
            }
        }

        private fun finalizeParentReorder(
            localId: Long,
            currentItems: List<EditableItem>,
            sortedItems: List<EditableItem>,
            targetIndex: Int,
        ) {
            val precedingParent =
                sortedItems
                    .take(targetIndex)
                    .asReversed()
                    .firstOrNull { item -> item.depth == 0 }
                    ?: return
            val parentId = precedingParent.localId
            val hasChildrenAfter =
                sortedItems
                    .drop(targetIndex + 1)
                    .any { item -> item.parentLocalId == parentId }
            if (!hasChildrenAfter) return

            val originalOrder = originalSortOrder ?: return
            val parentIdx = sortedItems.indexOfFirst { item -> item.localId == parentId }
            if (parentIdx < 0) return

            val childrenIndices =
                sortedItems.mapIndexedNotNull { index, item ->
                    if (item.parentLocalId == parentId) index else null
                }
            val resolvedToIndex =
                if (originalOrder > precedingParent.sortOrder) {
                    parentIdx
                } else {
                    childrenIndices.lastOrNull() ?: parentIdx
                }
            val fromIdx = sortedItems.indexOfFirst { item -> item.localId == localId }
            if (fromIdx < 0) return

            applyChecklistEdit(
                ChecklistEditor.reorderWithin(
                    items = currentItems,
                    visibleIds = sortedItems.map { item -> item.localId },
                    fromIndex = fromIdx,
                    toIndex = resolvedToIndex,
                    isDragging = false,
                ),
            )
        }

        private fun finalizeChildReorder(
            localId: Long,
            currentItems: List<EditableItem>,
        ) {
            val sortedItems = currentItems.sortedBy { item -> item.sortOrder }
            val targetIndex = sortedItems.indexOfFirst { item -> item.localId == localId }
            if (targetIndex < 0) {
                return
            }
            var newParentLocalId: Long? = null
            var newDepth = 0

            // Search backwards from the item's new position to find the nearest parent.
            for (idx in targetIndex - 1 downTo 0) {
                val prevItem = sortedItems[idx]
                if (prevItem.depth == 0) {
                    newParentLocalId = prevItem.localId
                    newDepth = 1
                    break
                } else if (prevItem.depth == 1 && prevItem.parentLocalId != null) {
                    newParentLocalId = prevItem.parentLocalId
                    newDepth = 1
                    break
                }
            }

            _items.value =
                currentItems.map { item ->
                    if (item.localId == localId) {
                        item.copy(parentLocalId = newParentLocalId, depth = newDepth)
                    } else {
                        item
                    }
                }
            markDirty()
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
            _items.value = result.items.sortedBy { item -> item.sortOrder }
            markDirty()
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
            markDirty()
        }

        private fun currentItems(): List<ChecklistItemEntity> {
            val id = loadedId ?: 0L
            val nonEmpty = _items.value.filter { it.text.isNotBlank() }
            return nonEmpty.map { item ->
                ChecklistItemEntity(
                    id = 0,
                    noteId = id,
                    text = item.text,
                    details = item.details,
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
                        details = item.details,
                        checked = item.checked,
                        sortOrder = item.sortOrder,
                        parentLocalKey = item.parentLocalId,
                        depth = item.depth.coerceIn(0, 1),
                    )
                }

        override suspend fun persistNewDraftForAttachment(): Long {
            val newId =
                repository.createListWithItems(
                    title = title.value,
                    colorIndex = 0,
                    items = currentPersistable(),
                    options = currentOptions(),
                )
            loadedId = newId
            syncHasPersistedRow()
            if (starred.value) repository.setStarred(newId, true)
            return newId
        }

        private fun hasNetChanges(): Boolean {
            val id = loadedId
            val t = title.value
            val nonEmpty = _items.value.filter { it.text.isNotBlank() }
            val opts = currentOptions()
            val starred = starred.value
            if (id == null) {
                return t.isNotBlank() || nonEmpty.isNotEmpty() || attachments.value.isNotEmpty() || opts.pictureUri != null || opts.tags.isNotEmpty() || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null
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
                    if (c.details != o.details) return true
                    if (c.checked != o.checked) return true
                    if (c.sortOrder != o.sortOrder) return true
                    if (c.depth != o.depth) return true
                    // Parent pointer equality: both sides refer to rows by Room id once saved, and by
                    // the stable editor localId while editing. Only flag a change when both sides have
                    // a parent and the ids disagree (or one side has a parent and the other does not).
                    val cParent = c.parentLocalId
                    val oParent = o.parentId
                    if (cParent != oParent) return true
                }
                return false
            }
        }

        override suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)? {
            return persistence.withLock {
                if (!hasNetChanges()) {
                    persistence.clearDirty()
                    return@withLock null
                }
                val t = title.value
                val id = loadedId
                val finalTitle = t.ifBlank { untitledName }
                val persistable = currentPersistable()
                if (id == null) {
                    if (!persistence.isDirty) return@withLock null
                    val epochAtWrite = persistence.currentEpoch()
                    // Creation goes through createListWithItems with the real persistable payload so
                    // pre-composed hierarchy/checked state in the draft is preserved on first save.
                    val newId = repository.createListWithItems(finalTitle, 0, persistable, currentOptions())
                    loadedId = newId
                    syncHasPersistedRow()
                    if (t.isBlank()) setTitle(finalTitle)
                    if (starred.value) repository.setStarred(newId, true)
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val savedList = repository.get(newId)
                    originalNote = savedList?.note
                    originalItems = savedList?.items ?: emptyList()
                    savedList?.note?.let { note ->
                        updateTimestamps(note.createdAt, note.updatedAt)
                    }

                    return@withLock {
                        repository.moveToTrash(newId)
                    }
                } else {
                    if (!persistence.isDirty) return@withLock null
                    val epochAtWrite = persistence.currentEpoch()
                    repository.updateList(id, finalTitle, 0, persistable, currentOptions())
                    if (t.isBlank()) setTitle(finalTitle)
                    val cur = repository.get(id)?.note
                    if (cur != null && cur.starred != starred.value) {
                        repository.setStarred(id, starred.value)
                    }
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val old = originalNote
                    val oldItems = originalItems

                    val savedList = repository.get(id)
                    originalNote = savedList?.note
                    originalItems = savedList?.items ?: emptyList()
                    savedList?.note?.let { note ->
                        updateTimestamps(note.createdAt, note.updatedAt)
                    }

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
                                            details = it.details,
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
    }
