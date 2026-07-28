package dev.bikram.remember.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.data.getActiveReminders
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.notifications.canPostNotifications
import dev.bikram.remember.notifications.postNotificationIfAllowed
import dev.bikram.remember.ui.edit.iconEmojiPayload
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

class ReminderScheduler(
    private val context: Context,
    private val reminderPrefs: ReminderPrefs? = null,
) {
    @SuppressLint("MissingPermission")
    fun schedule(
        noteId: Long,
        reminderIndex: Int,
        whenMillis: Long,
        importance: Importance = Importance.DEFAULT,
    ) {
        if (whenMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(noteId, reminderIndex)
        if (importance == Importance.HIGH) {
            // HIGH-importance reminders use setAlarmClock: it is exempt from Doze and
            // the per-app exact-alarm rate limit, needs no SCHEDULE_EXACT_ALARM
            // (playstore flavor) or USE_EXACT_ALARM (github) grant, and surfaces the
            // system's next-alarm indicator in the status bar. The show intent is what
            // fires when the user taps that indicator.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMillis, openAppPendingIntent()), pi)
            return
        }
        val canExact = am.canScheduleExactAlarms()
        if (canExact) {
            // Guarded by canScheduleExactAlarms(); inexact scheduling remains the fallback.
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
            val fallbackCount = inexactFallbackScheduleCounter.incrementAndGet()
            Log.w(TAG, "Scheduled reminder with inexact alarm fallback. fallbackCount=$fallbackCount")
            DiagnosticLog.record(context, "Scheduled reminder with inexact alarm fallback. fallbackCount=$fallbackCount")
        }
    }

    suspend fun scheduleOrShow(
        note: NoteEntity,
        items: List<ChecklistItemEntity> = emptyList(),
        silentDueNotification: Boolean = false,
    ) {
        val activeReminders = note.getActiveReminders()
        if (activeReminders.isEmpty()) {
            cancel(note.id)
            return
        }
        cancel(note.id)
        val now = System.currentTimeMillis()
        val keepUntilDone = keepReminderNotificationsUntilDone()
        activeReminders.take(MAX_REMINDERS_PER_NOTE).forEachIndexed { index, reminder ->
            val at = reminder.reminderAt
            if (at > now) {
                schedule(note.id, index, at, note.importance)
            }
        }
        val indexToShow = latestDueReminderIndex(note, now)
        if (indexToShow != null) {
            ReminderReceiver.showNotification(
                context = context,
                note = note,
                items = items,
                reminderIndex = indexToShow,
                keepUntilDone = keepUntilDone,
                onlyAlertOnce = silentDueNotification,
                silent = silentDueNotification,
            )
        }
    }

    fun cancel(noteId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0 until MAX_REMINDERS_PER_NOTE) {
            am.cancel(pendingIntent(noteId, index))
        }
    }

    fun cancelNotification(noteId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(pendingRequestCodeForNote(noteId))
        cancelReminderSlotNotifications(context, noteId)
    }

    suspend fun refreshNotificationIfActive(
        note: NoteEntity,
        items: List<ChecklistItemEntity>,
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeNotificationIds = notificationManager.activeNotifications.map { notification -> notification.id }.toSet()
        val keepUntilDone = keepReminderNotificationsUntilDone()
        if (pendingRequestCodeForNote(note.id) in activeNotificationIds) {
            ReminderReceiver.showNotification(
                context = context,
                note = note,
                items = items,
                reminderIndex = 0,
                keepUntilDone = keepUntilDone,
                onlyAlertOnce = true,
            )
            return
        }
        val legacyReminderIndex =
            (0 until MAX_REMINDERS_PER_NOTE)
                .lastOrNull { index -> pendingRequestCodeForNoteReminder(note.id, index) in activeNotificationIds }
        if (legacyReminderIndex != null) {
            ReminderReceiver.showNotification(
                context = context,
                note = note,
                items = items,
                reminderIndex = legacyReminderIndex,
                keepUntilDone = keepUntilDone,
                onlyAlertOnce = true,
            )
        }
    }

    suspend fun refreshSummaryNotification(
        notes: List<NoteWithItems>,
        nowMillis: Long,
    ) {
        val summaryEnabled = reminderPrefs?.snapshot()?.reminderSummaryNotificationEnabled ?: false
        if (!summaryEnabled || notes.isEmpty()) {
            cancelSummaryNotification()
            return
        }
        showSummaryNotification(notes, nowMillis)
    }

    fun cancelSummaryNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
    }

    suspend fun keepReminderNotificationsUntilDone(): Boolean = reminderPrefs?.snapshot()?.keepReminderNotificationsUntilDone ?: false

    private fun showSummaryNotification(
        notes: List<NoteWithItems>,
        nowMillis: Long,
    ) {
        val sortedNotes =
            notes
                .filter { noteWithItems -> noteWithItems.note.reminderAt != null }
                .sortedBy { noteWithItems -> noteWithItems.note.reminderAt }
        if (sortedNotes.isEmpty()) {
            cancelSummaryNotification()
            return
        }
        if (!canPostNotifications(context)) {
            cancelSummaryNotification()
            DiagnosticLog.record(context, "Reminder summary notification skipped: notifications are not allowed")
            return
        }

        val inboxStyle = NotificationCompat.InboxStyle()
        sortedNotes.take(SUMMARY_MAX_LINES).forEach { noteWithItems ->
            inboxStyle.addLine(summaryLine(noteWithItems.note, nowMillis))
        }
        if (sortedNotes.size > SUMMARY_MAX_LINES) {
            inboxStyle.setSummaryText(
                context.resources.getQuantityString(
                    R.plurals.reminder_summary_more,
                    sortedNotes.size - SUMMARY_MAX_LINES,
                    sortedNotes.size - SUMMARY_MAX_LINES,
                ),
            )
        }

        val summaryText =
            context.resources.getQuantityString(
                R.plurals.reminder_summary_count,
                sortedNotes.size,
                sortedNotes.size,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID_SUMMARY)
                .setSmallIcon(R.drawable.ic_stat_remember)
                .setContentTitle(context.getString(R.string.reminder_summary_title))
                .setContentText(summaryText)
                .setStyle(inboxStyle)
                .setContentIntent(openAppPendingIntent())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .build()
        postNotificationIfAllowed(
            context = context,
            notificationId = SUMMARY_NOTIFICATION_ID,
            notification = notification,
            source = "Reminder summary",
        )
    }

    private fun summaryLine(
        note: NoteEntity,
        nowMillis: Long,
    ): String {
        val reminderAt = note.reminderAt ?: nowMillis
        val title = summaryTitle(note)
        return context.getString(R.string.reminder_summary_line, summaryTimingLabel(reminderAt, nowMillis), title)
    }

    private fun summaryTitle(note: NoteEntity): String {
        if (note.visibility == Visibility.SECRET) return context.getString(R.string.reminder_notification_hidden_title)

        val title = note.title.ifBlank { context.getString(R.string.options_reminder) }
        val emoji = iconEmojiPayload(note.iconKey) ?: return title
        return "$emoji $title"
    }

    private fun summaryTimingLabel(
        reminderAt: Long,
        nowMillis: Long,
    ): String {
        val todayStart = startOfDay(nowMillis)
        val tomorrowStart = startOfTomorrow(nowMillis)
        return when {
            reminderAt < todayStart -> {
                val overdueDays = daysBetween(startOfDay(reminderAt), todayStart).coerceAtLeast(1)
                context.resources.getQuantityString(
                    R.plurals.reminder_summary_overdue_days,
                    overdueDays,
                    overdueDays,
                )
            }
            reminderAt < tomorrowStart -> context.getString(R.string.reminder_summary_due_today)
            reminderAt - nowMillis < HOUR_MILLIS * 24 -> {
                val hoursUntil =
                    ((reminderAt - nowMillis + HOUR_MILLIS - 1) / HOUR_MILLIS)
                        .coerceAtLeast(1)
                        .toInt()
                context.resources.getQuantityString(
                    R.plurals.reminder_summary_in_hours,
                    hoursUntil,
                    hoursUntil,
                )
            }
            else -> {
                val daysUntil = daysBetween(todayStart, startOfDay(reminderAt)).coerceAtLeast(1)
                context.resources.getQuantityString(
                    R.plurals.reminder_summary_in_days,
                    daysUntil,
                    daysUntil,
                )
            }
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        return PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startOfDay(millis: Long): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return calendar.timeInMillis
    }

    private fun startOfTomorrow(nowMillis: Long): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = startOfDay(nowMillis)
                add(Calendar.DAY_OF_MONTH, 1)
            }
        return calendar.timeInMillis
    }

    private fun daysBetween(
        startMillis: Long,
        endMillis: Long,
    ): Int = ((endMillis - startMillis) / DAY_MILLIS).toInt()

    private fun pendingIntent(
        noteId: Long,
        reminderIndex: Int,
    ): PendingIntent {
        val intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_REMINDER_INDEX, reminderIndex)
            }
        return PendingIntent.getBroadcast(
            context,
            pendingRequestCodeForNoteReminder(noteId, reminderIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val MAX_REMINDERS_PER_NOTE = dev.bikram.remember.data.MAX_REMINDERS_PER_NOTE
        private val inexactFallbackScheduleCounter = AtomicInteger(0)

        const val ACTION_FIRE_REMINDER = "dev.bikram.remember.reminders.FIRE"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_REMINDER_INDEX = "reminder_index"

        // Channel IDs are versioned (_v2 suffix) because Android freezes channel
        // settings - importance, sound, vibration - the moment a channel is first
        // registered, and re-calling createNotificationChannel with the same id can NOT
        // upgrade an existing low-importance channel to IMPORTANCE_HIGH. Bumping the
        // suffix forces the system to register a brand-new channel so the HIGH variant
        // actually delivers heads-up "pop on screen" notifications.
        const val CHANNEL_ID_LOW = "reminder_low_v2"
        const val CHANNEL_ID_DEFAULT = "reminder_default_v2"
        const val CHANNEL_ID_HIGH = "reminder_high_v2"
        const val CHANNEL_ID_SUMMARY = "reminder_summary_v1"
        const val SUMMARY_NOTIFICATION_ID = 0x524D4452
        private const val SUMMARY_MAX_LINES = 7
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val DAY_MILLIS = 24L * HOUR_MILLIS

        // Legacy ids - we delete them on app start to clean up the user's notification
        // settings UI rather than leaving stale channel rows behind.
        val LEGACY_CHANNEL_IDS: List<String> =
            listOf(
                "reminder",
                "reminder_low",
                "reminder_default",
                "reminder_high",
            )

        val inexactFallbackScheduleCount: Int
            get() = inexactFallbackScheduleCounter.get()

        /**
         * Folds a note row id into the int range required for [PendingIntent] request codes and
         * [android.app.NotificationManager] notification ids. Unlike [Long.toInt], uses the full
         * 64-bit value (same mix as [Long.hashCode]) so large ids do not wrap or collide by truncation alone.
         */
        fun pendingRequestCodeForNote(noteId: Long): Int = noteId.hashCode()

        fun pendingRequestCodeForNoteReminder(
            noteId: Long,
            reminderIndex: Int,
        ): Int {
            val salted = noteId xor ((reminderIndex.toLong() + 1L) shl 40)
            return (salted xor (salted ushr 32)).toInt()
        }

        /**
         * Distinct request codes for per-note notification action [PendingIntent]s.
         * Folds [noteId] and [actionIndex] with 64-bit xor before collapsing to [Int], so we avoid
         * multiplicative overflow from `31 * hash + index` and keep codes disjoint from
         * [pendingRequestCodeForNote] (which uses [Long.hashCode] of the raw id only).
         */
        fun pendingRequestCodeForNoteAction(
            noteId: Long,
            actionIndex: Int,
        ): Int {
            val salted = noteId xor ((actionIndex.toLong() + 1L) shl 48)
            return (salted xor (salted ushr 32)).toInt()
        }

        fun pendingRequestCodeForDismiss(
            noteId: Long,
            reminderIndex: Int = 0,
        ): Int {
            val salted = noteId xor (0x4B4DL shl 32) xor ((reminderIndex.toLong() + 1L) shl 44)
            return (salted xor (salted ushr 32)).toInt()
        }

        fun cancelReminderSlotNotifications(
            context: Context,
            noteId: Long,
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (index in 0 until MAX_REMINDERS_PER_NOTE) {
                notificationManager.cancel(pendingRequestCodeForNoteReminder(noteId, index))
            }
        }
    }
}

internal fun latestDueReminderIndex(
    note: NoteEntity,
    now: Long,
): Int? {
    if (note.trashed || note.archived || note.completedAt != null) return null

    var dueReminderIndex: Int? = null
    var dueReminderAt = Long.MIN_VALUE
    note.getActiveReminders().forEachIndexed { index, reminder ->
        val at = reminder.reminderAt
        if (at <= now && at >= dueReminderAt) {
            dueReminderIndex = index
            dueReminderAt = at
        }
    }
    return dueReminderIndex
}
