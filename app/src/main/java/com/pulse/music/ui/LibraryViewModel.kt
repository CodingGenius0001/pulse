package com.pulse.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulse.music.PulseApplication
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel powering the three library-facing screens (For you, Library, Settings).
 *
 * Kicks off an initial scan on construction and exposes typed Flows for
 * every list the UI needs. Screens collect from these flows via
 * [collectAsStateWithLifecycle].
 */
class LibraryViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

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

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning
            val count = repository.rescan()
            _scanState.value = ScanState.Completed(count, System.currentTimeMillis())
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

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
                return LibraryViewModel(PulseApplication.get().repository) as T
            }
        }
    }
}
