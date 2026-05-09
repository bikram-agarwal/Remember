package dev.bikram.remember.update

import dev.bikram.remember.data.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class RememberUpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val remoteApkFileName: String = "",
    val remoteApkAssetUpdatedAt: String = "",
    val isPlayStoreUpdateInProgress: Boolean = false,
    val isDevReleaseMock: Boolean = false,
)

fun RememberUpdateInfo.notificationDedupeKey(): String =
    if (remoteApkAssetUpdatedAt.isNotBlank()) "$versionName|$remoteApkAssetUpdatedAt" else versionName

@Serializable
private data class GithubRelease(
    val tag_name: String,
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    val browser_download_url: String,
    val updated_at: String = "",
)

@Singleton
class RememberUpdateChecker
    @Inject
    constructor(
        private val updatePrefs: UpdatePrefs,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun checkGithubReleaseForUpdate(
            repositoryName: String,
            currentVersionName: String,
        ): RememberUpdateInfo? =
            withContext(Dispatchers.IO) {
                checkGithubReleaseForUpdateBlocking(repositoryName, currentVersionName)
            }

        private suspend fun checkGithubReleaseForUpdateBlocking(
            repositoryName: String,
            currentVersionName: String,
        ): RememberUpdateInfo? {
            if (repositoryName.isBlank()) return null
            val connection =
                URL("https://api.github.com/repos/$repositoryName/releases/latest").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            return try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("GitHub returned HTTP ${connection.responseCode}")
                }
                val release = json.decodeFromString<GithubRelease>(connection.inputStream.bufferedReader().use { it.readText() })
                val remoteVersionName = release.tag_name.trim().removePrefix("v")
                if (remoteVersionName.isBlank()) return null
                val apkAsset = release.assets.firstOrNull { asset -> asset.name.endsWith(".apk", ignoreCase = true) } ?: return null
                val currentFingerprint = "$remoteVersionName|${apkAsset.updated_at}"
                val ack = updatePrefs.readGithubReleaseAck()
                val effectiveFingerprint =
                    if (ack.forInstalledVersion == currentVersionName) {
                        ack.fingerprint
                    } else {
                        null
                    }
                val versionComparison = compareVersionNames(remoteVersionName, currentVersionName)
                when {
                    versionComparison < 0 -> null
                    versionComparison > 0 -> {
                        if (effectiveFingerprint == currentFingerprint) {
                            null
                        } else {
                            release.toUpdateInfo(remoteVersionName, apkAsset)
                        }
                    }
                    effectiveFingerprint == null -> {
                        updatePrefs.writeGithubReleaseAck(
                            fingerprint = currentFingerprint,
                            installedVersionName = currentVersionName,
                        )
                        null
                    }
                    effectiveFingerprint == currentFingerprint -> null
                    else -> release.toUpdateInfo(remoteVersionName, apkAsset)
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun GithubRelease.toUpdateInfo(
            remoteVersionName: String,
            apkAsset: GithubAsset,
        ): RememberUpdateInfo =
            RememberUpdateInfo(
                versionName = remoteVersionName,
                downloadUrl = apkAsset.browser_download_url,
                releaseNotes = body,
                remoteApkFileName = apkAsset.name,
                remoteApkAssetUpdatedAt = apkAsset.updated_at,
            )

        private fun compareVersionNames(
            left: String,
            right: String,
        ): Int {
            val leftParts = left.trim().removePrefix("v").split('.', '-', limit = 10)
            val rightParts = right.trim().removePrefix("v").split('.', '-', limit = 10)
            val maxLength = maxOf(leftParts.size, rightParts.size)
            for (partIndex in 0 until maxLength) {
                val leftToken = leftParts.getOrNull(partIndex).orEmpty()
                val rightToken = rightParts.getOrNull(partIndex).orEmpty()
                val leftNumber = leftToken.toIntOrNull()
                val rightNumber = rightToken.toIntOrNull()
                val comparison =
                    when {
                        leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                        leftNumber != null -> -1
                        rightNumber != null -> 1
                        else -> leftToken.compareTo(rightToken)
                    }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
