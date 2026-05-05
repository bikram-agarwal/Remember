package dev.bikram.remember.update

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
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.data.UpdatePrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateAvailableNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val updatePrefs: UpdatePrefs,
    ) {
        fun ensureNotificationChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_updates_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_updates_description)
                }
            manager.createNotificationChannel(channel)
        }

        suspend fun notifyIfNewUpdateAvailable(
            info: RememberUpdateInfo,
            prefs: UpdatePreferencesState,
        ) {
            if (!prefs.notifyOnNewUpdates) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            val dedupeKey = info.notificationDedupeKey()
            if (dedupeKey == prefs.updateLastNotifiedDedupeKey) return

            ensureNotificationChannel()
            val openIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_remember)
                    .setContentTitle(context.getString(R.string.notification_update_available_title))
                    .setContentText(context.getString(R.string.notification_update_available_text, info.versionName))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(PendingIntent.getActivity(context, REQUEST_CODE_OPEN_UPDATES, openIntent, pendingFlags))
                    .setAutoCancel(true)
                    .build()

            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                updatePrefs.setUpdateLastNotifiedDedupeKey(dedupeKey)
            }
        }

        companion object {
            const val CHANNEL_ID = "remember_updates"
            private const val NOTIFICATION_ID = 71012
            private const val REQUEST_CODE_OPEN_UPDATES = 1012
        }
    }
