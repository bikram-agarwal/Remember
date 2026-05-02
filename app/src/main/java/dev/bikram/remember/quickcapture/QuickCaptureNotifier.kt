package dev.bikram.remember.quickcapture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R

/**
 * Manages the always-visible "New note" notification that the user can tap from anywhere on the
 * device to jump into a blank note without having to open the app first. The persistent form is
 * intentional: on modern Android the only way to keep a tappable shortcut in the status bar is
 * via an ongoing notification on a LOW-importance channel (so it doesn't beep or pop).
 */
object QuickCaptureNotifier {
    const val CHANNEL_ID = "quick_capture"

    /** Stable ID so repeated posts replace the same notification instead of stacking. */
    private const val NOTIFICATION_ID = 9_001

    /** Called once on app start to ensure the channel exists before any notify() call. */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.quick_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.quick_capture_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val tapIntent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHORTCUT_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pending =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_remember)
                .setContentTitle(context.getString(R.string.quick_capture_notification_title))
                .setContentText(context.getString(R.string.quick_capture_notification_text))
                .setContentIntent(pending)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
