package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class ReminderPreferencesState(
    val keepReminderNotificationsUntilDone: Boolean = false,
    val reminderSummaryNotificationEnabled: Boolean = false,
)

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_prefs")

class ReminderPrefs(
    private val context: Context,
) {
    private object Keys {
        val KEEP_UNTIL_DONE = booleanPreferencesKey("keep_reminder_notifications_until_done")
        val SUMMARY_NOTIFICATION = booleanPreferencesKey("reminder_summary_notification")
    }

    val state: Flow<ReminderPreferencesState> =
        context.reminderDataStore.data.map { prefs ->
            ReminderPreferencesState(
                keepReminderNotificationsUntilDone = prefs[Keys.KEEP_UNTIL_DONE] ?: false,
                reminderSummaryNotificationEnabled = prefs[Keys.SUMMARY_NOTIFICATION] ?: false,
            )
        }

    suspend fun snapshot(): ReminderPreferencesState = state.first()

    suspend fun setKeepReminderNotificationsUntilDone(enabled: Boolean) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.KEEP_UNTIL_DONE] = enabled
        }
    }

    suspend fun setReminderSummaryNotificationEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { prefs ->
            prefs[Keys.SUMMARY_NOTIFICATION] = enabled
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.reminderDataStore.data.first()
        return JSONObject().apply {
            put(Keys.KEEP_UNTIL_DONE.name, prefs[Keys.KEEP_UNTIL_DONE] ?: false)
            put(Keys.SUMMARY_NOTIFICATION.name, prefs[Keys.SUMMARY_NOTIFICATION] ?: false)
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.reminderDataStore.edit { mutable ->
            if (json.has(Keys.KEEP_UNTIL_DONE.name) && !json.isNull(Keys.KEEP_UNTIL_DONE.name)) {
                mutable[Keys.KEEP_UNTIL_DONE] = json.getBoolean(Keys.KEEP_UNTIL_DONE.name)
            }
            if (json.has(Keys.SUMMARY_NOTIFICATION.name) && !json.isNull(Keys.SUMMARY_NOTIFICATION.name)) {
                mutable[Keys.SUMMARY_NOTIFICATION] = json.getBoolean(Keys.SUMMARY_NOTIFICATION.name)
            }
        }
    }

    suspend fun reset() {
        context.reminderDataStore.edit { it.clear() }
    }
}
