package com.rigtrack.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore("user_preferences")

/** UI preferences are independent of tracking profiles and raw recordings. */
class UserPreferences(context: Context) {
    private val store = context.applicationContext.userDataStore
    val state = store.data.map { values ->
        Preferences(
            language = values[stringPreferencesKey("language")] ?: "tr",
            onboardingDone = values[booleanPreferencesKey("onboarding_done")] ?: false,
            keepAwake = values[booleanPreferencesKey("keep_awake")] ?: true,
            sound = values[booleanPreferencesKey("sound")] ?: true,
            haptic = values[booleanPreferencesKey("haptic")] ?: true,
            developer = values[booleanPreferencesKey("developer")] ?: false,
            markers = values[booleanPreferencesKey("markers")] ?: true,
            axes = values[booleanPreferencesKey("axes")] ?: false,
            stats = values[booleanPreferencesKey("stats")] ?: true,
            includeReferenceVideo = values[booleanPreferencesKey("include_reference_video")] ?: false,
        )
    }
    suspend fun language(value: String) {
        require(value in setOf("tr", "en"))
        store.edit { it[stringPreferencesKey("language")] = value }
    }
    suspend fun flag(key: String, value: Boolean) {
        require(key in setOf("onboarding_done", "keep_awake", "sound", "haptic", "developer", "markers", "axes", "stats", "include_reference_video"))
        store.edit { it[booleanPreferencesKey(key)] = value }
    }
}

data class Preferences(
    val language: String = "tr",
    val onboardingDone: Boolean = false,
    val keepAwake: Boolean = true,
    val sound: Boolean = true,
    val haptic: Boolean = true,
    val developer: Boolean = false,
    val markers: Boolean = true,
    val axes: Boolean = false,
    val stats: Boolean = true,
    val includeReferenceVideo: Boolean = false,
)
