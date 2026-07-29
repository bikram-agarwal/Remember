package dev.bikram.remember.reminders

import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReceiverDeliveryTest {
    @Test
    fun due_current_reminder_is_deliverable() {
        val note = note(reminderAt = 1_000L)

        assertTrue(isReminderDeliveryCurrent(note, reminderIndex = 0, now = 2_000L))
    }

    @Test
    fun future_rescheduled_reminder_rejects_stale_alarm() {
        val note = note(reminderAt = 3_000L)

        assertFalse(isReminderDeliveryCurrent(note, reminderIndex = 0, now = 2_000L))
    }

    @Test
    fun cleared_archived_and_completed_reminders_are_not_deliverable() {
        val clearedNote = note(reminderAt = null)
        val archivedNote = note(reminderAt = 1_000L).copy(archived = true)
        val completedNote = note(reminderAt = 1_000L).copy(completedAt = 1_500L)

        assertFalse(isReminderDeliveryCurrent(clearedNote, reminderIndex = 0, now = 2_000L))
        assertFalse(isReminderDeliveryCurrent(archivedNote, reminderIndex = 0, now = 2_000L))
        assertFalse(isReminderDeliveryCurrent(completedNote, reminderIndex = 0, now = 2_000L))
    }

    private fun note(reminderAt: Long?): NoteEntity =
        NoteEntity(
            id = 1L,
            kind = NoteKind.NOTE,
            title = "Reminder",
            body = "",
            colorIndex = 0,
            starred = false,
            trashed = false,
            createdAt = 0L,
            updatedAt = 0L,
            reminderAt = reminderAt,
            reminders = reminderAt?.let { time -> listOf(NoteReminder(reminderAt = time)) } ?: emptyList(),
        )
}
