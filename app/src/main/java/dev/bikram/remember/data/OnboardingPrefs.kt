package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

data class OnboardingState(
    val hasSeenIntro: Boolean = false,
)

class OnboardingPrefs(
    private val context: Context,
) {
    private object Keys {
        val HAS_SEEN_INTRO = booleanPreferencesKey("has_seen_intro")
    }

    val state: Flow<OnboardingState> =
        context.onboardingDataStore.data.map { preferences ->
            OnboardingState(
                hasSeenIntro = preferences[Keys.HAS_SEEN_INTRO] ?: false,
            )
        }

    suspend fun markIntroSeen() {
        context.onboardingDataStore.edit { preferences ->
            preferences[Keys.HAS_SEEN_INTRO] = true
        }
    }
}
