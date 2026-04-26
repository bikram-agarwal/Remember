package dev.bikram.remember.reminders

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.edit.CalendarPickerDialog
import dev.bikram.remember.ui.edit.ReminderTimePickerDialog
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.RememberTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SnoozeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Stack the snooze dialog OVER the lock screen / home screen rather than waking
        // the user back into the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        // Theme.Remember.Translucent declares the transparent system bars; we just
        // need to lay out edge-to-edge so Compose draws into that area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) {
            finish()
            return
        }

        // Pull the user's selected color theme + interaction prefs, same pattern
        // as MainActivity, so the snooze sheet honors their seed color, dark / black
        // mode, palette style, etc. instead of falling back to the M3 defaults.
        val container = (application as RememberApp).container

        setContent {
            val themeState by container.themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val tagColors by container.tagRepository.observeTagColorMap().collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            val interactionState by container.interactionPrefs.state.collectAsStateWithLifecycle(
                initialValue = InteractionState(),
            )
            RememberTheme(
                themeState = themeState.copy(tagColors = tagColors),
                interactionState = interactionState,
                paintBackground = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Scrim so the home / caller activity behind the snooze dialog
                        // is dimmed and the user's focus lands on the floating window.
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    SnoozeDialogContent(
                        onSnooze = { timeMillis -> snoozeAndFinish(noteId, timeMillis) },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun snoozeAndFinish(noteId: Long, timeMillis: Long) {
        val app = applicationContext as RememberApp
        val repo = app.container.noteRepository

        // Show the confirmation toast before launching the DB write so the user gets
        // immediate feedback even if the activity is finishing. Toast posts to the
        // system service and survives the activity destruction.
        Toast.makeText(
            applicationContext,
            formatSnoozeConfirmation(applicationContext, timeMillis),
            Toast.LENGTH_LONG,
        ).show()

        lifecycleScope.launch {
            val noteWithItems = repo.get(noteId)
            if (noteWithItems != null) {
                val note = noteWithItems.note
                val opts = dev.bikram.remember.data.NoteOptions(
                    reminderAt = timeMillis,
                    importance = note.importance,
                    visibility = note.visibility,
                    pictureUri = note.pictureUri,
                    pictureHeroFraming = note.pictureHeroFraming,
                    locked = note.locked,
                    iconKey = note.iconKey,
                    actions = note.actions,
                    tags = note.tags,
                    recurrence = note.recurrence,
                )
                if (note.kind == dev.bikram.remember.data.NoteKind.NOTE) {
                    repo.updateNote(note.id, note.title, note.body, note.colorIndex, opts)
                } else {
                    val persistable = noteWithItems.items.map {
                        dev.bikram.remember.data.PersistableChecklistItem(
                            localKey = it.id,
                            text = it.text,
                            checked = it.checked,
                            sortOrder = it.sortOrder,
                            parentLocalKey = it.parentId,
                            depth = it.depth,
                        )
                    }
                    repo.updateList(note.id, note.title, note.colorIndex, persistable, opts)
                }
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(ReminderScheduler.pendingRequestCodeForNote(noteId))

            finish()
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

/**
 * Smart snooze sheet. Each row commits the user to an absolute target time
 * (e.g. "5:30 PM" today), not a duration relative to "now", so users don't have
 * to do mental math. Times round to clean :00 / :15 boundaries; the preset
 * list adapts to time-of-day so options like "This evening" disappear once
 * it's late. The trailing "Pick a specific time" row falls through to the
 * existing date + time picker dialogs for the long-tail case.
 */
@Composable
fun SnoozeDialogContent(onSnooze: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Capture once at composition. We never re-read the wall clock during the
    // session; if the user lingers in the sheet for hours the absolute targets
    // would otherwise drift, and recomputing every recomposition would shift
    // the visible labels mid-tap.
    val nowMillis = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(nowMillis, zone) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    }
    val timeFormatter = remember(context) { timeFormatterFor(context) }
    val nowLabel = remember(now, timeFormatter) {
        val dayPart = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        val timePart = now.format(timeFormatter)
        context.getString(R.string.snooze_activity_now_format, "$dayPart $timePart")
    }
    val presets = remember(now) { computeSnoozePresets(context, now, timeFormatter) }

    var customDateMillis by remember { mutableStateOf<Long?>(null) }
    var customTimePickerOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Tonal lift tints the surface (Material's compositing) and shadow gives the
        // real drop-shadow that makes the dialog look lifted off the dimmed scrim.
        // Tonal alone does not paint a shadow at all in dark mode.
        tonalElevation = 6.dp,
        shadowElevation = 24.dp,
        modifier = Modifier
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
                    text = stringResource(R.string.snooze_activity_title).lowercase(Locale.getDefault()),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RememberTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    }

    // "Pick a specific time" flow: date picker → time picker → onSnooze.
    val pendingDate = customDateMillis
    if (pendingDate != null && !customTimePickerOpen) {
        CalendarPickerDialog(
            initial = pendingDate,
            onConfirm = { dayMillis ->
                customDateMillis = dayMillis
                customTimePickerOpen = true
            },
            onDismiss = { customDateMillis = null },
        )
    }
    if (customTimePickerOpen) {
        ReminderTimePickerDialog(
            initialHour = roundUpToNextHalfHour(now).hour,
            initialMinute = roundUpToNextHalfHour(now).minute,
            onConfirm = { hour, minute ->
                val day = customDateMillis ?: pickerDayMillisForLocalWallClock(nowMillis)
                val target = combineDayAndLocalTime(day, hour, minute, zone)
                customTimePickerOpen = false
                customDateMillis = null
                onSnooze(target)
            },
            onDismiss = {
                customTimePickerOpen = false
                customDateMillis = null
            },
        )
    }
}

private data class SnoozePreset(
    val title: String,
    val subtitle: String,
    val absoluteTime: String,
    val targetMillis: Long,
    val symbolName: String,
    val dividerBefore: Boolean = false,
)

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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .tapSoundClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
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
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Build the smart preset list. Adapts to time-of-day:
 * - "Soon" is always present (next :15, with a +10 min floor so it isn't
 *   instantly past).
 * - "Later today" only shows when now + 3h still lands inside today and before
 *   ~10 PM (otherwise it overlaps with the late-night options).
 * - "This evening" only shows when before 8 PM. After 8 PM it's swapped for
 *   "Late tonight" (today 11 PM) when applicable.
 * - "Tomorrow morning / afternoon" and "Next week" are always present.
 */
private fun computeSnoozePresets(
    context: android.content.Context,
    now: ZonedDateTime,
    timeFormatter: DateTimeFormatter,
): List<SnoozePreset> {
    val presets = mutableListOf<SnoozePreset>()
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val zone = now.zone

    // Soon: round up to next :15, with a +10 min floor.
    val soonTarget = run {
        val rounded = roundUpToNextQuarterHour(now)
        if (rounded.toInstant().toEpochMilli() - now.toInstant().toEpochMilli() < 10 * 60_000L) {
            rounded.plusMinutes(15)
        } else rounded
    }
    val soonMins = ((soonTarget.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()) / 60_000L).toInt()
    presets += SnoozePreset(
        title = context.getString(R.string.snooze_preset_soon),
        subtitle = context.getString(R.string.snooze_subtitle_in_minutes, soonMins),
        absoluteTime = soonTarget.format(timeFormatter),
        targetMillis = soonTarget.toInstant().toEpochMilli(),
        symbolName = "schedule",
    )

    // "Later today" - only emit if it gives the user meaningful separation from
    // the next-named-time-of-day preset below ("This evening" at 9 PM, or "Late
    // tonight" at 11 PM after 8 PM). At 5:47 PM `laterToday` rounds up to 9 PM
    // and collides with "This evening", so we'd be offering two rows committing
    // to the same time. Drop "Later today" when it lands within 60 minutes of
    // the next named slot.
    val laterToday = roundUpToNextHour(now.plusHours(3))
    val nextNamedSlotMillis: Long? = when {
        now.hour < 20 -> today.atTime(LocalTime.of(21, 0)).atZone(zone).toInstant().toEpochMilli()
        now.hour in 20 until 22 -> today.atTime(LocalTime.of(23, 0)).atZone(zone).toInstant().toEpochMilli()
        else -> null
    }
    val laterTodayMillis = laterToday.toInstant().toEpochMilli()
    val laterTodayWellSeparated = nextNamedSlotMillis == null ||
        (nextNamedSlotMillis - laterTodayMillis) >= 60 * 60_000L
    if (laterToday.toLocalDate() == today && laterToday.hour < 22 && laterTodayWellSeparated) {
        val hours = ((laterTodayMillis - now.toInstant().toEpochMilli()) / 3_600_000L).toInt()
        presets += SnoozePreset(
            title = context.getString(R.string.snooze_preset_later_today),
            subtitle = context.getString(R.string.snooze_subtitle_in_hours, hours),
            absoluteTime = laterToday.format(timeFormatter),
            targetMillis = laterTodayMillis,
            symbolName = "wb_twilight",
        )
    }

    if (now.hour < 20) {
        val evening = today.atTime(LocalTime.of(21, 0)).atZone(zone)
        presets += SnoozePreset(
            title = context.getString(R.string.snooze_preset_this_evening),
            subtitle = context.getString(R.string.snooze_subtitle_tonight),
            absoluteTime = evening.format(timeFormatter),
            targetMillis = evening.toInstant().toEpochMilli(),
            symbolName = "bedtime",
        )
    } else if (now.hour in 20 until 22) {
        // Past dinnertime but not yet bed: offer a late-tonight option at 11 PM.
        val lateTonight = today.atTime(LocalTime.of(23, 0)).atZone(zone)
        presets += SnoozePreset(
            title = context.getString(R.string.snooze_preset_late_tonight),
            subtitle = context.getString(R.string.snooze_subtitle_tonight),
            absoluteTime = lateTonight.format(timeFormatter),
            targetMillis = lateTonight.toInstant().toEpochMilli(),
            symbolName = "bedtime",
        )
    }

    val tomorrowDayLabel = tomorrow.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        Locale.getDefault(),
    )
    val tomorrowMorning = tomorrow.atTime(LocalTime.of(9, 0)).atZone(zone)
    presets += SnoozePreset(
        title = context.getString(R.string.snooze_preset_tomorrow_morning),
        subtitle = tomorrowDayLabel,
        absoluteTime = tomorrowMorning.format(timeFormatter),
        targetMillis = tomorrowMorning.toInstant().toEpochMilli(),
        symbolName = "wb_sunny",
        dividerBefore = true,
    )

    val tomorrowAfternoon = tomorrow.atTime(LocalTime.of(14, 0)).atZone(zone)
    presets += SnoozePreset(
        title = context.getString(R.string.snooze_preset_tomorrow_afternoon),
        subtitle = tomorrowDayLabel,
        absoluteTime = tomorrowAfternoon.format(timeFormatter),
        targetMillis = tomorrowAfternoon.toInstant().toEpochMilli(),
        // light_mode is Material's full-disc sun glyph, distinct from wb_sunny's
        // ray-burst sun used for "Tomorrow morning". Keeping both rows sun-themed
        // (no clock) so they read as time-of-day rather than generic snooze slots.
        symbolName = "light_mode",
    )

    // Next week: roll forward to next Monday at 9 AM.
    val daysToMonday = ((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7).let {
        if (it == 0) 7 else it
    }
    val nextMonday = today.plusDays(daysToMonday.toLong())
    val nextWeek = nextMonday.atTime(LocalTime.of(9, 0)).atZone(zone)
    presets += SnoozePreset(
        title = context.getString(R.string.snooze_preset_next_week),
        subtitle = nextMonday.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT,
            Locale.getDefault(),
        ),
        absoluteTime = nextWeek.format(timeFormatter),
        targetMillis = nextWeek.toInstant().toEpochMilli(),
        symbolName = "event",
    )

    return presets
}

private fun roundUpToNextQuarterHour(t: ZonedDateTime): ZonedDateTime {
    val minute = t.minute
    val rem = minute % 15
    val add = if (rem == 0 && t.second == 0 && t.nano == 0) 0 else 15 - rem
    return t.withSecond(0).withNano(0).plusMinutes(add.toLong())
}

private fun roundUpToNextHalfHour(t: ZonedDateTime): ZonedDateTime {
    val minute = t.minute
    val rem = minute % 30
    val add = if (rem == 0 && t.second == 0 && t.nano == 0) 0 else 30 - rem
    return t.withSecond(0).withNano(0).plusMinutes(add.toLong())
}

private fun roundUpToNextHour(t: ZonedDateTime): ZonedDateTime {
    val mins = t.minute
    return if (mins == 0 && t.second == 0 && t.nano == 0) {
        t
    } else {
        t.withMinute(0).withSecond(0).withNano(0).plusHours(1)
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
    return localDate.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

private fun timeFormatterFor(context: android.content.Context): DateTimeFormatter {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}

/**
 * Build the snooze confirmation toast text. Branches on whether [targetMillis]
 * falls today, tomorrow, or further out so the message reads naturally
 * ("Reminder set for 5:30 PM today" / "Reminder set for tomorrow at 9:00 AM" /
 * "Reminder set for Mon, Apr 28 at 9:00 AM"). Built fresh per-call - this is
 * called from [snoozeAndFinish] which fires once per snooze.
 */
private fun formatSnoozeConfirmation(context: android.content.Context, targetMillis: Long): String {
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
