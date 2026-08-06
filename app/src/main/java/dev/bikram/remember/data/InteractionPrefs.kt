package dev.bikram.remember.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

@Immutable
data class InteractionState(
    val swipeGestureMode: SwipeGestureMode = SwipeGestureMode.REVEAL_ACTIONS,
    /** Direct-mode default: swipe right pins, matching the first reveal slot. */
    val swipeStartToEnd: NoteSwipeAction = DEFAULT_SWIPE_START_TO_END_ACTION,
    val swipeEndToStart: NoteSwipeAction = DEFAULT_SWIPE_END_TO_START_ACTION,
    val swipeStartToEndRevealActions: PersistentList<NoteSwipeAction?> = DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS,
    val swipeEndToStartRevealActions: PersistentList<NoteSwipeAction?> = DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS,
)

enum class SwipeGestureMode {
    EXECUTE_ONE,
    REVEAL_ACTIONS,
}

/**
 * Reveal mode has exactly three slots per direction and its editor can only *swap* two occupied
 * slots - there is no palette to drag an unused action in from. So an action that is not in this
 * default (and not already in a user's saved layout) is unreachable on swipe.
 *
 * Edit is the one deliberately left out: it is the cheapest action to reach another way (tap the
 * note, then Edit), whereas Pin has no other one-gesture path from the list. Existing users keep
 * whatever they already saved - [InteractionPrefs.sanitizedRevealActions] only falls back to these
 * defaults when nothing is stored.
 */
val DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS: PersistentList<NoteSwipeAction?> =
    persistentListOf(NoteSwipeAction.TOGGLE_PIN, NoteSwipeAction.TOGGLE_STAR, NoteSwipeAction.DUPLICATE)

val DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS: PersistentList<NoteSwipeAction?> =
    persistentListOf(NoteSwipeAction.MARK_DONE, NoteSwipeAction.ARCHIVE, NoteSwipeAction.TRASH)

/**
 * Direct-mode ("execute one") defaults.
 *
 * Swipe right pins, matching [DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS]'s first slot, so the two
 * gesture modes agree on what a right swipe does out of the box.
 *
 * Swipe left deliberately stays Trash and does NOT follow the left reveal slots (which lead with
 * Mark done). Direct mode fires on the swipe itself with no confirmation step, and Trash is the
 * long-standing default there; quietly repointing it at Mark done would change what an existing
 * gesture does. Do not "fix" this into symmetry with the reveal defaults.
 */
val DEFAULT_SWIPE_START_TO_END_ACTION: NoteSwipeAction = NoteSwipeAction.TOGGLE_PIN

val DEFAULT_SWIPE_END_TO_START_ACTION: NoteSwipeAction = NoteSwipeAction.TRASH

private val Context.interactionDataStore by preferencesDataStore(name = "interaction_prefs")

class InteractionPrefs(
    private val context: Context,
) {
    private object Keys {
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
                swipeGestureMode =
                    prefs[Keys.SWIPE_GESTURE_MODE]
                        ?.let { runCatching { SwipeGestureMode.valueOf(it) }.getOrNull() }
                        ?: SwipeGestureMode.REVEAL_ACTIONS,
                swipeStartToEnd =
                    prefs[Keys.SWIPE_START_TO_END]
                        ?.let { noteSwipeActionFromStoredName(it) }
                        ?: DEFAULT_SWIPE_START_TO_END_ACTION,
                swipeEndToStart =
                    prefs[Keys.SWIPE_END_TO_START]
                        ?.let { noteSwipeActionFromStoredName(it) }
                        ?: DEFAULT_SWIPE_END_TO_START_ACTION,
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

    suspend fun setSwipeRevealActions(
        startToEndActions: List<NoteSwipeAction?>,
        endToStartActions: List<NoteSwipeAction?>,
    ) {
        context.interactionDataStore.edit { mutable ->
            val startToEnd = normalizedRevealSlots(startToEndActions)
            val endToStart = normalizedRevealSlots(endToStartActions)
            mutable[Keys.SWIPE_START_TO_END_REVEAL_1] = startToEnd[0]?.name.orEmpty()
            mutable[Keys.SWIPE_START_TO_END_REVEAL_2] = startToEnd[1]?.name.orEmpty()
            mutable[Keys.SWIPE_START_TO_END_REVEAL_3] = startToEnd[2]?.name.orEmpty()
            mutable[Keys.SWIPE_END_TO_START_REVEAL_1] = endToStart[0]?.name.orEmpty()
            mutable[Keys.SWIPE_END_TO_START_REVEAL_2] = endToStart[1]?.name.orEmpty()
            mutable[Keys.SWIPE_END_TO_START_REVEAL_3] = endToStart[2]?.name.orEmpty()
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.interactionDataStore.data.first()
        return JSONObject().apply {
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
    ): PersistentList<NoteSwipeAction?> {
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
        ).toPersistentList()
    }

    private fun noteSwipeActionFromStoredName(value: String): NoteSwipeAction? = runCatching { NoteSwipeAction.valueOf(value) }.getOrNull()

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

    suspend fun reset() {
        context.interactionDataStore.edit { it.clear() }
    }

    companion object {
        const val REVEAL_SLOT_COUNT = 3
    }
}
