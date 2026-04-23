package com.pulse.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulse.music.PulseApplication
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import com.pulse.music.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for the library-facing screens (For you, Library, Settings, Search).
 *
 * Owns:
 *  - library data flows (all songs, recently played/added, liked, playlists)
 *  - scan state (Idle / Scanning / Completed)
 *  - folder state (path, whether it exists on disk)
 *  - the user's display name (used by avatars + Settings profile card)
 *
 * Kicks off an initial scan in init. If the Pulse folder doesn't exist yet,
 * the scan returns 0 songs and the UI surfaces an empty state.
 */
class LibraryViewModel(
    private val repository: MusicRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    // ---------- Scan state ----------

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // ---------- Folder state ----------

    /** Describes where Pulse expects music to live and whether that folder exists. */
    data class FolderState(val path: String, val exists: Boolean)

    private val _folderState = MutableStateFlow(
        FolderState(repository.pulseFolderPath(), repository.pulseFolderExists())
    )
    val folderState: StateFlow<FolderState> = _folderState.asStateFlow()

    // ---------- Library data flows ----------

    val allSongs: StateFlow<List<Song>> = repository.observeAllSongs()
        .stateInEager(emptyList())

    val recentlyAdded: StateFlow<List<Song>> = repository.observeRecentlyAdded(20)
        .stateInEager(emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = repository.observeRecentlyPlayed(20)
        .stateInEager(emptyList())

    val topPlayed: StateFlow<List<Song>> = repository.observeTopPlayed(12)
        .stateInEager(emptyList())

    val likedSongs: StateFlow<List<Song>> = repository.observeLikedSongs()
        .stateInEager(emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateInEager(emptyList())

    // ---------- User preferences ----------

    val userName: StateFlow<String> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "You")

    init {
        rescan()
    }

    // ---------- Commands ----------

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning
            val count = repository.rescan()
            _scanState.value = ScanState.Completed(count, System.currentTimeMillis())
            // Refresh folder state in case the folder was created between scans
            _folderState.value = FolderState(
                path = repository.pulseFolderPath(),
                exists = repository.pulseFolderExists(),
            )
        }
    }

    /**
     * Creates the Pulse folder at /Music/Pulse/. Returns true on success.
     * After creation, re-checks folder state and triggers a rescan.
     */
    fun createPulseFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val created = repository.createPulseFolder()
            _folderState.value = FolderState(
                path = repository.pulseFolderPath(),
                exists = repository.pulseFolderExists(),
            )
            if (created) rescan()
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    /** Creates a new user playlist. Returns the new playlist's ID. */
    suspend fun createPlaylist(name: String): Long = repository.createPlaylist(name)

    /**
     * Fetches the top 4 songs for a playlist — used to build 2x2 mosaic
     * thumbnails. Called from list items on-demand so playlists with many
     * thumbnails don't block the UI.
     */
    suspend fun getPlaylistThumbnailArt(playlistId: Long): List<Song> =
        repository.getThumbnailSongs(playlistId)

    private fun <T> Flow<T>.stateInEager(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data class Completed(val count: Int, val finishedAt: Long) : ScanState
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = PulseApplication.get()
                return LibraryViewModel(app.repository, app.userPreferences) as T
            }
        }
    }
}
