package dev.bikram.remember.ui.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLockSession(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : DefaultLifecycleObserver {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAtElapsedRealtime: Long? = null
    private var observingProcessLifecycle = false

    fun start() {
        if (observingProcessLifecycle) return
        observingProcessLifecycle = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun unlock() {
        _unlocked.value = true
        backgroundedAtElapsedRealtime = null
    }

    override fun onStart(owner: LifecycleOwner) {
        onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppBackgrounded()
    }

    internal fun onAppBackgrounded() {
        if (_unlocked.value) {
            backgroundedAtElapsedRealtime = elapsedRealtime()
        }
    }

    internal fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtElapsedRealtime ?: return
        val elapsedSinceBackground = (elapsedRealtime() - backgroundedAt).coerceAtLeast(0L)
        if (elapsedSinceBackground >= GRACE_PERIOD_MILLIS) {
            _unlocked.value = false
        }
        backgroundedAtElapsedRealtime = null
    }

    companion object {
        const val GRACE_PERIOD_MILLIS = 5 * 60 * 1_000L
    }
}
