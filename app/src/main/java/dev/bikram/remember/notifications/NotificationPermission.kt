package dev.bikram.remember.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.remember.diagnostics.DiagnosticLog

fun canPostNotifications(context: Context): Boolean {
    val appContext = context.applicationContext
    if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

@SuppressLint("MissingPermission")
fun postNotificationIfAllowed(
    context: Context,
    notificationId: Int,
    notification: Notification,
    source: String,
): Boolean {
    val appContext = context.applicationContext
    if (!canPostNotifications(appContext)) {
        DiagnosticLog.record(appContext, "$source notification skipped: notifications are not allowed")
        return false
    }
    return runCatching {
        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
    }.onFailure { error ->
        DiagnosticLog.record(appContext, "$source notification failed", error)
    }.isSuccess
}
