package dev.bikram.remember.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.remember.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

object DiagnosticLog {
    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val LOG_FILE_NAME = "remember-diagnostics.log"
    private const val SHARE_FILE_NAME = "remember-diagnostics.txt"
    private const val MAX_LOG_BYTES = 256 * 1024

    @Volatile
    private var crashHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, "Uncaught exception on ${thread.name}", throwable)
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(2)
            }
        }
        crashHandlerInstalled = true
    }

    fun record(
        context: Context,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            val logFile = logFile(context)
            logFile.parentFile?.mkdirs()
            trimIfNeeded(logFile)
            logFile.appendText(
                buildString {
                    append(Instant.now())
                    append(" | ")
                    append(message)
                    append('\n')
                    if (throwable != null) {
                        append(stackTraceText(throwable))
                        append('\n')
                    }
                },
            )
        }
    }

    fun createShareFile(context: Context): File {
        val shareFile = File(File(context.cacheDir, DIAGNOSTICS_DIR), SHARE_FILE_NAME)
        shareFile.parentFile?.mkdirs()
        val logText = runCatching { logFile(context).readText() }.getOrDefault("")
        shareFile.writeText(
            buildString {
                appendLine("Remember diagnostics")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Package: ${context.packageName}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Flavor: ${BuildConfig.FLAVOR}")
                appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine()
                appendSystemSnapshot(context)
                appendLine()
                appendLine("App log")
                appendLine("=======")
                append(logText.ifBlank { "No app log entries captured yet.\n" })
            },
        )
        return shareFile
    }

    private fun logFile(context: Context): File = File(File(context.filesDir, DIAGNOSTICS_DIR), LOG_FILE_NAME)

    private fun StringBuilder.appendSystemSnapshot(context: Context) {
        val packageManager = context.packageManager
        val packageInfo =
            runCatching {
                packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        val appInfo = packageInfo?.applicationInfo
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        appendLine("Environment")
        appendLine("===========")
        appendLine("Locale: ${Locale.getDefault()}")
        appendLine("Timezone: ${TimeZone.getDefault().id}")
        appendLine("Uptime: ${SystemClock.uptimeMillis()} ms")
        appendLine("Elapsed realtime: ${SystemClock.elapsedRealtime()} ms")
        appendLine("Target SDK: ${appInfo?.targetSdkVersion ?: "unknown"}")
        appendLine("First install: ${packageInfo?.firstInstallTime?.let(Instant::ofEpochMilli) ?: "unknown"}")
        appendLine("Last update: ${packageInfo?.lastUpdateTime?.let(Instant::ofEpochMilli) ?: "unknown"}")
        appendLine("Installer: ${installerPackageName(context)}")
        appendLine()
        appendLine("Permissions and scheduling")
        appendLine("==========================")
        appendLine("Notifications enabled: ${notificationManagerCompat.areNotificationsEnabled()}")
        appendLine("POST_NOTIFICATIONS granted: ${postNotificationsGranted(context)}")
        appendLine("Can schedule exact alarms: ${runCatching { alarmManager.canScheduleExactAlarms() }.getOrNull() ?: "unknown"}")
        appendLine("Ignoring battery optimizations: ${powerManager.isIgnoringBatteryOptimizations(context.packageName)}")
        appendLine()
        appendNotificationChannels(context)
    }

    private fun StringBuilder.appendNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels =
            runCatching {
                notificationManager.notificationChannels.sortedBy { channel -> channel.id }
            }.getOrDefault(emptyList())
        appendLine("Notification channels")
        appendLine("=====================")
        if (channels.isEmpty()) {
            appendLine("No channels registered.")
            return
        }
        channels.forEach { channel ->
            appendLine(
                buildString {
                    append(channel.id)
                    append(": importance=")
                    append(channel.importance)
                    append(", sound=")
                    append(channel.sound != null)
                    append(", vibrate=")
                    append(channel.shouldVibrate())
                    append(", lights=")
                    append(channel.shouldShowLights())
                    append(", bypassDnd=")
                    append(channel.canBypassDnd())
                    append(", lockscreenVisibility=")
                    append(channel.lockscreenVisibility)
                },
            )
        }
    }

    private fun postNotificationsGranted(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            ).toString()
        } else {
            "not required"
        }

    private fun installerPackageName(context: Context): String =
        runCatching {
            val installingPackageName =
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
            installingPackageName.orEmpty().ifBlank { "unknown" }
        }.getOrDefault("unknown")

    private fun trimIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val text = logFile.readText()
        val keepFrom = (text.length / 2).coerceAtLeast(0)
        logFile.writeText(text.substring(keepFrom))
    }

    private fun stackTraceText(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
