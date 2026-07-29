package dev.bikram.remember.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorActionHandlersTest {
    @Test
    fun navigation_save_flushes_editor_before_launching_save_and_navigating() {
        val events = mutableListOf<String>()

        flushThenSaveAndNavigate(
            flushPendingEdits = { events += "flush" },
            launchSave = { events += "save" },
            navigate = { events += "navigate" },
        )

        assertEquals(listOf("flush", "save", "navigate"), events)
    }
}
