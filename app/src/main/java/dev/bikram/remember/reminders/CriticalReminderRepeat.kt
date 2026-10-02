package dev.bikram.remember.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * Critical reminders re-alert every [CRITICAL_REPEAT_INTERVAL_MILLIS] until they are done.
 *
 * One shared alarm serves every overdue Critical note: each tick re-alerts all of them at once
 * and books the next tick, so several overdue notes cost one wake-up per interval rather than
 * one each. The tick re-reads the database, so a note that stopped qualifying (done, snoozed,
 * importance lowered, archived, trashed) is simply skipped and the chain ends once none remain.
 * NoteRefreshObserver additionally reconciles on every change to the Critical notes, which
 * cancels the alarm straight away instead of leaving the alarm-clock icon up until the next tick.
 *
 * Each alert - the reminder's own fire and every tick - rings continuously (an insistent
 * notification on the alarm stream) for the duration set in Critical alert style. A second shared
 * alarm then re-posts the ringing notifications quietly, leaving them in the shade until the
 * next tick rings again. With the duration set not to ring, each alert plays High's tone once
 * instead and no ring stop is booked.
 */

const val CRITICAL_REPEAT_INTERVAL_MILLIS = 15L * 60L * 1000L

/** True while [note] has a due reminder that should keep re-alerting. */
internal fun needsCriticalRepeat(
    note: NoteEntity,
    now: Long,
): Boolean = note.importance == Importance.CRITICAL && latestDueReminderIndex(note, now) != null

/**
 * When the shared repeat should next fire. A pending tick (stored, still ahead, and no further
 * out than one interval) is kept, so a newly overdue note never postpones the alerts of notes
 * already repeating. Anything else - no tick, a missed tick, or one pushed too far out by a
 * backwards clock change - restarts the interval from [now].
 */
internal fun nextCriticalRepeatAt(
    storedAt: Long?,
    now: Long,
): Long {
    val latest = now + CRITICAL_REPEAT_INTERVAL_MILLIS
    return if (storedAt != null && storedAt in (now + 1)..latest) storedAt else latest
}

private val Context.criticalRepeatDataStore by preferencesDataStore(name = "critical_reminder_repeat")

/**
 * Remembers when the shared repeat alarm is due. AlarmManager offers no way to read back a
 * pending alarm's time, and without it every refresh would restart the interval - frequent
 * enough refreshes would postpone the repeat indefinitely.
 */
internal class CriticalRepeatStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.criticalRepeatDataStore)

    suspend fun nextAt(): Long? = dataStore.data.first()[NEXT_AT]

    suspend fun setNextAt(at: Long) {
        dataStore.edit { prefs -> prefs[NEXT_AT] = at }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(NEXT_AT) }
    }

    private companion object {
        val NEXT_AT = longPreferencesKey("next_at")
    }
}

/**
 * Handles both shared Critical alarms: the repeat tick, which rings every overdue Critical note
 * and books the next tick, and the ring stop, which quiets whatever is still ringing.
 */
@AndroidEntryPoint
class CriticalReminderRepeatReceiver : BroadcastReceiver() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != ReminderScheduler.ACTION_REPEAT_CRITICAL && action != ReminderScheduler.ACTION_STOP_CRITICAL_RING) return
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (action == ReminderScheduler.ACTION_REPEAT_CRITICAL) {
                    ringOverdueNotes(context, now)
                } else {
                    quietRingingNotifications(context, now)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun ringOverdueNotes(
        context: Context,
        now: Long,
    ) {
        val notes = noteRepository.criticalRepeatNotes(now)
        if (notes.isEmpty()) {
            reminderScheduler.cancelCriticalRepeat()
            return
        }
        val prefs = reminderPrefs.snapshot()
        notes.forEach { noteWithItems ->
            val note = noteWithItems.note
            ReminderReceiver.showNotification(
                context = context,
                note = note,
                items = noteWithItems.items,
                reminderIndex = latestDueReminderIndex(note, now) ?: 0,
                keepUntilDone = prefs.keepReminderNotificationsUntilDone,
                ringCritical = prefs.criticalRingDuration.ringsLikeAlarm,
            )
        }
        prefs.criticalRingDuration.ringMillis?.let { ringMillis ->
            reminderScheduler.scheduleCriticalRingStop(now + ringMillis)
        }
        reminderScheduler.scheduleNextCriticalRepeat(now)
    }

    /**
     * Re-posts every still-ringing notification silently, so it stays in the shade but stops
     * sounding. Works from the notifications themselves rather than from the Critical notes, so
     * a ring is ended even if its note changed since it started.
     */
    private suspend fun quietRingingNotifications(
        context: Context,
        now: Long,
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ringing =
            runCatching {
                notificationManager.activeNotifications.filter { active ->
                    active.notification.flags and Notification.FLAG_INSISTENT != 0
                }
            }.getOrDefault(emptyList())
        if (ringing.isEmpty()) return
        val keepUntilDone = reminderPrefs.snapshot().keepReminderNotificationsUntilDone
        ringing.forEach { active ->
            val noteId = active.notification.extras.getLong(ReminderReceiver.NOTIFICATION_EXTRA_NOTE_ID, -1L)
            val noteWithItems = noteId.takeIf { it > 0L }?.let { id -> noteRepository.get(id) }
            val reminderIndex = noteWithItems?.let { row -> latestDueReminderIndex(row.note, now) }
            if (noteWithItems == null || reminderIndex == null) {
                notificationManager.cancel(active.id)
                return@forEach
            }
            ReminderReceiver.showNotification(
                context = context,
                note = noteWithItems.note,
                items = noteWithItems.items,
                reminderIndex = reminderIndex,
                keepUntilDone = keepUntilDone,
                onlyAlertOnce = true,
                silent = true,
            )
        }
    }
}
