package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class ViewOptionsPrefs(
    private val context: Context,
) {
    private object Keys {
        val SORT_KEY = stringPreferencesKey("sort_key")
        val SORT_DIR = stringPreferencesKey("sort_dir")
        val GROUP_BY = stringPreferencesKey("group_by")
    }

    val state: Flow<ViewOptions> =
        context.viewOptionsDataStore.data.map { prefs ->
            val defaultViewOptions = ViewOptions()
            ViewOptions(
                sortKey =
                    runCatching { SortKey.valueOf(prefs[Keys.SORT_KEY] ?: "") }
                        .getOrDefault(defaultViewOptions.sortKey),
                sortDir =
                    runCatching { SortDir.valueOf(prefs[Keys.SORT_DIR] ?: "") }
                        .getOrDefault(defaultViewOptions.sortDir),
                groupBy =
                    runCatching { GroupBy.valueOf(prefs[Keys.GROUP_BY] ?: "") }
                        .getOrDefault(defaultViewOptions.groupBy),
            )
        }

    suspend fun setViewOptions(value: ViewOptions) {
        context.viewOptionsDataStore.edit {
            it[Keys.SORT_KEY] = value.sortKey.name
            it[Keys.SORT_DIR] = value.sortDir.name
            it[Keys.GROUP_BY] = value.groupBy.name
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.viewOptionsDataStore.data.first()
        val defaultViewOptions = ViewOptions()
        return JSONObject().apply {
            put(Keys.SORT_KEY.name, prefs[Keys.SORT_KEY] ?: defaultViewOptions.sortKey.name)
            put(Keys.SORT_DIR.name, prefs[Keys.SORT_DIR] ?: defaultViewOptions.sortDir.name)
            put(Keys.GROUP_BY.name, prefs[Keys.GROUP_BY] ?: defaultViewOptions.groupBy.name)
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.viewOptionsDataStore.edit { mutable ->
            fun stringOrNull(key: String): String? = if (json.has(key) && !json.isNull(key)) json.getString(key) else null
            stringOrNull(Keys.SORT_KEY.name)?.let { mutable[Keys.SORT_KEY] = it }
            stringOrNull(Keys.SORT_DIR.name)?.let { mutable[Keys.SORT_DIR] = it }
            stringOrNull(Keys.GROUP_BY.name)?.let { mutable[Keys.GROUP_BY] = it }
        }
    }

    suspend fun reset() {
        context.viewOptionsDataStore.edit { it.clear() }
    }
}
