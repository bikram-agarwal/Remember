package dev.bikram.remember.reminders

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.SnoozeType
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.edit.CalendarPickerDialog
import dev.bikram.remember.ui.edit.ReminderTimePickerDialog
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.tags.LocalTagColors
import dev.bikram.remember.ui.theme.RememberTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import android.graphics.Color as AndroidColor

@AndroidEntryPoint
class SnoozeActivity : ComponentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var tagRepository: TagRepository

    @Inject lateinit var themePrefs: ThemePrefs

    @Inject lateinit var interactionPrefs: InteractionPrefs

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Stack the snooze dialog OVER the lock screen / home screen rather than waking
        // the user back into the app.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Theme.Remember.Translucent declares the transparent system bars; we just
        // need to lay out edge-to-edge so Compose draws into that area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) {
            finish()
            return
        }

        // Pull the user's selected color theme + interaction prefs, same pattern
        // as MainActivity, so the snooze sheet honors their seed color, dark / black
        // mode, palette style, etc. instead of falling back to the M3 defaults.

        setContent {
            val themeState by themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val tagColorFlow = remember(tagRepository) { tagRepository.observeTagColorMap() }
            val tagColors by tagColorFlow.collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
                initialValue = InteractionState(),
            )
            val reminderState by reminderPrefs.state.collectAsStateWithLifecycle(
                initialValue = ReminderPreferencesState(),
            )
            CompositionLocalProvider(LocalTagColors provides tagColors) {
                RememberTheme(
                    themeState = themeState,
                    paintBackground = false,
                    hapticFeedbackEnabled = interactionState.hapticFeedbackEnabled,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                // Scrim so the home / caller activity behind the snooze dialog
                                // is dimmed and the user's focus lands on the floating window.
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { finish() },
                        contentAlignment = Alignment.Center,
                    ) {
                        SnoozeDialogContent(
                            snoozeType = reminderState.snoozeType,
                            onSnooze = { timeMillis -> snoozeAndFinish(noteId, timeMillis) },
                            onDismiss = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun snoozeAndFinish(
        noteId: Long,
        timeMillis: Long,
    ) {
        applicationScope.launch {
            val snoozeSucceeded =
                runCatching {
                    noteRepository.snoozeSoonestReminder(noteId, timeMillis)
                }.onFailure { error ->
                    DiagnosticLog.record(applicationContext, "Reminder snooze failed for noteId=$noteId", error)
                }.getOrDefault(false)
            if (snoozeSucceeded) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(ReminderScheduler.pendingRequestCodeForNote(noteId))
                for (reminderIndex in 0 until ReminderScheduler.MAX_REMINDERS_PER_NOTE) {
                    notificationManager.cancel(ReminderScheduler.pendingRequestCodeForNoteReminder(noteId, reminderIndex))
                }
            }
            Toast
                .makeText(
                    applicationContext,
                    if (snoozeSucceeded) {
                        formatSnoozeConfirmation(applicationContext, timeMillis)
                    } else {
                        getString(R.string.reminder_snooze_failed)
                    },
                    Toast.LENGTH_LONG,
                ).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

/**
 * Snooze sheet with named target times or a duration starting at confirmation.
 * Presets round to clean :00 / :15 boundaries and adapt to time of day, so options
 * like "This evening" disappear once it is late. Both modes offer the existing
 * date and time picker dialogs through "Pick a specific time".
 */
@Composable
fun SnoozeDialogContent(
    snoozeType: SnoozeType = SnoozeType.RELATIVE,
    onSnooze: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    // Keep named preset targets and labels stable during this session. Duration snoozes
    // read the clock separately when confirmed so the full requested delay starts then.
    val nowMillis = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val now =
        remember(nowMillis, zone) {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        }
    val timeFormatter = remember(context) { timeFormatterFor(context) }
    val nowLabel =
        remember(now, timeFormatter, locale, resources) {
            val dayPart = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
            val timePart = now.format(timeFormatter)
            resources.getString(R.string.snooze_activity_now_format, "$dayPart $timePart")
        }
    val presets = remember(now) { computeSnoozePresets(context, now, timeFormatter) }

    var customDateMillis by remember { mutableStateOf<Long?>(null) }
    var customTimePickerOpen by remember { mutableStateOf(false) }
    var durationValue by remember { mutableStateOf(10) }
    var durationUnit by remember { mutableStateOf(SnoozeDurationUnit.MINUTES) }
    var durationValueExpanded by remember { mutableStateOf(false) }
    var durationUnitExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Tonal lift tints the surface (Material's compositing) and shadow gives the
        // real drop-shadow that makes the dialog look lifted off the dimmed scrim.
        // Tonal alone does not paint a shadow at all in dark mode.
        tonalElevation = 6.dp,
        shadowElevation = 24.dp,
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 360.dp)
                // Swallow scrim taps so tapping inside the dialog does not dismiss it.
                .clickable(enabled = false) {},
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.snooze_activity_title).lowercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nowLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.snooze_activity_subtitle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(16.dp))

            if (snoozeType == SnoozeType.RELATIVE) {
                SnoozeRelativePresets(
                    presets = presets,
                    onSnooze = onSnooze,
                )
            } else {
                SnoozeDurationChooser(
                    durationValue = durationValue,
                    onDurationValueChange = { nextValue -> durationValue = nextValue },
                    durationUnit = durationUnit,
                    onDurationUnitChange = { nextUnit ->
                        durationUnit = nextUnit
                        durationValue = durationValue.coerceAtMost(nextUnit.maximumValue)
                    },
                    durationValueExpanded = durationValueExpanded,
                    onDurationValueExpandedChange = { expanded -> durationValueExpanded = expanded },
                    durationUnitExpanded = durationUnitExpanded,
                    onDurationUnitExpandedChange = { expanded -> durationUnitExpanded = expanded },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            SnoozePresetRow(
                symbolName = "more_time",
                title = stringResource(R.string.snooze_preset_pick_custom),
                subtitle = stringResource(R.string.snooze_preset_pick_custom_subtitle),
                trailing = null,
                onClick = { customDateMillis = pickerDayMillisForLocalWallClock(nowMillis) },
            )

            Spacer(Modifier.height(8.dp))
            SnoozeSheetButtons(
                showSnooze = snoozeType == SnoozeType.ABSOLUTE,
                onDismiss = onDismiss,
                onSnooze = {
                    onSnooze(durationUnit.targetMillis(durationValue))
                },
            )
        }
    }

    SnoozeCustomTimePickers(
        now = now,
        nowMillis = nowMillis,
        zone = zone,
        customDateMillis = customDateMillis,
        customTimePickerOpen = customTimePickerOpen,
        onCustomDateMillisChange = { nextDate -> customDateMillis = nextDate },
        onCustomTimePickerOpenChange = { open -> customTimePickerOpen = open },
        onSnooze = onSnooze,
    )
}

@Composable
private fun SnoozeRelativePresets(
    presets: List<SnoozePreset>,
    onSnooze: (Long) -> Unit,
) {
    presets.forEachIndexed { index, preset ->
        if (preset.dividerBefore && index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        SnoozePresetRow(
            symbolName = preset.symbolName,
            title = preset.title,
            subtitle = preset.subtitle,
            trailing = preset.absoluteTime,
            onClick = { onSnooze(preset.targetMillis) },
        )
    }
}

@Composable
private fun SnoozeDurationChooser(
    durationValue: Int,
    onDurationValueChange: (Int) -> Unit,
    durationUnit: SnoozeDurationUnit,
    onDurationUnitChange: (SnoozeDurationUnit) -> Unit,
    durationValueExpanded: Boolean,
    onDurationValueExpandedChange: (Boolean) -> Unit,
    durationUnitExpanded: Boolean,
    onDurationUnitExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberOutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onDurationValueExpandedChange(true) },
        ) {
            Text(durationValue.toString())
            DropdownMenu(
                expanded = durationValueExpanded,
                onDismissRequest = { onDurationValueExpandedChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                for (durationOption in 1..durationUnit.maximumValue) {
                    RememberDropdownMenuItem(
                        text = { Text(durationOption.toString()) },
                        onClick = {
                            onDurationValueChange(durationOption)
                            onDurationValueExpandedChange(false)
                        },
                    )
                }
            }
        }
        RememberOutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onDurationUnitExpandedChange(true) },
        ) {
            Text(stringResource(durationUnit.labelRes))
            DropdownMenu(
                expanded = durationUnitExpanded,
                onDismissRequest = { onDurationUnitExpandedChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                SnoozeDurationUnit.entries.forEach { unitOption ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(unitOption.labelRes)) },
                        onClick = {
                            onDurationUnitChange(unitOption)
                            onDurationUnitExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnoozeSheetButtons(
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberTextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_cancel))
        }
        if (showSnooze) {
            RememberButton(onClick = onSnooze) {
                Text(stringResource(R.string.action_type_snooze))
            }
        }
    }
}

@Composable
private fun SnoozeCustomTimePickers(
    now: ZonedDateTime,
    nowMillis: Long,
    zone: ZoneId,
    customDateMillis: Long?,
    customTimePickerOpen: Boolean,
    onCustomDateMillisChange: (Long?) -> Unit,
    onCustomTimePickerOpenChange: (Boolean) -> Unit,
    onSnooze: (Long) -> Unit,
) {
    val pendingDate = customDateMillis
    if (pendingDate != null && !customTimePickerOpen) {
        CalendarPickerDialog(
            initial = pendingDate,
            onConfirm = { dayMillis ->
                onCustomDateMillisChange(dayMillis)
                onCustomTimePickerOpenChange(true)
            },
            onDismiss = { onCustomDateMillisChange(null) },
        )
    }
    if (customTimePickerOpen) {
        ReminderTimePickerDialog(
            initialHour = roundUpToNextHalfHour(now).hour,
            initialMinute = roundUpToNextHalfHour(now).minute,
            onConfirm = { hour, minute ->
                val day = customDateMillis ?: pickerDayMillisForLocalWallClock(nowMillis)
                val target = combineDayAndLocalTime(day, hour, minute, zone)
                onCustomTimePickerOpenChange(false)
                onCustomDateMillisChange(null)
                onSnooze(target)
            },
            onDismiss = {
                onCustomTimePickerOpenChange(false)
                onCustomDateMillisChange(null)
            },
        )
    }
}

internal enum class SnoozeDurationUnit(
    val maximumValue: Int,
    val labelRes: Int,
) {
    MINUTES(60, R.string.snooze_duration_minutes),
    HOURS(24, R.string.snooze_duration_hours),
    DAYS(30, R.string.snooze_duration_days),
    ;

    fun targetMillis(
        value: Int,
        clock: Clock = Clock.systemDefaultZone(),
    ): Long {
        val now = ZonedDateTime.now(clock)
        val target =
            when (this) {
                MINUTES -> now.plusMinutes(value.toLong())
                HOURS -> now.plusHours(value.toLong())
                DAYS -> now.plusDays(value.toLong())
            }
        return target.toInstant().toEpochMilli()
    }
}

@Composable
private fun SnoozePresetRow(
    symbolName: String,
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    // Every chip uses the same muted neutral surface so no row reads as the
    // "primary" option visually - the difference between rows is the icon and
    // the time string. Using tertiaryContainer for the tomorrow group made those
    // rows pop on Material You schemes (often a saturated hue) and broke the
    // "list of equal options" reading.
    val accentBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val accentFg = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .appClickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentBg),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = symbolName,
                size = 18.dp,
                tint = accentFg,
                weight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                autoMirror = true,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}

/** Match the formatter used by [CalendarPickerDialog]: UTC start-of-day epoch. */
private fun pickerDayMillisForLocalWallClock(wallClockEpochMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val localDate = Instant.ofEpochMilli(wallClockEpochMillis).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun combineDayAndLocalTime(
    pickerDayMillis: Long,
    hour: Int,
    minute: Int,
    zone: ZoneId,
): Long {
    val localDate = Instant.ofEpochMilli(pickerDayMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return localDate
        .atTime(LocalTime.of(hour, minute))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

private fun timeFormatterFor(context: android.content.Context): DateTimeFormatter {
    val pattern =
        if (android.text.format.DateFormat
                .is24HourFormat(context)
        ) {
            "HH:mm"
        } else {
            "h:mm a"
        }
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}

/**
 * Build the snooze confirmation toast text. Branches on whether [targetMillis]
 * falls today, tomorrow, or further out so the message reads naturally
 * ("Reminder set for 5:30 PM today" / "Reminder set for tomorrow at 9:00 AM" /
 * "Reminder set for Mon, Apr 28 at 9:00 AM"). Built fresh per-call - this is
 * called from [snoozeAndFinish] which fires once per snooze.
 */
private fun formatSnoozeConfirmation(
    context: android.content.Context,
    targetMillis: Long,
): String {
    val zone = ZoneId.systemDefault()
    val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMillis), zone)
    val now = ZonedDateTime.now(zone)
    val timeFormatter = timeFormatterFor(context)
    val timeStr = target.format(timeFormatter)
    val targetDate = target.toLocalDate()
    val today = now.toLocalDate()
    return when (targetDate) {
        today -> context.getString(R.string.snooze_toast_today, timeStr)
        today.plusDays(1) -> context.getString(R.string.snooze_toast_tomorrow, timeStr)
        else -> {
            val dayPattern = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            context.getString(R.string.snooze_toast_future, target.format(dayPattern), timeStr)
        }
    }
}
