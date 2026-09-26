package dev.bikram.remember.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtainXTrackingTest {
    private fun payload(
        flavor: String,
        appId: String,
    ): JsonObject {
        val json = checkNotNull(buildObtainXTrackingJson(flavor, appId))
        return Json.parseToJsonElement(json).jsonObject
    }

    private fun apkFilterRegEx(payload: JsonObject): Regex {
        // ObtainX jsonDecode()s additionalSettings, so it must be a JSON string, not a nested object.
        val additionalSettings = payload.getValue("additionalSettings") as JsonPrimitive
        assertTrue(additionalSettings.isString)
        val settings = Json.parseToJsonElement(additionalSettings.content).jsonObject
        return Regex(settings.getValue("apkFilterRegEx").jsonPrimitive.content)
    }

    @Test
    fun githubTracksGithubReleasesWithGithubApkFilter() {
        val payload = payload("github", "dev.bikram.remember.gh")
        assertEquals("dev.bikram.remember.gh", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/Remember", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-github\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun offlineTracksGithubReleasesWithOfflineApkFilter() {
        val payload = payload("offline", "dev.bikram.remember.offline")
        assertEquals("dev.bikram.remember.offline", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/Remember", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-offline\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun fdroidTracksTheFdroidListingWithoutAnApkFilter() {
        val payload = payload("fdroid", "dev.bikram.remember.gh")
        assertEquals(
            "https://f-droid.org/packages/dev.bikram.remember.gh",
            payload.getValue("url").jsonPrimitive.content,
        )
        assertFalse(payload.containsKey("additionalSettings"))
    }

    @Test
    fun noPayloadOverridesTheSourceOrPinsAnApkIndex() {
        for ((flavor, appId) in listOf("github" to "a.gh", "offline" to "a.offline", "fdroid" to "a.gh")) {
            val payload = payload(flavor, appId)
            assertFalse(payload.containsKey("overrideSource"))
            assertFalse(payload.containsKey("preferredApkIndex"))
        }
    }

    @Test
    fun playStoreBuildsAreNotOfferedTracking() {
        assertNull(buildObtainXTrackingJson("playstore", "dev.bikram.remember"))
    }

    @Test
    fun releaseApplicationIdDropsOnlyTheDevReleaseSuffix() {
        assertEquals("dev.bikram.remember.gh", releaseApplicationId("dev.bikram.remember.gh.dev"))
        assertEquals("dev.bikram.remember.offline", releaseApplicationId("dev.bikram.remember.offline"))
        assertEquals("dev.bikram.remember.gh", releaseApplicationId("dev.bikram.remember.gh"))
    }

    @Test
    fun apkFiltersSelectOnlyTheMatchingFlavorAsset() {
        // ObtainX keeps an asset when the filter matches its name *or* its download URL.
        val downloadBase = "https://github.com/bikram-agarwal/Remember/releases/download/v1.9.0/"
        val assets = listOf("remember-v1.9.0-fdroid.apk", "remember-v1.9.0-github.apk", "remember-v1.9.0-offline.apk")
        val cases =
            mapOf(
                "github" to "remember-v1.9.0-github.apk",
                "offline" to "remember-v1.9.0-offline.apk",
            )
        for ((flavor, expected) in cases) {
            val filter = apkFilterRegEx(payload(flavor, "dev.bikram.remember.$flavor"))
            val kept = assets.filter { name -> filter.containsMatchIn(name) || filter.containsMatchIn(downloadBase + name) }
            assertEquals(listOf(expected), kept)
        }
    }
}
