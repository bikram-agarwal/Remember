package dev.bikram.remember.update

sealed interface PlayInAppUpdateBannerUiState {
    data object Hidden : PlayInAppUpdateBannerUiState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val indeterminateProgress: Boolean,
    ) : PlayInAppUpdateBannerUiState

    data object ReadyToInstall : PlayInAppUpdateBannerUiState
}
