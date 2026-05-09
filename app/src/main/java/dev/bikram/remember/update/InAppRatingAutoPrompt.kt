package dev.bikram.remember.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object InAppRatingAutoPrompt {
    const val AUTO_PROMPT_DELAY_MS: Long = 24 * 60 * 60 * 1000L

    /**
     * Suppresses an automatic request briefly after a manual or automatic review launch attempt.
     */
    object SessionCoordination {
        @Volatile
        var lastInAppReviewAttemptWallClockMillis: Long = 0L

        const val AUTO_VS_MANUAL_DEBOUNCE_MS: Long = 5_000L
    }

    fun packageLastUpdateTimeMillis(context: Context): Long {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        return packageInfo.lastUpdateTime
    }

    fun isEligibleForAutoPrompt(
        lastUpdateTimeMillis: Long,
        nowMillis: Long,
        neverAskAgain: Boolean,
        promptedForLastUpdateTimeMillis: Long,
    ): Boolean {
        if (neverAskAgain) return false
        if (nowMillis - lastUpdateTimeMillis < AUTO_PROMPT_DELAY_MS) return false
        if (lastUpdateTimeMillis == promptedForLastUpdateTimeMillis) return false
        return true
    }
}
