package dev.bikram.remember.reminders

import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.Visibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteRefreshObserverTest {
    @Test
    fun startup_refreshes_once_and_shares_each_source_subscription() =
        runTest {
            val fixture = RefreshFixture(listOf(note(id = 1L, reminderAt = 1_000L)))
            val observer = fixture.start(this)
            runCurrent()

            assertEquals(1, fixture.widgetRefreshes)
            assertEquals(1, fixture.summaryRefreshes)
            assertEquals(1, fixture.activeNotificationRefreshes)
            assertEquals(1, fixture.notes.subscriptionCount.value)
            assertEquals(1, fixture.preferences.subscriptionCount.value)

            // Room can invalidate a query even when its result is unchanged.
            fixture.notes.emit(
                fixture.notes.replayCache
                    .single()
                    .toList(),
            )
            fixture.preferences.emit(
                fixture.preferences.replayCache
                    .single()
                    .copy(),
            )
            runCurrent()
            assertEquals(1, fixture.widgetRefreshes)
            assertEquals(1, fixture.summaryRefreshes)
            assertEquals(1, fixture.activeNotificationRefreshes)

            observer.cancel()
            runCurrent()
            assertEquals(0, fixture.notes.subscriptionCount.value)
            assertEquals(0, fixture.preferences.subscriptionCount.value)
        }

    @Test
    fun pin_star_tag_and_checklist_changes_refresh_widgets_without_rebuilding_summary() =
        runTest {
            val reminder = note(id = 1L, reminderAt = 1_000L)
            val plainNote = note(id = 2L)
            val fixture = RefreshFixture(listOf(reminder, plainNote))
            fixture.start(this)
            runCurrent()

            val updatedReminder =
                reminder.copy(
                    note =
                        reminder.note.copy(
                            pinnedAt = 2_000L,
                            starred = true,
                            tags = listOf("Renamed tag"),
                            body = "Edited body",
                            updatedAt = 2_000L,
                        ),
                    items =
                        listOf(
                            ChecklistItemEntity(
                                id = 1L,
                                noteId = 1L,
                                text = "Checked item",
                                checked = true,
                                sortOrder = 0.0,
                            ),
                        ),
                )
            // updatedAt can also reorder observeActive's result; summary identity ignores it.
            fixture.notes.emit(listOf(plainNote, updatedReminder))
            runCurrent()

            assertEquals(2, fixture.widgetRefreshes)
            assertEquals(1, fixture.summaryRefreshes)
            assertEquals(1, fixture.activeNotificationRefreshes)
        }

    @Test
    fun reminder_content_completion_restore_and_removal_each_refresh_summary() =
        runTest {
            val original = note(id = 1L, reminderAt = 1_000L)
            val fixture = RefreshFixture(listOf(original))
            fixture.start(this)
            runCurrent()
            val edited = original.note.copy(title = "New title")
            val rescheduled = edited.copy(reminderAt = 2_000L)
            val privateReminder = rescheduled.copy(visibility = Visibility.SECRET)
            val withIcon = privateReminder.copy(iconKey = "emoji:calendar")
            val completed = withIcon.copy(completedAt = 3_000L)
            val snapshots =
                listOf(
                    listOf(original.copy(note = edited)),
                    listOf(original.copy(note = rescheduled)),
                    listOf(original.copy(note = privateReminder)),
                    listOf(original.copy(note = withIcon)),
                    listOf(original.copy(note = completed)),
                    listOf(original),
                    emptyList(), // Archive, trash, or deletion removes the active note.
                    listOf(original), // Restore or import brings it back.
                )

            snapshots.forEachIndexed { index, notes ->
                fixture.notes.emit(notes)
                runCurrent()
                assertEquals(index + 2, fixture.widgetRefreshes)
                assertEquals(index + 2, fixture.summaryRefreshes)
            }
        }

    @Test
    fun preference_changes_refresh_only_the_notification_type_they_control() =
        runTest {
            val fixture = RefreshFixture(listOf(note(id = 1L, reminderAt = 1_000L)))
            fixture.start(this)
            runCurrent()

            fixture.preferences.emit(
                ReminderPreferencesState(
                    keepReminderNotificationsUntilDone = true,
                    reminderSummaryNotificationEnabled = true,
                ),
            )
            runCurrent()
            assertEquals(2, fixture.activeNotificationRefreshes)
            assertEquals(1, fixture.summaryRefreshes)

            fixture.preferences.emit(ReminderPreferencesState(keepReminderNotificationsUntilDone = true))
            runCurrent()
            assertEquals(2, fixture.activeNotificationRefreshes)
            assertEquals(2, fixture.summaryRefreshes)
            assertEquals(1, fixture.widgetRefreshes)
        }

    @Test
    fun disabled_summary_ignores_note_changes_and_refreshes_when_enabled() =
        runTest {
            val fixture = RefreshFixture(listOf(note(id = 1L)), summaryEnabled = false)
            fixture.start(this)
            runCurrent()
            assertEquals(1, fixture.summaryRefreshes) // Clear any notification left by an earlier process.

            fixture.notes.emit(listOf(note(id = 2L, reminderAt = 1_000L)))
            runCurrent()
            assertEquals(2, fixture.widgetRefreshes)
            assertEquals(1, fixture.summaryRefreshes)

            fixture.preferences.emit(ReminderPreferencesState(reminderSummaryNotificationEnabled = true))
            runCurrent()
            assertEquals(2, fixture.summaryRefreshes)
            assertEquals(1, fixture.activeNotificationRefreshes)
        }

    @Test
    fun latest_database_change_is_retained_while_widget_refresh_is_busy() =
        runTest {
            val fixture = RefreshFixture(emptyList())
            val refreshGate = CompletableDeferred<Unit>()
            fixture.widgetRefreshGate = refreshGate
            fixture.start(this)
            runCurrent()
            assertEquals(1, fixture.widgetRefreshes)

            repeat(3) { index ->
                fixture.notes.emit(listOf(note(id = index.toLong() + 1, reminderAt = 1_000L)))
                runCurrent()
            }
            assertEquals(1, fixture.widgetRefreshes)
            assertEquals(4, fixture.summaryRefreshes)

            refreshGate.complete(Unit)
            runCurrent()
            assertEquals(2, fixture.widgetRefreshes)
            assertEquals(listOf(3L), fixture.lastWidgetNoteIds)
        }

    private class RefreshFixture(
        initialNotes: List<NoteWithItems>,
        summaryEnabled: Boolean = true,
    ) {
        val notes = MutableSharedFlow<List<NoteWithItems>>(replay = 1)
        val preferences = MutableSharedFlow<ReminderPreferencesState>(replay = 1)
        var widgetRefreshes = 0
        var summaryRefreshes = 0
        var activeNotificationRefreshes = 0
        var widgetRefreshGate: CompletableDeferred<Unit>? = null
        var lastWidgetNoteIds = emptyList<Long>()

        init {
            assertTrue(notes.tryEmit(initialNotes))
            assertTrue(preferences.tryEmit(ReminderPreferencesState(reminderSummaryNotificationEnabled = summaryEnabled)))
        }

        @Suppress("ktlint:standard:function-expression-body")
        fun start(scope: TestScope): Job {
            return scope.backgroundScope.observeNoteRefreshes(
                notesSource = notes,
                reminderPreferences = preferences,
                refreshWidgets = {
                    widgetRefreshes++
                    widgetRefreshGate?.await()
                    lastWidgetNoteIds = notes.replayCache.single().map { it.note.id }
                },
                refreshSummary = { summaryRefreshes++ },
                refreshActiveNotifications = { activeNotificationRefreshes++ },
                computationDispatcher = StandardTestDispatcher(scope.testScheduler),
            )
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun note(
        id: Long,
        reminderAt: Long? = null,
    ): NoteWithItems {
        return NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = "Note $id",
                    body = "",
                    colorIndex = 0,
                    starred = false,
                    trashed = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    reminderAt = reminderAt,
                ),
            items = emptyList(),
        )
    }
}
