package dev.bikram.remember.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubPlayUpdateSession
    @Inject
    constructor() : PlayUpdateSessionHandle {
        override fun clearPendingPlayUpdate() = Unit
    }

@Singleton
class GithubPlayInAppUpdateStarter
    @Inject
    constructor() : PlayInAppUpdateStarter {
        override fun startUpdateIfPending(
            activity: ComponentActivity,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): Boolean = false
    }

@Singleton
class GithubPlayInAppUpdateProgressController
    @Inject
    constructor() : PlayInAppUpdateProgressController {
        override val bannerUiState: StateFlow<PlayInAppUpdateBannerUiState> =
            MutableStateFlow(PlayInAppUpdateBannerUiState.Hidden)

        override fun onFlexibleUpdateFlowStarted() = Unit

        override fun ensureInstallStateListenerRegistered() = Unit

        override fun completeFlexibleUpdateIfReady(activity: Activity) = Unit
    }

@Singleton
class GithubPlayStoreUpdateChecker
    @Inject
    constructor() : PlayStoreUpdateChecker {
        override suspend fun checkForUpdate(): RememberUpdateInfo? = null
    }
