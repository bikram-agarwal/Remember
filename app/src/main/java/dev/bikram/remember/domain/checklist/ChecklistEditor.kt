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

            val resolvedToIndex =
                resolveParentGroupTargetIndex(
                    itemsByLocalId = itemsByLocalId,
                    visibleIds = visibleIds,
                    fromIndex = fromIndex,
                    toIndex = toIndex,
                    targetItem = targetItem,
                    isDragging = isDragging,
                )

            return reorderVisibleGroup(
                items = items,
                visibleIds = visibleIds,
                movingGroupIds = movingGroupIds,
                movingVisibleGroupIds = movingGroupIds.filter { id -> id in itemsByLocalId && id in visibleIds },
                fromIndex = fromIndex,
                toIndex = resolvedToIndex,
                keepParentGroupsTogether = !isDragging,
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

            // 2. Resolve new parentLocalId and depth based on the preceding item in rearrangedIds.
            val (newParentLocalId, newDepth) =
                resolveChildParentAfterMove(
                    movingItem = movingItem,
                    rearrangedIds = rearrangedIds,
                    toIndex = toIndex,
                    itemsByLocalId = itemsByLocalId,
                    isDragging = isDragging,
                )

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

    private fun resolveParentGroupTargetIndex(
        itemsByLocalId: Map<Long, EditableItem>,
        visibleIds: List<Long>,
        fromIndex: Int,
        toIndex: Int,
        targetItem: EditableItem,
        isDragging: Boolean,
    ): Int {
        if (isDragging) return toIndex

        val targetParentId = if (targetItem.depth == 0) targetItem.localId else targetItem.parentLocalId
        val visibleItems = visibleIds.mapNotNull { id -> itemsByLocalId[id] }
        val parentIdx = visibleItems.indexOfFirst { item -> item.localId == targetParentId }
        if (targetParentId == null || parentIdx < 0) return toIndex

        val lastChildIndex =
            visibleItems
                .mapIndexedNotNull { index, item ->
                    if (item.parentLocalId == targetParentId) index else null
                }.lastOrNull() ?: return toIndex

        return if (toIndex < fromIndex) parentIdx else lastChildIndex
    }

    private fun resolveChildParentAfterMove(
        movingItem: EditableItem,
        rearrangedIds: List<Long>,
        toIndex: Int,
        itemsByLocalId: Map<Long, EditableItem>,
        isDragging: Boolean,
    ): Pair<Long?, Int> {
        if (isDragging) return movingItem.parentLocalId to movingItem.depth

        for (idx in toIndex - 1 downTo 0) {
            val prevItem = itemsByLocalId[rearrangedIds[idx]] ?: continue
            if (prevItem.depth == 0) {
                return prevItem.localId to 1
            }
            if (prevItem.depth == 1 && prevItem.parentLocalId != null) {
                return prevItem.parentLocalId to 1
            }
        }

        return null to 0
    }

    private fun reorderVisibleGroup(
        items: List<EditableItem>,
        visibleIds: List<Long>,
        movingGroupIds: List<Long>,
        movingVisibleGroupIds: List<Long>,
        fromIndex: Int,
        toIndex: Int,
        keepParentGroupsTogether: Boolean,
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
        val fullReorderedIds =
            if (keepParentGroupsTogether) {
                expandParentGroups(reorderedIds, items, itemsByLocalId)
            } else {
                reorderedIds.distinct()
            }
        val sortOrdersById =
            calculateMovedGroupSortOrders(
                fullReorderedIds = fullReorderedIds,
                movingGroupIds = movingGroupIds,
                itemsByLocalId = itemsByLocalId,
            )
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

    private fun expandParentGroups(
        reorderedIds: List<Long>,
        items: List<EditableItem>,
        itemsByLocalId: Map<Long, EditableItem>,
    ): List<Long> {
        val fullReorderedIds = mutableListOf<Long>()
        val seenIds = mutableSetOf<Long>()
        for (id in reorderedIds) {
            if (id in seenIds) continue
            fullReorderedIds.add(id)
            seenIds.add(id)

            val item = itemsByLocalId[id]
            if (item != null && item.depth == 0) {
                val children =
                    items
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
        return fullReorderedIds
    }

    private fun calculateMovedGroupSortOrders(
        fullReorderedIds: List<Long>,
        movingGroupIds: List<Long>,
        itemsByLocalId: Map<Long, EditableItem>,
    ): Map<Long, Double> {
        val movedIndices =
            fullReorderedIds.mapIndexedNotNull { index, id ->
                if (id in movingGroupIds) index else null
            }
        if (movedIndices.isEmpty()) return emptyMap()

        val firstMovedIndex = movedIndices.first()
        val lastMovedIndex = movedIndices.last()
        val previousOrder =
            fullReorderedIds
                .take(firstMovedIndex)
                .lastOrNull { id -> id !in movingGroupIds }
                ?.let { id -> itemsByLocalId[id]?.sortOrder }
        val nextOrder =
            fullReorderedIds
                .drop(lastMovedIndex + 1)
                .firstOrNull { id -> id !in movingGroupIds }
                ?.let { id -> itemsByLocalId[id]?.sortOrder }

        return when {
            previousOrder != null && nextOrder != null -> {
                val step = (nextOrder - previousOrder) / (movingGroupIds.size + 1)
                movingGroupIds.mapIndexed { index, id -> id to previousOrder + step * (index + 1) }.toMap()
            }
            previousOrder != null ->
                movingGroupIds.mapIndexed { index, id -> id to previousOrder + index + 1 }.toMap()
            nextOrder != null -> {
                val firstOrder = nextOrder - movingGroupIds.size
                movingGroupIds.mapIndexed { index, id -> id to firstOrder + index }.toMap()
            }
            else ->
                movingGroupIds.mapNotNull { id -> itemsByLocalId[id]?.sortOrder?.let { sortOrder -> id to sortOrder } }.toMap()
        }
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
