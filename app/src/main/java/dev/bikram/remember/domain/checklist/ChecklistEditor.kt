package dev.bikram.remember.domain.checklist

/**
 * Editable checklist row during list editing.
 *
 * [localId] is stable for the editor session. Persisted rows reuse their Room id, and drafts use
 * negative ids until the repository remaps them during save.
 */
data class EditableItem(
    val localId: Long,
    val text: String,
    val checked: Boolean,
    val sortOrder: Double,
    val parentLocalId: Long? = null,
    val depth: Int = 0,
)

data class ChecklistEditResult(
    val items: List<EditableItem>,
    val changed: Boolean,
)

object ChecklistEditor {

    /**
     * Parent-context toggle:
     *
     * * Checking a parent cascades to every child.
     * * Checking the last unchecked child also checks the parent.
     * * Unchecking a child of a checked parent brings the parent back to active with it.
     */
    fun toggleChecked(items: List<EditableItem>, localId: Long): ChecklistEditResult {
        val target = items.firstOrNull { item -> item.localId == localId }
            ?: return ChecklistEditResult(items = items, changed = false)
        val newChecked = !target.checked
        val affectedIds: Set<Long> = if (target.depth == 0) {
            buildSet {
                add(target.localId)
                items.forEach { item ->
                    if (item.parentLocalId == target.localId) {
                        add(item.localId)
                    }
                }
            }
        } else {
            buildSet {
                add(target.localId)
                val parentId = target.parentLocalId
                val parent = parentId?.let { requestedParentId ->
                    items.firstOrNull { item -> item.localId == requestedParentId }
                }
                if (parent != null) {
                    if (newChecked) {
                        val siblings = items.filter { item -> item.parentLocalId == parentId }
                        val allWillBeChecked = siblings.all { sibling ->
                            sibling.localId == target.localId || sibling.checked
                        }
                        if (allWillBeChecked && !parent.checked) {
                            add(parent.localId)
                        }
                    } else if (parent.checked) {
                        add(parent.localId)
                    }
                }
            }
        }
        return ChecklistEditResult(
            items = items.map { item ->
                if (item.localId in affectedIds) {
                    item.copy(checked = newChecked)
                } else {
                    item
                }
            },
            changed = true,
        )
    }

    /**
     * Reorders within a filtered active or completed list by assigning a midpoint sort order.
     */
    fun reorderWithin(
        items: List<EditableItem>,
        visibleIds: List<Long>,
        fromIndex: Int,
        toIndex: Int,
    ): ChecklistEditResult {
        if (fromIndex == toIndex) {
            return ChecklistEditResult(items = items, changed = false)
        }
        if (fromIndex !in visibleIds.indices || toIndex !in visibleIds.indices) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val movingId = visibleIds[fromIndex]
        val movingItem = items.firstOrNull { item -> item.localId == movingId }
            ?: return ChecklistEditResult(items = items, changed = false)

        val rearrangedIds = visibleIds.toMutableList().apply {
            removeAt(fromIndex)
            add(toIndex, movingId)
        }
        val previousId = rearrangedIds.getOrNull(toIndex - 1)
        val nextId = rearrangedIds.getOrNull(toIndex + 1)
        val previousOrder = previousId?.let { id ->
            items.first { item -> item.localId == id }.sortOrder
        }
        val nextOrder = nextId?.let { id ->
            items.first { item -> item.localId == id }.sortOrder
        }

        val newSortOrder = when {
            previousOrder != null && nextOrder != null -> (previousOrder + nextOrder) / 2.0
            previousOrder != null -> previousOrder + 1.0
            nextOrder != null -> nextOrder - 1.0
            else -> movingItem.sortOrder
        }
        return ChecklistEditResult(
            items = items.map { item ->
                if (item.localId == movingId) {
                    item.copy(sortOrder = newSortOrder)
                } else {
                    item
                }
            },
            changed = true,
        )
    }

    /**
     * Sets a row's parent while enforcing the one-level nesting cap.
     */
    fun setParent(
        items: List<EditableItem>,
        localId: Long,
        newParentLocalId: Long?,
    ): ChecklistEditResult {
        val target = items.firstOrNull { item -> item.localId == localId }
            ?: return ChecklistEditResult(items = items, changed = false)
        val resolvedParent = newParentLocalId?.let { requestedParentId ->
            val candidate = items.firstOrNull { item -> item.localId == requestedParentId }
                ?: return@let null
            if (candidate.depth == 1) {
                candidate.parentLocalId
            } else {
                candidate.localId
            }
        }
        if (target.parentLocalId == resolvedParent) {
            return ChecklistEditResult(items = items, changed = false)
        }
        return ChecklistEditResult(
            items = items.map { item ->
                if (item.localId == localId) {
                    item.copy(
                        parentLocalId = resolvedParent,
                        depth = if (resolvedParent != null) 1 else 0,
                    )
                } else {
                    item
                }
            },
            changed = true,
        )
    }

    /**
     * Indents under the nearest prior top-level sibling in the current unchecked list.
     */
    fun indent(items: List<EditableItem>, localId: Long): ChecklistEditResult {
        val target = items.firstOrNull { item -> item.localId == localId }
            ?: return ChecklistEditResult(items = items, changed = false)
        if (target.depth != 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val orderedActive = items
            .filter { item -> !item.checked }
            .sortedBy { item -> item.sortOrder }
        val targetIndex = orderedActive.indexOfFirst { item -> item.localId == localId }
        if (targetIndex <= 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val anchor = (targetIndex - 1 downTo 0)
            .map { index -> orderedActive[index] }
            .firstOrNull { item -> item.depth == 0 }
            ?: return ChecklistEditResult(items = items, changed = false)
        return setParent(items, localId, anchor.localId)
    }

    fun outdent(items: List<EditableItem>, localId: Long): ChecklistEditResult {
        val target = items.firstOrNull { item -> item.localId == localId }
            ?: return ChecklistEditResult(items = items, changed = false)
        if (target.depth != 1) {
            return ChecklistEditResult(items = items, changed = false)
        }
        return setParent(items, localId, null)
    }
}
