package dev.bikram.remember.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.data.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
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

fun RememberUpdateInfo.notificationDedupeKey(): String = if (remoteApkAssetUpdatedAt.isNotBlank()) "$versionName|$remoteApkAssetUpdatedAt" else versionName

@Serializable
private data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    @SerialName("updated_at")
    val updatedAt: String = "",
)

@Singleton
class RememberUpdateChecker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val updatePrefs: UpdatePrefs,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun checkGithubReleaseForUpdate(
            repositoryName: String,
            currentVersionName: String,
        ): RememberUpdateInfo? =
            withContext(Dispatchers.IO) {
                if (BuildConfig.FLAVOR == "fdroid") {
                    return@withContext checkFdroidForUpdate()
                }
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
                val remoteVersionName = release.tagName.trim().removePrefix("v")
                if (remoteVersionName.isBlank()) return null
                val apkAsset = release.assets.firstOrNull { asset -> asset.name.endsWith(".apk", ignoreCase = true) } ?: return null
                if (!isGithubReleaseNewerThanInstalled(remoteVersionName, currentVersionName)) return null

                val remoteReleaseFingerprint = "$remoteVersionName|${apkAsset.updatedAt}"
                val ack = updatePrefs.readGithubReleaseAck()
                val effectiveFingerprint =
                    if (ack.forInstalledVersion == currentVersionName) {
                        ack.fingerprint
                    } else {
                        null
                    }
                if (effectiveFingerprint == remoteReleaseFingerprint) {
                    null
                } else {
                    release.toUpdateInfo(remoteVersionName, apkAsset)
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
                downloadUrl = apkAsset.browserDownloadUrl,
                releaseNotes = body,
                remoteApkFileName = apkAsset.name,
                remoteApkAssetUpdatedAt = apkAsset.updatedAt,
            )

        private fun checkFdroidForUpdate(): RememberUpdateInfo? =
            runCatching {
                val connection =
                    URL("https://f-droid.org/api/v1/packages/${context.packageName}").openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", "application/json")
                val responseText =
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) {
                            error("F-Droid returned HTTP ${connection.responseCode}")
                        }
                        connection.inputStream.bufferedReader().use { reader -> reader.readText() }
                    } finally {
                        connection.disconnect()
                    }
                val packageJson = json.parseToJsonElement(responseText).jsonObject
                val packages =
                    (packageJson["packages"] as? JsonArray)
                        ?: (packageJson["versions"] as? JsonArray)
                        ?: return@runCatching null
                val latestPackage =
                    packages
                        .mapNotNull { element -> element as? JsonObject }
                        .mapNotNull { element ->
                            val versionCode = element.longOrNull("versionCode") ?: return@mapNotNull null
                            val versionName = (element["versionName"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            FdroidPackageVersion(versionCode = versionCode, versionName = versionName)
                        }.filter { version -> version.versionCode > BuildConfig.VERSION_CODE.toLong() }
                        .maxByOrNull { version -> version.versionCode }
                        ?: return@runCatching null

                RememberUpdateInfo(
                    versionName = latestPackage.versionName.ifBlank { latestPackage.versionCode.toString() },
                    downloadUrl = "",
                    releaseNotes = "",
                )
            }.getOrNull()
    }

private data class FdroidPackageVersion(
    val versionCode: Long,
    val versionName: String,
)

private fun JsonObject.longOrNull(key: String): Long? {
    val element = this[key] as? JsonPrimitive ?: return null
    return element.longOrNull ?: element.contentOrNull?.toLongOrNull()
}

private val recognizedPrereleasePattern =
    Regex(
        pattern = """^(.+)-(preview|alpha|beta|rc)(?:[.-]?\d+)?$""",
        option = RegexOption.IGNORE_CASE,
    )

private fun normalizeLeadingVersionPrefix(version: String): String {
    val trimmedVersion = version.trim()
    return if (trimmedVersion.length > 1 &&
        trimmedVersion[0].equals('v', ignoreCase = true) &&
        trimmedVersion[1].isDigit()
    ) {
        trimmedVersion.substring(1)
    } else {
        trimmedVersion
    }
}

private fun compareMatchingPrereleaseAndStableVersions(
    left: String,
    right: String,
): Int? {
    val normalizedLeft = normalizeLeadingVersionPrefix(left).lowercase()
    val normalizedRight = normalizeLeadingVersionPrefix(right).lowercase()
    val leftPrerelease = recognizedPrereleasePattern.matchEntire(normalizedLeft)
    val rightPrerelease = recognizedPrereleasePattern.matchEntire(normalizedRight)

    if (leftPrerelease?.groupValues?.get(1) == normalizedRight) return -1
    if (rightPrerelease?.groupValues?.get(1) == normalizedLeft) return 1
    return null
}

internal fun compareGithubVersionNames(
    left: String,
    right: String,
): Int {
    compareMatchingPrereleaseAndStableVersions(left, right)?.let { return it }

    val leftParts = normalizeLeadingVersionPrefix(left).lowercase().split('.', '-', limit = 10)
    val rightParts = normalizeLeadingVersionPrefix(right).lowercase().split('.', '-', limit = 10)
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

internal fun isGithubReleaseNewerThanInstalled(
    remoteVersionName: String,
    currentVersionName: String,
): Boolean = compareGithubVersionNames(remoteVersionName, currentVersionName) > 0
