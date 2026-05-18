package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.devModeDataStore by preferencesDataStore(name = "dev_mode_prefs")

class DevModePrefs(
    private val context: Context,
) {
    private object Keys {
        val DEV_MODE_ENABLED = booleanPreferencesKey("dev_mode_enabled")
    }

    val isEnabled: Flow<Boolean> =
        context.devModeDataStore.data.map { prefs ->
            prefs[Keys.DEV_MODE_ENABLED] ?: false
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.devModeDataStore.edit { prefs ->
            prefs[Keys.DEV_MODE_ENABLED] = enabled
        }
    }
}
