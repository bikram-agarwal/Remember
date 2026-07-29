package dev.bikram.remember.data

import dev.bikram.remember.ui.edit.toDraft
import dev.bikram.remember.ui.edit.toReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NoteRepositoryRecurrenceTest {
    @Test
    fun `snooze persists new time and original recurring schedule`() =
        runBlocking {
            val originalReminderAt = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
            val snoozedUntil = calendarMillis(2026, Calendar.APRIL, 26, 17, 0)
            val recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Daily task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = originalReminderAt,
                        updatedAt = originalReminderAt,
                        reminderAt = originalReminderAt,
                        recurrence = recurrence,
                        reminders =
                            listOf(
                                NoteReminder(
                                    reminderAt = originalReminderAt,
                                    recurrence = recurrence,
                                ),
                            ),
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { originalReminderAt + 1_000L },
                )

            val snoozeSucceeded = repository.snoozeSoonestReminder(1L, snoozedUntil)

            assertTrue(snoozeSucceeded)
            val updatedReminder =
                noteDao.stored.note.reminders
                    .single()
            assertEquals(snoozedUntil, updatedReminder.reminderAt)
            assertEquals(originalReminderAt, updatedReminder.originalReminderAt)
            assertEquals(recurrence, updatedReminder.recurrence)
        }

    @Test
    fun `mark completed advances recurring reminder and keeps note active`() =
        runBlocking {
            val reminderAt = calendarMillis(2026, Calendar.APRIL, 26, 8, 0)
            val expectedNextReminder = calendarMillis(2026, Calendar.APRIL, 27, 8, 0)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Daily task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = reminderAt,
                        updatedAt = reminderAt,
                        reminderAt = reminderAt,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { reminderAt + 1_000L },
                )

            repository.markCompleted(1L)

            val updated = noteDao.stored.note
            assertEquals(expectedNextReminder, updated.reminderAt)
            assertNotNull(updated.recurrence)
            assertNull(updated.completedAt)
        }

    @Test
    fun `mark completed advances hourly recurring reminder`() =
        runBlocking {
            val reminderAt = calendarMillis(2026, Calendar.APRIL, 26, 8, 0)
            val expectedNextReminder = calendarMillis(2026, Calendar.APRIL, 26, 11, 0)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Hourly task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = reminderAt,
                        updatedAt = reminderAt,
                        reminderAt = reminderAt,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.HOUR, interval = 3),
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { reminderAt + 1_000L },
                )

            repository.markCompleted(1L)

            val updated = noteDao.stored.note
            assertEquals(expectedNextReminder, updated.reminderAt)
            assertNotNull(updated.recurrence)
            assertNull(updated.completedAt)
        }

    @Test
    fun `mark completed stamps completedAt for one shot reminder`() =
        runBlocking {
            val reminderAt = calendarMillis(2026, Calendar.APRIL, 26, 8, 0)
            val completedAt = reminderAt + 1_000L
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "One shot task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = reminderAt,
                        updatedAt = reminderAt,
                        reminderAt = reminderAt,
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { completedAt },
                )

            repository.markCompleted(1L)

            assertEquals(completedAt, noteDao.stored.note.completedAt)
        }

    @Test
    fun `updating completed note with reminder restores it to active`() =
        runBlocking {
            val completedAt = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
            val newReminderAt = calendarMillis(2026, Calendar.APRIL, 27, 8, 0)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Done task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = completedAt,
                        updatedAt = completedAt,
                        reminderAt = null,
                        completedAt = completedAt,
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { completedAt + 1_000L },
                )

            repository.updateNote(
                id = 1L,
                title = "Done task",
                body = "",
                colorIndex = 0,
                options =
                    NoteOptions(
                        reminderAt = newReminderAt,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                    ),
            )

            assertNull(noteDao.stored.note.completedAt)
            assertEquals(newReminderAt, noteDao.stored.note.reminderAt)
            assertNotNull(noteDao.stored.note.recurrence)
        }

    @Test
    fun `updating completed list with reminder restores it to active`() =
        runBlocking {
            val completedAt = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
            val newReminderAt = calendarMillis(2026, Calendar.APRIL, 27, 8, 0)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.LIST,
                        title = "Done list",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = completedAt,
                        updatedAt = completedAt,
                        reminderAt = null,
                        completedAt = completedAt,
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { completedAt + 1_000L },
                )

            repository.updateList(
                id = 1L,
                title = "Done list",
                colorIndex = 0,
                items = emptyList(),
                options =
                    NoteOptions(
                        reminderAt = newReminderAt,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                    ),
            )

            assertNull(noteDao.stored.note.completedAt)
            assertEquals(newReminderAt, noteDao.stored.note.reminderAt)
            assertNotNull(noteDao.stored.note.recurrence)
        }

    @Test
    fun `mark incomplete with completion snapshot restores multi reminders`() =
        runBlocking {
            val completedAt = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
            val firstReminder = calendarMillis(2026, Calendar.APRIL, 27, 8, 0)
            val secondReminder = calendarMillis(2026, Calendar.APRIL, 28, 17, 30)
            val reminders =
                listOf(
                    NoteReminder(firstReminder, RecurrenceRule(unit = RecurrenceUnit.DAY)),
                    NoteReminder(secondReminder),
                )
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Done task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = completedAt,
                        updatedAt = completedAt,
                        completedAt = completedAt,
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { completedAt + 1_000L },
                )

            repository.markIncomplete(
                noteId = 1L,
                snapshot =
                    NoteCompletionSnapshot(
                        reminderAt = firstReminder,
                        recurrence = reminders.first().recurrence,
                        reminders = reminders,
                    ),
            )

            val restored = noteDao.stored.note
            assertNull(restored.completedAt)
            assertEquals(firstReminder, restored.reminderAt)
            assertEquals(reminders.first().recurrence, restored.recurrence)
            assertEquals(reminders, restored.reminders)
        }

    @Test
    fun `resolve updated reminders caps provided reminders`() {
        val reminders =
            listOf(
                NoteReminder(1_000L),
                NoteReminder(2_000L),
                NoteReminder(3_000L),
                NoteReminder(4_000L),
            )
        val repository =
            NoteRepository(
                noteDao = FakeNoteDao(baseNote()),
                itemDao = FakeChecklistItemDao(),
                attachmentDao = FakeAttachmentDao(),
            )

        val resolved = repository.resolveUpdatedReminders(null, NoteOptions(reminders = reminders))

        assertEquals(reminders.take(MAX_REMINDERS_PER_NOTE), resolved)
    }

    @Test
    fun `import note with children caps reminders before insert`() =
        runBlocking {
            val reminders =
                listOf(
                    NoteReminder(1_000L),
                    NoteReminder(2_000L),
                    NoteReminder(3_000L),
                    NoteReminder(4_000L),
                )
            val noteDao = FakeNoteDao(baseNote())
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                )

            repository.importNoteWithChildren(
                note = baseNote().copy(reminders = reminders),
                items = emptyList(),
                attachments = emptyList(),
                suppressReminderSchedule = true,
            )

            assertEquals(reminders.take(MAX_REMINDERS_PER_NOTE), noteDao.stored.note.reminders)
        }

    @Test
    fun `mark completed on a snoozed recurring reminder advances from original time`() =
        runBlocking {
            val originalReminderAt = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
            val snoozedReminderAt = calendarMillis(2026, Calendar.APRIL, 26, 17, 0)
            val expectedNextReminder = calendarMillis(2026, Calendar.APRIL, 27, 9, 0)
            val noteDao =
                FakeNoteDao(
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Daily task",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = originalReminderAt,
                        updatedAt = originalReminderAt,
                        reminderAt = snoozedReminderAt,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                        reminders =
                            listOf(
                                NoteReminder(
                                    reminderAt = snoozedReminderAt,
                                    recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY),
                                    originalReminderAt = originalReminderAt,
                                ),
                            ),
                    ),
                )
            val repository =
                NoteRepository(
                    noteDao = noteDao,
                    itemDao = FakeChecklistItemDao(),
                    attachmentDao = FakeAttachmentDao(),
                    clock = { snoozedReminderAt + 1_000L },
                )

            repository.markCompleted(1L)

            val updated = noteDao.stored.note
            assertEquals(expectedNextReminder, updated.reminderAt)
            assertNotNull(updated.recurrence)
            assertNull(updated.completedAt)
            val nextReminder = updated.reminders.firstOrNull()
            assertNotNull(nextReminder)
            assertNull(nextReminder?.originalReminderAt)
            assertEquals(expectedNextReminder, nextReminder?.reminderAt)
        }

    @Test
    fun `toDraft uses originalReminderAt for date and time fields when snoozed`() {
        val originalTime = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
        val snoozedTime = calendarMillis(2026, Calendar.APRIL, 26, 17, 0)
        val recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY)
        val reminder =
            NoteReminder(
                reminderAt = snoozedTime,
                recurrence = recurrence,
                originalReminderAt = originalTime,
            )

        val draft = reminder.toDraft()

        val cal = Calendar.getInstance().apply { timeInMillis = originalTime }
        assertEquals(cal.get(Calendar.HOUR_OF_DAY), draft.reminderHour)
        assertEquals(cal.get(Calendar.MINUTE), draft.reminderMinute)
        assertEquals(originalTime, draft.originalReminderAt)
        assertEquals(snoozedTime, draft.snoozedUntil)
        assertEquals(recurrence, draft.originalRecurrence)
    }

    @Test
    fun `toReminder preserves snooze when picker schedule has not changed`() {
        val originalTime = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
        val snoozedTime = calendarMillis(2026, Calendar.APRIL, 26, 17, 0)
        val recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY)
        val reminder =
            NoteReminder(
                reminderAt = snoozedTime,
                recurrence = recurrence,
                originalReminderAt = originalTime,
            )

        val draft = reminder.toDraft()
        val restored = draft.toReminder()

        assertEquals(snoozedTime, restored.reminderAt)
        assertEquals(originalTime, restored.originalReminderAt)
        assertEquals(recurrence, restored.recurrence)
    }

    @Test
    fun `toReminder cancels snooze when picker schedule has changed`() {
        val originalTime = calendarMillis(2026, Calendar.APRIL, 26, 9, 0)
        val snoozedTime = calendarMillis(2026, Calendar.APRIL, 26, 17, 0)
        val recurrence = RecurrenceRule(unit = RecurrenceUnit.DAY)
        val reminder =
            NoteReminder(
                reminderAt = snoozedTime,
                recurrence = recurrence,
                originalReminderAt = originalTime,
            )

        val draft = reminder.toDraft().copy(reminderHour = 10, reminderMinute = 0)
        val restored = draft.toReminder()

        val expectedNewTime = calendarMillis(2026, Calendar.APRIL, 26, 10, 0)
        assertEquals(expectedNewTime, restored.reminderAt)
        assertNull(restored.originalReminderAt)
        assertEquals(recurrence, restored.recurrence)
    }

    private fun calendarMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    private fun baseNote(): NoteEntity =
        NoteEntity(
            id = 1L,
            kind = NoteKind.NOTE,
            title = "Task",
            body = "",
            colorIndex = 0,
            starred = false,
            trashed = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

    private class FakeNoteDao(
        initialNote: NoteEntity,
    ) : NoteDao {
        var stored = NoteWithItems(initialNote, emptyList())

        override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(listOf(stored))

        override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())

        override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())

        override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(stored.takeIf { it.note.id == id })

        override suspend fun get(id: Long): NoteWithItems? = stored.takeIf { it.note.id == id }

        override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> {
            val reminderAt = stored.note.reminderAt ?: return emptyList()
            return if (
                !stored.note.trashed &&
                !stored.note.archived &&
                stored.note.completedAt == null &&
                reminderAt <= untilMillis
            ) {
                listOf(stored)
            } else {
                emptyList()
            }
        }

        override suspend fun activeStarred(): List<NoteWithItems> =
            if (
                !stored.note.trashed &&
                !stored.note.archived &&
                stored.note.starred &&
                stored.note.completedAt == null
            ) {
                listOf(stored)
            } else {
                emptyList()
            }

        override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

        override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

        override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

        override suspend fun insert(note: NoteEntity): Long {
            stored = NoteWithItems(note, emptyList())
            return note.id
        }

        override suspend fun update(note: NoteEntity) {
            stored = stored.copy(note = note)
        }

        override suspend fun setStarred(
            id: Long,
            starred: Boolean,
            updatedAt: Long,
        ) = Unit

        override suspend fun updateTagCache(
            id: Long,
            tags: List<String>,
        ) = Unit

        override suspend fun setTrashed(
            id: Long,
            trashed: Boolean,
            updatedAt: Long,
        ) = Unit

        override suspend fun setArchived(
            id: Long,
            archived: Boolean,
            updatedAt: Long,
        ) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun allNoteIds(): List<Long> = listOf(stored.note.id)

        override suspend fun countPictureUri(uri: String): Int = 0

        override suspend fun trashedNoteIds(): List<Long> = emptyList()

        override suspend fun archivedNoteIds(): List<Long> = emptyList()

        override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

        override suspend fun deleteAllNotes() = Unit

        override suspend fun emptyTrash() = Unit

        override suspend fun deleteTrashedOlderThan(cutoff: Long) = Unit
    }

    private class FakeChecklistItemDao : ChecklistItemDao {
        override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = emptyList()

        override suspend fun insert(item: ChecklistItemEntity): Long = item.id

        override suspend fun insertAll(items: List<ChecklistItemEntity>) = Unit

        override suspend fun update(item: ChecklistItemEntity) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteForNote(noteId: Long) = Unit
    }

    private class FakeAttachmentDao : AttachmentDao {
        override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = emptyList()

        override suspend fun insert(attachment: NoteAttachmentEntity): Long = attachment.id

        override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) = Unit

        override suspend fun getById(id: Long): NoteAttachmentEntity? = null

        override suspend fun countByUri(uri: String): Int = 0

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteForNote(noteId: Long) = Unit
    }
}
