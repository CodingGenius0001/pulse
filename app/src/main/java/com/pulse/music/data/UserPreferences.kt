package com.pulse.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val pendingUpdateBuildKey = intPreferencesKey("pending_update_build")
    private val pendingUpdateVersionKey = stringPreferencesKey("pending_update_version")
    private val pendingUpdateNotesKey = stringPreferencesKey("pending_update_notes")

    val theme: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        ThemePreference.fromValue(prefs[themeKey])
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[userNameKey] ?: "You"
    }

    val pendingUpdateInfo: Flow<PendingUpdateInfo?> = context.dataStore.data.map { prefs ->
        val build = prefs[pendingUpdateBuildKey] ?: return@map null
        PendingUpdateInfo(
            buildNumber = build,
            versionName = prefs[pendingUpdateVersionKey].orEmpty(),
            releaseNotes = prefs[pendingUpdateNotesKey].orEmpty(),
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[themeKey] = theme.value }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name }
    }

    suspend fun setPendingUpdateInfo(info: PendingUpdateInfo) {
        context.dataStore.edit {
            it[pendingUpdateBuildKey] = info.buildNumber
            it[pendingUpdateVersionKey] = info.versionName
            it[pendingUpdateNotesKey] = info.releaseNotes
        }
    }

    suspend fun clearPendingUpdateInfo() {
        context.dataStore.edit {
            it.remove(pendingUpdateBuildKey)
            it.remove(pendingUpdateVersionKey)
            it.remove(pendingUpdateNotesKey)
        }
    }
}

data class PendingUpdateInfo(
    val buildNumber: Int,
    val versionName: String,
    val releaseNotes: String,
)
