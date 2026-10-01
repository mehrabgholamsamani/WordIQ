package com.wordiq.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore(name = "wordiq_preferences")

data class UserPreferences(
    val dailyMinutes: Int = 5,
    val explanationLanguage: String = "Both",
    val hapticsEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val autoPronounceEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val dailyMinutes = intPreferencesKey("daily_minutes")
        val explanationLanguage = stringPreferencesKey("explanation_language")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val audioEnabled = booleanPreferencesKey("audio_enabled")
        val autoPronounceEnabled = booleanPreferencesKey("auto_pronounce_enabled")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    val preferences: Flow<UserPreferences> = context.userDataStore.data.map { values ->
        UserPreferences(
            dailyMinutes = values[Keys.dailyMinutes] ?: 5,
            explanationLanguage = values[Keys.explanationLanguage] ?: "Both",
            hapticsEnabled = values[Keys.hapticsEnabled] ?: true,
            audioEnabled = values[Keys.audioEnabled] ?: true,
            autoPronounceEnabled = values[Keys.autoPronounceEnabled] ?: true,
            onboardingCompleted = values[Keys.onboardingCompleted] ?: false,
        )
    }

    suspend fun setDailyMinutes(minutes: Int) {
        context.userDataStore.edit { it[Keys.dailyMinutes] = minutes }
    }

    suspend fun setExplanationLanguage(language: String) {
        context.userDataStore.edit { it[Keys.explanationLanguage] = language }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[Keys.hapticsEnabled] = enabled }
    }

    suspend fun setAudioEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[Keys.audioEnabled] = enabled }
    }

    suspend fun setAutoPronounceEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[Keys.autoPronounceEnabled] = enabled }
    }

    suspend fun completeOnboarding(language: String, minutes: Int) {
        context.userDataStore.edit {
            it[Keys.explanationLanguage] = language
            it[Keys.dailyMinutes] = minutes
            it[Keys.onboardingCompleted] = true
        }
    }
}
