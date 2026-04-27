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
import com.pulse.music.data.SongMetadata
import com.pulse.music.data.UserPreferences
import com.pulse.music.lyrics.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 */
class LibraryViewModel(
    private val repository: MusicRepository,
    private val prefs: UserPreferences,
    private val metadataRepository: MetadataRepository,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    data class FolderState(
        val displayPath: String,
        val fullPath: String,
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
    private var enrichmentJob: Job? = null

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

    val userName: StateFlow<String> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "You")

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning
            val count = repository.rescan()
            _scanState.value = ScanState.Completed(count, System.currentTimeMillis())
            _folderState.value = FolderState(
                displayPath = repository.pulseFolderDisplayPath(),
                fullPath = repository.pulseFolderPath(),
                exists = repository.pulseFolderExists(),
            )
            launchLibraryEnrichment(force = false, userInitiated = false)
        }
    }

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
        launchLibraryEnrichment(force = true, userInitiated = true)
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Song>> =
        repository.observeSongsInPlaylist(playlistId)

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

    suspend fun getPlaylistThumbnailArt(playlistId: Long): List<Song> =
        repository.getThumbnailSongs(playlistId)

    suspend fun getSongsInPlaylist(playlistId: Long): List<Song> =
        repository.getSongsInPlaylist(playlistId)

    suspend fun getPlaylistSongCount(playlistId: Long): Int =
        repository.getPlaylistSongCount(playlistId)

    private fun launchLibraryEnrichment(force: Boolean, userInitiated: Boolean) {
        if (enrichmentJob?.isActive == true) {
            if (!userInitiated) return
            enrichmentJob?.cancel()
        }

        enrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            val library = repository.observeAllSongs().first()
            val candidates = if (force) {
                library
            } else {
                library.filter { song ->
                    metadataRepository.needsBackgroundEnrichment(song) ||
                        lyricsRepository.needsBackgroundFetch(song, metadataRepository.getCached(song.id))
                }
            }
            val total = candidates.size
            if (total == 0) {
                _metadataRefreshState.value = MetadataRefreshState.Completed(
                    refreshed = 0,
                    total = 0,
                    artistUpdates = 0,
                    artworkUpdates = 0,
                    lyricsUpdates = 0,
                    finishedAt = System.currentTimeMillis(),
                    userInitiated = userInitiated,
                )
                return@launch
            }

            var processed = 0
            var artistUpdates = 0
            var artworkUpdates = 0
            var lyricsUpdates = 0
            _metadataRefreshState.value = MetadataRefreshState.Running(
                processed = 0,
                total = total,
                currentTitle = null,
                artistUpdates = 0,
                artworkUpdates = 0,
                lyricsUpdates = 0,
                userInitiated = userInitiated,
            )

            try {
                candidates.chunked(ENRICHMENT_CONCURRENCY).forEach { chunk ->
                    val outcomes = chunk.map { song ->
                        async { enrichSong(song, force) }
                    }.awaitAll()

                    outcomes.forEach { outcome ->
                        processed += 1
                        if (outcome.artistUpdated) artistUpdates += 1
                        if (outcome.artworkUpdated) artworkUpdates += 1
                        if (outcome.lyricsUpdated) lyricsUpdates += 1
                        _metadataRefreshState.value = MetadataRefreshState.Running(
                            processed = processed,
                            total = total,
                            currentTitle = outcome.songTitle,
                            artistUpdates = artistUpdates,
                            artworkUpdates = artworkUpdates,
                            lyricsUpdates = lyricsUpdates,
                            userInitiated = userInitiated,
                        )
                    }
                }

                _metadataRefreshState.value = MetadataRefreshState.Completed(
                    refreshed = processed,
                    total = total,
                    artistUpdates = artistUpdates,
                    artworkUpdates = artworkUpdates,
                    lyricsUpdates = lyricsUpdates,
                    finishedAt = System.currentTimeMillis(),
                    userInitiated = userInitiated,
                )
            } catch (t: Throwable) {
                _metadataRefreshState.value = MetadataRefreshState.Error(
                    message = t.message ?: "Couldn't refresh metadata right now.",
                )
            }
        }
    }

    private suspend fun enrichSong(song: Song, force: Boolean): EnrichmentOutcome {
        val metadataBefore = metadataRepository.getCached(song.id)
        val lyricsBeforeFound = (metadataBefore?.lyricsResolvedAt ?: 0L) > 0L
        val metadataAfter = if (force) {
            metadataRepository.refresh(song)
        } else {
            metadataRepository.resolve(song)
        }

        val lyricsResult = if (
            force ||
            lyricsRepository.needsBackgroundFetch(song, metadataAfter)
        ) {
            if (force) lyricsRepository.refresh(song) else lyricsRepository.lyricsFor(song)
        } else {
            null
        }

        val lyricsAfterFound = when (lyricsResult) {
            is LyricsResult.Found -> true
            else -> (metadataRepository.getCached(song.id)?.lyricsResolvedAt ?: 0L) > 0L
        }

        return EnrichmentOutcome(
            songTitle = song.title,
            artistUpdated = metadataBefore.artistMissingForCounting() &&
                metadataAfter.resolvedArtist.isKnownArtistForCounting(),
            artworkUpdated = metadataBefore?.artworkUrl.isNullOrBlank() &&
                !metadataAfter.artworkUrl.isNullOrBlank(),
            lyricsUpdated = !lyricsBeforeFound && lyricsAfterFound,
        )
    }

    private data class EnrichmentOutcome(
        val songTitle: String,
        val artistUpdated: Boolean,
        val artworkUpdated: Boolean,
        val lyricsUpdated: Boolean,
    )

    private fun SongMetadata?.artistMissingForCounting(): Boolean =
        this == null || resolvedArtist.isNullOrBlank() || !resolvedArtist.isKnownArtistForCounting()

    private fun String?.isKnownArtistForCounting(): Boolean =
        !isNullOrBlank() &&
            !equals("Unknown artist", ignoreCase = true) &&
            !equals("<unknown>", ignoreCase = true)

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
            val artistUpdates: Int,
            val artworkUpdates: Int,
            val lyricsUpdates: Int,
            val userInitiated: Boolean,
        ) : MetadataRefreshState
        data class Completed(
            val refreshed: Int,
            val total: Int,
            val artistUpdates: Int,
            val artworkUpdates: Int,
            val lyricsUpdates: Int,
            val finishedAt: Long,
            val userInitiated: Boolean,
        ) : MetadataRefreshState
        data class Error(val message: String) : MetadataRefreshState
    }

    companion object {
        private const val ENRICHMENT_CONCURRENCY = 4

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
