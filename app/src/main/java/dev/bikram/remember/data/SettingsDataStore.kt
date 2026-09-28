package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.themePrefsDataStore by preferencesDataStore(name = "theme_prefs")

internal val Context.notesUiPrefsDataStore by preferencesDataStore(name = "notes_ui_prefs")

internal val Context.viewOptionsDataStore by preferencesDataStore(name = "view_options_prefs")
