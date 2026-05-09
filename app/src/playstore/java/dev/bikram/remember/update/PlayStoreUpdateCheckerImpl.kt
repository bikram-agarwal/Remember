package dev.bikram.remember.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreUpdateCheckerImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val playInAppUpdateSession: PlayInAppUpdateSession,
    ) : PlayStoreUpdateChecker {
        override suspend fun checkForUpdate(): RememberUpdateInfo? {
            val appUpdateManager = AppUpdateManagerFactory.create(context)
            val updateInfo =
                try {
                    appUpdateManager.requestAppUpdateInfo()
                } catch (_: Exception) {
                    playInAppUpdateSession.clearPendingPlayUpdate()
                    return null
                }
            return when (updateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    val flexibleAllowed = updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    val immediateAllowed = updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    if (!flexibleAllowed && !immediateAllowed) {
                        playInAppUpdateSession.clearPendingPlayUpdate()
                        return null
                    }
                    playInAppUpdateSession.setPendingAppUpdateInfo(updateInfo)
                    RememberUpdateInfo(
                        versionName = semanticVersionNameFromPlayUpdateInfo(updateInfo),
                        downloadUrl = "",
                        releaseNotes = "",
                        isPlayStoreUpdateInProgress = false,
                    )
                }
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    val flexibleAllowed = updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    val immediateAllowed = updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    if (!flexibleAllowed && !immediateAllowed) {
                        playInAppUpdateSession.clearPendingPlayUpdate()
                        return null
                    }
                    playInAppUpdateSession.setPendingAppUpdateInfo(updateInfo)
                    RememberUpdateInfo(
                        versionName = semanticVersionNameFromPlayUpdateInfo(updateInfo),
                        downloadUrl = "",
                        releaseNotes = "",
                        isPlayStoreUpdateInProgress = true,
                    )
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE,
                UpdateAvailability.UNKNOWN,
                -> {
                    playInAppUpdateSession.clearPendingPlayUpdate()
                    null
                }
                else -> {
                    playInAppUpdateSession.clearPendingPlayUpdate()
                    null
                }
            }
        }
    }
