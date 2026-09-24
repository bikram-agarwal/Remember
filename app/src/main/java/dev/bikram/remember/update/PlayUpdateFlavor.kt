package dev.bikram.remember.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

interface PlayUpdateSessionHandle {
    fun clearPendingPlayUpdate()
}

interface PlayInAppUpdateStarter {
    fun startUpdateIfPending(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean
}

interface PlayInAppUpdateProgressController {
    val bannerUiState: kotlinx.coroutines.flow.StateFlow<PlayInAppUpdateBannerUiState>

    fun onFlexibleUpdateFlowStarted()

    fun ensureInstallStateListenerRegistered()

    fun completeFlexibleUpdateIfReady(activity: Activity)
}

interface PlayStoreUpdateChecker {
    /** Null when Play reports no update; throws when Play can't be queried, so callers can show "check failed". */
    suspend fun checkForUpdate(): RememberUpdateInfo?
}
