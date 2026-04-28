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
    val swipeGestureMode: SwipeGestureMode = SwipeGestureMode.REVEAL_ACTIONS,
    val swipeStartToEnd: NoteSwipeAction = NoteSwipeAction.EDIT,
    val swipeEndToStart: NoteSwipeAction = NoteSwipeAction.TRASH,
    val swipeStartToEndRevealActions: List<NoteSwipeAction?> = DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS,
    val swipeEndToStartRevealActions: List<NoteSwipeAction?> = DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS,
)

enum class SwipeGestureMode {
    EXECUTE_ONE,
    REVEAL_ACTIONS,
}

val DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS: List<NoteSwipeAction?> =
    listOf(NoteSwipeAction.EDIT, NoteSwipeAction.DUPLICATE, NoteSwipeAction.TOGGLE_FAVORITE)

val DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS: List<NoteSwipeAction?> =
    listOf(NoteSwipeAction.MARK_DONE, NoteSwipeAction.ARCHIVE, NoteSwipeAction.TRASH)

private val Context.interactionDataStore by preferencesDataStore(name = "interaction_prefs")

class InteractionPrefs(
    private val context: Context,
) {
    private object Keys {
        val HAPTIC = booleanPreferencesKey("haptic_feedback_enabled")
        val SWIPE_GESTURE_MODE = stringPreferencesKey("swipe_gesture_mode")
        val SWIPE_START_TO_END = stringPreferencesKey("swipe_start_to_end")
        val SWIPE_END_TO_START = stringPreferencesKey("swipe_end_to_start")
        val SWIPE_START_TO_END_REVEAL_1 = stringPreferencesKey("swipe_start_to_end_reveal_1")
        val SWIPE_START_TO_END_REVEAL_2 = stringPreferencesKey("swipe_start_to_end_reveal_2")
        val SWIPE_START_TO_END_REVEAL_3 = stringPreferencesKey("swipe_start_to_end_reveal_3")
        val SWIPE_END_TO_START_REVEAL_1 = stringPreferencesKey("swipe_end_to_start_reveal_1")
        val SWIPE_END_TO_START_REVEAL_2 = stringPreferencesKey("swipe_end_to_start_reveal_2")
        val SWIPE_END_TO_START_REVEAL_3 = stringPreferencesKey("swipe_end_to_start_reveal_3")
    }

    val state: Flow<InteractionState> =
        context.interactionDataStore.data.map { prefs ->
            InteractionState(
                hapticFeedbackEnabled = prefs[Keys.HAPTIC] ?: true,
                swipeGestureMode =
                    prefs[Keys.SWIPE_GESTURE_MODE]
                        ?.let { runCatching { SwipeGestureMode.valueOf(it) }.getOrNull() }
                        ?: SwipeGestureMode.REVEAL_ACTIONS,
                swipeStartToEnd =
                    prefs[Keys.SWIPE_START_TO_END]
                        ?.let { noteSwipeActionFromStoredName(it) }
                        ?: NoteSwipeAction.EDIT,
                swipeEndToStart =
                    prefs[Keys.SWIPE_END_TO_START]
                        ?.let { noteSwipeActionFromStoredName(it) }
                        ?: NoteSwipeAction.TRASH,
                swipeStartToEndRevealActions =
                    sanitizedRevealActions(
                        listOf(
                            prefs[Keys.SWIPE_START_TO_END_REVEAL_1],
                            prefs[Keys.SWIPE_START_TO_END_REVEAL_2],
                            prefs[Keys.SWIPE_START_TO_END_REVEAL_3],
                        ),
                        DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS,
                    ),
                swipeEndToStartRevealActions =
                    sanitizedRevealActions(
                        listOf(
                            prefs[Keys.SWIPE_END_TO_START_REVEAL_1],
                            prefs[Keys.SWIPE_END_TO_START_REVEAL_2],
                            prefs[Keys.SWIPE_END_TO_START_REVEAL_3],
                        ),
                        DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS,
                    ),
            )
        }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.interactionDataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun setSwipeGestureMode(mode: SwipeGestureMode) {
        context.interactionDataStore.edit { it[Keys.SWIPE_GESTURE_MODE] = mode.name }
    }

    suspend fun setSwipeStartToEnd(action: NoteSwipeAction) {
        context.interactionDataStore.edit { it[Keys.SWIPE_START_TO_END] = action.name }
    }

    suspend fun setSwipeEndToStart(action: NoteSwipeAction) {
        context.interactionDataStore.edit { it[Keys.SWIPE_END_TO_START] = action.name }
    }

    suspend fun setSwipeStartToEndRevealActions(actions: List<NoteSwipeAction?>) {
        context.interactionDataStore.edit { mutable ->
            val normalized = normalizedRevealSlots(actions)
            mutable[Keys.SWIPE_START_TO_END_REVEAL_1] = normalized[0]?.name.orEmpty()
            mutable[Keys.SWIPE_START_TO_END_REVEAL_2] = normalized[1]?.name.orEmpty()
            mutable[Keys.SWIPE_START_TO_END_REVEAL_3] = normalized[2]?.name.orEmpty()
        }
    }

    suspend fun setSwipeEndToStartRevealActions(actions: List<NoteSwipeAction?>) {
        context.interactionDataStore.edit { mutable ->
            val normalized = normalizedRevealSlots(actions)
            mutable[Keys.SWIPE_END_TO_START_REVEAL_1] = normalized[0]?.name.orEmpty()
            mutable[Keys.SWIPE_END_TO_START_REVEAL_2] = normalized[1]?.name.orEmpty()
            mutable[Keys.SWIPE_END_TO_START_REVEAL_3] = normalized[2]?.name.orEmpty()
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.interactionDataStore.data.first()
        return JSONObject().apply {
            put(Keys.HAPTIC.name, prefs[Keys.HAPTIC] ?: true)
            put(Keys.SWIPE_GESTURE_MODE.name, prefs[Keys.SWIPE_GESTURE_MODE] ?: SwipeGestureMode.REVEAL_ACTIONS.name)
            put(Keys.SWIPE_START_TO_END.name, prefs[Keys.SWIPE_START_TO_END].orEmpty())
            put(Keys.SWIPE_END_TO_START.name, prefs[Keys.SWIPE_END_TO_START].orEmpty())
            put(Keys.SWIPE_START_TO_END_REVEAL_1.name, prefs[Keys.SWIPE_START_TO_END_REVEAL_1].orEmpty())
            put(Keys.SWIPE_START_TO_END_REVEAL_2.name, prefs[Keys.SWIPE_START_TO_END_REVEAL_2].orEmpty())
            put(Keys.SWIPE_START_TO_END_REVEAL_3.name, prefs[Keys.SWIPE_START_TO_END_REVEAL_3].orEmpty())
            put(Keys.SWIPE_END_TO_START_REVEAL_1.name, prefs[Keys.SWIPE_END_TO_START_REVEAL_1].orEmpty())
            put(Keys.SWIPE_END_TO_START_REVEAL_2.name, prefs[Keys.SWIPE_END_TO_START_REVEAL_2].orEmpty())
            put(Keys.SWIPE_END_TO_START_REVEAL_3.name, prefs[Keys.SWIPE_END_TO_START_REVEAL_3].orEmpty())
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.interactionDataStore.edit { mutable ->
            if (json.has(Keys.HAPTIC.name) && !json.isNull(Keys.HAPTIC.name)) {
                mutable[Keys.HAPTIC] = json.getBoolean(Keys.HAPTIC.name)
            }
            if (json.has(Keys.SWIPE_GESTURE_MODE.name) && !json.isNull(Keys.SWIPE_GESTURE_MODE.name)) {
                mutable[Keys.SWIPE_GESTURE_MODE] = json.getString(Keys.SWIPE_GESTURE_MODE.name)
            }
            if (json.has(Keys.SWIPE_START_TO_END.name) && !json.isNull(Keys.SWIPE_START_TO_END.name)) {
                mutable[Keys.SWIPE_START_TO_END] = json.getString(Keys.SWIPE_START_TO_END.name)
            }
            if (json.has(Keys.SWIPE_END_TO_START.name) && !json.isNull(Keys.SWIPE_END_TO_START.name)) {
                mutable[Keys.SWIPE_END_TO_START] = json.getString(Keys.SWIPE_END_TO_START.name)
            }
            if (json.has(Keys.SWIPE_START_TO_END_REVEAL_1.name) && !json.isNull(Keys.SWIPE_START_TO_END_REVEAL_1.name)) {
                mutable[Keys.SWIPE_START_TO_END_REVEAL_1] = json.getString(Keys.SWIPE_START_TO_END_REVEAL_1.name)
            }
            if (json.has(Keys.SWIPE_START_TO_END_REVEAL_2.name) && !json.isNull(Keys.SWIPE_START_TO_END_REVEAL_2.name)) {
                mutable[Keys.SWIPE_START_TO_END_REVEAL_2] = json.getString(Keys.SWIPE_START_TO_END_REVEAL_2.name)
            }
            if (json.has(Keys.SWIPE_START_TO_END_REVEAL_3.name) && !json.isNull(Keys.SWIPE_START_TO_END_REVEAL_3.name)) {
                mutable[Keys.SWIPE_START_TO_END_REVEAL_3] = json.getString(Keys.SWIPE_START_TO_END_REVEAL_3.name)
            }
            if (json.has(Keys.SWIPE_END_TO_START_REVEAL_1.name) && !json.isNull(Keys.SWIPE_END_TO_START_REVEAL_1.name)) {
                mutable[Keys.SWIPE_END_TO_START_REVEAL_1] = json.getString(Keys.SWIPE_END_TO_START_REVEAL_1.name)
            }
            if (json.has(Keys.SWIPE_END_TO_START_REVEAL_2.name) && !json.isNull(Keys.SWIPE_END_TO_START_REVEAL_2.name)) {
                mutable[Keys.SWIPE_END_TO_START_REVEAL_2] = json.getString(Keys.SWIPE_END_TO_START_REVEAL_2.name)
            }
            if (json.has(Keys.SWIPE_END_TO_START_REVEAL_3.name) && !json.isNull(Keys.SWIPE_END_TO_START_REVEAL_3.name)) {
                mutable[Keys.SWIPE_END_TO_START_REVEAL_3] = json.getString(Keys.SWIPE_END_TO_START_REVEAL_3.name)
            }
        }
    }

    private fun sanitizedRevealActions(
        stored: List<String?>,
        defaults: List<NoteSwipeAction?>,
    ): List<NoteSwipeAction?> {
        val hasStoredValue = stored.any { value -> value != null }
        val source =
            if (hasStoredValue) {
                stored
            } else {
                defaults.map { action -> action?.name.orEmpty() }
            }
        return normalizedRevealSlots(
            source.map { value ->
                if (value.isNullOrBlank()) {
                    null
                } else {
                    noteSwipeActionFromStoredName(value)
                }
            },
        )
    }

    private fun noteSwipeActionFromStoredName(value: String): NoteSwipeAction? {
        if (value == "TOGGLE_PIN") return NoteSwipeAction.TOGGLE_FAVORITE
        return runCatching { NoteSwipeAction.valueOf(value) }.getOrNull()
    }

    private fun normalizedRevealSlots(actions: List<NoteSwipeAction?>): List<NoteSwipeAction?> {
        val used = mutableSetOf<NoteSwipeAction>()
        return List(REVEAL_SLOT_COUNT) { index ->
            val action = actions.getOrNull(index)
            if (action == null || action in used) {
                null
            } else {
                used += action
                action
            }
        }
    }

    companion object {
        const val REVEAL_SLOT_COUNT = 3
    }
}
