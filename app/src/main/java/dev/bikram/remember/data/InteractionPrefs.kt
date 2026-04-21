package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class InteractionState(
    val hapticFeedbackEnabled: Boolean = true,
    val swipeStartToEnd: NoteSwipeAction = NoteSwipeAction.EDIT,
    val swipeEndToStart: NoteSwipeAction = NoteSwipeAction.TRASH,
)

private val Context.interactionDataStore by preferencesDataStore(name = "interaction_prefs")

class InteractionPrefs(private val context: Context) {

    private object Keys {
        val HAPTIC = booleanPreferencesKey("haptic_feedback_enabled")
        val SWIPE_START_TO_END = stringPreferencesKey("swipe_start_to_end")
        val SWIPE_END_TO_START = stringPreferencesKey("swipe_end_to_start")
    }

    val state: Flow<InteractionState> = context.interactionDataStore.data.map { prefs ->
        InteractionState(
            hapticFeedbackEnabled = prefs[Keys.HAPTIC] ?: true,
            swipeStartToEnd = prefs[Keys.SWIPE_START_TO_END]
                ?.let { runCatching { NoteSwipeAction.valueOf(it) }.getOrNull() }
                ?: NoteSwipeAction.EDIT,
            swipeEndToStart = prefs[Keys.SWIPE_END_TO_START]
                ?.let { runCatching { NoteSwipeAction.valueOf(it) }.getOrNull() }
                ?: NoteSwipeAction.TRASH,
        )
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.interactionDataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun setSwipeStartToEnd(action: NoteSwipeAction) {
        context.interactionDataStore.edit { it[Keys.SWIPE_START_TO_END] = action.name }
    }

    suspend fun setSwipeEndToStart(action: NoteSwipeAction) {
        context.interactionDataStore.edit { it[Keys.SWIPE_END_TO_START] = action.name }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.interactionDataStore.data.first()
        return JSONObject().apply {
            put(Keys.HAPTIC.name, prefs[Keys.HAPTIC] ?: true)
            put(Keys.SWIPE_START_TO_END.name, prefs[Keys.SWIPE_START_TO_END].orEmpty())
            put(Keys.SWIPE_END_TO_START.name, prefs[Keys.SWIPE_END_TO_START].orEmpty())
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.interactionDataStore.edit { mutable ->
            if (json.has(Keys.HAPTIC.name) && !json.isNull(Keys.HAPTIC.name)) {
                mutable[Keys.HAPTIC] = json.getBoolean(Keys.HAPTIC.name)
            }
            if (json.has(Keys.SWIPE_START_TO_END.name) && !json.isNull(Keys.SWIPE_START_TO_END.name)) {
                mutable[Keys.SWIPE_START_TO_END] = json.getString(Keys.SWIPE_START_TO_END.name)
            }
            if (json.has(Keys.SWIPE_END_TO_START.name) && !json.isNull(Keys.SWIPE_END_TO_START.name)) {
                mutable[Keys.SWIPE_END_TO_START] = json.getString(Keys.SWIPE_END_TO_START.name)
            }
        }
    }
}
