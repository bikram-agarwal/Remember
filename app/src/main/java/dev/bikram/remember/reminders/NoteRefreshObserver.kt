package dev.bikram.remember.reminders

import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.Visibility
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Owns automatic widget and reminder refreshes for committed note data and reminder settings.
 * Observing Room also covers tag edits and imports, without a second post-write request.
 * Alarm scheduling and the explicit boot/time-change refresh remain with their callers.
 */
@Suppress("ktlint:standard:function-expression-body")
internal fun CoroutineScope.observeNoteRefreshes(
    notesSource: Flow<List<NoteWithItems>>,
    reminderPreferences: Flow<ReminderPreferencesState>,
    refreshWidgets: suspend () -> Unit,
    refreshSummary: suspend () -> Unit,
    refreshActiveNotifications: suspend () -> Unit,
    computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
): Job {
    return launch {
        supervisorScope {
            val notes =
                notesSource
                    .distinctUntilChanged()
                    .flowOn(computationDispatcher)
                    .shareIn(this, SharingStarted.Lazily, replay = 1)
            val preferences =
                reminderPreferences
                    .distinctUntilChanged()
                    .shareIn(this, SharingStarted.Lazily, replay = 1)

            launch {
                // Include the first snapshot: a write may precede observer startup.
                // Keep the latest change if a widget update is already in progress.
                notes.conflate().collect { refreshWidgets() }
            }
            launch {
                val summaryNotes =
                    notes
                        .map { rows ->
                            buildMap {
                                for (row in rows) {
                                    val note = row.note
                                    val reminderAt = note.reminderAt ?: continue
                                    if (note.trashed || note.archived || note.completedAt != null) continue
                                    put(
                                        note.id,
                                        SummaryNote(
                                            title = note.title,
                                            reminderAt = reminderAt,
                                            visibility = note.visibility,
                                            iconKey = note.iconKey,
                                        ),
                                    )
                                }
                            }
                        }.distinctUntilChanged()
                        .flowOn(computationDispatcher)
                combine(
                    summaryNotes,
                    preferences.map { it.reminderSummaryNotificationEnabled }.distinctUntilChanged(),
                ) { reminders, enabled ->
                    SummaryRefreshState(enabled, if (enabled) reminders else emptyMap())
                }.distinctUntilChanged()
                    .conflate()
                    .collect { refreshSummary() }
            }
            launch {
                preferences
                    .map { it.keepReminderNotificationsUntilDone }
                    .distinctUntilChanged()
                    .collect { refreshActiveNotifications() }
            }
        }
    }
}

// Match the fields used by ReminderScheduler's summary. Map equality ignores note ordering,
// so pinning, starring, checklist edits, and updatedAt changes do not rebuild the summary.
private data class SummaryNote(
    val title: String,
    val reminderAt: Long,
    val visibility: Visibility,
    val iconKey: String?,
)

private data class SummaryRefreshState(
    val enabled: Boolean,
    val notes: Map<Long, SummaryNote>,
)
