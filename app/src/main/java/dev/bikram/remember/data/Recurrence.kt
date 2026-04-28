package dev.bikram.remember.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

enum class RecurrenceEndKind { NEVER, ON_DATE, AFTER_COUNT }

/** Monthly recurrence sub-rule. Either by fixed day-of-month, or by Nth weekday. */
sealed class MonthlyMode {
    /** Fire on the N-th day of the month (1..31). If the month is shorter the closest valid day is used. */
    data class ByDayOfMonth(
        val day: Int,
    ) : MonthlyMode()

    /**
     * Fire on the [ordinal]-th [weekday] of the month.
     * ordinal: 1=first, 2=second, 3=third, 4=fourth, 5=last (uses LAST).
     * weekday: Calendar.SUNDAY (1) … Calendar.SATURDAY (7).
     */
    data class ByNthWeekday(
        val ordinal: Int,
        val weekday: Int,
    ) : MonthlyMode()
}

data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int = 1,
    /** Used only when [unit] == WEEK. Calendar.SUNDAY (1) … Calendar.SATURDAY (7). Empty = same weekday as reminderAt. */
    val daysOfWeek: Set<Int> = emptySet(),
    /** Used only when [unit] == MONTH. Null = same day-of-month as reminderAt. */
    val monthlyMode: MonthlyMode? = null,
    val endKind: RecurrenceEndKind = RecurrenceEndKind.NEVER,
    val endDate: Long? = null,
    /** Remaining fires when endKind == AFTER_COUNT. Decrements per fire; must be non-null when endKind is AFTER_COUNT (see [sanitized]). */
    val endCount: Int? = null,
) {
    /**
     * Compute the next fire time strictly after [afterMillis].
     * Returns null when the rule is exhausted by [endDate]. Count handling is done
     * by the repository when the user completes an occurrence.
     */
    fun nextAfter(afterMillis: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = afterMillis }
        when (unit) {
            RecurrenceUnit.DAY -> cal.add(Calendar.DAY_OF_MONTH, interval)
            RecurrenceUnit.WEEK -> {
                if (daysOfWeek.isEmpty()) {
                    cal.add(Calendar.WEEK_OF_YEAR, interval)
                } else {
                    val nextWeekly =
                        nextWeeklyWithDaysAfter(
                            afterMillis = afterMillis,
                            interval = interval,
                            daysOfWeek = daysOfWeek,
                        )
                    if (nextWeekly == null) return null
                    cal.timeInMillis = nextWeekly
                }
            }
            RecurrenceUnit.MONTH -> {
                when (val mode = monthlyMode) {
                    null -> cal.add(Calendar.MONTH, interval)
                    is MonthlyMode.ByDayOfMonth -> {
                        cal.add(Calendar.MONTH, interval)
                        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.DAY_OF_MONTH, minOf(mode.day, maxDay))
                    }
                    is MonthlyMode.ByNthWeekday -> {
                        cal.add(Calendar.MONTH, interval)
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        advanceToNthWeekday(cal, mode.ordinal, mode.weekday)
                    }
                }
            }
            RecurrenceUnit.YEAR -> cal.add(Calendar.YEAR, interval)
        }
        val next = cal.timeInMillis
        if (endKind == RecurrenceEndKind.ON_DATE && endDate != null && next > endDate) return null
        return next
    }

    /**
     * Normalizes invalid combinations so scheduling does not treat a missing count as one shot.
     * [AFTER_COUNT] requires a positive [endCount]; otherwise we behave like no end-by-count.
     */
    fun sanitized(): RecurrenceRule =
        if (endKind == RecurrenceEndKind.AFTER_COUNT && endCount == null) {
            copy(endKind = RecurrenceEndKind.NEVER, endCount = null)
        } else {
            this
        }

    /** Returned rule after consuming one fire — decrements count if applicable. */
    fun afterFire(): RecurrenceRule =
        when (endKind) {
            RecurrenceEndKind.AFTER_COUNT ->
                when (endCount) {
                    null -> this
                    else -> copy(endCount = (endCount - 1).coerceAtLeast(0))
                }
            else -> this
        }

    companion object {
        fun toJson(rule: RecurrenceRule?): String? {
            if (rule == null) return null
            val safe = rule.sanitized()
            val o = JSONObject()
            o.put("u", safe.unit.name)
            o.put("i", safe.interval)
            if (safe.daysOfWeek.isNotEmpty()) {
                o.put("dw", safe.daysOfWeek.sorted().joinToString(","))
            }
            safe.monthlyMode?.let { mm ->
                when (mm) {
                    is MonthlyMode.ByDayOfMonth -> o.put("md", mm.day)
                    is MonthlyMode.ByNthWeekday -> {
                        o.put("mo", mm.ordinal)
                        o.put("mw", mm.weekday)
                    }
                }
            }
            o.put("ek", safe.endKind.name)
            safe.endDate?.let { o.put("ed", it) }
            safe.endCount?.let { o.put("ec", it) }
            return o.toString()
        }

        fun fromJson(value: String?): RecurrenceRule? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(value)
                val mode =
                    when {
                        o.has("md") -> MonthlyMode.ByDayOfMonth(o.getInt("md"))
                        o.has("mo") -> MonthlyMode.ByNthWeekday(o.getInt("mo"), o.getInt("mw"))
                        else -> null
                    }
                RecurrenceRule(
                    unit = RecurrenceUnit.valueOf(o.getString("u")),
                    interval = o.optInt("i", 1),
                    daysOfWeek =
                        o
                            .optString("dw", "")
                            .takeIf { it.isNotBlank() }
                            ?.split(",")
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.toSet() ?: emptySet(),
                    monthlyMode = mode,
                    endKind = RecurrenceEndKind.valueOf(o.optString("ek", RecurrenceEndKind.NEVER.name)),
                    endDate = if (o.has("ed")) o.getLong("ed") else null,
                    endCount = if (o.has("ec")) o.getInt("ec") else null,
                ).sanitized()
            }.getOrNull()
        }
    }
}

private fun calendarDayOfWeek(day: DayOfWeek): Int =
    when (day) {
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
    }

private fun startOfIsoWeekMonday(date: java.time.LocalDate): java.time.LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Next fire strictly after [afterMillis] for WEEKLY rules with explicit [daysOfWeek].
 * [interval] counts ISO weeks from the week of the last occurrence: week offsets divisible
 * by [interval] are eligible (0 = same ISO week as [afterMillis]).
 *
 * Returns null when no slot exists within the search horizon (corrupt [daysOfWeek] values, or
 * exhaustive scan without a hit) so callers do not fabricate a bogus far-future instant.
 */
private fun nextWeeklyWithDaysAfter(
    afterMillis: Long,
    interval: Int,
    daysOfWeek: Set<Int>,
): Long? {
    if (daysOfWeek.any { day ->
            day < Calendar.SUNDAY || day > Calendar.SATURDAY
        }
    ) {
        return null
    }
    val safeInterval = interval.coerceAtLeast(1)
    val zone = ZoneId.systemDefault()
    val baseZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
    val wallClock = baseZdt.toLocalTime()
    val anchorMonday = startOfIsoWeekMonday(baseZdt.toLocalDate())
    var currentDate = baseZdt.toLocalDate().plusDays(1)
    val maxDays = 371 * safeInterval
    repeat(maxDays) {
        val candidateZdt = ZonedDateTime.of(currentDate, wallClock, zone)
        val candidateMillis = candidateZdt.toInstant().toEpochMilli()
        if (candidateMillis > afterMillis) {
            val calendarDow = calendarDayOfWeek(candidateZdt.dayOfWeek)
            if (calendarDow in daysOfWeek) {
                val candidateMonday = startOfIsoWeekMonday(currentDate)
                val weeksDiff = ChronoUnit.WEEKS.between(anchorMonday, candidateMonday)
                if (weeksDiff % safeInterval.toLong() == 0L) {
                    return candidateMillis
                }
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    return null
}

private fun advanceToNthWeekday(
    cal: Calendar,
    ordinal: Int,
    weekday: Int,
) {
    // cal is at the 1st of the target month. Find first matching weekday.
    while (cal.get(Calendar.DAY_OF_WEEK) != weekday) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    if (ordinal == 5) {
        // "Last" — keep advancing 7 days while still in the same month.
        val month = cal.get(Calendar.MONTH)
        while (true) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
            if (cal.get(Calendar.MONTH) != month) {
                cal.add(Calendar.DAY_OF_MONTH, -7)
                return
            }
        }
    } else {
        cal.add(Calendar.DAY_OF_MONTH, 7 * (ordinal - 1))
    }
}
