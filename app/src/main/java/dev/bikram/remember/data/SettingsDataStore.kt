package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.settingsDataStore by preferencesDataStore(name = "theme_prefs")
