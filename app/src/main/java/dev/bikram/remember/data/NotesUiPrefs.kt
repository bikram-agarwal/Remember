package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Cover image, adaptive note themes, and note content on cards.
 *
 * These used to be stored in [ThemePrefs]. [migrateFromThemePrefsIfNeeded] moves an existing
 * install across, and [importFromBackup] still accepts them inside an older theme backup.
 */
data class NotesUiState(
    val heroOnCards: Boolean = true,
    val adaptiveNoteThemes: Boolean = true,
    val showNoteContentOnCards: Boolean = true,
)

class NotesUiPrefs(
    private val context: Context,
) {
    private object Keys {
        val HERO_ON_CARDS = booleanPreferencesKey("hero_on_cards")
        val ADAPTIVE_NOTE_THEMES = booleanPreferencesKey("adaptive_note_themes")
        val SHOW_NOTE_CONTENT_ON_CARDS = booleanPreferencesKey("show_note_content_on_cards")
    }

    val state: Flow<NotesUiState> =
        combine(
            context.notesUiPrefsDataStore.data,
            context.themePrefsDataStore.data,
        ) { notesUi, theme ->
            NotesUiState(
                heroOnCards = notesUi[Keys.HERO_ON_CARDS] ?: theme[Keys.HERO_ON_CARDS] ?: true,
                adaptiveNoteThemes = notesUi[Keys.ADAPTIVE_NOTE_THEMES] ?: theme[Keys.ADAPTIVE_NOTE_THEMES] ?: true,
                showNoteContentOnCards =
                    notesUi[Keys.SHOW_NOTE_CONTENT_ON_CARDS] ?: theme[Keys.SHOW_NOTE_CONTENT_ON_CARDS] ?: true,
            )
        }

    suspend fun setHeroOnCards(value: Boolean) {
        context.notesUiPrefsDataStore.edit { it[Keys.HERO_ON_CARDS] = value }
    }

    suspend fun setAdaptiveNoteThemes(value: Boolean) {
        context.notesUiPrefsDataStore.edit { it[Keys.ADAPTIVE_NOTE_THEMES] = value }
    }

    suspend fun setShowNoteContentOnCards(value: Boolean) {
        context.notesUiPrefsDataStore.edit { it[Keys.SHOW_NOTE_CONTENT_ON_CARDS] = value }
    }

    /**
     * Copy any of these keys still sitting in theme prefs into this store, then drop them from
     * theme prefs. A no-op once the move has happened.
     */
    suspend fun migrateFromThemePrefsIfNeeded() {
        val themeSnapshot = context.themePrefsDataStore.data.first()
        val legacyKeys =
            listOf(Keys.HERO_ON_CARDS, Keys.ADAPTIVE_NOTE_THEMES, Keys.SHOW_NOTE_CONTENT_ON_CARDS)
                .filter { key -> themeSnapshot.contains(key) }
        if (legacyKeys.isEmpty()) return
        context.notesUiPrefsDataStore.edit { notesUi ->
            legacyKeys.forEach { key ->
                if (!notesUi.contains(key)) {
                    themeSnapshot[key]?.let { stored -> notesUi[key] = stored }
                }
            }
        }
        context.themePrefsDataStore.edit { theme ->
            legacyKeys.forEach { key -> theme.remove(key) }
        }
    }

    suspend fun exportForBackup(): JSONObject {
        val notesUi = context.notesUiPrefsDataStore.data.first()
        val theme = context.themePrefsDataStore.data.first()
        return JSONObject().apply {
            put(Keys.HERO_ON_CARDS.name, notesUi[Keys.HERO_ON_CARDS] ?: theme[Keys.HERO_ON_CARDS] ?: true)
            put(
                Keys.ADAPTIVE_NOTE_THEMES.name,
                notesUi[Keys.ADAPTIVE_NOTE_THEMES] ?: theme[Keys.ADAPTIVE_NOTE_THEMES] ?: true,
            )
            put(
                Keys.SHOW_NOTE_CONTENT_ON_CARDS.name,
                notesUi[Keys.SHOW_NOTE_CONTENT_ON_CARDS] ?: theme[Keys.SHOW_NOTE_CONTENT_ON_CARDS] ?: true,
            )
        }
    }

    /**
     * [json] is the notes UI block from a current backup. [legacyThemeJson] is the theme block,
     * which older backups used for these same keys. A key in [json] wins.
     */
    suspend fun importFromBackup(
        json: JSONObject?,
        legacyThemeJson: JSONObject?,
    ) {
        val hasNotesUi = json != null && json.length() > 0
        val hasLegacyTheme = legacyThemeJson != null && legacyThemeJson.length() > 0
        if (!hasNotesUi && !hasLegacyTheme) return
        context.notesUiPrefsDataStore.edit { mutable ->
            fun booleanOrNull(
                source: JSONObject?,
                key: String,
            ): Boolean? {
                if (source == null || !source.has(key) || source.isNull(key)) return null
                return when (val rawValue = source.opt(key)) {
                    null -> null
                    is Boolean -> rawValue
                    is String ->
                        when (rawValue.trim().lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    else -> null
                }
            }
            (
                booleanOrNull(json, Keys.HERO_ON_CARDS.name)
                    ?: booleanOrNull(legacyThemeJson, Keys.HERO_ON_CARDS.name)
            )?.let { value -> mutable[Keys.HERO_ON_CARDS] = value }
            (
                booleanOrNull(json, Keys.ADAPTIVE_NOTE_THEMES.name)
                    ?: booleanOrNull(legacyThemeJson, Keys.ADAPTIVE_NOTE_THEMES.name)
            )?.let { value -> mutable[Keys.ADAPTIVE_NOTE_THEMES] = value }
            (
                booleanOrNull(json, Keys.SHOW_NOTE_CONTENT_ON_CARDS.name)
                    ?: booleanOrNull(legacyThemeJson, Keys.SHOW_NOTE_CONTENT_ON_CARDS.name)
            )?.let { value -> mutable[Keys.SHOW_NOTE_CONTENT_ON_CARDS] = value }
        }
    }

    suspend fun reset() {
        context.notesUiPrefsDataStore.edit { it.clear() }
    }
}
