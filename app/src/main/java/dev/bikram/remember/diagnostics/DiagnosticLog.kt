package dev.bikram.remember.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.widget.NotesWidgetReceiver
import dev.bikram.remember.widget.QuickCaptureWidgetReceiver
import dev.bikram.remember.widget.StarredWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

object DiagnosticLog {
    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val LOG_FILE_NAME = "remember-diagnostics.log"
    private const val SHARE_FILE_NAME = "remember-diagnostics.txt"
    private const val MAX_LOG_BYTES = 256 * 1024
    private const val LOG_WRITER_THREAD_NAME = "remember-diagnostic-writer"
    private const val CRASH_LOG_WRITE_TIMEOUT_SECONDS = 2L

    private val logWriterExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, LOG_WRITER_THREAD_NAME).apply {
                isDaemon = true
            }
        }

    @Volatile
    private var crashHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordSynchronously(
                appContext,
                appContext.getString(R.string.diagnostics_uncaught_exception_format, thread.name),
                throwable,
            )
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
        val appContext = context.applicationContext
        val logEntry = formatLogEntry(message, throwable)
        logWriterExecutor.execute {
            writeLogEntry(appContext, logEntry)
        }
    }

    /**
     * Builds the shareable diagnostics file. Runs on [Dispatchers.IO] because it reads the log
     * file and several DataStore-backed preferences off disk; callers can invoke it from a
     * coroutine without blocking the main thread.
     */
    suspend fun createShareFile(context: Context): File =
        withContext(Dispatchers.IO) {
            awaitPendingWrites()
            val shareFile = File(File(context.cacheDir, DIAGNOSTICS_DIR), SHARE_FILE_NAME)
            shareFile.parentFile?.mkdirs()
            val logText = runCatching { logFile(context).readText() }.getOrDefault("")
            val preferences = loadPreferencesSnapshot(context)
            shareFile.writeText(
                buildString {
                    appendLine(context.getString(R.string.diagnostics_title))
                    appendLine(context.getString(R.string.diagnostics_generated_format, Instant.now().toString()))
                    appendLine(context.getString(R.string.diagnostics_package_format, context.packageName))
                    appendLine(context.getString(R.string.diagnostics_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                    appendLine(context.getString(R.string.diagnostics_flavor_format, BuildConfig.FLAVOR))
                    appendLine(context.getString(R.string.diagnostics_build_type_format, BuildConfig.BUILD_TYPE))
                    appendLine(context.getString(R.string.diagnostics_device_format, Build.MANUFACTURER, Build.MODEL))
                    appendLine(context.getString(R.string.diagnostics_android_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                    appendLine()
                    appendSystemSnapshot(context)
                    appendLine()
                    appendPreferencesSnapshot(context, preferences)
                    appendLine()
                    appendDiagnosticSection(context.getString(R.string.diagnostics_section_app_log))
                    append(logText.ifBlank { context.getString(R.string.diagnostics_no_app_log_entries) })
                },
            )
            shareFile
        }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        logWriterExecutor.execute {
            runCatching {
                val logFile = logFile(appContext)
                if (logFile.exists()) {
                    logFile.writeText("")
                }
            }
        }
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
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        appendDiagnosticSection(context.getString(R.string.diagnostics_section_environment))
        appendLine(context.getString(R.string.diagnostics_locale_format, Locale.getDefault().toString()))
        appendLine(context.getString(R.string.diagnostics_timezone_format, TimeZone.getDefault().id))
        appendLine(context.getString(R.string.diagnostics_uptime_format, SystemClock.uptimeMillis()))
        appendLine(context.getString(R.string.diagnostics_elapsed_realtime_format, SystemClock.elapsedRealtime()))
        appendDisplaySnapshot(context)
        appendLine(context.getString(R.string.diagnostics_target_sdk_format, appInfo?.targetSdkVersion?.toString() ?: unknownValue(context)))
        appendLine(
            context.getString(
                R.string.diagnostics_first_install_format,
                packageInfo?.firstInstallTime?.let(Instant::ofEpochMilli)?.toString() ?: unknownValue(context),
            ),
        )
        appendLine(
            context.getString(
                R.string.diagnostics_last_update_format,
                packageInfo?.lastUpdateTime?.let(Instant::ofEpochMilli)?.toString() ?: unknownValue(context),
            ),
        )
        appendLine(context.getString(R.string.diagnostics_installer_format, installerPackageName(context)))
        appendLine(context.getString(R.string.diagnostics_launcher_package_format, launcherPackageName(context)))
        val filesDirAllocatableBytes = allocatableBytes(context, context.filesDir)
        appendLine(context.getString(R.string.diagnostics_files_dir_space_format, filesDirAllocatableBytes))
        val cacheDirAllocatableBytes = allocatableBytes(context, context.cacheDir)
        appendLine(context.getString(R.string.diagnostics_cache_dir_space_format, cacheDirAllocatableBytes))
        appendLine(context.getString(R.string.diagnostics_external_storage_state_format, Environment.getExternalStorageState()))
        appendLine()
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_permissions_app_access))
        appendLine(context.getString(R.string.diagnostics_notifications_enabled_format, notificationManagerCompat.areNotificationsEnabled().toString()))
        appendLine(context.getString(R.string.diagnostics_post_notifications_granted_format, postNotificationsGranted(context)))
        appendLine(
            context.getString(
                R.string.diagnostics_ignoring_battery_optimizations_format,
                powerManager.isIgnoringBatteryOptimizations(context.packageName).toString(),
            ),
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        appendLine(
            context.getString(
                R.string.diagnostics_exact_alarms_allowed_format,
                alarmManager.canScheduleExactAlarms().toString(),
            ),
        )
        appendLine()
        appendNotificationChannels(context)
        appendLine()
        appendWidgetOptions(context)
    }

    private suspend fun loadPreferencesSnapshot(context: Context): PreferencesSnapshot? =
        runCatching {
            val theme = ThemePrefs(context).state.first()
            val interaction = InteractionPrefs(context).state.first()
            val reminder = ReminderPrefs(context).snapshot()
            val backup = BackupPrefs(context).snapshot()
            val update = UpdatePrefs(context).snapshot()
            val quickCapture = QuickCapturePrefs(context).snapshot()
            val onboarding = OnboardingPrefs(context).state.first()
            val lock = LockPrefs(context).state.first()
            PreferencesSnapshot(theme, interaction, reminder, backup, update, quickCapture, onboarding, lock)
        }.getOrNull()

    private fun StringBuilder.appendPreferencesSnapshot(
        context: Context,
        snapshot: PreferencesSnapshot?,
    ) {
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_settings))
        if (snapshot == null) {
            appendLine(context.getString(R.string.diagnostics_settings_snapshot_unavailable))
            return
        }

        // Theme / appearance.
        appendLine(context.getString(R.string.diagnostics_theme_mode_format, snapshot.theme.themeMode.toString()))
        appendLine(context.getString(R.string.diagnostics_color_source_format, snapshot.theme.colorSource.toString()))
        appendLine(context.getString(R.string.diagnostics_palette_style_format, snapshot.theme.paletteStyle.toString()))
        appendLine(context.getString(R.string.diagnostics_saved_custom_seeds_format, snapshot.theme.customSeeds.size))
        appendLine(
            context.getString(
                R.string.diagnostics_active_custom_seed_format,
                snapshot.theme.activeCustomSeed
                    .isNotBlank()
                    .toString(),
            ),
        )
        appendLine(context.getString(R.string.diagnostics_gradient_background_format, snapshot.theme.useGradient.toString()))
        appendLine(context.getString(R.string.diagnostics_surface_shading_format, snapshot.theme.shadingIntensity.toString()))
        appendLine(context.getString(R.string.diagnostics_hero_on_cards_format, snapshot.theme.heroOnCards.toString()))
        appendLine(context.getString(R.string.diagnostics_adaptive_note_themes_format, snapshot.theme.adaptiveNoteThemes.toString()))
        appendLine(context.getString(R.string.diagnostics_blur_bars_format, snapshot.theme.blurBars.toString()))

        // Interaction / swipe.
        appendLine(context.getString(R.string.diagnostics_swipe_gesture_mode_format, snapshot.interaction.swipeGestureMode.toString()))
        appendLine(context.getString(R.string.diagnostics_swipe_start_to_end_format, snapshot.interaction.swipeStartToEnd.toString()))
        appendLine(context.getString(R.string.diagnostics_swipe_end_to_start_format, snapshot.interaction.swipeEndToStart.toString()))

        // Reminders.
        appendLine(context.getString(R.string.diagnostics_keep_reminders_until_done_format, snapshot.reminder.keepReminderNotificationsUntilDone.toString()))
        appendLine(context.getString(R.string.diagnostics_reminder_summary_enabled_format, snapshot.reminder.reminderSummaryNotificationEnabled.toString()))

        // Backup.
        appendLine(context.getString(R.string.diagnostics_auto_export_on_change_format, snapshot.backup.autoExportOnChange.toString()))
        appendLine(context.getString(R.string.diagnostics_scheduled_export_enabled_format, snapshot.backup.scheduledExportEnabled.toString()))
        appendLine(context.getString(R.string.diagnostics_include_media_in_backup_format, snapshot.backup.includeMediaInBackup.toString()))
        appendLine(context.getString(R.string.diagnostics_local_backup_folder_format, redactedLocation(context, snapshot.backup.exportFolderUri)))
        appendLine(context.getString(R.string.diagnostics_cloud_backup_folder_format, redactedLocation(context, snapshot.backup.cloudExportFolderUri)))

        // Updates.
        appendLine(context.getString(R.string.diagnostics_update_check_schedule_format, snapshot.update.updateCheckSchedule.toString()))
        appendLine(context.getString(R.string.diagnostics_notify_new_updates_format, snapshot.update.notifyOnNewUpdates.toString()))
        appendLine(context.getString(R.string.diagnostics_save_apk_downloads_format, snapshot.update.saveUpdateApkToDownloads.toString()))
        appendLine(context.getString(R.string.diagnostics_apk_downloads_copy_succeeded_format, snapshot.update.updateApkDownloadsCopySucceeded.toString()))
        appendLine(context.getString(R.string.diagnostics_in_app_review_never_ask_again_format, snapshot.update.inAppReviewAutoNeverAskAgain.toString()))

        // Quick capture / onboarding.
        appendLine(context.getString(R.string.diagnostics_quick_capture_enabled_format, snapshot.quickCapture.enabled.toString()))
        appendLine(context.getString(R.string.diagnostics_intro_seen_format, snapshot.onboarding.hasSeenIntro.toString()))

        // App lock — only non-sensitive booleans. PIN hash/salt/length are never logged.
        appendLine(context.getString(R.string.diagnostics_app_lock_enabled_format, snapshot.lock.enabled.toString()))
        appendLine(context.getString(R.string.diagnostics_app_lock_biometric_format, snapshot.lock.biometric.toString()))
        appendLine(context.getString(R.string.diagnostics_app_lock_has_pin_format, snapshot.lock.hasPin.toString()))
    }

    private fun StringBuilder.appendNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels =
            runCatching {
                notificationManager.notificationChannels.sortedBy { channel -> channel.id }
            }.getOrDefault(emptyList())
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_notification_channels))
        if (channels.isEmpty()) {
            appendLine(context.getString(R.string.diagnostics_no_channels_registered))
            return
        }
        channels.forEach { channel ->
            appendLine(
                context.getString(
                    R.string.diagnostics_notification_channel_format,
                    channel.id,
                    channel.importance,
                    (channel.sound != null).toString(),
                    channel.shouldVibrate().toString(),
                    channel.shouldShowLights().toString(),
                    channel.canBypassDnd().toString(),
                    channel.lockscreenVisibility,
                ),
            )
        }
    }

    private fun StringBuilder.appendDisplaySnapshot(context: Context) {
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics
        appendLine(context.getString(R.string.diagnostics_font_scale_format, configuration.fontScale))
        appendLine(
            context.getString(
                R.string.diagnostics_screen_dp_format,
                configuration.screenWidthDp,
                configuration.screenHeightDp,
                configuration.smallestScreenWidthDp,
            ),
        )
        appendLine(context.getString(R.string.diagnostics_display_pixels_format, displayMetrics.widthPixels, displayMetrics.heightPixels))
        appendLine(
            context.getString(
                R.string.diagnostics_display_density_format,
                displayMetrics.density,
                displayMetrics.density * configuration.fontScale,
                displayMetrics.densityDpi,
            ),
        )
    }

    private fun StringBuilder.appendWidgetOptions(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetProviders =
            listOf(
                R.string.widget_agenda_label to NotesWidgetReceiver::class.java,
                R.string.widget_quick_capture_label to QuickCaptureWidgetReceiver::class.java,
                R.string.widget_starred_label to StarredWidgetReceiver::class.java,
            )
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_widget_options))
        var widgetCount = 0
        widgetProviders.forEach { (labelRes, receiverClass) ->
            val widgetIds =
                appWidgetManager.getAppWidgetIds(
                    ComponentName(context, receiverClass),
                )
            widgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                widgetCount++
                appendLine(
                    context.getString(
                        R.string.diagnostics_widget_options_format,
                        context.getString(labelRes),
                        widgetId,
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY),
                    ),
                )
            }
        }
        if (widgetCount == 0) {
            appendLine(context.getString(R.string.diagnostics_no_widgets_installed))
        }
    }

    private fun allocatableBytes(
        context: Context,
        directory: File,
    ): Long {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return directory.usableSpace
        return runCatching {
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
        }.getOrDefault(directory.usableSpace)
    }

    private fun postNotificationsGranted(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            ).toString()
        } else {
            context.getString(R.string.diagnostics_value_not_required)
        }

    private fun installerPackageName(context: Context): String =
        runCatching {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
                .orEmpty()
                .ifBlank { unknownValue(context) }
        }.getOrDefault(unknownValue(context))

    private fun launcherPackageName(context: Context): String =
        runCatching {
            context.packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )?.activityInfo
                ?.packageName
                .orEmpty()
                .ifBlank { unknownValue(context) }
        }.getOrDefault(unknownValue(context))

    private fun redactedLocation(
        context: Context,
        value: String,
    ): String =
        when {
            value.isBlank() -> {
                context.getString(R.string.diagnostics_value_not_configured)
            }

            value.startsWith("content://") -> {
                context.getString(R.string.diagnostics_value_configured_content_uri)
            }

            value.startsWith("/") -> {
                context.getString(R.string.diagnostics_value_configured_filesystem_path)
            }

            else -> {
                context.getString(
                    R.string.diagnostics_value_configured_reference_format,
                    value.substringBefore(':', missingDelimiterValue = unknownValue(context)),
                )
            }
        }

    private fun unknownValue(context: Context): String = context.getString(R.string.diagnostics_value_unknown)

    private fun StringBuilder.appendDiagnosticSection(title: String) {
        appendLine(title)
        appendLine("=".repeat(title.length))
    }

    private fun trimIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val text = logFile.readText()
        val keepFrom = (text.length / 2).coerceAtLeast(0)
        logFile.writeText(text.substring(keepFrom))
    }

    private fun recordSynchronously(
        context: Context,
        message: String,
        throwable: Throwable?,
    ) {
        val logEntry = formatLogEntry(message, throwable)
        if (Thread.currentThread().name == LOG_WRITER_THREAD_NAME) {
            writeLogEntry(context, logEntry)
            return
        }
        runCatching {
            logWriterExecutor
                .submit { writeLogEntry(context, logEntry) }
                .get(CRASH_LOG_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun writeLogEntry(
        context: Context,
        logEntry: String,
    ) {
        runCatching {
            val logFile = logFile(context)
            logFile.parentFile?.mkdirs()
            trimIfNeeded(logFile)
            logFile.appendText(logEntry)
        }
    }

    private fun awaitPendingWrites() {
        runCatching {
            logWriterExecutor.submit { }.get()
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun formatLogEntry(
        message: String,
        throwable: Throwable?,
    ): String {
        return buildString {
            append(Instant.now())
            append(" | ")
            append(message)
            append('\n')
            if (throwable != null) {
                append(stackTraceText(throwable))
                append('\n')
            }
        }
    }

    private fun stackTraceText(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }

    private data class PreferencesSnapshot(
        val theme: dev.bikram.remember.data.ThemeState,
        val interaction: dev.bikram.remember.data.InteractionState,
        val reminder: dev.bikram.remember.data.ReminderPreferencesState,
        val backup: dev.bikram.remember.data.BackupPreferencesState,
        val update: dev.bikram.remember.data.UpdatePreferencesState,
        val quickCapture: dev.bikram.remember.data.QuickCaptureState,
        val onboarding: dev.bikram.remember.data.OnboardingState,
        val lock: LockPrefs.State,
    )
}
