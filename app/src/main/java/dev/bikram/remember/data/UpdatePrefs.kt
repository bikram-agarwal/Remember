package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class UpdateCheckSchedule {
    AT_APP_START,
    DAILY_AT_21,
    WEEKLY_MONDAY_AT_21,
    NEVER,
}

data class UpdatePreferencesState(
    val updateCheckSchedule: UpdateCheckSchedule = UpdateCheckSchedule.AT_APP_START,
    val notifyOnNewUpdates: Boolean = false,
    val updateLastNotifiedDedupeKey: String = "",
    val saveUpdateApkToDownloads: Boolean = false,
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
    }

    val state: Flow<UpdatePreferencesState> =
        context.updateDataStore.data.map { prefs ->
            UpdatePreferencesState(
                updateCheckSchedule =
                    prefs[Keys.UPDATE_CHECK_SCHEDULE]
                        ?.let { raw -> runCatching { UpdateCheckSchedule.valueOf(raw) }.getOrNull() }
                        ?: UpdateCheckSchedule.AT_APP_START,
                notifyOnNewUpdates = prefs[Keys.NOTIFY_ON_NEW_UPDATES] ?: false,
                updateLastNotifiedDedupeKey = prefs[Keys.UPDATE_LAST_NOTIFIED_DEDUPE_KEY].orEmpty(),
                saveUpdateApkToDownloads = prefs[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] ?: false,
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

    suspend fun setSaveUpdateApkToDownloads(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.SAVE_UPDATE_APK_TO_DOWNLOADS] = enabled
        }
    }
}
