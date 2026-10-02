package dev.bikram.remember.reminders

import dev.bikram.remember.data.CriticalRingDuration
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalReminderRepeatTest {
    private val now = 10_000_000L

    @Test
    fun overdueCriticalNoteRepeats() {
        assertTrue(needsCriticalRepeat(note(reminderAt = now - 1L), now))
    }

    @Test
    fun noteRepeatsOnlyWhileCritical() {
        Importance.entries.filter { it != Importance.CRITICAL }.forEach { importance ->
            assertFalse(importance.name, needsCriticalRepeat(note(reminderAt = now - 1L, importance = importance), now))
        }
    }

    @Test
    fun criticalNoteDoesNotRepeatBeforeItIsDue() {
        assertFalse(needsCriticalRepeat(note(reminderAt = now + 1L), now))
    }

    @Test
    fun doneArchivedOrTrashedCriticalNoteDoesNotRepeat() {
        val overdue = note(reminderAt = now - 1L)
        assertFalse(needsCriticalRepeat(overdue.copy(completedAt = now - 1L), now))
        assertFalse(needsCriticalRepeat(overdue.copy(archived = true), now))
        assertFalse(needsCriticalRepeat(overdue.copy(trashed = true), now))
    }

    @Test
    fun snoozedCriticalNoteStopsRepeatingUntilItIsDueAgain() {
        val snoozed =
            note(
                reminderAt = now + 60_000L,
                reminders = listOf(NoteReminder(reminderAt = now + 60_000L, originalReminderAt = now - 1L)),
            )
        assertFalse(needsCriticalRepeat(snoozed, now))
        assertTrue(needsCriticalRepeat(snoozed, now + 60_000L))
    }

    @Test
    fun anyDueSlotKeepsANoteWithSeveralRemindersRepeating() {
        val note =
            note(
                reminderAt = now - 1L,
                reminders = listOf(NoteReminder(reminderAt = now + 60_000L), NoteReminder(reminderAt = now - 1L)),
            )
        assertTrue(needsCriticalRepeat(note, now))
    }

    @Test
    fun pendingTickKeepsItsTime() {
        val pending = now + 5 * 60_000L
        assertEquals(pending, nextCriticalRepeatAt(storedAt = pending, now = now))
        val atFullInterval = now + CRITICAL_REPEAT_INTERVAL_MILLIS
        assertEquals(atFullInterval, nextCriticalRepeatAt(storedAt = atFullInterval, now = now))
    }

    @Test
    fun missingOrMissedTickRestartsTheInterval() {
        val expected = now + CRITICAL_REPEAT_INTERVAL_MILLIS
        assertEquals(expected, nextCriticalRepeatAt(storedAt = null, now = now))
        assertEquals(expected, nextCriticalRepeatAt(storedAt = now, now = now))
        assertEquals(expected, nextCriticalRepeatAt(storedAt = now - 60_000L, now = now))
    }

    @Test
    fun tickPushedPastOneIntervalByAClockChangeIsPulledBack() {
        val expected = now + CRITICAL_REPEAT_INTERVAL_MILLIS
        assertEquals(expected, nextCriticalRepeatAt(storedAt = now + 2 * 60 * 60_000L, now = now))
    }

    @Test
    fun onlyRingingCriticalAlertsRingContinuously() {
        assertTrue(ringsContinuously(Importance.CRITICAL, ringCritical = true, silent = false, onlyAlertOnce = false))
        Importance.entries.filter { it != Importance.CRITICAL }.forEach { importance ->
            assertFalse(importance.name, ringsContinuously(importance, ringCritical = true, silent = false, onlyAlertOnce = false))
        }
    }

    @Test
    fun refreshAndQuietCriticalPostsDoNotRing() {
        // Edits, restores after dismissal, and the ring stop all re-post without ringing.
        assertFalse(ringsContinuously(Importance.CRITICAL, ringCritical = false, silent = false, onlyAlertOnce = false))
        assertFalse(ringsContinuously(Importance.CRITICAL, ringCritical = true, silent = true, onlyAlertOnce = false))
        assertFalse(ringsContinuously(Importance.CRITICAL, ringCritical = true, silent = false, onlyAlertOnce = true))
    }

    @Test
    fun criticalAlertsOutsideItsOwnRingSoundLikeHigh() {
        // Restores after a swipe, saves of an overdue note, and posts from the editor.
        assertEquals(Importance.HIGH, channelImportance(Importance.CRITICAL, ringCritical = false, silent = false))
    }

    @Test
    fun criticalRingsAndQuietPostsStayOnTheCriticalChannel() {
        assertEquals(Importance.CRITICAL, channelImportance(Importance.CRITICAL, ringCritical = true, silent = false))
        // Ring stops and the reboot restore.
        assertEquals(Importance.CRITICAL, channelImportance(Importance.CRITICAL, ringCritical = false, silent = true))
    }

    @Test
    fun otherImportancesAlwaysUseTheirOwnChannel() {
        Importance.entries.filter { it != Importance.CRITICAL }.forEach { importance ->
            listOf(true, false).forEach { ringCritical ->
                listOf(true, false).forEach { silent ->
                    assertEquals(importance.name, importance, channelImportance(importance, ringCritical, silent))
                }
            }
        }
    }

    @Test
    fun onlyNoRingSkipsTheAlarm() {
        CriticalRingDuration.entries.forEach { duration ->
            assertEquals(duration.name, duration != CriticalRingDuration.NO_RING, duration.ringsLikeAlarm)
        }
    }

    @Test
    fun everyRingEndsBeforeTheNextRepeat() {
        CriticalRingDuration.entries.mapNotNull { it.ringMillis }.forEach { ringMillis ->
            assertTrue(ringMillis in 1 until CRITICAL_REPEAT_INTERVAL_MILLIS)
        }
    }

    private fun note(
        reminderAt: Long,
        importance: Importance = Importance.CRITICAL,
        reminders: List<NoteReminder> = emptyList(),
    ) = NoteEntity(
        id = 1L,
        kind = NoteKind.NOTE,
        title = "Critical",
        body = "",
        colorIndex = 0,
        starred = false,
        trashed = false,
        createdAt = 0L,
        updatedAt = 0L,
        reminderAt = reminderAt,
        importance = importance,
        reminders = reminders,
    )
}
