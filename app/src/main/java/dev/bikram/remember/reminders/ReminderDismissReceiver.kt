package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ReminderPrefs
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
                val reminderAt = note.reminderAt ?: return@launch
                val unresolved =
                    !note.trashed &&
                        !note.archived &&
                        note.completedAt == null &&
                        reminderAt <= System.currentTimeMillis()
                if (unresolved) {
                    ReminderReceiver.showNotification(
                        context = context,
                        note = note,
                        items = noteWithItems.items,
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
    }
}
