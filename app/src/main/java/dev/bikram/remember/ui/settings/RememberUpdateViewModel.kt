package dev.bikram.remember.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.di.IoDispatcher
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.PlayInAppUpdateStarter
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.PlayUpdateSessionHandle
import dev.bikram.remember.update.RememberUpdateChecker
import dev.bikram.remember.update.RememberUpdateInfo
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.UpdateAvailableNotifier
import dev.bikram.remember.update.notificationDedupeKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Owns the Settings update-sheet state and the check / download / install / Play-start
 * orchestration. The sheet UI and the Play [ActivityResultLauncher] stay in the composable
 * (the launcher is passed into [downloadOrInstall]); everything else lives here so it is
 * testable and so the flow mirrors FilePipe's `SettingsViewModel`.
 *
 * [RememberUpdateState] remains the app-wide holder that the background worker and the alert
 * bar observe; this view model feeds it via [RememberUpdateState.showUpdate].
 */
@HiltViewModel
class RememberUpdateViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val rememberUpdateChecker: RememberUpdateChecker,
        private val playStoreUpdateChecker: PlayStoreUpdateChecker,
        private val rememberUpdateState: RememberUpdateState,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val updatePrefs: UpdatePrefs,
        private val playInAppUpdateStarter: PlayInAppUpdateStarter,
        private val playInAppUpdateProgressController: PlayInAppUpdateProgressController,
        private val playUpdateSessionHandle: PlayUpdateSessionHandle,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _updateInfo = MutableStateFlow<RememberUpdateInfo?>(null)
        val updateInfo: StateFlow<RememberUpdateInfo?> = _updateInfo.asStateFlow()

        private val _isCheckingUpdate = MutableStateFlow(false)
        val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

        private val _updateCheckFinishedWithoutResult = MutableStateFlow(false)
        val updateCheckFinishedWithoutResult: StateFlow<Boolean> = _updateCheckFinishedWithoutResult.asStateFlow()

        /** null = idle. 0f..100f = determinate. -2f = indeterminate download. -1f = installing. */
        private val _downloadProgress = MutableStateFlow<Float?>(null)
        val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

        private val _updateSheetChangelog = MutableStateFlow<ChangelogUiState>(ChangelogUiState.Hidden)
        val updateSheetChangelog: StateFlow<ChangelogUiState> = _updateSheetChangelog.asStateFlow()

        private val _showUpdateSheet = MutableStateFlow(false)
        val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

        /**
         * Set by the alert bar / notification tap (which live outside the Settings screen and share
         * this activity-scoped VM) to ask the Settings screen to open the update sheet when it composes.
         */
        private val _openSheetRequested = MutableStateFlow(false)
        val openSheetRequested: StateFlow<Boolean> = _openSheetRequested.asStateFlow()

        fun requestOpenSheet() {
            _openSheetRequested.value = true
        }

        fun markOpenSheetHandled() {
            _openSheetRequested.value = false
        }

        /** Adopt a background-discovered update (worker → [RememberUpdateState]) if the sheet has none yet. */
        fun adoptGlobalUpdateIfNone(globalUpdateInfo: RememberUpdateInfo?) {
            if (globalUpdateInfo != null && _updateInfo.value == null) {
                _updateInfo.value = globalUpdateInfo
            }
        }

        /** Opens the update sheet and kicks off a check. Changelog loads via [loadChangelog] when the sheet shows. */
        fun openSheetAndCheck() {
            _showUpdateSheet.value = true
            runUpdateCheck()
        }

        private fun runUpdateCheck() {
            _isCheckingUpdate.value = true
            _updateCheckFinishedWithoutResult.value = false
            _downloadProgress.value = null
            if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
                viewModelScope.launch {
                    val checkedUpdate = withContext(ioDispatcher) { runCatching { playStoreUpdateChecker.checkForUpdate() } }
                    _isCheckingUpdate.value = false
                    checkedUpdate.fold(
                        onSuccess = { availableUpdate ->
                            _updateInfo.value = availableUpdate
                            rememberUpdateState.showUpdate(availableUpdate)
                            if (availableUpdate != null && availableUpdate.isPlayStoreUpdateInProgress) {
                                playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
                                updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                            } else if (availableUpdate != null) {
                                updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                            }
                            _updateCheckFinishedWithoutResult.value = availableUpdate == null
                        },
                        onFailure = { throwable ->
                            DiagnosticLog.record(appContext, "Play Store update check failed from Settings", throwable)
                            _updateInfo.value = null
                            _updateCheckFinishedWithoutResult.value = true
                            toast(R.string.settings_update_check_failed)
                        },
                    )
                }
            } else {
                viewModelScope.launch {
                    val checkedUpdate =
                        withContext(ioDispatcher) {
                            runCatching {
                                rememberUpdateChecker.checkGithubReleaseForUpdate(
                                    repositoryName = BuildConfig.GITHUB_REPO,
                                    currentVersionName = BuildConfig.VERSION_NAME,
                                )
                            }
                        }
                    _isCheckingUpdate.value = false
                    checkedUpdate.fold(
                        onSuccess = { availableUpdate ->
                            _updateInfo.value = availableUpdate
                            rememberUpdateState.showUpdate(availableUpdate)
                            if (availableUpdate != null) {
                                updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                            }
                            _updateCheckFinishedWithoutResult.value = availableUpdate == null
                        },
                        onFailure = { throwable ->
                            DiagnosticLog.record(appContext, "GitHub update check failed from Settings", throwable)
                            _updateInfo.value = null
                            _updateCheckFinishedWithoutResult.value = true
                            toast(R.string.settings_update_check_failed)
                        },
                    )
                }
            }
        }

        /**
         * For Play in-app updates with no direct download URL, starts the Play flow (needs the host
         * activity + launcher from the composable). Otherwise downloads the GitHub APK and launches the installer.
         */
        fun downloadOrInstall(
            availableUpdate: RememberUpdateInfo,
            activity: ComponentActivity?,
            playInAppUpdateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        ) {
            viewModelScope.launch {
                if (BuildConfig.FLAVOR == "fdroid") {
                    return@launch
                }
                if (BuildConfig.USE_PLAY_IN_APP_UPDATES && availableUpdate.downloadUrl.isBlank()) {
                    val started = activity != null && playInAppUpdateStarter.startUpdateIfPending(activity, playInAppUpdateLauncher)
                    if (started) {
                        _showUpdateSheet.value = false
                        playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
                    } else {
                        toast(R.string.settings_play_in_app_update_failed)
                    }
                    return@launch
                }
                _downloadProgress.value = 0f
                val downloadResult =
                    withContext(ioDispatcher) {
                        runCatching {
                            updatePrefs.clearUpdateApkDownloadsCopySucceeded()
                            downloadUpdateApk(
                                context = appContext,
                                updateInfo = availableUpdate,
                                onProgress = { progress -> _downloadProgress.value = progress },
                            )
                        }
                    }
                downloadResult.fold(
                    onSuccess = { apkFile ->
                        if (BuildConfig.FLAVOR == "github" && updatePrefs.snapshot().saveUpdateApkToDownloads) {
                            withContext(ioDispatcher) {
                                copyUpdateApkToMediaStoreDownloads(
                                    context = appContext,
                                    cacheApkFile = apkFile,
                                    displayName =
                                        availableUpdate.remoteApkFileName.ifBlank {
                                            "Remember-${availableUpdate.versionName}.apk"
                                        },
                                )
                            }.onFailure { throwable ->
                                DiagnosticLog.record(appContext, "Saving update APK to Downloads failed", throwable)
                                toast(R.string.settings_update_apk_save_to_downloads_failed)
                            }.onSuccess {
                                updatePrefs.markUpdateApkDownloadsCopySucceeded()
                            }
                        }
                        _downloadProgress.value = -1f
                        val apkUri =
                            FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                apkFile,
                            )
                        val installIntent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                        runCatching { appContext.startActivity(installIntent) }
                            .onFailure { throwable ->
                                DiagnosticLog.record(appContext, "Launching update APK installer failed", throwable)
                                toast(R.string.settings_update_download_failed)
                            }
                        _downloadProgress.value = null
                    },
                    onFailure = { throwable ->
                        DiagnosticLog.record(appContext, "Update APK download failed", throwable)
                        _downloadProgress.value = null
                        toast(R.string.settings_update_download_failed)
                    },
                )
            }
        }

        fun skipVersion(availableUpdate: RememberUpdateInfo) {
            if (availableUpdate.remoteApkAssetUpdatedAt.isBlank()) return
            viewModelScope.launch {
                updatePrefs.writeGithubReleaseAck(
                    fingerprint = availableUpdate.notificationDedupeKey(),
                    installedVersionName = BuildConfig.VERSION_NAME,
                )
                _updateInfo.value = null
                rememberUpdateState.showUpdate(null)
                _showUpdateSheet.value = false
                _updateSheetChangelog.value = ChangelogUiState.Hidden
            }
        }

        /** Sheet dismissed by the user. Clears any pending Play update unless a Play download/install is in flight. */
        fun dismissSheet() {
            _showUpdateSheet.value = false
            _downloadProgress.value = null
            _updateSheetChangelog.value = ChangelogUiState.Hidden
            val playState = playInAppUpdateProgressController.bannerUiState.value
            val blocksPendingPlayClear =
                playState is PlayInAppUpdateBannerUiState.Downloading ||
                    playState is PlayInAppUpdateBannerUiState.ReadyToInstall
            if (!blocksPendingPlayClear) {
                playUpdateSessionHandle.clearPendingPlayUpdate()
            }
        }

        /** Close the sheet because a Play download/install began behind it (banner takes over). */
        fun closeSheetForPlayProgress() {
            _showUpdateSheet.value = false
            _downloadProgress.value = null
            _updateSheetChangelog.value = ChangelogUiState.Hidden
        }

        fun loadChangelog() {
            if (BuildConfig.CHANGELOG_GITHUB_REPO.isBlank()) {
                _updateSheetChangelog.value =
                    ChangelogUiState.Failed(appContext.getString(R.string.settings_changelog_load_failed))
                return
            }
            _updateSheetChangelog.value = ChangelogUiState.Loading
            viewModelScope.launch {
                val loaded = withContext(ioDispatcher) { runCatching { fetchRawChangelog() } }
                _updateSheetChangelog.value =
                    loaded.fold(
                        onSuccess = { markdown -> ChangelogUiState.Ready(markdown) },
                        onFailure = { error ->
                            DiagnosticLog.record(appContext, "Update changelog load failed", error)
                            ChangelogUiState.Failed(appContext.getString(R.string.settings_changelog_load_failed))
                        },
                    )
            }
        }

        private fun fetchRawChangelog(): String {
            val repo = BuildConfig.CHANGELOG_GITHUB_REPO
            val branch = BuildConfig.CHANGELOG_GITHUB_BRANCH
            val connection =
                URL("https://raw.githubusercontent.com/$repo/$branch/docs/CHANGELOG.md").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            return try {
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Changelog request returned HTTP $responseCode")
                }
                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            } finally {
                connection.disconnect()
            }
        }

        private fun toast(resId: Int) {
            Toast.makeText(appContext, appContext.getString(resId), Toast.LENGTH_SHORT).show()
        }
    }
