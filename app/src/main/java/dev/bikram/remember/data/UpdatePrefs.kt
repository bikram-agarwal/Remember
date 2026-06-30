package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.bikram.remember.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class UpdateCheckSchedule {
    AT_APP_START,
    DAILY_AT_21,
    WEEKLY_MONDAY_AT_21,
    NEVER,
}

fun defaultUpdateCheckSchedule(): UpdateCheckSchedule =
    if (BuildConfig.FLAVOR == "fdroid") {
        UpdateCheckSchedule.NEVER
    } else {
        UpdateCheckSchedule.AT_APP_START
    }

data class UpdatePreferencesState(
    val updateCheckSchedule: UpdateCheckSchedule = defaultUpdateCheckSchedule(),
    val notifyOnNewUpdates: Boolean = false,
    val updateLastNotifiedDedupeKey: String = "",
    val saveUpdateApkToDownloads: Boolean = false,
    val updateApkDownloadsCopySucceeded: Boolean = false,
    val inAppReviewAutoNeverAskAgain: Boolean = false,
    val playAutoReviewPromptedForLastUpdateTime: Long = 0L,
)

data class GithubReleaseAckState(
    val fingerprint: String?,
    val forInstalledVersion: String?,
)

private val Context.updateDataStore by preferencesDataStore(name = "update_prefs")

class UpdatePrefs(
    private val context: Context,
) {
    private object Keys {
        val UPDATE_CHECK_SCHEDULE = stringPreferencesKey("update_check_schedule")
        val NOTIFY_ON_NEW_UPDATES = booleanPreferencesKey("notify_on_new_updates")
        val UPDATE_LAST_NOTIFIED_DEDUPE_KEY = stringPreferencesKey("update_last_notified_dedupe_key")
        val SAVE_UPDATE_APK_TO_DOWNLOADS = booleanPreferencesKey("save_update_apk_to_downloads")
        val UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED = booleanPreferencesKey("update_apk_downloads_copy_succeeded")
        val GITHUB_ACK_FINGERPRINT = stringPreferencesKey("github_last_acknowledged_release_fingerprint")
        val GITHUB_ACK_INSTALLED_VERSION = stringPreferencesKey("github_acknowledged_for_installed_version")
        val IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN = booleanPreferencesKey("in_app_review_auto_never_ask_again")
        val PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME =
            longPreferencesKey("play_auto_review_prompted_for_last_update_time")
    }

    val state: Flow<UpdatePreferencesState> =
        context.updateDataStore.data.map { prefs ->
            UpdatePreferencesState(
                updateCheckSchedule =
                    prefs[Keys.UPDATE_CHECK_SCHEDULE]
                        ?.let { raw -> runCatching { UpdateCheckSchedule.valueOf(raw) }.getOrNull() }
                        ?: defaultUpdateCheckSchedule(),
                notifyOnNewUpdates = prefs[Keys.NOTIFY_ON_NEW_UPDATES] ?: false,
                updateLastNotifiedDedupeKey = prefs[Keys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY].orEmpty(),
                saveUpdateApkToDownloads = prefs[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] ?: false,
                updateApkDownloadsCopySucceeded = prefs[Keys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED] ?: false,
                inAppReviewAutoNeverAskAgain = prefs[Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] ?: false,
                playAutoReviewPromptedForLastUpdateTime =
                    prefs[Keys.PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME] ?: 0L,
            )
        }

    suspend fun snapshot(): UpdatePreferencesState = state.first()

    suspend fun setUpdateCheckSchedule(schedule: UpdateCheckSchedule) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.UPDATE_CHECK_SCHEDULE] = schedule.name
            if (schedule == UpdateCheckSchedule.NEVER) {
                prefs[Keys.NOTIFY_ON_NEW_UPDATES] = false
            }
        }
    }

    suspend fun setNotifyOnNewUpdates(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.NOTIFY_ON_NEW_UPDATES] = enabled
        }
    }

    suspend fun setUpdateLastNotifiedDedupeKey(dedupeKey: String) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY] = dedupeKey
        }
    }

    suspend fun clearUpdateLastNotifiedDedupeKey() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(Keys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY)
        }
    }

    suspend fun setSaveUpdateApkToDownloads(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] = enabled
            prefs.remove(Keys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED)
        }
    }

    suspend fun clearUpdateApkDownloadsCopySucceeded() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(Keys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED)
        }
    }

    suspend fun markUpdateApkDownloadsCopySucceeded() {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.UPDATE_APK_DOWNLOADS_COPY_SUCCEEDED] = true
        }
    }

    suspend fun readGithubReleaseAck(): GithubReleaseAckState {
        val prefs = context.updateDataStore.data.first()
        return GithubReleaseAckState(
            fingerprint = prefs[Keys.GITHUB_ACK_FINGERPRINT],
            forInstalledVersion = prefs[Keys.GITHUB_ACK_INSTALLED_VERSION],
        )
    }

    suspend fun writeGithubReleaseAck(
        fingerprint: String,
        installedVersionName: String,
    ) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.GITHUB_ACK_FINGERPRINT] = fingerprint
            prefs[Keys.GITHUB_ACK_INSTALLED_VERSION] = installedVersionName
        }
    }

    suspend fun setInAppReviewAutoNeverAskAgain(neverAskAgain: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] = neverAskAgain
        }
    }

    suspend fun setPlayAutoReviewPromptedForLastUpdateTime(lastUpdateTimeMillis: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.PLAY_AUTO_REVIEW_PROMPTED_FOR_LAST_UPDATE_TIME] = lastUpdateTimeMillis
        }
    }

    suspend fun clearGithubReleaseAck() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(Keys.GITHUB_ACK_FINGERPRINT)
            prefs.remove(Keys.GITHUB_ACK_INSTALLED_VERSION)
        }
    }

    /**
     * Backs up only the user-facing update preferences. Install/device-specific bookkeeping
     * (last-notified dedupe key, APK-copy success flag, GitHub release acknowledgement fingerprint,
     * Play review-prompt timestamps) is intentionally excluded: restoring it onto a fresh install
     * would wrongly suppress update notifications or misrepresent state for the new install.
     */
    suspend fun exportForBackup(): JSONObject {
        val prefs = context.updateDataStore.data.first()
        return JSONObject().apply {
            put(
                Keys.UPDATE_CHECK_SCHEDULE.name,
                prefs[Keys.UPDATE_CHECK_SCHEDULE] ?: defaultUpdateCheckSchedule().name,
            )
            put(Keys.NOTIFY_ON_NEW_UPDATES.name, prefs[Keys.NOTIFY_ON_NEW_UPDATES] ?: false)
            put(Keys.SAVE_UPDATE_APK_TO_DOWNLOADS.name, prefs[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] ?: false)
            put(
                Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN.name,
                prefs[Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] ?: false,
            )
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.updateDataStore.edit { mutable ->
            if (json.has(Keys.UPDATE_CHECK_SCHEDULE.name) && !json.isNull(Keys.UPDATE_CHECK_SCHEDULE.name)) {
                val raw = json.getString(Keys.UPDATE_CHECK_SCHEDULE.name)
                if (runCatching { UpdateCheckSchedule.valueOf(raw) }.isSuccess) {
                    mutable[Keys.UPDATE_CHECK_SCHEDULE] = raw
                }
            }
            if (json.has(Keys.NOTIFY_ON_NEW_UPDATES.name) && !json.isNull(Keys.NOTIFY_ON_NEW_UPDATES.name)) {
                mutable[Keys.NOTIFY_ON_NEW_UPDATES] = json.getBoolean(Keys.NOTIFY_ON_NEW_UPDATES.name)
            }
            if (json.has(Keys.SAVE_UPDATE_APK_TO_DOWNLOADS.name) && !json.isNull(Keys.SAVE_UPDATE_APK_TO_DOWNLOADS.name)) {
                mutable[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] = json.getBoolean(Keys.SAVE_UPDATE_APK_TO_DOWNLOADS.name)
            }
            if (json.has(Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN.name) &&
                !json.isNull(Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN.name)
            ) {
                mutable[Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN] =
                    json.getBoolean(Keys.IN_APP_REVIEW_AUTO_NEVER_ASK_AGAIN.name)
            }
        }
    }

    suspend fun reset() {
        context.updateDataStore.edit { it.clear() }
    }
}
