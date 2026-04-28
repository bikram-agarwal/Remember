package dev.bikram.remember.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class RememberUpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

fun RememberUpdateInfo.notificationDedupeKey(): String = versionName

@Singleton
class RememberUpdateChecker
    @Inject
    constructor() {
        fun checkGithubReleaseForUpdate(
            repositoryName: String,
            currentVersionName: String,
        ): RememberUpdateInfo? {
            if (repositoryName.isBlank()) return null
            val connection =
                URL("https://api.github.com/repos/$repositoryName/releases/latest").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            return try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("GitHub returned HTTP ${connection.responseCode}")
                }
                val releaseJson = JSONObject(connection.inputStream.bufferedReader().use { reader -> reader.readText() })
                val versionName =
                    releaseJson
                        .optString("tag_name")
                        .trim()
                        .removePrefix("v")
                        .ifBlank { releaseJson.optString("name").trim().removePrefix("v") }
                if (versionName.isBlank() || !isRemoteVersionNewer(versionName, currentVersionName)) return null
                val downloadUrl = releaseJson.firstApkDownloadUrl() ?: return null
                RememberUpdateInfo(
                    versionName = versionName,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseJson.optString("body"),
                )
            } finally {
                connection.disconnect()
            }
        }

        private fun JSONObject.firstApkDownloadUrl(): String? {
            val assets = getJSONArray("assets")
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                val assetName = asset.optString("name")
                val browserDownloadUrl = asset.optString("browser_download_url")
                if (assetName.endsWith(".apk", ignoreCase = true) && browserDownloadUrl.isNotBlank()) {
                    return browserDownloadUrl
                }
            }
            return null
        }

        private fun isRemoteVersionNewer(
            remoteVersionName: String,
            currentVersionName: String,
        ): Boolean {
            val remoteParts = versionNumberParts(remoteVersionName)
            val currentParts = versionNumberParts(currentVersionName)
            val partCount = maxOf(remoteParts.size, currentParts.size)
            for (partIndex in 0 until partCount) {
                val remotePart = remoteParts.getOrElse(partIndex) { 0 }
                val currentPart = currentParts.getOrElse(partIndex) { 0 }
                if (remotePart != currentPart) return remotePart > currentPart
            }
            return false
        }

        private fun versionNumberParts(versionName: String): List<Int> =
            versionName
                .removePrefix("v")
                .substringBefore('-')
                .split('.')
                .mapNotNull { versionPart -> versionPart.toIntOrNull() }
    }
