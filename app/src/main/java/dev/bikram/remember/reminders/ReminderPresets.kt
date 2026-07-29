package dev.bikram.remember.reminders

import android.content.Context
import dev.bikram.remember.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class SnoozePreset(
    val title: String,
    val subtitle: String,
    val absoluteTime: String,
    val targetMillis: Long,
    val symbolName: String,
    val dividerBefore: Boolean = false,
)

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
internal fun computeSnoozePresets(
    context: Context,
    now: ZonedDateTime,
    timeFormatter: DateTimeFormatter,
): List<SnoozePreset> {
    val presets = mutableListOf<SnoozePreset>()
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val zone = now.zone

    // Soon: round up to next :15, with a +10 min floor.
    val soonTarget =
        run {
            val rounded = roundUpToNextQuarterHour(now)
            if (rounded.toInstant().toEpochMilli() - now.toInstant().toEpochMilli() < 10 * 60_000L) {
                rounded.plusMinutes(15)
            } else {
                rounded
            }
        }
    val soonMins = ((soonTarget.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()) / 60_000L).toInt()
    presets +=
        SnoozePreset(
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
    val nextNamedSlotMillis: Long? =
        when {
            now.hour < 20 ->
                today
                    .atTime(LocalTime.of(21, 0))
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            now.hour in 20 until 22 ->
                today
                    .atTime(LocalTime.of(23, 0))
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            else -> null
        }
    val laterTodayMillis = laterToday.toInstant().toEpochMilli()
    val laterTodayWellSeparated =
        nextNamedSlotMillis == null ||
            (nextNamedSlotMillis - laterTodayMillis) >= 60 * 60_000L
    if (laterToday.toLocalDate() == today && laterToday.hour < 22 && laterTodayWellSeparated) {
        val hours = ((laterTodayMillis - now.toInstant().toEpochMilli()) / 3_600_000L).toInt()
        presets +=
            SnoozePreset(
                title = context.getString(R.string.snooze_preset_later_today),
                subtitle = context.getString(R.string.snooze_subtitle_in_hours, hours),
                absoluteTime = laterToday.format(timeFormatter),
                targetMillis = laterTodayMillis,
                symbolName = "wb_twilight",
            )
    }

    if (now.hour < 20) {
        val evening = today.atTime(LocalTime.of(21, 0)).atZone(zone)
        presets +=
            SnoozePreset(
                title = context.getString(R.string.snooze_preset_this_evening),
                subtitle = context.getString(R.string.snooze_subtitle_tonight),
                absoluteTime = evening.format(timeFormatter),
                targetMillis = evening.toInstant().toEpochMilli(),
                symbolName = "bedtime",
            )
    } else if (now.hour in 20 until 22) {
        // Past dinnertime but not yet bed: offer a late-tonight option at 11 PM.
        val lateTonight = today.atTime(LocalTime.of(23, 0)).atZone(zone)
        presets +=
            SnoozePreset(
                title = context.getString(R.string.snooze_preset_late_tonight),
                subtitle = context.getString(R.string.snooze_subtitle_tonight),
                absoluteTime = lateTonight.format(timeFormatter),
                targetMillis = lateTonight.toInstant().toEpochMilli(),
                symbolName = "bedtime",
            )
    }

    val tomorrowDayLabel =
        tomorrow.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT,
            Locale.getDefault(),
        )
    val tomorrowMorning = tomorrow.atTime(LocalTime.of(9, 0)).atZone(zone)
    presets +=
        SnoozePreset(
            title = context.getString(R.string.snooze_preset_tomorrow_morning),
            subtitle = tomorrowDayLabel,
            absoluteTime = tomorrowMorning.format(timeFormatter),
            targetMillis = tomorrowMorning.toInstant().toEpochMilli(),
            symbolName = "wb_sunny",
            dividerBefore = true,
        )

    val tomorrowAfternoon = tomorrow.atTime(LocalTime.of(14, 0)).atZone(zone)
    presets +=
        SnoozePreset(
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
    val daysToMonday =
        ((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7).let {
            if (it == 0) 7 else it
        }
    val nextMonday = today.plusDays(daysToMonday.toLong())
    val nextWeek = nextMonday.atTime(LocalTime.of(9, 0)).atZone(zone)
    presets +=
        SnoozePreset(
            title = context.getString(R.string.snooze_preset_next_week),
            subtitle =
                nextMonday.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    Locale.getDefault(),
                ),
            absoluteTime = nextWeek.format(timeFormatter),
            targetMillis = nextWeek.toInstant().toEpochMilli(),
            symbolName = "event",
        )

    return presets
}

internal fun roundUpToNextQuarterHour(t: ZonedDateTime): ZonedDateTime {
    val minute = t.minute
    val rem = minute % 15
    val add = if (rem == 0 && t.second == 0 && t.nano == 0) 0 else 15 - rem
    return t.withSecond(0).withNano(0).plusMinutes(add.toLong())
}

internal fun roundUpToNextHalfHour(t: ZonedDateTime): ZonedDateTime {
    val minute = t.minute
    val rem = minute % 30
    val add = if (rem == 0 && t.second == 0 && t.nano == 0) 0 else 30 - rem
    return t.withSecond(0).withNano(0).plusMinutes(add.toLong())
}

internal fun roundUpToNextHour(t: ZonedDateTime): ZonedDateTime {
    val mins = t.minute
    return if (mins == 0 && t.second == 0 && t.nano == 0) {
        t
    } else {
        t
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1)
    }
}
