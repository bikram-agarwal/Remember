package dev.bikram.remember.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ReminderDurationPresetsTest {
    @Test
    fun durationChipsAreThirtyMinutesThroughSevenDays() {
        val amountsAndUnits =
            REMINDER_DURATION_CHIPS.map { chip -> chip.amount to chip.unit }
        assertEquals(
            listOf(
                30 to ChronoUnit.MINUTES,
                1 to ChronoUnit.HOURS,
                6 to ChronoUnit.HOURS,
                12 to ChronoUnit.HOURS,
                24 to ChronoUnit.HOURS,
                7 to ChronoUnit.DAYS,
            ),
            amountsAndUnits,
        )
    }

    @Test
    fun durationChipsOffsetFromNowWithoutRounding() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = ZonedDateTime.of(2026, 9, 23, 15, 7, 13, 0, zone)
        val expectedMillis =
            listOf(
                now.plusMinutes(30),
                now.plusHours(1),
                now.plusHours(6),
                now.plusHours(12),
                now.plusHours(24),
                now.plusDays(7),
            ).map { target -> target.toInstant().toEpochMilli() }
        val actualMillis =
            REMINDER_DURATION_CHIPS.map { chip ->
                applyReminderDurationChip(now, chip).toInstant().toEpochMilli()
            }
        assertEquals(expectedMillis, actualMillis)
    }

    @Test
    fun twentyFourHoursIsElapsedTimeWhileSevenDaysKeepsLocalWallClock() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-03-07T20:00:00Z").atZone(zone)
        val twentyFourHoursChip =
            REMINDER_DURATION_CHIPS.first { chip ->
                chip.amount == 24 && chip.unit == ChronoUnit.HOURS
            }
        val sevenDaysChip =
            REMINDER_DURATION_CHIPS.first { chip ->
                chip.amount == 7 && chip.unit == ChronoUnit.DAYS
            }
        val twentyFourHours = applyReminderDurationChip(now, twentyFourHoursChip).toInstant()
        val sevenDays = applyReminderDurationChip(now, sevenDaysChip).toInstant()
        assertEquals(Instant.parse("2026-03-08T20:00:00Z"), twentyFourHours)
        assertEquals(now.plusDays(7).toInstant(), sevenDays)
    }
}
