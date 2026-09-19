package dev.bikram.remember.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SnoozeDurationTest {
    @Test
    fun durationStartsAtEachConfirmationEvenAfterTheSheetHasBeenOpen() {
        val openedAt = Instant.parse("2026-09-18T10:00:00Z")
        val confirmedAt = openedAt.plusSeconds(120)
        for ((unit, delay) in listOf(
            SnoozeDurationUnit.MINUTES to Duration.ofMinutes(1),
            SnoozeDurationUnit.HOURS to Duration.ofHours(1),
            SnoozeDurationUnit.DAYS to Duration.ofDays(1),
        )) {
            val clock = MutableSnoozeClock(openedAt)
            assertEquals(openedAt.plus(delay).toEpochMilli(), unit.targetMillis(1, clock))
            clock.current = confirmedAt
            assertEquals(confirmedAt.plus(delay).toEpochMilli(), unit.targetMillis(1, clock))
        }
    }

    @Test
    fun daysPreserveLocalTimeAcrossDaylightSavingWhileHoursRemainElapsedTime() {
        val zone = ZoneId.of("America/Los_Angeles")
        val clock = Clock.fixed(Instant.parse("2026-03-07T20:00:00Z"), zone)

        assertEquals(Instant.parse("2026-03-08T19:00:00Z").toEpochMilli(), SnoozeDurationUnit.DAYS.targetMillis(1, clock))
        assertEquals(Instant.parse("2026-03-08T20:00:00Z").toEpochMilli(), SnoozeDurationUnit.HOURS.targetMillis(24, clock))
    }
}

private class MutableSnoozeClock(
    var current: Instant,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = fixed(current, zone)
}
