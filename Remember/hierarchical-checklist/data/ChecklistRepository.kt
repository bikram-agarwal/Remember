package com.example.checklist.data

import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [ChecklistDao] that owns the weighted-sortOrder rules.
 *
 * The core rule: newly inserted items get `max(sortOrder) + 1.0`, so they
 * always land at the end of the list without shifting any existing rows.
 * Reorders use midpoint arithmetic (see [reorderBetween]) so neighbors also
 * never need updating.
 */
class ChecklistRepository(private val dao: ChecklistDao) {

    fun observeAll(): Flow<List<ChecklistItem>> = dao.observeAll()

    suspend fun findById(id: Int): ChecklistItem? = dao.findById(id)

    suspend fun childrenOf(parentId: Int): List<ChecklistItem> = dao.childrenOf(parentId)

    suspend fun add(text: String, parentId: Int? = null): Int {
        val nextOrder = dao.maxSortOrder() + 1.0
        val depth = if (parentId == null) 0 else 1
        val newId = dao.insert(
            ChecklistItem(
                text = text,
                sortOrder = nextOrder,
                parentId = parentId,
                depth = depth,
            )
        )
        return newId.toInt()
    }

    suspend fun update(item: ChecklistItem) = dao.update(item)

    suspend fun delete(item: ChecklistItem) = dao.delete(item)

    /** Cascades isChecked to the row and all its direct children in one transaction. */
    suspend fun setCheckedForFamily(id: Int, checked: Boolean) =
        dao.checkFamily(id, checked)

    /**
     * Compute a weighted sortOrder between [before] and [after] neighbors and
     * persist it on [moved]. Pass null for either neighbor if [moved] lands at
     * the start or end of the list.
     *
     * Returns the assigned sortOrder so the caller can verify drift. If the
     * midpoint gap drops below a small epsilon, callers should schedule a
     * re-normalization pass (not done here to avoid write storms).
     */
    suspend fun reorderBetween(
        moved: ChecklistItem,
        before: ChecklistItem?,
        after: ChecklistItem?,
    ): Double {
        val newOrder = when {
            before != null && after != null -> (before.sortOrder + after.sortOrder) / 2.0
            before != null -> before.sortOrder + 1.0
            after != null -> after.sortOrder - 1.0
            else -> moved.sortOrder
        }
        dao.update(moved.copy(sortOrder = newOrder))
        return newOrder
    }
}
