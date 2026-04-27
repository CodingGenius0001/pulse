package com.pulse.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulse.music.PulseApplication
import com.pulse.music.data.LyricsRepository
import com.pulse.music.data.MetadataRepository
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
import kotlinx.coroutines.flow.first
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
    private val metadataRepository: MetadataRepository,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    // ---------- Scan state ----------

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // ---------- Folder state ----------

    /** Describes where Pulse expects music to live and whether that folder exists. */
    data class FolderState(
        val displayPath: String,   // short path for UI, e.g. "Music/Pulse"
        val fullPath: String,      // real absolute path (for debugging)
        val exists: Boolean,
    )

    private val _folderState = MutableStateFlow(
        FolderState(
            displayPath = repository.pulseFolderDisplayPath(),
            fullPath = repository.pulseFolderPath(),
            exists = repository.pulseFolderExists(),
        )
    )
    val folderState: StateFlow<FolderState> = _folderState.asStateFlow()

    private val _metadataRefreshState = MutableStateFlow<MetadataRefreshState>(MetadataRefreshState.Idle)
    val metadataRefreshState: StateFlow<MetadataRefreshState> = _metadataRefreshState.asStateFlow()

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
                displayPath = repository.pulseFolderDisplayPath(),
                fullPath = repository.pulseFolderPath(),
                exists = repository.pulseFolderExists(),
            )

            // Background-fetch Genius metadata for any song we haven't resolved
            // yet. Throttled and fire-and-forget — the UI reacts when rows land
            // in Room. Short-circuits cleanly if the Genius token isn't set.
            enrichMetadataAsync()
        }
    }

    /**
     * Walks the current library, hitting Genius only for songs we've never
     * resolved before. Runs serially on the IO dispatcher so we don't spray
     * the API with parallel requests — Genius is generous but not infinite.
     * Silently no-ops on network errors or missing tokens.
     */
    private suspend fun enrichMetadataAsync() {
        val library = repository.observeAllSongs().first()
        for (song in library) {
            metadataRepository.resolve(song)
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
                displayPath = repository.pulseFolderDisplayPath(),
                fullPath = repository.pulseFolderPath(),
                exists = repository.pulseFolderExists(),
            )
            if (created) rescan()
        }
    }

    fun refreshAllMetadata() {
        if (_metadataRefreshState.value is MetadataRefreshState.Running) return

        viewModelScope.launch(Dispatchers.IO) {
            val library = repository.observeAllSongs().first()
            val total = library.size
            if (total == 0) {
                _metadataRefreshState.value = MetadataRefreshState.Completed(
                    refreshed = 0,
                    total = 0,
                    finishedAt = System.currentTimeMillis(),
                )
                return@launch
            }

            _metadataRefreshState.value = MetadataRefreshState.Running(
                processed = 0,
                total = total,
                currentTitle = null,
            )

            try {
                library.forEachIndexed { index, song ->
                    _metadataRefreshState.value = MetadataRefreshState.Running(
                        processed = index,
                        total = total,
                        currentTitle = song.title,
                    )
                    metadataRepository.refresh(song)
                    lyricsRepository.refresh(song)
                }
                _metadataRefreshState.value = MetadataRefreshState.Completed(
                    refreshed = total,
                    total = total,
                    finishedAt = System.currentTimeMillis(),
                )
            } catch (t: Throwable) {
                _metadataRefreshState.value = MetadataRefreshState.Error(
                    message = t.message ?: "Couldn't refresh metadata right now.",
                )
            }
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Song>> =
        repository.observeSongsInPlaylist(playlistId)

    /** Creates a new user playlist. Returns the new playlist's ID. */
    suspend fun createPlaylist(name: String): Long = repository.createPlaylist(name)

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        repository.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: Long) =
        repository.deletePlaylist(playlistId)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) =
        repository.addSongToPlaylist(playlistId, songId)

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        repository.removeSongFromPlaylist(playlistId, songId)

    suspend fun playlistContainsSong(playlistId: Long, songId: Long): Boolean =
        repository.playlistContainsSong(playlistId, songId)

    /**
     * Fetches the top 4 songs for a playlist — used to build 2x2 mosaic
     * thumbnails. Called from list items on-demand so playlists with many
     * thumbnails don't block the UI.
     */
    suspend fun getPlaylistThumbnailArt(playlistId: Long): List<Song> =
        repository.getThumbnailSongs(playlistId)

    suspend fun getSongsInPlaylist(playlistId: Long): List<Song> =
        repository.getSongsInPlaylist(playlistId)

    suspend fun getPlaylistSongCount(playlistId: Long): Int =
        repository.getPlaylistSongCount(playlistId)

    private fun <T> Flow<T>.stateInEager(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data class Completed(val count: Int, val finishedAt: Long) : ScanState
    }

    sealed interface MetadataRefreshState {
        data object Idle : MetadataRefreshState
        data class Running(
            val processed: Int,
            val total: Int,
            val currentTitle: String?,
        ) : MetadataRefreshState
        data class Completed(
            val refreshed: Int,
            val total: Int,
            val finishedAt: Long,
        ) : MetadataRefreshState
        data class Error(val message: String) : MetadataRefreshState
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = PulseApplication.get()
                return LibraryViewModel(
                    repository = app.repository,
                    prefs = app.userPreferences,
                    metadataRepository = app.metadataRepository,
                    lyricsRepository = app.lyricsRepository,
                ) as T
            }
        }
    }
}
