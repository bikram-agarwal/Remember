package dev.bikram.remember.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.labelRes
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return
        
        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        val repo = app.container.noteRepository
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        app.container.applicationScope.launch {
            try {
                val note = repo.get(noteId)?.note ?: return@launch
                if (note.trashed) return@launch

                val builder = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_note)
                    .setContentTitle(
                        note.title.ifBlank { context.getString(R.string.options_reminder) },
                    )
                    .setContentText(summary(context, note))
                    .setPriority(priorityFor(note.importance))
                    .setAutoCancel(true)
                    .setContentIntent(openNotePendingIntent(context, noteId))

                note.actions.forEachIndexed { idx, action ->
                    if (idx >= 3) return@forEachIndexed
                    builder.addAction(actionButton(context, noteId, idx, action))
                }
                nm.notify(ReminderScheduler.pendingRequestCodeForNote(noteId), builder.build())

                // Reschedule the next occurrence for recurring reminders. This mutates
                // note.reminderAt and (when the rule is exhausted) clears it.
                repo.advanceReminderOnFire(noteId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun summary(context: Context, note: NoteEntity): String =
        if (note.body.isNotBlank()) {
            note.body.take(120)
        } else {
            context.getString(R.string.reminder_notification_fallback)
        }

    private fun priorityFor(importance: Importance): Int = when (importance) {
        Importance.LOW -> NotificationCompat.PRIORITY_LOW
        Importance.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
    }

    private fun openNotePendingIntent(context: Context, noteId: Long): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_note_id", noteId)
        }
        return PendingIntent.getActivity(
            context,
            ReminderScheduler.pendingRequestCodeForNote(noteId),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionButton(
        context: Context,
        noteId: Long,
        index: Int,
        action: NoteAction,
    ): NotificationCompat.Action {
        val i = Intent(context, ActionReceiver::class.java).apply {
            this.action = ActionReceiver.ACTION_FIRE
            putExtra(ActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ActionReceiver.EXTRA_ACTION_INDEX, index)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ReminderScheduler.pendingRequestCodeForNoteAction(noteId, index),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionLabel = action.title.trim().ifBlank {
            if (action.type == ActionType.MARK_AS_DONE) {
                context.getString(R.string.action_type_mark_as_done)
            } else {
                context.getString(action.type.labelRes())
            }
        }
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_note,
            actionLabel,
            pi,
        ).build()
    }
}
