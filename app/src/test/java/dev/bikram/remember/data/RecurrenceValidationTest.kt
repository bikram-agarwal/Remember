package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class RecurrenceValidationTest {
    private val monday = LocalDate.of(2026, 9, 7).atTime(9, 0).atZone(ZoneId.systemDefault())

    @Test
    fun nonpositive_intervals_never_repeat_the_same_or_an_earlier_instant() {
        RecurrenceUnit.entries.forEach { unit ->
            assertNull(RecurrenceRule(unit, interval = 0).nextAfter(monday.toInstant().toEpochMilli()))
            assertNull(RecurrenceRule(unit, interval = -1).nextAfter(monday.toInstant().toEpochMilli()))
        }
    }

    @Test(timeout = 1_000L)
    fun invalid_monthly_weekdays_and_ordinals_do_not_loop_or_roll_into_another_month() {
        listOf(
            MonthlyMode.ByNthWeekday(1, 0),
            MonthlyMode.ByNthWeekday(1, 8),
            MonthlyMode.ByNthWeekday(0, Calendar.MONDAY),
            MonthlyMode.ByNthWeekday(6, Calendar.MONDAY),
            MonthlyMode.ByDayOfMonth(0),
            MonthlyMode.ByDayOfMonth(32),
        ).forEach { mode ->
            assertNull(RecurrenceRule(RecurrenceUnit.MONTH, monthlyMode = mode).nextAfter(monday.toInstant().toEpochMilli()))
        }
    }

    @Test(timeout = 1_000L)
    fun large_weekly_intervals_are_computed_without_integer_overflow_or_a_day_by_day_scan() {
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, interval = Int.MAX_VALUE, daysOfWeek = setOf(Calendar.MONDAY))
        assertEquals(
            monday.plusWeeks(Int.MAX_VALUE.toLong()).toInstant().toEpochMilli(),
            rule.nextAfter(monday.toInstant().toEpochMilli()),
        )
    }

    @Test
    fun selected_days_in_the_current_week_precede_the_next_eligible_week() {
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, interval = 3, daysOfWeek = setOf(Calendar.MONDAY, Calendar.FRIDAY))
        val friday = monday.plusDays(4)
        assertEquals(friday.toInstant().toEpochMilli(), rule.nextAfter(monday.toInstant().toEpochMilli()))
        assertEquals(monday.plusWeeks(3).toInstant().toEpochMilli(), rule.nextAfter(friday.toInstant().toEpochMilli()))
    }
}
