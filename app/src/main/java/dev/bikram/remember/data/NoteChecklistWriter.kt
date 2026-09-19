package dev.bikram.remember.data

/**
 * Persists the checklist rows of a list-kind note, including the two-pass remap that turns a
 * caller's [PersistableChecklistItem.localKey] pointers into the real Room ids Room hands back
 * on insert. Split out of [NoteRepository] so the hierarchy bookkeeping lives beside itself
 * instead of in the middle of note-level code, and so first-save and re-save share it.
 */
internal class NoteChecklistWriter(
    private val itemDao: ChecklistItemDao,
) {
    /**
     * Writes a flat list of rows that already carry weighted [PersistableChecklistItem.sortOrder]
     * and [PersistableChecklistItem.parentLocalKey] relations. Works in two passes:
     *
     *  1. Insert every row with `parentId = null` so the table is always in a valid state, even
     *     if the caller ordered children before their parents.
     *  2. Re-update children with the freshly minted parent id resolved via [PersistableChecklistItem.localKey].
     *
     * Dangling pointers (children whose parent is missing from [items]) are left as top-level rows.
     */
    suspend fun insertHierarchy(
        noteId: Long,
        items: List<PersistableChecklistItem>,
    ) {
        if (items.isEmpty()) return
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 1. Insert parents (depth 0)
        parents.forEach { draft ->
            val newId =
                itemDao.insert(
                    ChecklistItemEntity(
                        id = 0,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = null,
                        depth = 0,
                    ),
                )
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }

        // 2. Insert children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0
            val newId =
                itemDao.insert(
                    ChecklistItemEntity(
                        id = 0,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    ),
                )
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }
    }

    /**
     * Re-saves the rows of an existing list: updates rows the draft still references, inserts the
     * new ones, and deletes whatever the user removed while editing.
     */
    suspend fun syncHierarchy(
        noteId: Long,
        items: List<PersistableChecklistItem>,
    ) {
        val existingItems = itemDao.itemsFor(noteId)
        val existingById = existingItems.associateBy { it.id }

        // 1. Partition incoming items into parents and children
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 2. Process parents (depth 0)
        parents.forEach { draft ->
            val existing = existingById[draft.localKey]
            if (existing != null) {
                itemDao.update(
                    ChecklistItemEntity(
                        id = existing.id,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = null,
                        depth = 0,
                    ),
                )
                keyToRealId[draft.localKey] = existing.id
            } else {
                val newId =
                    itemDao.insert(
                        ChecklistItemEntity(
                            id = 0,
                            noteId = noteId,
                            text = draft.text,
                            details = draft.details,
                            checked = draft.checked,
                            sortOrder = draft.sortOrder,
                            parentId = null,
                            depth = 0,
                        ),
                    )
                keyToRealId[draft.localKey] = newId
            }
        }

        // 3. Process children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0

            val existing = existingById[draft.localKey]
            if (existing != null) {
                itemDao.update(
                    ChecklistItemEntity(
                        id = existing.id,
                        noteId = noteId,
                        text = draft.text,
                        details = draft.details,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    ),
                )
                keyToRealId[draft.localKey] = existing.id
            } else {
                val newId =
                    itemDao.insert(
                        ChecklistItemEntity(
                            id = 0,
                            noteId = noteId,
                            text = draft.text,
                            details = draft.details,
                            checked = draft.checked,
                            sortOrder = draft.sortOrder,
                            parentId = realParentId,
                            depth = resolvedDepth,
                        ),
                    )
                keyToRealId[draft.localKey] = newId
            }
        }

        // 4. Delete items that were in the database but are no longer in our saved set
        val savedRealIds = keyToRealId.values.toSet()
        existingItems.forEach { existing ->
            if (existing.id !in savedRealIds) {
                itemDao.deleteById(existing.id)
            }
        }
    }
}
