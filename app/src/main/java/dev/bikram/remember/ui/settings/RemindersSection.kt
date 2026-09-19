package dev.bikram.remember.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.bikram.remember.R
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.QuickCaptureState
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.SnoozeType
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.appClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reminders / notifications section. Bundles five interlocking concerns:
 *   - System notification permission (POST_NOTIFICATIONS on Android 13+, app-settings
 *     intent on older releases or once granted).
 *   - Reliable-reminders capability: combined exact-alarm permission + battery-
 *     optimisation exemption when the platform offers them as one toggle, otherwise
 *     surfaced as two separate rows.
 *   - Sticky reminder notifications until the note is marked done.
 *   - Snooze sheet style (named presets vs a duration).
 *   - Reminder summary notification (the persistent multi-reminder summary).
 *   - Quick-capture persistent notification.
 *
 * Pulled out of [SettingsRoute] in audit 3.1.
 */
@Composable
internal fun RemindersSection(
    reminderState: ReminderPreferencesState,
    reminderPrefs: ReminderPrefs,
    quickCaptureState: QuickCaptureState,
    quickCapturePrefs: QuickCapturePrefs,
    notificationsGranted: Boolean,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    permissionLinked: Boolean,
    canScheduleExactAlarms: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    scope: CoroutineScope,
    highlightItemKey: String? = null,
    highlightItemRequestId: Int = 0,
) {
    val context = LocalContext.current
    val keepUntilDoneHighlightActive =
        rememberSettingsItemHighlightActive(
            itemKey = "keep_until_done",
            highlightItemKey = highlightItemKey,
            highlightItemRequestId = highlightItemRequestId,
        )
    val keepUntilDoneHighlightAlpha = rememberSectionHighlightPulseAlpha(keepUntilDoneHighlightActive)
    val snoozeTypeHighlightActive =
        rememberSettingsItemHighlightActive(
            itemKey = "snooze_type",
            highlightItemKey = highlightItemKey,
            highlightItemRequestId = highlightItemRequestId,
        )
    val snoozeTypeHighlightAlpha = rememberSectionHighlightPulseAlpha(snoozeTypeHighlightActive)
    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .appClickable {
                            if (!notificationsGranted) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                    context.startActivity(notificationsAppSettingsIntent(context))
                                }
                            } else {
                                context.startActivity(notificationsAppSettingsIntent(context))
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_notifications),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_notifications_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(16.dp))
                RememberSwitch(
                    checked = notificationsGranted,
                    onCheckedChange = { wantEnabled ->
                        when {
                            wantEnabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED ->
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            wantEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                context.startActivity(notificationsAppSettingsIntent(context))
                            !wantEnabled ->
                                context.startActivity(notificationsAppSettingsIntent(context))
                            else -> { }
                        }
                    },
                    thumbContent =
                        if (notificationsGranted) {
                            {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = SwitchDefaults.IconSize,
                                    weight = FontWeight.Bold,
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
        if (permissionLinked) {
            GroupedListItem(position = GroupPosition.MIDDLE) {
                SettingsToggleRow(
                    materialSymbolName = "timer",
                    title = stringResource(R.string.settings_reliable_reminders),
                    subtitle = stringResource(R.string.settings_reliable_reminders_desc),
                    checked = canScheduleExactAlarms && isIgnoringBatteryOptimizations,
                    onCheckedChange = { wantEnabled ->
                        val intent =
                            if (wantEnabled && !canScheduleExactAlarms) {
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            } else if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                batteryOptimizationIntent(context)
                            } else {
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            }
                        context.startActivity(intent)
                    },
                )
            }
        } else {
            GroupedListItem(position = GroupPosition.MIDDLE) {
                SettingsToggleRow(
                    materialSymbolName = "timer",
                    title = stringResource(R.string.settings_reliable_reminders),
                    subtitle = stringResource(R.string.settings_reliable_reminders_exact_desc),
                    checked = canScheduleExactAlarms,
                    onCheckedChange = {
                        val intent =
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        context.startActivity(intent)
                    },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                SettingsToggleRow(
                    materialSymbolName = "battery_full",
                    title = stringResource(R.string.settings_run_in_background),
                    subtitle = stringResource(R.string.settings_run_in_background_desc),
                    checked = isIgnoringBatteryOptimizations,
                    onCheckedChange = { wantEnabled ->
                        val intent =
                            if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                batteryOptimizationIntent(context)
                            } else {
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            }
                        context.startActivity(intent)
                    },
                )
            }
        }
        GroupedListItem(
            position = GroupPosition.MIDDLE,
            modifier =
                Modifier
                    .zIndex(if (keepUntilDoneHighlightActive) 1f else 0f)
                    .pulsingSectionHighlightOutline(
                        active = keepUntilDoneHighlightActive,
                        outlineColor =
                            MaterialTheme.colorScheme.primary.copy(alpha = keepUntilDoneHighlightAlpha),
                        expandDp = 4.dp,
                        cornerRadiusDp = 12.dp,
                    ),
        ) {
            SettingsToggleRow(
                materialSymbolName = "notification_important",
                title = stringResource(R.string.settings_keep_reminders_until_done),
                subtitle = stringResource(R.string.settings_keep_reminders_until_done_desc),
                checked = reminderState.keepReminderNotificationsUntilDone,
                onCheckedChange = { enabled ->
                    scope.launch {
                        reminderPrefs.setKeepReminderNotificationsUntilDone(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            SettingsToggleRow(
                materialSymbolName = "format_list_bulleted",
                title = stringResource(R.string.settings_reminder_summary_notification),
                subtitle = stringResource(R.string.settings_reminder_summary_notification_desc),
                checked = reminderState.reminderSummaryNotificationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        reminderPrefs.setReminderSummaryNotificationEnabled(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            SettingsToggleRow(
                materialSymbolName = "bolt",
                title = stringResource(R.string.settings_quick_capture_title),
                subtitle = stringResource(R.string.settings_quick_capture_subtitle),
                checked = quickCaptureState.enabled,
                onCheckedChange = { enabled ->
                    scope.launch { quickCapturePrefs.setEnabled(enabled) }
                },
            )
        }
        GroupedListItem(
            position = GroupPosition.LAST,
            modifier =
                Modifier
                    .zIndex(if (snoozeTypeHighlightActive) 1f else 0f)
                    .pulsingSectionHighlightOutline(
                        active = snoozeTypeHighlightActive,
                        outlineColor =
                            MaterialTheme.colorScheme.primary.copy(alpha = snoozeTypeHighlightAlpha),
                        expandDp = 4.dp,
                        cornerRadiusDp = 12.dp,
                    ),
        ) {
            SnoozeTypeRow(
                snoozeType = reminderState.snoozeType,
                onSelect = { selected ->
                    scope.launch { reminderPrefs.setSnoozeType(selected) }
                },
            )
        }
    }
}

/**
 * Item-level deep-link highlight for a single settings row.
 *
 * The request id is a monotonic counter owned by [SettingsRoute]; acknowledging it in
 * `rememberSaveable` state is what lets the same row be deep-linked twice in a row. Both halves of
 * that handshake must survive configuration change and back-stack removal together, otherwise the
 * second deep link raises a request id the acknowledger has already seen and nothing pulses.
 */
@Composable
private fun rememberSettingsItemHighlightActive(
    itemKey: String,
    highlightItemKey: String?,
    highlightItemRequestId: Int,
): Boolean {
    var highlighted by rememberSaveable { mutableStateOf(false) }
    var highlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var handledHighlightRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(highlightItemKey, highlightItemRequestId) {
        if (highlightItemKey == itemKey && highlightItemRequestId != handledHighlightRequestId) {
            handledHighlightRequestId = highlightItemRequestId
            highlighted = true
            highlightExpiresAtMillis =
                SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
        } else if (highlightItemKey != itemKey) {
            highlighted = false
            highlightExpiresAtMillis = 0L
        }
    }
    LaunchedEffect(highlighted, highlightExpiresAtMillis) {
        if (!highlighted) return@LaunchedEffect
        val remainingHighlightMillis = highlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        highlighted = false
        highlightExpiresAtMillis = 0L
    }
    return highlighted && highlightExpiresAtMillis > SystemClock.elapsedRealtime()
}

@Composable
private fun SnoozeTypeRow(
    snoozeType: SnoozeType,
    onSelect: (SnoozeType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabelRes =
        when (snoozeType) {
            SnoozeType.RELATIVE -> R.string.settings_snooze_type_presets
            SnoozeType.ABSOLUTE -> R.string.settings_snooze_type_duration
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "snooze",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_snooze_type_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_snooze_type_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(selectedLabelRes))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                SnoozeType.entries.forEach { option ->
                    val optionLabelRes =
                        when (option) {
                            SnoozeType.RELATIVE -> R.string.settings_snooze_type_presets
                            SnoozeType.ABSOLUTE -> R.string.settings_snooze_type_duration
                        }
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(optionLabelRes)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Builds the intent that opens the system "app notifications" settings page for this
 * package. Used for both the row click (when notifications are already on, taps go
 * directly to system settings) and the switch (when the user wants to disable, we
 * route through system settings rather than revoking silently).
 */
private fun notificationsAppSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

@SuppressLint("BatteryLife")
private fun batteryOptimizationIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
    }
