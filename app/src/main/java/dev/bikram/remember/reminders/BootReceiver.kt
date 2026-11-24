package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.quickcapture.QuickCaptureNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminderScheduler: ReminderScheduler

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
        applicationScope.launch {
            try {
                val reminderNotes = noteRepository.activeReminderNotes()
                reminderNotes.forEach { noteWithItems ->
                    reminderScheduler.scheduleOrShow(
                        note = noteWithItems.note,
                        items = noteWithItems.items,
                        silentDueNotification = true,
                    )
                }
                restorePinnedNotifications(context)
                noteRepository.refreshReminderSummaryNotification()
                // Alarms do not survive a reboot, and a backwards clock change can leave the
                // pending repeat far out; reconciling re-books it within one interval.
                noteRepository.refreshCriticalReminderRepeat()
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

    /**
     * A reboot clears every notification, but a manual pin lasts until the user clears it, so
     * pinned notes come back (silently) even without a due reminder. Notes the reminder pass
     * above already re-posted are left alone, which also keeps time-change broadcasts from
     * re-posting a pinned notification that is still showing.
     */
    private suspend fun restorePinnedNotifications(context: Context) {
        val now = System.currentTimeMillis()
        ManualPinnedNotificationStore.pinnedNoteIds(context).forEach { noteId ->
            if (reminderScheduler.isNotificationActive(noteId)) return@forEach
            val noteWithItems = noteRepository.get(noteId)
            if (noteWithItems == null || !pinSurvivesReboot(noteWithItems.note)) {
                ManualPinnedNotificationStore.setPinned(context, noteId, pinned = false)
                return@forEach
            }
            ReminderReceiver.showNotification(
                context = context,
                note = noteWithItems.note,
                items = noteWithItems.items,
                reminderIndex = latestDueReminderIndex(noteWithItems.note, now) ?: 0,
                keepUntilDone = true,
                onlyAlertOnce = true,
                silent = true,
            )
        }
    }
}
