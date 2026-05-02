package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

data class IconPickerFavoritesState(
    val iconKeys: List<String> = emptyList(),
    val emojis: List<String> = emptyList(),
)

const val ICON_PICKER_MAX_FAVORITES = 30

private val Context.iconPickerDataStore by preferencesDataStore(name = "icon_picker_prefs")

class IconPickerPrefs(
    private val context: Context,
) {
    private object Keys {
        val FAVORITE_ICON_KEYS = stringPreferencesKey("favorite_icon_keys")
        val FAVORITE_EMOJIS = stringPreferencesKey("favorite_emojis")
    }

    val favorites: Flow<IconPickerFavoritesState> =
        context.iconPickerDataStore.data.map { prefs ->
            IconPickerFavoritesState(
                iconKeys = decodeList(prefs[Keys.FAVORITE_ICON_KEYS].orEmpty()),
                emojis = decodeList(prefs[Keys.FAVORITE_EMOJIS].orEmpty()),
            )
        }

    suspend fun setFavoriteIconKeys(iconKeys: List<String>) {
        context.iconPickerDataStore.edit { prefs ->
            prefs[Keys.FAVORITE_ICON_KEYS] = encodeList(iconKeys)
        }
    }

    suspend fun setFavoriteEmojis(emojis: List<String>) {
        context.iconPickerDataStore.edit { prefs ->
            prefs[Keys.FAVORITE_EMOJIS] = encodeList(emojis)
        }
    }

    private fun decodeList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(value)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val entry = jsonArray.optString(index).trim()
                    if (entry.isNotEmpty() && !contains(entry)) {
                        add(entry)
                    }
                }
            }.take(ICON_PICKER_MAX_FAVORITES)
        }.getOrDefault(emptyList())
    }

    private fun encodeList(values: List<String>): String {
        val jsonArray = JSONArray()
        values
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(ICON_PICKER_MAX_FAVORITES)
            .forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
