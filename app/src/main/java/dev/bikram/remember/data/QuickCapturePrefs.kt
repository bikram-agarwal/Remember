package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class QuickCaptureState(
    /** When true, a persistent notification is shown so the user can jump to a blank note. */
    val enabled: Boolean = false,
)

private val Context.quickCaptureDataStore by preferencesDataStore(name = "quick_capture_prefs")

class QuickCapturePrefs(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("quick_capture_enabled")
    }

    val state: Flow<QuickCaptureState> = context.quickCaptureDataStore.data.map { prefs ->
        QuickCaptureState(enabled = prefs[Keys.ENABLED] ?: false)
    }

    suspend fun snapshot(): QuickCaptureState = state.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.quickCaptureDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.quickCaptureDataStore.data.first()
        return JSONObject().apply {
            put(Keys.ENABLED.name, prefs[Keys.ENABLED] ?: false)
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.quickCaptureDataStore.edit { mutable ->
            if (json.has(Keys.ENABLED.name) && !json.isNull(Keys.ENABLED.name)) {
                mutable[Keys.ENABLED] = json.getBoolean(Keys.ENABLED.name)
            }
        }
    }
}
