package dev.bikram.remember.ui.edit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared save-lifecycle state for note and checklist editors.
 *
 * Saves can suspend inside the repository, so dirty clearing must be tied to the mutation epoch
 * that was current when the write began. Otherwise a quick edit during an in-flight save can be
 * lost by a stale `dirty = false`.
 */
class EditorPersistenceSession(
    initialDirty: Boolean = false,
) {
    private val persistMutex = Mutex()
    private val mutationEpoch = AtomicInteger(0)
    private val _hasUnsavedChanges = MutableStateFlow(initialDirty)

    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    val isDirty: Boolean
        get() = _hasUnsavedChanges.value

    fun markDirty() {
        mutationEpoch.incrementAndGet()
        _hasUnsavedChanges.value = true
    }

    fun clearDirty() {
        _hasUnsavedChanges.value = false
    }

    fun currentEpoch(): Int = mutationEpoch.get()

    fun clearDirtyIfUnchanged(epoch: Int) {
        if (mutationEpoch.get() == epoch) {
            clearDirty()
        }
    }

    fun tryLock(): Boolean = persistMutex.tryLock()

    fun unlock() {
        persistMutex.unlock()
    }

    suspend fun <T> withLock(block: suspend () -> T): T = persistMutex.withLock { block() }
}
