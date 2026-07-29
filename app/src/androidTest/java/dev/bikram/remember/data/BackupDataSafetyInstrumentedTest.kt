package dev.bikram.remember.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDataSafetyInstrumentedTest {
    @Test
    fun snoozed_reminder_round_trip_preserves_original_schedule() {
        val reminder =
            NoteReminder(
                reminderAt = 2_000L,
                recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                originalReminderAt = 1_000L,
            )

        val restoredReminder = decodeReminderFromBackup(encodeReminderForBackup(reminder))

        assertEquals(reminder, restoredReminder)
    }
}
