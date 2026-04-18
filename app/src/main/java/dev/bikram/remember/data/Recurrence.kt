package dev.bikram.remember.data

import org.json.JSONObject
import java.util.Calendar

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

enum class RecurrenceEndKind { NEVER, ON_DATE, AFTER_COUNT }

/** Monthly recurrence sub-rule. Either by fixed day-of-month, or by Nth weekday. */
sealed class MonthlyMode {
    /** Fire on the N-th day of the month (1..31). If the month is shorter the closest valid day is used. */
    data class ByDayOfMonth(val day: Int) : MonthlyMode()

    /**
     * Fire on the [ordinal]-th [weekday] of the month.
     * ordinal: 1=first, 2=second, 3=third, 4=fourth, 5=last (uses LAST).
     * weekday: Calendar.SUNDAY (1) … Calendar.SATURDAY (7).
     */
    data class ByNthWeekday(val ordinal: Int, val weekday: Int) : MonthlyMode()
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
    /** Remaining fires when endKind == AFTER_COUNT. Decrements per fire. Null otherwise. */
    val endCount: Int? = null,
) {
    /**
     * Compute the next fire time strictly after [afterMillis].
     * Returns null when the rule is exhausted by [endDate]. Count handling is done
     * by the caller (see NoteRepository.advanceReminderOnFire).
     */
    fun nextAfter(afterMillis: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = afterMillis }
        when (unit) {
            RecurrenceUnit.DAY -> cal.add(Calendar.DAY_OF_MONTH, interval)
            RecurrenceUnit.WEEK -> {
                if (daysOfWeek.isEmpty()) {
                    cal.add(Calendar.WEEK_OF_YEAR, interval)
                } else {
                    // Advance one day at a time until hitting a matching weekday
                    // inside the next allowed week block.
                    val maxIter = 7 * interval + 7
                    var iter = 0
                    while (iter < maxIter) {
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        iter++
                        if (cal.get(Calendar.DAY_OF_WEEK) in daysOfWeek) break
                    }
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

    /** Returned rule after consuming one fire — decrements count if applicable. */
    fun afterFire(): RecurrenceRule = when (endKind) {
        RecurrenceEndKind.AFTER_COUNT -> copy(endCount = (endCount ?: 1) - 1)
        else -> this
    }

    companion object {
        fun toJson(rule: RecurrenceRule?): String? {
            if (rule == null) return null
            val o = JSONObject()
            o.put("u", rule.unit.name)
            o.put("i", rule.interval)
            if (rule.daysOfWeek.isNotEmpty()) {
                o.put("dw", rule.daysOfWeek.sorted().joinToString(","))
            }
            rule.monthlyMode?.let { mm ->
                when (mm) {
                    is MonthlyMode.ByDayOfMonth -> o.put("md", mm.day)
                    is MonthlyMode.ByNthWeekday -> {
                        o.put("mo", mm.ordinal)
                        o.put("mw", mm.weekday)
                    }
                }
            }
            o.put("ek", rule.endKind.name)
            rule.endDate?.let { o.put("ed", it) }
            rule.endCount?.let { o.put("ec", it) }
            return o.toString()
        }

        fun fromJson(value: String?): RecurrenceRule? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(value)
                val mode = when {
                    o.has("md") -> MonthlyMode.ByDayOfMonth(o.getInt("md"))
                    o.has("mo") -> MonthlyMode.ByNthWeekday(o.getInt("mo"), o.getInt("mw"))
                    else -> null
                }
                RecurrenceRule(
                    unit = RecurrenceUnit.valueOf(o.getString("u")),
                    interval = o.optInt("i", 1),
                    daysOfWeek = o.optString("dw", "").takeIf { it.isNotBlank() }
                        ?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
                    monthlyMode = mode,
                    endKind = RecurrenceEndKind.valueOf(o.optString("ek", RecurrenceEndKind.NEVER.name)),
                    endDate = if (o.has("ed")) o.getLong("ed") else null,
                    endCount = if (o.has("ec")) o.getInt("ec") else null,
                )
            }.getOrNull()
        }
    }
}

private fun advanceToNthWeekday(cal: Calendar, ordinal: Int, weekday: Int) {
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
