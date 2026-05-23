package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.quickcapture.QuickCaptureNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var quickCapturePrefs: QuickCapturePrefs

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val pendingResult = goAsync()
        val now = System.currentTimeMillis()
        applicationScope.launch {
            try {
                val keepUntilDone =
                    reminderPrefs
                        .snapshot()
                        .keepReminderNotificationsUntilDone
                val all = noteRepository.observeActive().first()
                all.forEach { item ->
                    val at = item.note.reminderAt ?: return@forEach
                    if (at > now) {
                        reminderScheduler.schedule(item.note.id, at, item.note.importance)
                    } else if (item.note.completedAt == null) {
                        ReminderReceiver.showNotification(
                            context = context,
                            note = item.note,
                            items = item.items,
                            keepUntilDone = keepUntilDone,
                        )
                    }
                }
                noteRepository.refreshReminderSummaryNotification()
                // Re-post the quick-capture notification if the user has it enabled. The
                // notification is cleared by the OS on reboot, and the flow-based observer
                // may not fire quickly enough before the broadcast's pending-result window
                // closes, so we read the snapshot and post synchronously here.
                if (quickCapturePrefs.snapshot().enabled) {
                    QuickCaptureNotifier.show(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
