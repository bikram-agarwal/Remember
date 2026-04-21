package com.example.checklist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checklist.data.ChecklistItem
import com.example.checklist.data.ChecklistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A row rendered in either the active or completed LazyColumn section.
 *
 *  - [Item] wraps a real persisted [ChecklistItem].
 *  - [GhostParent] is a non-persisted header synthesised by the UI state builder
 *    when a checked child sits in the completed section but its parent is still
 *    unchecked and therefore lives in the active section. Ghosts have no id;
 *    they are derived from the current snapshot and disappear when the last
 *    checked child under them is unchecked.
 */
sealed interface ChecklistRow {
    val key: String
    val depth: Int

    data class Item(val item: ChecklistItem) : ChecklistRow {
        override val key: String get() = "item-${item.id}"
        override val depth: Int get() = item.depth
    }

    data class GhostParent(val parent: ChecklistItem) : ChecklistRow {
        override val key: String get() = "ghost-${parent.id}"
        override val depth: Int get() = 0
    }
}

data class ChecklistUiState(
    val active: List<ChecklistRow> = emptyList(),
    val completed: List<ChecklistRow> = emptyList(),
    /**
     * For each active row key, the id of the nearest preceding top-level
     * (depth=0) item, or null if none. Used by the UI to decide which parent
     * a right-drag should nest under.
     */
    val precedingTopLevelIds: Map<String, Int?> = emptyMap(),
)

class ChecklistViewModel(
    private val repo: ChecklistRepository,
) : ViewModel() {

    val uiState: StateFlow<ChecklistUiState> = repo.observeAll()
        .map { items -> buildUiState(items) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChecklistUiState(),
        )

    // ---------- mutations ----------

    fun addItem(text: String, parentId: Int? = null) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.add(text.trim(), parentId) }
    }

    /**
     * Toggle isChecked on an item, applying the Parent-Context Rule:
     *  - Checking a parent cascades isChecked=true to all of its children so the
     *    entire block moves to Completed in one step. Internal sortOrder is
     *    preserved (we never mutate sortOrder here) so their relative positions
     *    carry over.
     *  - Checking a child or unchecking anything affects only that single row.
     *    When an unchecked-parent/checked-child combination exists, the UI
     *    state builder will synthesise a GhostParent header in the completed
     *    section so the child retains visible context.
     */
    fun toggleChecked(itemId: Int, checked: Boolean) {
        viewModelScope.launch {
            val target = repo.findById(itemId) ?: return@launch
            val shouldCascade = target.depth == 0 && checked
            if (shouldCascade) {
                repo.setCheckedForFamily(target.id, true)
            } else {
                repo.update(target.copy(isChecked = checked))
            }
        }
    }

    /**
     * Vertical reorder: [movedId] is dropped between [beforeId] (the row now
     * above it) and [afterId] (the row now below it). Assigns a midpoint
     * sortOrder so neighbors don't need to shift.
     */
    fun reorder(movedId: Int, beforeId: Int?, afterId: Int?) {
        viewModelScope.launch {
            val moved = repo.findById(movedId) ?: return@launch
            val before = beforeId?.let { repo.findById(it) }
            val after = afterId?.let { repo.findById(it) }
            repo.reorderBetween(moved, before, after)
        }
    }

    /**
     * Horizontal drag-right on a top-level item nests it under [newParentId].
     * Rejected when:
     *  - the item is already a child (depth=1) - nesting depth is capped at 1
     *  - the target parent is itself a child (would create depth=2)
     *  - the item has children of its own (would orphan or deepen them)
     */
    fun nestUnder(itemId: Int, newParentId: Int) {
        viewModelScope.launch {
            val item = repo.findById(itemId) ?: return@launch
            if (item.depth == 1) return@launch
            val parent = repo.findById(newParentId) ?: return@launch
            if (parent.depth != 0) return@launch
            if (repo.childrenOf(item.id).isNotEmpty()) return@launch
            repo.update(item.copy(parentId = newParentId, depth = 1))
        }
    }

    /** Drag-off-the-left-edge on a child (depth=1) promotes it to top-level. */
    fun unnest(itemId: Int) {
        viewModelScope.launch {
            val item = repo.findById(itemId) ?: return@launch
            if (item.depth != 1) return@launch
            repo.update(item.copy(parentId = null, depth = 0))
        }
    }

    // ---------- state derivation ----------

    private fun buildUiState(items: List<ChecklistItem>): ChecklistUiState {
        // Exact requirement from the spec:
        //   activeList    = items.filter { !it.isChecked }.sortedBy { it.sortOrder }
        //   completedList = items.filter {  it.isChecked }.sortedBy { it.sortOrder }
        val activeItems = items.filter { !it.isChecked }.sortedBy { it.sortOrder }
        val completedItems = items.filter { it.isChecked }.sortedBy { it.sortOrder }

        val active: List<ChecklistRow> = activeItems.map { ChecklistRow.Item(it) }

        val checkedParentIds: Set<Int> = completedItems
            .asSequence()
            .filter { it.depth == 0 }
            .map { it.id }
            .toSet()
        val byId: Map<Int, ChecklistItem> = items.associateBy { it.id }

        // Walk completed items in sortOrder. Before each checked child whose
        // parent is still unchecked, inject a GhostParent header (only once per
        // parent, since multiple checked children under the same ghost share a
        // single header).
        val completed = mutableListOf<ChecklistRow>()
        val emittedGhostIds = mutableSetOf<Int>()
        for (item in completedItems) {
            val needsGhost =
                item.depth == 1 &&
                    item.parentId != null &&
                    item.parentId !in checkedParentIds &&
                    item.parentId !in emittedGhostIds
            if (needsGhost) {
                val parentRow = byId[item.parentId]
                if (parentRow != null) {
                    completed.add(ChecklistRow.GhostParent(parentRow))
                    emittedGhostIds.add(parentRow.id)
                }
            }
            completed.add(ChecklistRow.Item(item))
        }

        // Precompute nearest preceding top-level id for each active row so the
        // UI knows which parent a right-drag should nest under.
        val precedingTopLevelIds: Map<String, Int?> = buildMap {
            var lastTopLevelId: Int? = null
            for (row in active) {
                put(row.key, lastTopLevelId)
                val rowItem = (row as? ChecklistRow.Item)?.item
                if (rowItem != null && rowItem.depth == 0) {
                    lastTopLevelId = rowItem.id
                }
            }
        }

        return ChecklistUiState(
            active = active,
            completed = completed,
            precedingTopLevelIds = precedingTopLevelIds,
        )
    }
}
