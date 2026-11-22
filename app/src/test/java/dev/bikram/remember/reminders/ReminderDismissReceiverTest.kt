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

    @Test
    fun manualPinnedNotificationRestoresWithoutDueReminderEvenWhenPrefOff() {
        val note = note(reminderAt = null, reminders = emptyList())

        assertEquals(
            0,
            resolveDismissRestoreReminderIndex(
                note = note,
                nowMillis = 2_000L,
                isManualPinned = true,
                keepUntilDonePref = false,
            ),
        )
    }

    @Test
    fun manualPinnedNotificationDoesNotRestoreWhenTrashedOrCompleted() {
        val trashed = note(reminderAt = null, reminders = emptyList(), trashed = true)
        val completed = note(reminderAt = null, reminders = emptyList(), completedAt = 1_000L)

        assertNull(
            resolveDismissRestoreReminderIndex(
                note = trashed,
                nowMillis = 2_000L,
                isManualPinned = true,
                keepUntilDonePref = false,
            ),
        )
        assertNull(
            resolveDismissRestoreReminderIndex(
                note = completed,
                nowMillis = 2_000L,
                isManualPinned = true,
                keepUntilDonePref = false,
            ),
        )
    }

    @Test
    fun normalNotificationDoesNotRestoreWhenPrefOff() {
        val note =
            note(
                reminderAt = 1_000L,
                reminders = listOf(NoteReminder(reminderAt = 1_000L)),
            )

        assertNull(
            resolveDismissRestoreReminderIndex(
                note = note,
                nowMillis = 2_000L,
                isManualPinned = false,
                keepUntilDonePref = false,
            ),
        )
    }

    private fun note(
        reminderAt: Long?,
        reminders: List<NoteReminder>,
        trashed: Boolean = false,
        completedAt: Long? = null,
    ): NoteEntity =
        NoteEntity(
            id = 1L,
            kind = NoteKind.NOTE,
            title = "Note",
            body = "",
            colorIndex = 0,
            starred = false,
            trashed = trashed,
            createdAt = 0L,
            updatedAt = 0L,
            reminderAt = reminderAt,
            reminders = reminders,
            completedAt = completedAt,
        )
}
