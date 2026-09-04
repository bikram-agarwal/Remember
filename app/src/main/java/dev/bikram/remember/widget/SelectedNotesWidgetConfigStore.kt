package dev.bikram.remember.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.stringPreferencesKey

val KEY_SELECTED_NOTES_FILTER_TYPE = stringPreferencesKey("selected_notes_filter_type")
val KEY_SELECTED_NOTES_TAG = stringPreferencesKey("selected_notes_tag")

enum class SelectedNotesFilterType {
    ALL,
    STARRED,
    PINNED,
    TAG,
}

data class SelectedNotesWidgetConfig(
    val filterType: SelectedNotesFilterType = SelectedNotesFilterType.ALL,
    val tag: String = "",
)

object SelectedNotesWidgetConfigStore {
    private const val PREFS_NAME = "selected_notes_widget_prefs"
    private const val KEY_FILTER_TYPE_PREFIX = "widget_filter_type_"
    private const val KEY_TAG_PREFIX = "widget_tag_"
    private const val KEY_REVISION = "widget_config_revision"

    private fun getPreferences(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRevision(context: Context): Long = getPreferences(context).getLong(KEY_REVISION, 0L)

    fun loadConfig(
        context: Context,
        appWidgetId: Int,
    ): SelectedNotesWidgetConfig {
        val preferences = getPreferences(context)
        val rawFilterType =
            preferences.getString("$KEY_FILTER_TYPE_PREFIX$appWidgetId", null)
                ?: preferences.getString(KEY_FILTER_TYPE_PREFIX, null)
                ?: return SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL)
        val filterType =
            runCatching {
                SelectedNotesFilterType.valueOf(rawFilterType)
            }.getOrDefault(SelectedNotesFilterType.ALL)
        val tag =
            preferences.getString("$KEY_TAG_PREFIX$appWidgetId", null)
                ?: preferences.getString(KEY_TAG_PREFIX, "")
                ?: ""
        return SelectedNotesWidgetConfig(filterType = filterType, tag = tag)
    }

    fun saveConfig(
        context: Context,
        appWidgetId: Int,
        config: SelectedNotesWidgetConfig,
    ) {
        val nextRevision = getRevision(context) + 1L
        getPreferences(context).edit {
            putString("$KEY_FILTER_TYPE_PREFIX$appWidgetId", config.filterType.name)
            putString("$KEY_TAG_PREFIX$appWidgetId", config.tag)
            // Also store as latest default so any widget instance without an explicit ID can fall back to it
            putString(KEY_FILTER_TYPE_PREFIX, config.filterType.name)
            putString(KEY_TAG_PREFIX, config.tag)
            putLong(KEY_REVISION, nextRevision)
        }
    }

    fun deleteConfig(
        context: Context,
        appWidgetId: Int,
    ) {
        getPreferences(context).edit {
            remove("$KEY_FILTER_TYPE_PREFIX$appWidgetId")
            remove("$KEY_TAG_PREFIX$appWidgetId")
        }
    }

    fun deleteConfigs(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        getPreferences(context).edit {
            for (widgetId in appWidgetIds) {
                remove("$KEY_FILTER_TYPE_PREFIX$widgetId")
                remove("$KEY_TAG_PREFIX$widgetId")
            }
        }
    }
}
