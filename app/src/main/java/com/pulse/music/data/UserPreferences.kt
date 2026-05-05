package com.pulse.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val updateNotificationsEnabledKey = booleanPreferencesKey("update_notifications_enabled")
    private val updateNotificationsPromptedKey = booleanPreferencesKey("update_notifications_prompted")
    private val updateNotificationsLastCheckKey = longPreferencesKey("update_notifications_last_check")
    private val updateNotificationsLastNotifiedBuildKey = intPreferencesKey("update_notifications_last_notified_build")
    private val dismissedUpdatePromptBuildKey = intPreferencesKey("dismissed_update_prompt_build")
    private val lastLibraryScanAtKey = longPreferencesKey("last_library_scan_at")
    private val lastMetadataRefreshAtKey = longPreferencesKey("last_metadata_refresh_at")
    private val recentLibrarySearchesKey = stringPreferencesKey("recent_library_searches")

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

    val updateNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[updateNotificationsEnabledKey] ?: true
    }

    val updateNotificationsPrompted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[updateNotificationsPromptedKey] ?: false
    }

    val updateNotificationsLastCheck: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[updateNotificationsLastCheckKey] ?: 0L
    }

    val updateNotificationsLastNotifiedBuild: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[updateNotificationsLastNotifiedBuildKey] ?: 0
    }

    val dismissedUpdatePromptBuild: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[dismissedUpdatePromptBuildKey] ?: 0
    }

    val lastLibraryScanAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[lastLibraryScanAtKey] ?: 0L
    }

    val lastMetadataRefreshAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[lastMetadataRefreshAtKey] ?: 0L
    }

    val recentLibrarySearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[recentLibrarySearchesKey]
            .orEmpty()
            .split(RECENT_SEARCH_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(MAX_RECENT_LIBRARY_SEARCHES)
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

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[updateNotificationsEnabledKey] = enabled }
    }

    suspend fun setUpdateNotificationsPrompted(prompted: Boolean) {
        context.dataStore.edit { it[updateNotificationsPromptedKey] = prompted }
    }

    suspend fun setUpdateNotificationsLastCheck(timestampMs: Long) {
        context.dataStore.edit { it[updateNotificationsLastCheckKey] = timestampMs }
    }

    suspend fun setUpdateNotificationsLastNotifiedBuild(buildNumber: Int) {
        context.dataStore.edit { it[updateNotificationsLastNotifiedBuildKey] = buildNumber }
    }

    suspend fun setDismissedUpdatePromptBuild(buildNumber: Int) {
        context.dataStore.edit { it[dismissedUpdatePromptBuildKey] = buildNumber }
    }

    suspend fun setLastLibraryScanAt(timestampMs: Long) {
        context.dataStore.edit { it[lastLibraryScanAtKey] = timestampMs }
    }

    suspend fun setLastMetadataRefreshAt(timestampMs: Long) {
        context.dataStore.edit { it[lastMetadataRefreshAtKey] = timestampMs }
    }

    suspend fun addRecentLibrarySearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[recentLibrarySearchesKey]
                .orEmpty()
                .split(RECENT_SEARCH_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotBlank)
            val updated = listOf(normalized) + existing.filterNot { it.equals(normalized, ignoreCase = true) }
            prefs[recentLibrarySearchesKey] = updated.take(MAX_RECENT_LIBRARY_SEARCHES).joinToString(RECENT_SEARCH_SEPARATOR)
        }
    }
}

private const val RECENT_SEARCH_SEPARATOR = "\u001F"
private const val MAX_RECENT_LIBRARY_SEARCHES = 5

data class PendingUpdateInfo(
    val buildNumber: Int,
    val versionName: String,
    val releaseNotes: String,
)
