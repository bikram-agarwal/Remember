package dev.bikram.remember.reminders

import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderDismissReceiverTest {
    @Test
    fun dismissedReminderRepostsLatestDueReminder() {
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

        assertEquals(1, latestDueReminderIndex(note, now))
    }

    @Test
    fun dismissedReminderDoesNotRepostWhenNoReminderIsDue() {
        val note =
            note(
                reminderAt = 3_000L,
                reminders = listOf(NoteReminder(reminderAt = 3_000L)),
            )

        assertNull(latestDueReminderIndex(note, now = 2_000L))
    }

    @Test
    fun dismissedReminderPrefersNewerDueReminderOverOlderDueReminder() {
        val note =
            note(
                reminderAt = 1_000L,
                reminders =
                    listOf(
                        NoteReminder(reminderAt = 1_000L),
                        NoteReminder(reminderAt = 1_500L),
                    ),
            )

        assertEquals(1, latestDueReminderIndex(note, now = 2_000L))
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
