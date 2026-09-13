package dev.bikram.remember.ui.edit

import androidx.lifecycle.SavedStateHandle
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.data.withSyncedPrimaryReminder
import dev.bikram.remember.ui.nav.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class EditorReminderPersistenceTest(
    private val kind: NoteKind,
) {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeRepositoryStore()
    private val originalReminders =
        listOf(
            NoteReminder(10_000L),
            NoteReminder(20_000L, RecurrenceRule(RecurrenceUnit.DAY), originalReminderAt = 15_000L),
        )

    @Test
    fun changing_only_a_secondary_reminder_is_saved() =
        runTest {
            val editor = existingEditor()
            advanceUntilIdle()
            val editedReminders = originalReminders.mapIndexed { index, reminder -> if (index == 1) reminder.copy(reminderAt = 30_000L) else reminder }

            editor.setReminders(editedReminders)
            assertNotNull(editor.saveIfNeeded("Untitled"))

            assertEquals(editedReminders, store.notes.getValue(1L).reminders)
        }

    @Test
    fun unrelated_database_updates_preserve_unsaved_reminder_edits() =
        runTest {
            val editor = existingEditor()
            advanceUntilIdle()
            val editedReminders = listOf(NoteReminder(30_000L))
            editor.setReminders(editedReminders)

            store.repository().setPinned(1L, true)
            advanceUntilIdle()

            assertTrue(editor.pinned.value)
            assertEquals(editedReminders, editor.reminders.value)
            editor.saveIfNeeded("Untitled")
            assertEquals(editedReminders, store.notes.getValue(1L).reminders)
        }

    @Test
    fun undo_restores_all_reminder_slots_recurrence_and_snooze_anchor() =
        runTest {
            val editor = existingEditor()
            advanceUntilIdle()
            store.afterNoteUpdate = { yield() }
            editor.setReminders(listOf(NoteReminder(40_000L)))

            val undo = editor.saveIfNeeded("Untitled")
            assertNotNull(undo)
            undo?.invoke()

            assertEquals(originalReminders, store.notes.getValue(1L).reminders)
        }

    @Test
    fun reminder_edits_during_a_save_remain_pending() =
        runTest {
            val editor = existingEditor()
            advanceUntilIdle()
            editor.setReminders(listOf(NoteReminder(30_000L)))
            val newerReminders = listOf(NoteReminder(40_000L))
            store.afterNoteUpdate = {
                store.afterNoteUpdate = {}
                editor.setReminders(newerReminders)
                yield()
            }

            editor.saveIfNeeded("Untitled")
            advanceUntilIdle()

            assertTrue(editor.hasUnsavedChanges.value)
            assertEquals(newerReminders, editor.reminders.value)
            editor.saveIfNeeded("Untitled")
            assertEquals(newerReminders, store.notes.getValue(1L).reminders)
        }

    @Test
    fun external_snooze_still_updates_an_unedited_reminder() =
        runTest {
            val editor = existingEditor()
            advanceUntilIdle()

            store.repository().snoozeSoonestReminder(1L, 30_000L)
            advanceUntilIdle()

            assertEquals(store.notes.getValue(1L).reminders, editor.reminders.value)
        }

    private fun existingEditor(): BaseEditorViewModel {
        store.notes[1L] =
            NoteEntity(
                id = 1L,
                kind = kind,
                title = "Reminder",
                body = "",
                colorIndex = 0,
                starred = false,
                trashed = false,
                createdAt = 1L,
                updatedAt = 1L,
                reminders = originalReminders,
            ).withSyncedPrimaryReminder()
        val savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_ID to 1L))
        return when (kind) {
            NoteKind.NOTE -> EditNoteViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
            NoteKind.LIST -> EditListViewModel(repository = store.repository(), savedStateHandle = savedStateHandle)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun kinds(): List<Array<NoteKind>> = NoteKind.entries.map { kind -> arrayOf(kind) }
    }
}
