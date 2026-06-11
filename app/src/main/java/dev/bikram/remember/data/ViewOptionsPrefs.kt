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
        val SETTINGS_COLLAPSED_SECTION_KEYS = stringPreferencesKey("settings_collapsed_section_keys")
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
                settingsCollapsedSectionKeys =
                    prefs[Keys.SETTINGS_COLLAPSED_SECTION_KEYS]
                        ?.split(SETTINGS_SECTION_SEPARATOR)
                        ?.filter { sectionKey -> sectionKey.isNotBlank() }
                        .orEmpty(),
            )
        }

    suspend fun setViewOptions(value: ViewOptions) {
        context.viewOptionsDataStore.edit {
            it[Keys.SORT_KEY] = value.sortKey.name
            it[Keys.SORT_DIR] = value.sortDir.name
            it[Keys.GROUP_BY] = value.groupBy.name
            it[Keys.SETTINGS_COLLAPSED_SECTION_KEYS] =
                value.settingsCollapsedSectionKeys.joinToString(SETTINGS_SECTION_SEPARATOR)
        }
    }

    suspend fun setSettingsCollapsedSectionKeys(sectionKeys: Collection<String>) {
        context.viewOptionsDataStore.edit {
            it[Keys.SETTINGS_COLLAPSED_SECTION_KEYS] =
                sectionKeys
                    .filter { sectionKey -> sectionKey.isNotBlank() }
                    .distinct()
                    .joinToString(SETTINGS_SECTION_SEPARATOR)
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.viewOptionsDataStore.data.first()
        val defaultViewOptions = ViewOptions()
        return JSONObject().apply {
            put(Keys.SORT_KEY.name, prefs[Keys.SORT_KEY] ?: defaultViewOptions.sortKey.name)
            put(Keys.SORT_DIR.name, prefs[Keys.SORT_DIR] ?: defaultViewOptions.sortDir.name)
            put(Keys.GROUP_BY.name, prefs[Keys.GROUP_BY] ?: defaultViewOptions.groupBy.name)
            put(
                Keys.SETTINGS_COLLAPSED_SECTION_KEYS.name,
                prefs[Keys.SETTINGS_COLLAPSED_SECTION_KEYS].orEmpty(),
            )
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.viewOptionsDataStore.edit { mutable ->
            fun stringOrNull(key: String): String? = if (json.has(key) && !json.isNull(key)) json.getString(key) else null
            stringOrNull(Keys.SORT_KEY.name)?.let { mutable[Keys.SORT_KEY] = it }
            stringOrNull(Keys.SORT_DIR.name)?.let { mutable[Keys.SORT_DIR] = it }
            stringOrNull(Keys.GROUP_BY.name)?.let { mutable[Keys.GROUP_BY] = it }
            stringOrNull(Keys.SETTINGS_COLLAPSED_SECTION_KEYS.name)?.let {
                mutable[Keys.SETTINGS_COLLAPSED_SECTION_KEYS] = it
            }
        }
    }

    suspend fun reset() {
        context.viewOptionsDataStore.edit { it.clear() }
    }

    private companion object {
        const val SETTINGS_SECTION_SEPARATOR = ","
    }
}
