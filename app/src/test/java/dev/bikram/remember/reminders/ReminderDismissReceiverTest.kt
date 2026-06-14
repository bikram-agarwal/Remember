package dev.bikram.remember.reminders

import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDismissReceiverTest {
    @Test
    fun dismissedReminderUsesReminderIndexInsteadOfPrimaryReminder() {
        val now = 2_000L
        val note =
            note(
                reminderAt = 3_000L,
                reminders =
                    listOf(
                        NoteReminder(reminderAt = 3_000L),
                        NoteReminder(reminderAt = 1_000L),
                    ),
            )

        assertTrue(shouldRepostDismissedReminder(note, reminderIndex = 1, now = now))
        assertFalse(shouldRepostDismissedReminder(note, reminderIndex = 0, now = now))
    }

    @Test
    fun dismissedReminderDoesNotRepostMissingReminderIndex() {
        val note =
            note(
                reminderAt = 1_000L,
                reminders = listOf(NoteReminder(reminderAt = 1_000L)),
            )

        assertFalse(shouldRepostDismissedReminder(note, reminderIndex = 1, now = 2_000L))
    }

    private fun note(
        reminderAt: Long?,
        reminders: List<NoteReminder>,
    ): NoteEntity =
        NoteEntity(
            id = 1L,
            kind = NoteKind.NOTE,
            title = "Note",
            body = "",
            colorIndex = 0,
            starred = false,
            trashed = false,
            createdAt = 0L,
            updatedAt = 0L,
            reminderAt = reminderAt,
            reminders = reminders,
        )
}
