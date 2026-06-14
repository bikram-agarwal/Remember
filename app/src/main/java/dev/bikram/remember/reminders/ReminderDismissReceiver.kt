package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.getActiveReminders
import dev.bikram.remember.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return
        val reminderIndex = intent.getIntExtra(EXTRA_REMINDER_INDEX, 0)

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val keepUntilDone =
                    reminderPrefs
                        .snapshot()
                        .keepReminderNotificationsUntilDone
                if (!keepUntilDone) return@launch

                val noteWithItems = noteRepository.get(noteId) ?: return@launch
                val note = noteWithItems.note
                if (shouldRepostDismissedReminder(note, reminderIndex, System.currentTimeMillis())) {
                    ReminderReceiver.showNotification(
                        context = context,
                        note = note,
                        items = noteWithItems.items,
                        reminderIndex = reminderIndex,
                        keepUntilDone = true,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISSED = "dev.bikram.remember.reminders.REMINDER_DISMISSED"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_REMINDER_INDEX = "reminder_index"
    }
}

internal fun shouldRepostDismissedReminder(
    note: NoteEntity,
    reminderIndex: Int,
    now: Long,
): Boolean {
    val reminderAt = note.getActiveReminders().getOrNull(reminderIndex)?.reminderAt ?: return false
    return !note.trashed &&
        !note.archived &&
        note.completedAt == null &&
        reminderAt <= now
}
