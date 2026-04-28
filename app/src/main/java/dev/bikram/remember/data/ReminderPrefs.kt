package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
}
