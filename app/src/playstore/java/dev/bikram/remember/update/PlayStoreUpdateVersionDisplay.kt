package dev.bikram.remember.update

import com.google.android.play.core.appupdate.AppUpdateInfo

/** major*10000 + minor*100 + patch, two digits each for minor and patch (10901 → 1.9.1). */
internal fun semanticVersionNameFromPlayStoreVersionCode(versionCode: Int): String {
    if (versionCode <= 0) return ""
    if (versionCode > 999_999) return versionCode.toString()
    val major = versionCode / 10_000
    val minor = (versionCode / 100) % 100
    val patch = versionCode % 100
    return "$major.$minor.$patch"
}

internal fun semanticVersionNameFromPlayUpdateInfo(appUpdateInfo: AppUpdateInfo): String = semanticVersionNameFromPlayStoreVersionCode(appUpdateInfo.availableVersionCode())
