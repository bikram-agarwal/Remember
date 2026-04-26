package dev.bikram.remember.domain.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistEditorTest {

    @Test
    fun parent_toggle_cascades_to_children() {
        val items = listOf(
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
        val items = listOf(
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
        val items = listOf(
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
        val items = listOf(
            editableItem(localId = 1L, sortOrder = 10.0),
            editableItem(localId = 2L, sortOrder = 20.0),
            editableItem(localId = 3L, sortOrder = 30.0),
        )

        val movedToTop = ChecklistEditor.reorderWithin(
            items = items,
            visibleIds = listOf(1L, 2L, 3L),
            fromIndex = 2,
            toIndex = 0,
        )
        val movedToBottom = ChecklistEditor.reorderWithin(
            items = items,
            visibleIds = listOf(1L, 2L, 3L),
            fromIndex = 0,
            toIndex = 2,
        )

        assertEquals(9.0, movedToTop.items.first { item -> item.localId == 3L }.sortOrder, 0.0)
        assertEquals(31.0, movedToBottom.items.first { item -> item.localId == 1L }.sortOrder, 0.0)
    }

    @Test
    fun reparenting_to_child_resolves_to_top_level_parent() {
        val items = listOf(
            editableItem(localId = 1L),
            editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
            editableItem(localId = 3L),
        )

        val result = ChecklistEditor.setParent(
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
        val items = listOf(
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
    fun outdent_promotes_child_to_top_level() {
        val items = listOf(
            editableItem(localId = 1L),
            editableItem(localId = 2L, parentLocalId = 1L, depth = 1),
        )

        val result = ChecklistEditor.outdent(items, 2L)
        val target = result.items.first { item -> item.localId == 2L }

        assertTrue(result.changed)
        assertEquals(null, target.parentLocalId)
        assertEquals(0, target.depth)
    }

    private fun editableItem(
        localId: Long,
        checked: Boolean = false,
        sortOrder: Double = localId.toDouble(),
        parentLocalId: Long? = null,
        depth: Int = 0,
    ): EditableItem {
        return EditableItem(
            localId = localId,
            text = "Item $localId",
            checked = checked,
            sortOrder = sortOrder,
            parentLocalId = parentLocalId,
            depth = depth,
        )
    }
}
