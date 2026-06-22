package dev.bikram.remember.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun reminder_alarm_request_codes_are_unique_per_reminder_index() {
        val noteId = 42L
        val legacyNoteId = ReminderScheduler.pendingRequestCodeForNote(noteId)
        val reminderIds =
            (0 until ReminderScheduler.MAX_REMINDERS_PER_NOTE)
                .map { reminderIndex -> ReminderScheduler.pendingRequestCodeForNoteReminder(noteId, reminderIndex) }

        assertEquals(reminderIds.size, reminderIds.toSet().size)
        assertFalse(legacyNoteId in reminderIds)
    }

    @Test
    fun dismiss_request_codes_are_unique_per_reminder_index() {
        val noteId = 42L
        val dismissIds =
            (0 until ReminderScheduler.MAX_REMINDERS_PER_NOTE)
                .map { reminderIndex -> ReminderScheduler.pendingRequestCodeForDismiss(noteId, reminderIndex) }

        assertEquals(dismissIds.size, dismissIds.toSet().size)
    }
}
