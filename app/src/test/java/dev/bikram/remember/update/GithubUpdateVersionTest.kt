package dev.bikram.remember.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUpdateVersionTest {
    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isGithubReleaseNewerThanInstalled("1.0.0", "1.0.0"))
        assertFalse(isGithubReleaseNewerThanInstalled("v1.0.0", "1.0.0"))
    }

    @Test
    fun newerSemanticVersionSortsAfterInstalledVersion() {
        assertTrue(isGithubReleaseNewerThanInstalled("1.0.1", "1.0.0"))
        assertTrue(isGithubReleaseNewerThanInstalled("1.1.0", "1.0.9"))
        assertTrue(isGithubReleaseNewerThanInstalled("2.0.0", "1.9.9"))
    }

    @Test
    fun olderSemanticVersionSortsBeforeInstalledVersion() {
        assertFalse(isGithubReleaseNewerThanInstalled("1.0.0", "1.0.1"))
        assertFalse(isGithubReleaseNewerThanInstalled("1.0.9", "1.1.0"))
        assertFalse(isGithubReleaseNewerThanInstalled("1.9.9", "2.0.0"))
    }

    @Test
    fun stableReleaseIsNewerThanMatchingPreview() {
        assertTrue(isGithubReleaseNewerThanInstalled("v1.2.4", "1.2.4-preview-239"))
        assertFalse(isGithubReleaseNewerThanInstalled("v1.2.4-Preview-239", "1.2.4"))
    }

    @Test
    fun newerPreviewRunIsNewer() {
        assertTrue(isGithubReleaseNewerThanInstalled("v1.2.4-Preview-240", "1.2.4-preview-239"))
    }

    @Test
    fun selectsGithubApkWhenFdroidIsListedFirst() {
        val selected =
            selectGithubReleaseApkAsset(
                listOf(
                    GithubAsset(
                        name = "remember-v1.8.0-fdroid.apk",
                        browserDownloadUrl = "https://example.com/fdroid.apk",
                    ),
                    GithubAsset(
                        name = "remember-v1.8.0-github.apk",
                        browserDownloadUrl = "https://example.com/github.apk",
                    ),
                    GithubAsset(
                        name = "remember-v1.8.0-offline.apk",
                        browserDownloadUrl = "https://example.com/offline.apk",
                    ),
                ),
            )
        assertEquals("remember-v1.8.0-github.apk", selected?.name)
        assertEquals("https://example.com/github.apk", selected?.browserDownloadUrl)
    }

    @Test
    fun returnsNullWhenNoGithubApkAssetExists() {
        val selected =
            selectGithubReleaseApkAsset(
                listOf(
                    GithubAsset(
                        name = "remember-v1.8.0-fdroid.apk",
                        browserDownloadUrl = "https://example.com/fdroid.apk",
                    ),
                    GithubAsset(
                        name = "remember-v1.8.0-offline.apk",
                        browserDownloadUrl = "https://example.com/offline.apk",
                    ),
                ),
            )
        assertNull(selected)
    }
}
