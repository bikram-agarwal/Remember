package dev.bikram.remember.domain.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistEditorTest {
    @Test
    fun parent_toggle_cascades_to_children() {
        val items =
            listOf(
                editableItem(localId = 1L),
                editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L),
            )

        val result = ChecklistEditor.toggleChecked(items, 1L)
        val itemsByLocalId = result.items.associateBy { item -> item.localId }

        assertTrue(result.changed)
        assertTrue(itemsByLocalId.getValue(1L).checked)
        assertTrue(itemsByLocalId.getValue(2L).checked)
        assertTrue(itemsByLocalId.getValue(3L).checked)
        assertFalse(itemsByLocalId.getValue(4L).checked)
    }

    @Test
    fun checking_last_unchecked_child_checks_parent() {
        val items =
            listOf(
                editableItem(localId = 1L),
                editableItem(localId = 2L, checked = true, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, parentLocalId = 1L, depth = 1),
            )

        val result = ChecklistEditor.toggleChecked(items, 3L)
        val itemsByLocalId = result.items.associateBy { item -> item.localId }

        assertTrue(result.changed)
        assertTrue(itemsByLocalId.getValue(1L).checked)
        assertTrue(itemsByLocalId.getValue(2L).checked)
        assertTrue(itemsByLocalId.getValue(3L).checked)
    }

    @Test
    fun unchecking_child_of_checked_parent_unchecks_parent() {
        val items =
            listOf(
                editableItem(localId = 1L, checked = true),
                editableItem(localId = 2L, checked = true, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, checked = true, parentLocalId = 1L, depth = 1),
            )

        val result = ChecklistEditor.toggleChecked(items, 2L)
        val itemsByLocalId = result.items.associateBy { item -> item.localId }

        assertTrue(result.changed)
        assertFalse(itemsByLocalId.getValue(1L).checked)
        assertFalse(itemsByLocalId.getValue(2L).checked)
        assertTrue(itemsByLocalId.getValue(3L).checked)
    }

    @Test
    fun reorder_top_and_bottom_uses_neighbor_math() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0),
                editableItem(localId = 3L, sortOrder = 30.0),
            )

        val movedToTop =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 2L, 3L),
                fromIndex = 2,
                toIndex = 0,
            )
        val movedToBottom =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 2L, 3L),
                fromIndex = 0,
                toIndex = 2,
            )

        assertEquals(9.0, movedToTop.items.first { item -> item.localId == 3L }.sortOrder, 0.0)
        assertEquals(31.0, movedToBottom.items.first { item -> item.localId == 1L }.sortOrder, 0.0)
    }

    @Test
    fun reorder_no_ops_keep_original_items() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0),
            )
        val visibleIds = listOf(1L, 2L)
        val sameIndex = ChecklistEditor.reorderWithin(items, visibleIds, fromIndex = 0, toIndex = 0)
        val invalidFromIndex = ChecklistEditor.reorderWithin(items, visibleIds, fromIndex = -1, toIndex = 1)
        val invalidToIndex = ChecklistEditor.reorderWithin(items, visibleIds, fromIndex = 0, toIndex = 2)

        assertFalse(sameIndex.changed)
        assertEquals(items, sameIndex.items)
        assertFalse(invalidFromIndex.changed)
        assertEquals(items, invalidFromIndex.items)
        assertFalse(invalidToIndex.changed)
        assertEquals(items, invalidToIndex.items)
    }

    @Test
    fun reordering_parent_moves_visible_children_with_it() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0),
                editableItem(localId = 5L, sortOrder = 50.0),
            )

        val result =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 2L, 3L, 4L, 5L),
                fromIndex = 0,
                toIndex = 4,
            )
        val sortedIds = result.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(result.changed)
        assertEquals(listOf(4L, 5L, 1L, 2L, 3L), sortedIds)
        assertEquals(1L, result.items.first { item -> item.localId == 2L }.parentLocalId)
        assertEquals(1L, result.items.first { item -> item.localId == 3L }.parentLocalId)
    }

    @Test
    fun reordering_parent_moves_checked_child_with_it_even_when_child_is_not_visible() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, checked = true, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
                editableItem(localId = 4L, sortOrder = 40.0),
            )

        val result =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 3L, 4L),
                fromIndex = 0,
                toIndex = 2,
            )
        val sortedIds = result.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(result.changed)
        assertEquals(listOf(3L, 4L, 1L, 2L), sortedIds)
        assertEquals(1L, result.items.first { item -> item.localId == 2L }.parentLocalId)
    }

    @Test
    fun reordering_collapsed_parent_moves_hidden_active_children_with_it() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0),
                editableItem(localId = 5L, sortOrder = 50.0),
            )

        val result =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 4L, 5L),
                fromIndex = 0,
                toIndex = 2,
            )
        val sortedIds = result.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(result.changed)
        assertEquals(listOf(4L, 5L, 1L, 2L, 3L), sortedIds)
        assertEquals(1L, result.items.first { item -> item.localId == 2L }.parentLocalId)
        assertEquals(1L, result.items.first { item -> item.localId == 3L }.parentLocalId)
    }

    @Test
    fun reordering_parent_within_own_children_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
            )

        val result =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L, 2L, 3L),
                fromIndex = 0,
                toIndex = 1,
            )

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun single_visible_row_reorder_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
            )

        val result =
            ChecklistEditor.reorderWithin(
                items = items,
                visibleIds = listOf(1L),
                fromIndex = 0,
                toIndex = 0,
            )

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun insert_after_parent_creates_top_level_sibling() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
            )

        val result = ChecklistEditor.insertAfter(items, targetLocalId = 1L, newLocalId = 4L)
        val newItem = result.items.first { item -> item.localId == 4L }

        assertTrue(result.changed)
        assertEquals(null, newItem.parentLocalId)
        assertEquals(0, newItem.depth)
        assertEquals(25.0, newItem.sortOrder, 0.0)
    }

    @Test
    fun insert_after_child_creates_child_sibling() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
            )

        val result = ChecklistEditor.insertAfter(items, targetLocalId = 2L, newLocalId = 4L)
        val newItem = result.items.first { item -> item.localId == 4L }

        assertTrue(result.changed)
        assertEquals(1L, newItem.parentLocalId)
        assertEquals(1, newItem.depth)
        assertEquals(25.0, newItem.sortOrder, 0.0)
    }

    @Test
    fun insert_after_missing_target_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
            )

        val result = ChecklistEditor.insertAfter(items, targetLocalId = 2L, newLocalId = 3L)

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun reparenting_to_child_resolves_to_top_level_parent() {
        val items =
            listOf(
                editableItem(localId = 1L),
                editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L),
            )

        val result =
            ChecklistEditor.setParent(
                items = items,
                localId = 3L,
                newParentLocalId = 2L,
            )
        val target = result.items.first { item -> item.localId == 3L }

        assertTrue(result.changed)
        assertEquals(1L, target.parentLocalId)
        assertEquals(1, target.depth)
    }

    @Test
    fun indent_uses_nearest_prior_unchecked_top_level_anchor() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, checked = true, sortOrder = 30.0),
                editableItem(localId = 4L, sortOrder = 40.0),
            )

        val result = ChecklistEditor.indent(items, 4L)
        val target = result.items.first { item -> item.localId == 4L }

        assertTrue(result.changed)
        assertEquals(1L, target.parentLocalId)
        assertEquals(1, target.depth)
    }

    @Test
    fun indenting_nested_row_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
            )

        val result = ChecklistEditor.indent(items, 2L)

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun indent_without_prior_active_top_level_anchor_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L, checked = true, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0),
            )

        val result = ChecklistEditor.indent(items, 2L)

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun child_toggle_with_missing_parent_only_updates_child() {
        val items =
            listOf(
                editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L),
            )

        val result = ChecklistEditor.toggleChecked(items, 2L)
        val itemsByLocalId = result.items.associateBy { item -> item.localId }

        assertTrue(result.changed)
        assertTrue(itemsByLocalId.getValue(2L).checked)
        assertFalse(itemsByLocalId.getValue(3L).checked)
    }

    @Test
    fun outdent_promotes_child_to_top_level() {
        val items =
            listOf(
                editableItem(localId = 1L),
                editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
            )

        val result = ChecklistEditor.outdent(items, 2L)
        val target = result.items.first { item -> item.localId == 2L }

        assertTrue(result.changed)
        assertEquals(null, target.parentLocalId)
        assertEquals(0, target.depth)
    }

    @Test
    fun outdent_top_level_row_is_no_op() {
        val items =
            listOf(
                editableItem(localId = 1L),
            )

        val result = ChecklistEditor.outdent(items, 1L)

        assertFalse(result.changed)
        assertEquals(items, result.items)
    }

    @Test
    fun reordering_parent_coerces_target_index_to_avoid_splitting_other_parent_when_dragged_up() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 5L, sortOrder = 50.0),
                editableItem(localId = 6L, sortOrder = 60.0, parentLocalId = 5L, depth = 1),
            )
        // visibleIds simulates dragging parent 5L up. Its children (6L) are filtered out of activeIds.
        val visibleIdsList = listOf(1L, 2L, 3L, 4L, 5L)
        // Drag parent 5L (index 4) and drop on child 2L (index 1).
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 4,
                toIndex = 1,
            )
        val sortedIdsList = editResult.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(editResult.changed)
        // Parent 5L (and child 6L) should be coerced to index 0 (parentIdx of 1L) to prevent splitting the 1L group.
        assertEquals(listOf(5L, 6L, 1L, 2L, 3L, 4L), sortedIdsList)
    }

    @Test
    fun reordering_parent_coerces_target_index_to_avoid_splitting_other_parent_when_dragged_down() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 5L, sortOrder = 50.0),
                editableItem(localId = 6L, sortOrder = 60.0, parentLocalId = 5L, depth = 1),
            )
        // visibleIds simulates dragging parent 1L down. Its children (2L, 3L, 4L) are filtered out.
        val visibleIdsList = listOf(1L, 5L, 6L)
        // Drag parent 1L (index 0) and drop on child 6L (index 2).
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 0,
                toIndex = 2,
            )
        val sortedIdsList = editResult.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(editResult.changed)
        // Parent 1L (and children 2L, 3L, 4L) should be coerced to index 2 (last child index of 5L) to prevent splitting.
        assertEquals(listOf(5L, 6L, 1L, 2L, 3L, 4L), sortedIdsList)
    }

    @Test
    fun reordering_parent_coerces_target_index_to_avoid_splitting_other_parent_when_dragged_down_to_parent() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 5L, sortOrder = 50.0),
                editableItem(localId = 6L, sortOrder = 60.0, parentLocalId = 5L, depth = 1),
            )
        // visibleIds simulates dragging parent 1L down. Its children (2L, 3L, 4L) are filtered out.
        val visibleIdsList = listOf(1L, 5L, 6L)
        // Drag parent 1L (index 0) and drop on parent 5L (index 1).
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 0,
                toIndex = 1,
            )
        val sortedIdsList = editResult.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(editResult.changed)
        // Parent 1L (and children 2L, 3L, 4L) should be coerced to index 2 (last child index of 5L) to prevent splitting.
        assertEquals(listOf(5L, 6L, 1L, 2L, 3L, 4L), sortedIdsList)
    }

    @Test
    fun reordering_parent_coerces_target_index_to_avoid_splitting_other_parent_when_dragged_up_to_parent() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 5L, sortOrder = 50.0),
                editableItem(localId = 6L, sortOrder = 60.0, parentLocalId = 5L, depth = 1),
            )
        // visibleIds simulates dragging parent 5L up. Its children (6L) are filtered out of activeIds.
        val visibleIdsList = listOf(1L, 2L, 3L, 4L, 5L)
        // Drag parent 5L (index 4) and drop on parent 1L (index 0).
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 4,
                toIndex = 0,
            )
        val sortedIdsList = editResult.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(editResult.changed)
        // Parent 5L (and child 6L) should be coerced to index 0 (parentIdx of 1L) to prevent splitting the 1L group.
        assertEquals(listOf(5L, 6L, 1L, 2L, 3L, 4L), sortedIdsList)
    }

    @Test
    fun reordering_child_reparents_to_new_parent_when_dragged_between_sections() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 3L, depth = 1),
            )
        val visibleIdsList = listOf(1L, 2L, 3L, 4L)
        // Drag child 2L (index 1) and drop after parent 3L (to index 3).
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 1,
                toIndex = 3,
            )
        val movedItem = editResult.items.first { item -> item.localId == 2L }

        assertTrue(editResult.changed)
        assertEquals(3L, movedItem.parentLocalId)
        assertEquals(1, movedItem.depth)
    }

    @Test
    fun reordering_child_promoted_to_parent_when_dragged_to_top() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
            )
        val visibleIdsList = listOf(1L, 2L)
        // Drag child 2L (index 1) and drop at index 0.
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 1,
                toIndex = 0,
            )
        val movedItem = editResult.items.first { item -> item.localId == 2L }

        assertTrue(editResult.changed)
        assertEquals(null, movedItem.parentLocalId)
        assertEquals(0, movedItem.depth)
    }

    @Test
    fun reordering_parent_does_not_coerce_target_index_during_active_drag() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 5L, sortOrder = 50.0),
                editableItem(localId = 6L, sortOrder = 60.0, parentLocalId = 5L, depth = 1),
            )
        val visibleIdsList = listOf(1L, 2L, 3L, 4L, 5L)
        // Drag parent 5L (index 4) and hover on child 2L (index 1) with isDragging = true.
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 4,
                toIndex = 1,
                isDragging = true,
            )
        val sortedIdsList = editResult.items.sortedBy { item -> item.sortOrder }.map { item -> item.localId }

        assertTrue(editResult.changed)
        // Coercion should not occur during active drag, so parent 5L should swap simply to index 1 (between 1L and 2L).
        assertEquals(listOf(1L, 5L, 6L, 2L, 3L, 4L), sortedIdsList)
    }

    @Test
    fun reordering_child_does_not_reparent_during_active_drag() {
        val itemsList =
            listOf(
                editableItem(localId = 1L, sortOrder = 10.0),
                editableItem(localId = 2L, sortOrder = 20.0, parentLocalId = 1L, depth = 1),
                editableItem(localId = 3L, sortOrder = 30.0),
                editableItem(localId = 4L, sortOrder = 40.0, parentLocalId = 3L, depth = 1),
            )
        val visibleIdsList = listOf(1L, 2L, 3L, 4L)
        // Drag child 2L (index 1) and hover after parent 3L (to index 3) with isDragging = true.
        val editResult =
            ChecklistEditor.reorderWithin(
                items = itemsList,
                visibleIds = visibleIdsList,
                fromIndex = 1,
                toIndex = 3,
                isDragging = true,
            )
        val movedItem = editResult.items.first { item -> item.localId == 2L }

        assertTrue(editResult.changed)
        // Should NOT reparent to 3L during active drag.
        assertEquals(1L, movedItem.parentLocalId)
        assertEquals(1, movedItem.depth)
    }


    private fun editableItem(
        localId: Long,
        checked: Boolean = false,
        sortOrder: Double = localId.toDouble(),
        parentLocalId: Long? = null,
        depth: Int = 0,
    ): EditableItem =
        EditableItem(
            localId = localId,
            text = "Item $localId",
            checked = checked,
            sortOrder = sortOrder,
            parentLocalId = parentLocalId,
            depth = depth,
        )
}
