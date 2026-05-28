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
    fun insertAfter(
        items: List<EditableItem>,
        targetLocalId: Long,
        newLocalId: Long,
    ): ChecklistEditResult {
        val sortedItems = items.sortedBy { item -> item.sortOrder }
        val targetIndex = sortedItems.indexOfFirst { item -> item.localId == targetLocalId }
        if (targetIndex < 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val target = sortedItems[targetIndex]
        val anchor =
            if (target.depth == 0) {
                sortedItems
                    .filter { item -> item.localId == target.localId || item.parentLocalId == target.localId }
                    .maxBy { item -> item.sortOrder }
            } else {
                target
            }
        val anchorIndex = sortedItems.indexOfFirst { item -> item.localId == anchor.localId }
        val nextOrder = sortedItems.getOrNull(anchorIndex + 1)?.sortOrder
        val newSortOrder =
            if (nextOrder != null) {
                (anchor.sortOrder + nextOrder) / 2.0
            } else {
                anchor.sortOrder + 1.0
            }
        val newParentLocalId = target.parentLocalId.takeIf { target.depth == 1 }
        val newDepth = if (newParentLocalId != null) 1 else 0
        return ChecklistEditResult(
            items =
                items +
                    EditableItem(
                        localId = newLocalId,
                        text = "",
                        checked = false,
                        sortOrder = newSortOrder,
                        parentLocalId = newParentLocalId,
                        depth = newDepth,
                    ),
            changed = true,
        )
    }

    /**
     * Parent-context toggle:
     *
     * * Checking a parent cascades to every child.
     * * Checking the last unchecked child also checks the parent.
     * * Unchecking a child of a checked parent brings the parent back to active with it.
     */
    fun toggleChecked(
        items: List<EditableItem>,
        localId: Long,
    ): ChecklistEditResult {
        val target =
            items.firstOrNull { item -> item.localId == localId }
                ?: return ChecklistEditResult(items = items, changed = false)
        val newChecked = !target.checked
        val affectedIds: Set<Long> =
            if (target.depth == 0) {
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
                    val parent =
                        parentId?.let { requestedParentId ->
                            items.firstOrNull { item -> item.localId == requestedParentId }
                        }
                    if (parent != null) {
                        if (newChecked) {
                            val siblings = items.filter { item -> item.parentLocalId == parentId }
                            val allWillBeChecked =
                                siblings.all { sibling ->
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
            items =
                items.map { item ->
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
        isDragging: Boolean = false,
    ): ChecklistEditResult {
        if (fromIndex == toIndex) {
            return ChecklistEditResult(items = items, changed = false)
        }
        if (fromIndex !in visibleIds.indices || toIndex !in visibleIds.indices) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val movingId = visibleIds[fromIndex]
        val movingItem =
            items.firstOrNull { item -> item.localId == movingId }
                ?: return ChecklistEditResult(items = items, changed = false)
        val itemsByLocalId = items.associateBy { item -> item.localId }

        val isParent = movingItem.depth == 0

        if (isParent) {
            val movingGroupIds =
                items
                    .sortedBy { item -> item.sortOrder }
                    .filter { item -> item.localId == movingId || item.parentLocalId == movingId }
                    .map { item -> item.localId }

            // Find target item
            val targetId = visibleIds[toIndex]
            val targetItem = itemsByLocalId[targetId] ?: return ChecklistEditResult(items = items, changed = false)

            var resolvedToIndex = toIndex
            // If target item belongs to a parent group with children, coerce toIndex to avoid splitting the group
            if (!isDragging) {
                val targetParentId = if (targetItem.depth == 0) targetItem.localId else targetItem.parentLocalId
                if (targetParentId != null) {
                    val visibleItems = visibleIds.mapNotNull { id -> itemsByLocalId[id] }
                    val parentIdx = visibleItems.indexOfFirst { it.localId == targetParentId }
                    if (parentIdx >= 0) {
                        val childrenIndices =
                            visibleItems.mapIndexedNotNull { idx, item ->
                                if (item.parentLocalId == targetParentId) idx else null
                            }
                        if (childrenIndices.isNotEmpty()) {
                            resolvedToIndex =
                                if (toIndex < fromIndex) {
                                    parentIdx
                                } else {
                                    childrenIndices.last()
                                }
                        }
                    }
                }
            }

            return reorderVisibleGroup(
                items = items,
                visibleIds = visibleIds,
                movingGroupIds = movingGroupIds,
                movingVisibleGroupIds = movingGroupIds.filter { id -> id in itemsByLocalId && id in visibleIds },
                fromIndex = fromIndex,
                toIndex = resolvedToIndex,
            )
        } else {
            // Moving a child item
            // 1. Compute the new list order after vertical reordering of this single item
            val rearrangedIds =
                visibleIds.toMutableList().apply {
                    removeAt(fromIndex)
                    add(toIndex, movingId)
                }

            val previousId = rearrangedIds.getOrNull(toIndex - 1)
            val nextId = rearrangedIds.getOrNull(toIndex + 1)
            val previousOrder =
                previousId?.let { id ->
                    items.first { item -> item.localId == id }.sortOrder
                }
            val nextOrder =
                nextId?.let { id ->
                    items.first { item -> item.localId == id }.sortOrder
                }

            val newSortOrder =
                when {
                    previousOrder != null && nextOrder != null -> (previousOrder + nextOrder) / 2.0
                    previousOrder != null -> previousOrder + 1.0
                    nextOrder != null -> nextOrder - 1.0
                    else -> movingItem.sortOrder
                }

            // 2. Resolve new parentLocalId and depth based on the preceding item in rearrangedIds
            var newParentLocalId: Long? = movingItem.parentLocalId
            var newDepth = movingItem.depth

            if (!isDragging) {
                newParentLocalId = null
                newDepth = 0
                // Search backwards from the item's new position to find the nearest parent (depth 0)
                for (idx in toIndex - 1 downTo 0) {
                    val prevId = rearrangedIds[idx]
                    val prevItem = itemsByLocalId[prevId]
                    if (prevItem != null) {
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
                }
            }

            return ChecklistEditResult(
                items =
                    items.map { item ->
                        if (item.localId == movingId) {
                            item.copy(
                                sortOrder = newSortOrder,
                                parentLocalId = newParentLocalId,
                                depth = newDepth,
                            )
                        } else {
                            item
                        }
                    },
                changed = true,
            )
        }
    }

    private fun reorderVisibleGroup(
        items: List<EditableItem>,
        visibleIds: List<Long>,
        movingGroupIds: List<Long>,
        movingVisibleGroupIds: List<Long>,
        fromIndex: Int,
        toIndex: Int,
    ): ChecklistEditResult {
        val targetId = visibleIds[toIndex]
        if (targetId in movingVisibleGroupIds) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val remainingIds = visibleIds.filterNot { id -> id in movingVisibleGroupIds }
        val targetIndex = remainingIds.indexOf(targetId)
        if (targetIndex < 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val insertionIndex =
            if (toIndex > fromIndex) {
                targetIndex + 1
            } else {
                targetIndex
            }
        val reorderedIds =
            remainingIds.toMutableList().apply {
                // Reinsert the full persisted group, not just visible rows. During a parent drag
                // the UI may collapse active children, and checked children live in the completed
                // section; both still need new sort orders so they remain attached after drop.
                addAll(insertionIndex, movingGroupIds)
            }
        val itemsByLocalId = items.associateBy { item -> item.localId }
        val fullReorderedIds = mutableListOf<Long>()
        val seenIds = mutableSetOf<Long>()
        for (id in reorderedIds) {
            if (id in seenIds) continue
            fullReorderedIds.add(id)
            seenIds.add(id)

            val item = itemsByLocalId[id]
            if (item != null && item.depth == 0) {
                val children = items
                    .filter { childItem -> childItem.parentLocalId == id }
                    .sortedBy { childItem -> childItem.sortOrder }
                    .map { childItem -> childItem.localId }
                for (childId in children) {
                    if (childId !in seenIds) {
                        fullReorderedIds.add(childId)
                        seenIds.add(childId)
                    }
                }
            }
        }
        val sortOrdersById =
            fullReorderedIds
                .mapIndexed { index, id ->
                    id to (index + 1).toDouble()
                }.toMap()
        return ChecklistEditResult(
            items =
                items.map { item ->
                    sortOrdersById[item.localId]?.let { sortOrder ->
                        item.copy(sortOrder = sortOrder)
                    } ?: item
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
        val target =
            items.firstOrNull { item -> item.localId == localId }
                ?: return ChecklistEditResult(items = items, changed = false)
        val resolvedParent =
            newParentLocalId?.let { requestedParentId ->
                val candidate =
                    items.firstOrNull { item -> item.localId == requestedParentId }
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
            items =
                items.map { item ->
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
    fun indent(
        items: List<EditableItem>,
        localId: Long,
    ): ChecklistEditResult {
        val target =
            items.firstOrNull { item -> item.localId == localId }
                ?: return ChecklistEditResult(items = items, changed = false)
        if (target.depth != 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val orderedActive =
            items
                .filter { item -> !item.checked }
                .sortedBy { item -> item.sortOrder }
        val targetIndex = orderedActive.indexOfFirst { item -> item.localId == localId }
        if (targetIndex <= 0) {
            return ChecklistEditResult(items = items, changed = false)
        }
        val anchor =
            (targetIndex - 1 downTo 0)
                .map { index -> orderedActive[index] }
                .firstOrNull { item -> item.depth == 0 }
                ?: return ChecklistEditResult(items = items, changed = false)
        return setParent(items, localId, anchor.localId)
    }

    fun outdent(
        items: List<EditableItem>,
        localId: Long,
    ): ChecklistEditResult {
        val target =
            items.firstOrNull { item -> item.localId == localId }
                ?: return ChecklistEditResult(items = items, changed = false)
        if (target.depth != 1) {
            return ChecklistEditResult(items = items, changed = false)
        }
        return setParent(items, localId, null)
    }
}
