package dev.bikram.remember.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderScheduler(private val context: Context) {

    fun schedule(noteId: Long, whenMillis: Long) {
        if (whenMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(noteId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        }
    }

    fun cancel(noteId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(noteId))
    }

    private fun pendingIntent(noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            pendingRequestCodeForNote(noteId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "dev.bikram.remember.reminders.FIRE"
        const val EXTRA_NOTE_ID = "note_id"
        // Channel IDs are versioned (_v2 suffix) because Android freezes channel
        // settings - importance, sound, vibration - the moment a channel is first
        // registered, and re-calling createNotificationChannel with the same id can NOT
        // upgrade an existing low-importance channel to IMPORTANCE_HIGH. Bumping the
        // suffix forces the system to register a brand-new channel so the HIGH variant
        // actually delivers heads-up "pop on screen" notifications.
        const val CHANNEL_ID_LOW = "reminder_low_v2"
        const val CHANNEL_ID_DEFAULT = "reminder_default_v2"
        const val CHANNEL_ID_HIGH = "reminder_high_v2"
        // Legacy ids - we delete them on app start to clean up the user's notification
        // settings UI rather than leaving stale channel rows behind.
        val LEGACY_CHANNEL_IDS: List<String> = listOf(
            "reminder",
            "reminder_low",
            "reminder_default",
            "reminder_high",
        )

        /**
         * Folds a note row id into the int range required for [PendingIntent] request codes and
         * [android.app.NotificationManager] notification ids. Unlike [Long.toInt], uses the full
         * 64-bit value (same mix as [Long.hashCode]) so large ids do not wrap or collide by truncation alone.
         */
        fun pendingRequestCodeForNote(noteId: Long): Int = noteId.hashCode()

        /**
         * Distinct request codes for per-note notification action [PendingIntent]s.
         * Folds [noteId] and [actionIndex] with 64-bit xor before collapsing to [Int], so we avoid
         * multiplicative overflow from `31 * hash + index` and keep codes disjoint from
         * [pendingRequestCodeForNote] (which uses [Long.hashCode] of the raw id only).
         */
        fun pendingRequestCodeForNoteAction(noteId: Long, actionIndex: Int): Int {
            val salted = noteId xor ((actionIndex.toLong() + 1L) shl 48)
            return (salted xor (salted ushr 32)).toInt()
        }
    }
}
