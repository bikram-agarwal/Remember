package dev.bikram.remember.ui.edit

import dev.bikram.remember.domain.checklist.EditableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRenderingVisibilityTest {
    private val parentId = 1L
    private val childId = 2L

    private fun parentRow(): ActiveEntry.Row =
        ActiveEntry.Row(
            item =
                EditableItem(
                    localId = parentId,
                    text = "Parent",
                    checked = false,
                    sortOrder = 1.0,
                ),
            sortKey = 1.0,
        )

    private fun childRow(): ActiveEntry.Row =
        ActiveEntry.Row(
            item =
                EditableItem(
                    localId = childId,
                    text = "Child",
                    checked = false,
                    sortOrder = 2.0,
                    parentLocalId = parentId,
                    depth = 1,
                ),
            sortKey = 2.0,
        )

    private fun activeGhostHeader(): ActiveEntry.Ghost =
        ActiveEntry.Ghost(
            header =
                GhostParentHeader(
                    realParentLocalId = parentId,
                    text = "Parent",
                    parentChecked = true,
                ),
            sortKey = 2.0,
        )

    private fun completedChildRow(): CompletedEntry.Row =
        CompletedEntry.Row(
            item =
                EditableItem(
                    localId = childId,
                    text = "Child",
                    checked = true,
                    sortOrder = 2.0,
                    parentLocalId = parentId,
                    depth = 1,
                ),
            sortKey = 2.0,
        )

    private fun completedGhostHeader(): CompletedEntry.Ghost =
        CompletedEntry.Ghost(
            header =
                GhostParentHeader(
                    realParentLocalId = parentId,
                    text = "Parent",
                    parentChecked = false,
                ),
            sortKey = 2.0,
        )

    @Test
    fun parent_row_stays_visible_when_collapsed() {
        assertTrue(
            isActiveEntryVisible(
                entry = parentRow(),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun active_child_hidden_when_parent_collapsed() {
        assertFalse(
            isActiveEntryVisible(
                entry = childRow(),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun active_ghost_stays_visible_when_parent_collapsed() {
        assertTrue(
            isActiveEntryVisible(
                entry = activeGhostHeader(),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun active_child_hidden_while_parent_dragging() {
        assertFalse(
            isActiveEntryVisible(
                entry = childRow(),
                collapsedParentIds = emptySet(),
                draggingParentLocalId = parentId,
            ),
        )
    }

    @Test
    fun active_parent_visible_while_dragging() {
        assertTrue(
            isActiveEntryVisible(
                entry = parentRow(),
                collapsedParentIds = emptySet(),
                draggingParentLocalId = parentId,
            ),
        )
    }

    @Test
    fun completed_child_hidden_when_parent_collapsed() {
        assertFalse(
            isCompletedEntryVisible(
                entry = completedChildRow(),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun completed_ghost_stays_visible_when_parent_collapsed() {
        assertTrue(
            isCompletedEntryVisible(
                entry = completedGhostHeader(),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun completed_parent_stays_visible_when_collapsed() {
        val checkedParent =
            CompletedEntry.Row(
                item =
                    EditableItem(
                        localId = parentId,
                        text = "Parent",
                        checked = true,
                        sortOrder = 1.0,
                    ),
                sortKey = 1.0,
            )
        assertTrue(
            isCompletedEntryVisible(
                entry = checkedParent,
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            ),
        )
    }

    @Test
    fun filter_active_entries_removes_collapsed_children() {
        val filtered =
            filterVisibleActiveEntries(
                entries = listOf(parentRow(), childRow()),
                collapsedParentIds = setOf(parentId),
                draggingParentLocalId = null,
            )

        assertEquals(1, filtered.size)
        val visibleRow = filtered.single() as ActiveEntry.Row
        assertEquals(parentId, visibleRow.item.localId)
    }
}
