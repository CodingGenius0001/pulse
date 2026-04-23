package com.pulse.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed preferences. Used for theme mode and anything else
 * that needs to persist across app launches outside of Room.
 */
private val Context.dataStore by preferencesDataStore(name = "pulse_prefs")

enum class ThemePreference(val value: String) {
    Light("light"),
    Dark("dark"),
    Auto("auto");

    companion object {
        fun fromValue(v: String?): ThemePreference = entries.firstOrNull { it.value == v } ?: Dark
    }
}

class UserPreferences(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val userNameKey = stringPreferencesKey("user_name")

    val theme: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        ThemePreference.fromValue(prefs[themeKey])
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[userNameKey] ?: "You"
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[themeKey] = theme.value }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name }
    }
}
