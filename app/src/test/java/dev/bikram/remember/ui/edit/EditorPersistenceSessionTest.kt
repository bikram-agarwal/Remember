package dev.bikram.remember.ui.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPersistenceSessionTest {
    @Test
    fun clearDirtyIfUnchanged_keepsDirtyWhenNewMutationArrived() {
        val session = EditorPersistenceSession()
        session.markDirty()
        val saveEpoch = session.currentEpoch()

        session.markDirty()
        session.clearDirtyIfUnchanged(saveEpoch)

        assertTrue(session.isDirty)
        assertTrue(session.hasUnsavedChanges.value)
    }

    @Test
    fun clearDirtyIfUnchanged_clearsDirtyWhenNoNewMutationArrived() {
        val session = EditorPersistenceSession()
        session.markDirty()
        val saveEpoch = session.currentEpoch()

        session.clearDirtyIfUnchanged(saveEpoch)

        assertFalse(session.isDirty)
        assertFalse(session.hasUnsavedChanges.value)
    }
}
