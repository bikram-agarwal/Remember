package dev.bikram.remember.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactAlarmFallbackTest {
    @Test
    fun denied_permission_never_attempts_an_exact_alarm() {
        var fallbackCalls = 0
        scheduleWithExactAlarmFallback(
            canScheduleExactAlarms = false,
            scheduleExact = { error("Exact alarms must not be attempted without access") },
            scheduleInexact = { fallbackCalls++ },
        )
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun revocation_during_scheduling_falls_back_once() {
        var fallbackCalls = 0
        scheduleWithExactAlarmFallback(
            canScheduleExactAlarms = true,
            scheduleExact = { throw SecurityException("Exact-alarm access was revoked") },
            scheduleInexact = { fallbackCalls++ },
        )
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun granted_permission_preserves_exact_scheduling() {
        var exactCalls = 0
        scheduleWithExactAlarmFallback(
            canScheduleExactAlarms = true,
            scheduleExact = { exactCalls++ },
            scheduleInexact = { error("An exact alarm was already scheduled") },
        )
        assertEquals(1, exactCalls)
    }
}
