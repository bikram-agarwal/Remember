package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bikram.remember.RememberApp
import dev.bikram.remember.quickcapture.QuickCaptureNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        val scheduler = app.container.reminderScheduler
        val now = System.currentTimeMillis()
        app.container.applicationScope.launch {
            try {
                val keepUntilDone = app.container.reminderPrefs
                    .snapshot()
                    .keepReminderNotificationsUntilDone
                val all = app.container.noteRepository.observeActive().first()
                all.forEach { item ->
                    val at = item.note.reminderAt ?: return@forEach
                    if (at > now) {
                        scheduler.schedule(item.note.id, at)
                    } else if (item.note.completedAt == null) {
                        ReminderReceiver.showNotification(
                            context = context,
                            note = item.note,
                            items = item.items,
                            keepUntilDone = keepUntilDone,
                        )
                    }
                }
                app.container.noteRepository.refreshReminderSummaryNotification()
                // Re-post the quick-capture notification if the user has it enabled. The
                // notification is cleared by the OS on reboot, and the flow-based observer
                // may not fire quickly enough before the broadcast's pending-result window
                // closes, so we read the snapshot and post synchronously here.
                if (app.container.quickCapturePrefs.snapshot().enabled) {
                    QuickCaptureNotifier.show(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
