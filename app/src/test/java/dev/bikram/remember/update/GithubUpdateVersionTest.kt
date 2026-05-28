package dev.bikram.remember.update

import org.junit.Assert.assertFalse
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
}
