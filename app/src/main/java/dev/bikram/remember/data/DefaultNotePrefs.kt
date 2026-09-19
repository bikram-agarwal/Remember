package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Defaults applied when creating a new note or list.
 *
 * Remember-only domain prefs (FilePipe has no analogous "new note defaults" concept).
 *
 * [defaultReminderMinutesOfDay] is minutes since local midnight (0..1439). It seeds the reminder
 * picker only: creating a note or list never attaches a reminder on its own.
 */
data class DefaultNotePreferencesState(
    val defaultVisibility: Visibility = Visibility.DEFAULT,
    val defaultImportance: Importance = Importance.DEFAULT,
    val defaultReminderMinutesOfDay: Int = DEFAULT_REMINDER_MINUTES_OF_DAY,
    val defaultRecurrence: RecurrenceRule? = null,
)

/** 9:00 in local wall-clock time. */
const val DEFAULT_REMINDER_MINUTES_OF_DAY = 9 * 60

private val Context.defaultNoteDataStore by preferencesDataStore(name = "default_note_prefs")

class DefaultNotePrefs internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.defaultNoteDataStore)

    private object Keys {
        val VISIBILITY = stringPreferencesKey("default_visibility")
        val IMPORTANCE = stringPreferencesKey("default_importance")
        val REMINDER_MINUTES = intPreferencesKey("default_reminder_minutes_of_day")
        val RECURRENCE_JSON = stringPreferencesKey("default_recurrence_json")
    }

    val state: Flow<DefaultNotePreferencesState> =
        dataStore.data.map { prefs ->
            DefaultNotePreferencesState(
                defaultVisibility =
                    prefs[Keys.VISIBILITY]?.let { raw ->
                        runCatching { Visibility.valueOf(raw) }.getOrNull()
                    } ?: Visibility.DEFAULT,
                defaultImportance =
                    prefs[Keys.IMPORTANCE]?.let { raw ->
                        runCatching { Importance.valueOf(raw) }.getOrNull()
                    } ?: Importance.DEFAULT,
                defaultReminderMinutesOfDay =
                    prefs[Keys.REMINDER_MINUTES]?.takeIf { minutes -> minutes in 0..1439 }
                        ?: DEFAULT_REMINDER_MINUTES_OF_DAY,
                defaultRecurrence = RecurrenceRule.fromJson(prefs[Keys.RECURRENCE_JSON]),
            )
        }

    suspend fun snapshot(): DefaultNotePreferencesState = state.first()

    suspend fun setDefaultVisibility(visibility: Visibility) {
        dataStore.edit { prefs ->
            prefs[Keys.VISIBILITY] = visibility.name
        }
    }

    suspend fun setDefaultImportance(importance: Importance) {
        dataStore.edit { prefs ->
            prefs[Keys.IMPORTANCE] = importance.name
        }
    }

    suspend fun setDefaultReminderMinutesOfDay(minutesOfDay: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_MINUTES] = minutesOfDay.coerceIn(0, 1439)
        }
    }

    suspend fun setDefaultRecurrence(rule: RecurrenceRule?) {
        dataStore.edit { prefs ->
            val encoded = RecurrenceRule.toJson(rule)
            if (encoded == null) {
                prefs.remove(Keys.RECURRENCE_JSON)
            } else {
                prefs[Keys.RECURRENCE_JSON] = encoded
            }
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val snapshot = snapshot()
        return JSONObject().apply {
            put(Keys.VISIBILITY.name, snapshot.defaultVisibility.name)
            put(Keys.IMPORTANCE.name, snapshot.defaultImportance.name)
            put(Keys.REMINDER_MINUTES.name, snapshot.defaultReminderMinutesOfDay)
            val recurrenceJson = RecurrenceRule.toJson(snapshot.defaultRecurrence)
            if (recurrenceJson != null) {
                put(Keys.RECURRENCE_JSON.name, recurrenceJson)
            } else {
                put(Keys.RECURRENCE_JSON.name, JSONObject.NULL)
            }
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        dataStore.edit { mutable ->
            if (json.has(Keys.VISIBILITY.name) && !json.isNull(Keys.VISIBILITY.name)) {
                val visibility =
                    runCatching { Visibility.valueOf(json.getString(Keys.VISIBILITY.name)) }.getOrNull()
                if (visibility != null) {
                    mutable[Keys.VISIBILITY] = visibility.name
                }
            }
            if (json.has(Keys.IMPORTANCE.name) && !json.isNull(Keys.IMPORTANCE.name)) {
                val importance =
                    runCatching { Importance.valueOf(json.getString(Keys.IMPORTANCE.name)) }.getOrNull()
                if (importance != null) {
                    mutable[Keys.IMPORTANCE] = importance.name
                }
            }
            if (json.has(Keys.REMINDER_MINUTES.name) && !json.isNull(Keys.REMINDER_MINUTES.name)) {
                val minutes = json.getInt(Keys.REMINDER_MINUTES.name)
                if (minutes in 0..1439) {
                    mutable[Keys.REMINDER_MINUTES] = minutes
                }
            }
            if (json.has(Keys.RECURRENCE_JSON.name)) {
                if (json.isNull(Keys.RECURRENCE_JSON.name)) {
                    mutable.remove(Keys.RECURRENCE_JSON)
                } else {
                    val rule = RecurrenceRule.fromJson(json.getString(Keys.RECURRENCE_JSON.name))
                    val encoded = RecurrenceRule.toJson(rule)
                    if (encoded != null) {
                        mutable[Keys.RECURRENCE_JSON] = encoded
                    } else {
                        mutable.remove(Keys.RECURRENCE_JSON)
                    }
                }
            }
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    companion object {
        fun minutesOfDay(
            hour: Int,
            minute: Int,
        ): Int = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

        fun hourFromMinutesOfDay(minutesOfDay: Int): Int = minutesOfDay.coerceIn(0, 1439) / 60

        fun minuteFromMinutesOfDay(minutesOfDay: Int): Int = minutesOfDay.coerceIn(0, 1439) % 60
    }
}
