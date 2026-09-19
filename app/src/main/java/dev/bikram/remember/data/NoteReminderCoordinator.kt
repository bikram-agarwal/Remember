package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.reminders.ReminderScheduler

/**
 * Everything a note's reminders need once the note itself is stored: resolving which reminder
 * slots a save should end up with, keeping the scheduler's alarms and notifications in step with
 * the stored rows, and the mark-done / mark-not-done transitions that advance or consume a
 * recurrence rule. Split out of [NoteRepository] so reminder rules live in one place rather than
 * being threaded through every write path.
 */
internal class NoteReminderCoordinator(
    private val noteDao: NoteDao,
    private val scheduler: ReminderScheduler?,
    private val clock: () -> Long,
    private val database: RememberDatabase?,
) {
    fun resolveUpdatedReminders(
        existingNote: NoteEntity?,
        options: NoteOptions,
    ): List<NoteReminder> {
        if (options.reminders.isNotEmpty()) {
            return options.reminders.limitedToReminderSlots()
        }
        val optionPrimaryAt = options.reminderAt ?: return emptyList()
        val existingReminders = existingNote?.reminders?.limitedToReminderSlots() ?: emptyList()
        if (existingReminders.isNotEmpty()) {
            val soonest = existingReminders.minByOrNull { it.reminderAt }
            return existingReminders.map { reminder ->
                if (reminder == soonest) {
                    NoteReminder(optionPrimaryAt, options.recurrence)
                } else {
                    reminder
                }
            }
        }
        return listOf(NoteReminder(optionPrimaryAt, options.recurrence)).limitedToReminderSlots()
    }

    suspend fun rescheduleReminder(
        id: Long,
        at: Long?,
    ) {
        scheduler?.cancel(id)
        if (at != null) {
            val noteWithItems = noteDao.get(id)
            if (noteWithItems != null) {
                scheduler?.scheduleOrShow(noteWithItems.note, noteWithItems.items)
            }
        }
    }

    suspend fun refreshNotificationIfActive(id: Long) {
        val row = noteDao.get(id) ?: return
        scheduler?.refreshNotificationIfActive(row.note, row.items)
    }

    suspend fun refreshNotificationVisibilityPreview(
        id: Long,
        visibility: Visibility,
    ) {
        val row = noteDao.get(id) ?: return
        scheduler?.refreshNotificationIfActive(row.note.copy(visibility = visibility), row.items)
    }

    suspend fun refreshActiveReminderNotifications() {
        val schedulerNonNull = scheduler ?: return
        val reminders = noteDao.activeRemindersUntil(Long.MAX_VALUE)
        reminders.forEach { noteWithItems ->
            schedulerNonNull.refreshNotificationIfActive(noteWithItems.note, noteWithItems.items)
        }
    }

    suspend fun refreshReminderSummaryNotification() {
        val schedulerNonNull = scheduler ?: return
        val now = clock()
        val reminders = reminderSummaryItems(now)
        schedulerNonNull.refreshSummaryNotification(reminders, now)
    }

    suspend fun reminderSummaryItems(now: Long): List<NoteWithItems> = noteDao.activeRemindersUntil(now + REMINDER_SUMMARY_WINDOW_MILLIS)

    /**
     * Consume the currently due recurring occurrence after the user marks it done. Merely
     * firing the notification must not call this: until completion, the card should keep
     * its past [NoteEntity.reminderAt] and remain in Overdue.
     */
    private suspend fun advanceRecurringReminderAfterCompletion(id: Long) {
        val note = noteDao.get(id)?.note ?: return
        val rule = note.recurrence?.sanitized() ?: return
        val current = note.reminderAt ?: return
        val consumedRule = rule.afterFire()
        val stoppedByCount =
            consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
                consumedRule.endCount != null &&
                consumedRule.endCount <= 0
        val nextTime = if (stoppedByCount) null else consumedRule.nextAfter(current)
        val nextRule = if (stoppedByCount || nextTime == null) null else consumedRule
        noteDao.update(
            note.copy(
                reminderAt = nextTime,
                recurrence = nextRule,
                updatedAt = clock(),
            ),
        )
        if (nextTime != null) {
            val noteWithItems = noteDao.get(id)
            if (noteWithItems != null) {
                scheduler?.scheduleOrShow(noteWithItems.note, noteWithItems.items)
            }
        }
    }

    /**
     * Mark a note done. Behavior depends on whether the note has a live recurrence rule:
     *
     * - **Recurring** (rule still has occurrences): roll [reminderAt] forward via
     *   [advanceRecurringReminderAfterCompletion] and leave [completedAt] null. The note stays active and
     *   reappears in Today / Upcoming for the next occurrence. This is what the user means
     *   by "I completed this fire of the reminder, but the task itself isn't done."
     * - **Recurring but exhausted** (rule's end condition is consumed): the next-fire
     *   computation returns null, the note has no future, so set [completedAt] = now and
     *   route the note into the Done bucket.
     * - **Non-recurring**: set [completedAt] = now and cancel any pending alarm.
     *
     * Returns the pre-completion [NoteCompletionSnapshot] when the row was written
     * (or null when the note id no longer exists). The snackbar Undo path uses this
     * snapshot to restore the original reminderAt + recurrence -- including for
     * recurring rows whose rule was advanced or consumed in place. Captured from the
     * single [noteDao.get] this method already does, so undo support is free of any
     * extra DB read.
     */
    suspend fun markCompleted(noteId: Long): NoteCompletionSnapshot? {
        val existingWithItems = noteDao.get(noteId) ?: return null
        val existing = existingWithItems.note
        val activeReminders = existing.getActiveReminders()
        val snapshot =
            NoteCompletionSnapshot(
                reminderAt = existing.reminderAt,
                recurrence = existing.recurrence,
                reminders = existing.reminders,
            )

        if (activeReminders.isEmpty()) {
            noteDao.update(
                existing.copy(
                    completedAt = clock(),
                    updatedAt = clock(),
                ),
            )
            scheduler?.cancel(noteId)
            scheduler?.cancelNotification(noteId)
            return snapshot
        }

        val soonest = activeReminders.minByOrNull { it.reminderAt } ?: return snapshot
        val updatedReminders =
            activeReminders.mapNotNull { reminder ->
                if (reminder == soonest) {
                    val rule = reminder.recurrence?.sanitized()
                    if (rule != null) {
                        val current = reminder.originalReminderAt ?: reminder.reminderAt
                        val consumedRule = rule.afterFire()
                        val stoppedByCount =
                            consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
                                consumedRule.endCount != null &&
                                consumedRule.endCount <= 0
                        val nextTime = if (stoppedByCount) null else consumedRule.nextAfter(current)
                        val nextRule = if (stoppedByCount || nextTime == null) null else consumedRule
                        if (nextTime != null) {
                            NoteReminder(reminderAt = nextTime, recurrence = nextRule)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    reminder
                }
            }

        val nextNote =
            if (updatedReminders.isEmpty()) {
                existing.copy(
                    reminders = emptyList(),
                    reminderAt = null,
                    recurrence = null,
                    completedAt = clock(),
                    updatedAt = clock(),
                )
            } else {
                existing
                    .copy(
                        reminders = updatedReminders,
                        updatedAt = clock(),
                    ).withSyncedPrimaryReminder()
            }

        noteDao.update(nextNote)
        scheduler?.cancelNotification(noteId)
        if (nextNote.completedAt != null) {
            scheduler?.cancel(noteId)
        } else {
            scheduler?.scheduleOrShow(nextNote, existingWithItems.items)
        }
        return snapshot
    }

    suspend fun markIncomplete(
        noteId: Long,
        snapshot: NoteCompletionSnapshot?,
    ): Boolean {
        val existingWithItems = noteDao.get(noteId) ?: return false
        val existing = existingWithItems.note
        if (existing.completedAt == null && snapshot == null) return false
        val restoredNote = restoredIncompleteNote(existing, snapshot)
        noteDao.update(restoredNote)
        scheduler?.scheduleOrShow(restoredNote, existingWithItems.items)
        return true
    }

    suspend fun restoreCompletionStates(snapshots: Map<Long, NoteCompletionSnapshot>) {
        if (snapshots.isEmpty()) return
        val applyAll: suspend () -> Unit = {
            snapshots.forEach { (id, snapshot) ->
                val existingWithItems = noteDao.get(id) ?: return@forEach
                val existing = existingWithItems.note
                val restoredNote = restoredIncompleteNote(existing, snapshot)
                noteDao.update(restoredNote)
                scheduler?.cancel(id)
                scheduler?.scheduleOrShow(restoredNote, existingWithItems.items)
            }
        }
        if (database != null) database.withTransaction { applyAll() } else applyAll()
    }

    private fun restoredIncompleteNote(
        existing: NoteEntity,
        snapshot: NoteCompletionSnapshot?,
    ): NoteEntity {
        val restoredNote =
            existing.copy(
                completedAt = null,
                reminderAt = snapshot?.reminderAt ?: existing.reminderAt,
                recurrence = snapshot?.recurrence?.sanitized() ?: existing.recurrence,
                reminders = snapshot?.reminders ?: existing.reminders,
                updatedAt = clock(),
            )
        return restoredNote.withSyncedPrimaryReminder()
    }

    private companion object {
        const val REMINDER_SUMMARY_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}
