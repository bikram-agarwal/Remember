package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteEntity
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
                val isManualPinned = ManualPinnedNotificationStore.isPinned(context, noteId)
                val keepUntilDonePref =
                    reminderPrefs
                        .snapshot()
                        .keepReminderNotificationsUntilDone
                if (!isManualPinned && !keepUntilDonePref) return@launch

                val noteWithItems = noteRepository.get(noteId) ?: return@launch
                val note = noteWithItems.note
                val reminderIndex =
                    resolveDismissRestoreReminderIndex(
                        note = note,
                        nowMillis = System.currentTimeMillis(),
                        isManualPinned = isManualPinned,
                        keepUntilDonePref = keepUntilDonePref,
                    )
                if (reminderIndex == null) {
                    if (isManualPinned) {
                        ManualPinnedNotificationStore.setPinned(context, noteId, pinned = false)
                    }
                    return@launch
                }
                ReminderReceiver.showNotification(
                    context = context,
                    note = note,
                    items = noteWithItems.items,
                    reminderIndex = reminderIndex,
                    keepUntilDone = true,
                    onlyAlertOnce = true,
                )
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

internal fun resolveDismissRestoreReminderIndex(
    note: NoteEntity,
    nowMillis: Long,
    isManualPinned: Boolean,
    keepUntilDonePref: Boolean,
): Int? {
    if (note.trashed || note.completedAt != null) return null
    if (isManualPinned) {
        return latestDueReminderIndex(note, nowMillis) ?: 0
    }
    if (!keepUntilDonePref) return null
    return latestDueReminderIndex(note, nowMillis)
}
