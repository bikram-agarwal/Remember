package dev.bikram.remember.update

import dev.bikram.remember.BuildConfig
import dev.bikram.remember.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RememberUpdateState
    @Inject
    constructor(
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val _updateInfo = MutableStateFlow<RememberUpdateInfo?>(null)
        val updateInfo: StateFlow<RememberUpdateInfo?> = _updateInfo.asStateFlow()

        // Bumped every time an update is delivered, even when the value is identical to
        // the current one (StateFlow would swallow that emission). Lets the alert chrome
        // re-present on every (re-)trigger of the update flow, not just on value changes.
        private val _updateSignalEpoch = MutableStateFlow(0)
        val updateSignalEpoch: StateFlow<Int> = _updateSignalEpoch.asStateFlow()

        private val _devReleasePlayBannerMockUiState =
            MutableStateFlow<PlayInAppUpdateBannerUiState>(PlayInAppUpdateBannerUiState.Hidden)
        val devReleasePlayBannerMockUiState: StateFlow<PlayInAppUpdateBannerUiState> =
            _devReleasePlayBannerMockUiState.asStateFlow()
        private var devReleasePlayBannerMockSequenceJob: Job? = null

        fun showUpdate(info: RememberUpdateInfo?) {
            _updateInfo.value = info
            if (info != null) {
                _updateSignalEpoch.value += 1
            }
        }

        fun devReleaseMockShowUpdateAvailable() {
            if (!BuildConfig.SHOW_UPDATES) return
            _updateInfo.value =
                RememberUpdateInfo(
                    versionName = "9.9.9",
                    downloadUrl = "",
                    releaseNotes = "",
                    remoteApkAssetUpdatedAt =
                        if (BuildConfig.FLAVOR == "github") {
                            DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT
                        } else {
                            ""
                        },
                    isDevReleaseMock = true,
                )
            _updateSignalEpoch.value += 1
        }

        fun devReleaseMockStartPlayUpdateBannerSequence() {
            devReleasePlayBannerMockSequenceJob?.cancel()
            devReleasePlayBannerMockSequenceJob =
                applicationScope.launch {
                    _devReleasePlayBannerMockUiState.value =
                        PlayInAppUpdateBannerUiState.Downloading(
                            bytesDownloaded = 0L,
                            totalBytesToDownload = 0L,
                            indeterminateProgress = true,
                        )
                    delay(1_200L)
                    if (!isActive) return@launch
                    _devReleasePlayBannerMockUiState.value =
                        PlayInAppUpdateBannerUiState.Downloading(
                            bytesDownloaded = MOCK_PLAY_UPDATE_BYTES_DOWNLOADED,
                            totalBytesToDownload = MOCK_PLAY_UPDATE_BYTES_TOTAL,
                            indeterminateProgress = false,
                        )
                    delay(2_500L)
                    if (!isActive) return@launch
                    _devReleasePlayBannerMockUiState.value = PlayInAppUpdateBannerUiState.ReadyToInstall
                }
        }

        fun devReleaseCompletePlayUpdateIfReady(): Boolean {
            if (_devReleasePlayBannerMockUiState.value != PlayInAppUpdateBannerUiState.ReadyToInstall) return false
            devReleasePlayBannerMockSequenceJob?.cancel()
            _devReleasePlayBannerMockUiState.value = PlayInAppUpdateBannerUiState.Hidden
            if (_updateInfo.value?.isDevReleaseMock == true) {
                _updateInfo.value = null
            }
            return true
        }

        companion object {
            private const val MOCK_PLAY_UPDATE_BYTES_DOWNLOADED: Long = 3_000_000L
            private const val MOCK_PLAY_UPDATE_BYTES_TOTAL: Long = 10_000_000L
            private const val DEV_RELEASE_MOCK_GITHUB_ASSET_UPDATED_AT = "2000-01-01T00:00:00Z"
        }
    }
