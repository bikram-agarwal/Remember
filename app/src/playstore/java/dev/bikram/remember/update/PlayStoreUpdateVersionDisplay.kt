package dev.bikram.remember.update

import com.google.android.play.core.appupdate.AppUpdateInfo

internal fun semanticVersionNameFromPlayStoreVersionCode(versionCode: Int): String {
    if (versionCode <= 0) return ""
    if (versionCode > 99_999) return versionCode.toString()
    val major = versionCode / 100
    val minor = (versionCode / 10) % 10
    val patch = versionCode % 10
    return "$major.$minor.$patch"
}

internal fun semanticVersionNameFromPlayUpdateInfo(appUpdateInfo: AppUpdateInfo): String = semanticVersionNameFromPlayStoreVersionCode(appUpdateInfo.availableVersionCode())
