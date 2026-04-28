package dev.bikram.remember.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayInAppUpdateSession
    @Inject
    constructor() : PlayUpdateSessionHandle {
        @Volatile
        private var pendingInfo: AppUpdateInfo? = null

        fun pendingAppUpdate(): AppUpdateInfo? = pendingInfo

        internal fun setPendingAppUpdateInfo(info: AppUpdateInfo?) {
            pendingInfo = info
        }

        override fun clearPendingPlayUpdate() {
            pendingInfo = null
        }
    }
