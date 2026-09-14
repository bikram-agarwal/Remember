package dev.bikram.remember.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bikram.remember.data.Importance
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAlarmInstrumentedTest {
    @Test
    fun high_importance_uses_exact_access_or_the_inexact_fallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduler = ReminderScheduler(context)
        val previousFallbackCount = ReminderScheduler.inexactFallbackScheduleCount
        val hasExactAccess = alarmManager.canScheduleExactAlarms()
        val noteId = 987_654_321L

        try {
            scheduler.schedule(noteId, reminderIndex = 0, whenMillis = System.currentTimeMillis() + 60_000L, importance = Importance.HIGH)

            assertEquals(
                previousFallbackCount + if (hasExactAccess) 0 else 1,
                ReminderScheduler.inexactFallbackScheduleCount,
            )
        } finally {
            scheduler.cancel(noteId)
        }
    }
}
