package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bikram.remember.RememberApp
import kotlinx.coroutines.launch

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return

        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        app.container.applicationScope.launch {
            try {
                val keepUntilDone = app.container.reminderPrefs
                    .snapshot()
                    .keepReminderNotificationsUntilDone
                if (!keepUntilDone) return@launch

                val noteWithItems = app.container.noteRepository.get(noteId) ?: return@launch
                val note = noteWithItems.note
                val reminderAt = note.reminderAt ?: return@launch
                val unresolved = !note.trashed &&
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
