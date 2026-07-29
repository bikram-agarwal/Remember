package dev.bikram.remember.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUpdateDownloadsTest {
    @Test
    fun sanitizesPathCharactersAndForcesApkExtension() {
        val sanitizedName =
            sanitizeUpdateApkDisplayName(
                "folder/release\\candidate.APK",
                REMEMBER_UPDATE_APK_CACHE_NAME,
            )

        assertEquals("folder_release_candidate.apk", sanitizedName)
    }

    @Test
    fun removesUnsafeCharactersAndLimitsLength() {
        val sanitizedName =
            sanitizeUpdateApkDisplayName(
                "<>:\"/\\|?*\u0000" + "a".repeat(200),
                REMEMBER_UPDATE_APK_CACHE_NAME,
            )

        assertFalse(sanitizedName.any { character -> character.isISOControl() })
        assertFalse(sanitizedName.any { character -> character in "<>:\"/\\|?*" })
        assertTrue(sanitizedName.length <= 120)
        assertTrue(sanitizedName.endsWith(".apk"))
    }

    @Test
    fun usesFallbackForBlankOrPunctuationOnlyName() {
        assertEquals(
            REMEMBER_UPDATE_APK_CACHE_NAME,
            sanitizeUpdateApkDisplayName(" ... ", REMEMBER_UPDATE_APK_CACHE_NAME),
        )
    }
}
