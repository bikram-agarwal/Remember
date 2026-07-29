package dev.bikram.remember.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {
    @Test
    fun returning_within_grace_period_keeps_session_unlocked() {
        var elapsedRealtime = 10_000L
        val session = AppLockSession(elapsedRealtime = { elapsedRealtime })
        session.unlock()
        session.onAppBackgrounded()

        elapsedRealtime += AppLockSession.GRACE_PERIOD_MILLIS - 1L
        session.onAppForegrounded()

        assertTrue(session.unlocked.value)
    }

    @Test
    fun returning_after_grace_period_locks_session() {
        var elapsedRealtime = 20_000L
        val session = AppLockSession(elapsedRealtime = { elapsedRealtime })
        session.unlock()
        session.onAppBackgrounded()

        elapsedRealtime += AppLockSession.GRACE_PERIOD_MILLIS
        session.onAppForegrounded()

        assertFalse(session.unlocked.value)
    }
}
