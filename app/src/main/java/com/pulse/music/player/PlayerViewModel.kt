package com.pulse.music.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.pulse.music.PulseApplication
import com.pulse.music.data.MetadataRepository
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.PlaybackSession
import com.pulse.music.data.Song
import com.pulse.music.data.SongMetadata
import com.pulse.music.data.displayWith
import com.pulse.music.data.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the current playback state for the UI to render.
 *
 * [queue] is always in play order. Shuffle is implemented by reordering the
 * player's playlist (see [PlayerViewModel.toggleShuffle]) rather than with
 * Media3's shuffle mode, so the Queue screen, "Play next" and "Add to queue"
 * all mean exactly what they show.
 */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    /** Wall-clock time the sleep timer pauses playback, or null when no timed sleep is set. */
    val sleepTimerEndsAtMs: Long? = null,
    /** True when playback should pause as soon as the current track ends. */
    val sleepAfterCurrentTrack: Boolean = false,
)

/**
 * Owns the MediaController and exposes playback state + commands to the UI.
 *
 * One instance lives for the whole app (scoped to the activity). It connects
 * to [PlayerService] on start and disconnects in onCleared. The service can
 * outlive this ViewModel (music keeps playing after the activity is gone), so
 * on connect the queue is rebuilt from whatever the service is already
 * playing; on a cold start the last saved session is restored, paused.
 */
class PlayerViewModel(
    application: android.app.Application,
    private val repository: MusicRepository,
    private val metadataRepository: MetadataRepository,
    private val prefs: UserPreferences,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null

    /** Songs in the player's playlist, in play order. Mirrors the controller's media items. */
    private var currentQueue: List<Song> = emptyList()

    /** The pre-shuffle order, so turning shuffle off restores it. Empty when shuffle is off. */
    private var originalQueue: List<Song> = emptyList()
    private var shuffleEnabled = false

    private var likedIds: Set<Long> = emptySet()
    private var metadataById: Map<Long, SongMetadata> = emptyMap()

    /** The last media id counted as a play, so metadata-only item swaps don't inflate play counts. */
    private var lastCountedMediaId: String? = null
    private var lastResolvedSongId: Long? = null

    /** What each song's media item currently shows, to avoid redundant item replacements. */
    private val appliedDisplay = mutableMapOf<Long, List<Any?>>()

    private var sleepTimerJob: Job? = null
    private var positionTickerRunning = false

    init {
        viewModelScope.launch {
            repository.observeLikedIds().collect { ids ->
                likedIds = ids
                applyLikedState()
            }
        }
        viewModelScope.launch {
            metadataRepository.observeAll().collect { rows ->
                metadataById = rows.associateBy { it.songId }
                refreshMediaItemMetadataNearCurrent()
            }
        }
        connectToService(application)
    }

    private fun connectToService(context: Context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlayerService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val c = try {
                    controllerFuture.get()
                } catch (e: Exception) {
                    // Service not ready yet — UI will show empty state
                    return@addListener
                }
                controller = c
                // Shuffle is handled by reordering the playlist, never by the player.
                if (c.shuffleModeEnabled) c.shuffleModeEnabled = false
                attachListener(c)
                viewModelScope.launch { restoreQueue(c) }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Rebuilds [currentQueue] when the service is already playing (the activity
     * was recreated), or restores the last saved session on a cold start.
     */
    private suspend fun restoreQueue(c: MediaController) {
        val saved = runCatching { prefs.playbackSession() }.getOrNull()
        if (c.mediaItemCount > 0) {
            val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
            currentQueue = repository.getSongsInOrder(ids)
            if (saved != null && saved.shuffleEnabled && saved.queueIds == ids) {
                shuffleEnabled = true
                originalQueue = repository.getSongsInOrder(saved.originalQueueIds)
            }
            lastCountedMediaId = c.currentMediaItem?.mediaId
            applyLikedState()
            refreshFromController(c)
            return
        }

        saved ?: return
        val songs = repository.getSongsInOrder(saved.queueIds).filter { it.isAvailable }
        if (songs.isEmpty()) return
        val savedCurrentId = saved.queueIds.getOrNull(saved.index)
        val startIndex = songs.indexOfFirst { it.id == savedCurrentId }.takeIf { it >= 0 } ?: 0
        val startPosition = if (songs.getOrNull(startIndex)?.id == savedCurrentId) saved.positionMs else 0L

        currentQueue = songs.withLikes()
        shuffleEnabled = saved.shuffleEnabled
        originalQueue = if (shuffleEnabled) {
            repository.getSongsInOrder(saved.originalQueueIds).filter { it.isAvailable }.withLikes()
        } else {
            emptyList()
        }
        // Restoring is not a new play.
        lastCountedMediaId = songs[startIndex].id.toString()
        c.setMediaItems(songs.map { it.toMediaItem(metadataById[it.id]) }, startIndex, startPosition)
        c.repeatMode = saved.repeatMode
        c.prepare()
        refreshFromController(c)
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshFromController(player)
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                    persistSession()
                }
                if (player.playbackState == Player.STATE_ENDED && _state.value.sleepAfterCurrentTrack) {
                    _state.value = _state.value.copy(sleepAfterCurrentTrack = false)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFromController(c)
                if (_state.value.sleepAfterCurrentTrack &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                ) {
                    c.pause()
                    _state.value = _state.value.copy(sleepAfterCurrentTrack = false)
                }
                countPlay(mediaItem, reason)
                persistSession()
            }
        })
        startPositionTicker()
    }

    /**
     * Counts a play for recently-played and play counts. Replacing the current
     * item to refresh its artwork/title also reports a transition (reason
     * PLAYLIST_CHANGED), so those only count when the media id actually changed.
     */
    private fun countPlay(mediaItem: MediaItem?, reason: Int) {
        val mediaId = mediaItem?.mediaId ?: return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaId == lastCountedMediaId) return
        lastCountedMediaId = mediaId
        val songId = mediaId.toLongOrNull() ?: return
        viewModelScope.launch { repository.markPlayed(songId) }
    }

    private fun refreshFromController(player: Player) {
        val currentItem = player.currentMediaItem
        val currentId = currentItem?.mediaId?.toLongOrNull()
        val index = player.currentMediaItemIndex
        val song = currentQueue.getOrNull(index)?.takeIf { it.id == currentId }
            ?: currentQueue.firstOrNull { it.id == currentId }
            ?: _state.value.currentSong?.takeIf { it.id == currentId }

        _state.value = _state.value.copy(
            currentSong = song,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffleEnabled = shuffleEnabled,
            repeatMode = player.repeatMode,
            queue = currentQueue,
            currentIndex = index,
        )

        if (song != null && song.id != lastResolvedSongId) {
            lastResolvedSongId = song.id
            // Make sure the notification / lock screen get real metadata even if
            // the user never opens Now Playing.
            viewModelScope.launch { runCatching { metadataRepository.resolve(song) } }
            refreshMediaItemMetadataNearCurrent()
        }
    }

    private fun startPositionTicker() {
        if (positionTickerRunning) return
        positionTickerRunning = true
        viewModelScope.launch {
            var ticks = 0
            while (positionTickerRunning) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.coerceAtLeast(0),
                    )
                    // Save the position every ~5s so a killed process resumes close by.
                    if (++ticks % 10 == 0) persistSession()
                }
                delay(500)
            }
        }
    }

    // -------- Playback commands --------

    /**
     * Play a list of songs starting at [startIndex].
     * Dedupes by song ID to guard against the same track being queued twice.
     * If shuffle is on, the tapped song plays first and the rest are shuffled.
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val deduped = songs.distinctBy { it.id }
        if (deduped.isEmpty()) return
        val start = startIndex.coerceIn(0, deduped.size - 1)
        if (shuffleEnabled) {
            val first = deduped[start]
            startPlayback(
                queue = listOf(first) + deduped.filterIndexed { i, _ -> i != start }.shuffled(),
                startIndex = 0,
                original = deduped,
            )
        } else {
            startPlayback(queue = deduped, startIndex = start, original = emptyList())
        }
    }

    /** Plays [songs] in a random order and turns shuffle on. */
    fun playShuffled(songs: List<Song>) {
        val deduped = songs.distinctBy { it.id }
        if (deduped.isEmpty()) return
        shuffleEnabled = true
        startPlayback(queue = deduped.shuffled(), startIndex = 0, original = deduped)
    }

    private fun startPlayback(queue: List<Song>, startIndex: Int, original: List<Song>) {
        val c = controller ?: return
        currentQueue = queue.withLikes()
        originalQueue = original.withLikes()
        c.setMediaItems(currentQueue.map { it.toMediaItem(metadataById[it.id]) }, startIndex, 0L)
        c.prepare()
        c.play()
        refreshFromController(c)
        persistSession()
    }

    /** Inserts [song] right after the current track. Moves it there if it's already queued. */
    fun playNext(song: Song) {
        val c = controller ?: return
        if (currentQueue.isEmpty() || c.mediaItemCount == 0) {
            playQueue(listOf(song))
            return
        }
        val current = c.currentMediaItemIndex
        if (currentQueue.getOrNull(current)?.id == song.id) return
        val existing = currentQueue.indexOfFirst { it.id == song.id }
        if (existing >= 0) {
            val target = if (existing > current) current + 1 else current
            moveQueueItem(existing, target)
            return
        }
        val withLike = song.withLike()
        c.addMediaItem(current + 1, withLike.toMediaItem(metadataById[song.id]))
        currentQueue = currentQueue.toMutableList().apply { add(current + 1, withLike) }
        if (shuffleEnabled) originalQueue = originalQueue + withLike
        refreshFromController(c)
        persistSession()
    }

    /** Appends [song] to the end of the queue unless it's already there. */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (currentQueue.isEmpty() || c.mediaItemCount == 0) {
            playQueue(listOf(song))
            return
        }
        if (currentQueue.any { it.id == song.id }) return
        val withLike = song.withLike()
        c.addMediaItem(withLike.toMediaItem(metadataById[song.id]))
        currentQueue = currentQueue + withLike
        if (shuffleEnabled) originalQueue = originalQueue + withLike
        refreshFromController(c)
        persistSession()
    }

    fun isQueued(songId: Long): Boolean = currentQueue.any { it.id == songId }

    fun playOrPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        val c = controller ?: return
        // Standard music-app behavior: if we're >3s in, restart the current song;
        // otherwise jump to the previous track.
        if (c.currentPosition > 3000) {
            c.seekTo(0)
        } else {
            c.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** Jump to a specific index in the current queue (for tapping items in Queue view). */
    fun jumpToQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    /**
     * Move an item in the queue from [fromIndex] to [toIndex].
     * Both indices are in terms of the current mediaItem positions.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) return
        if (fromIndex !in currentQueue.indices) return
        c.moveMediaItem(fromIndex, toIndex)

        currentQueue = currentQueue.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex.coerceAtMost(size), item)
        }
        refreshFromController(c)
        persistSession()
    }

    /** Remove a queue item at the given index. Cannot remove the currently playing one. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index == c.currentMediaItemIndex) return
        if (index !in 0 until c.mediaItemCount || index !in currentQueue.indices) return
        val removed = currentQueue[index]
        c.removeMediaItem(index)
        currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
        if (shuffleEnabled) originalQueue = originalQueue.filterNot { it.id == removed.id }
        refreshFromController(c)
        persistSession()
    }

    /**
     * Shuffle reorders the playlist around the current track (which keeps
     * playing untouched). Turning it off puts the original order back.
     */
    fun toggleShuffle() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        val currentSong = currentQueue.getOrNull(current)
        if (currentSong == null || c.mediaItemCount != currentQueue.size) {
            shuffleEnabled = !shuffleEnabled
            originalQueue = if (shuffleEnabled) currentQueue else emptyList()
            refreshFromController(c)
            return
        }

        val (before, after) = if (!shuffleEnabled) {
            originalQueue = currentQueue
            emptyList<Song>() to currentQueue.filterIndexed { i, _ -> i != current }.shuffled()
        } else {
            val restored = originalQueue.ifEmpty { currentQueue }
            val anchor = restored.indexOfFirst { it.id == currentSong.id }
            if (anchor < 0) {
                currentQueue.take(current) to currentQueue.drop(current + 1)
            } else {
                restored.take(anchor) to restored.drop(anchor + 1)
            }
        }
        shuffleEnabled = !shuffleEnabled
        if (!shuffleEnabled) originalQueue = emptyList()

        // Swap everything around the current item so playback isn't interrupted.
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
        if (before.isNotEmpty()) c.addMediaItems(0, before.map { it.toMediaItem(metadataById[it.id]) })
        if (after.isNotEmpty()) c.addMediaItems(after.map { it.toMediaItem(metadataById[it.id]) })
        currentQueue = before + currentSong + after
        refreshFromController(c)
        persistSession()
    }

    fun toggleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        persistSession()
    }

    fun toggleLike() {
        val song = _state.value.currentSong ?: return
        toggleLike(song)
    }

    /** Liked state flows back through [MusicRepository.observeLikedIds] to every screen. */
    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    // -------- Sleep timer --------

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepTimerEndsAtMs = endsAt, sleepAfterCurrentTrack = false)
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.value = _state.value.copy(sleepTimerEndsAtMs = null)
        }
    }

    fun sleepAfterCurrentTrack() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.value = _state.value.copy(sleepTimerEndsAtMs = null, sleepAfterCurrentTrack = true)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.value = _state.value.copy(sleepTimerEndsAtMs = null, sleepAfterCurrentTrack = false)
    }

    override fun onCleared() {
        persistSession()
        positionTickerRunning = false
        controller?.release()
        controller = null
        super.onCleared()
    }

    // -------- Internals --------

    private fun persistSession() {
        val c = controller ?: return
        if (currentQueue.isEmpty()) return
        val session = PlaybackSession(
            queueIds = currentQueue.map { it.id },
            originalQueueIds = if (shuffleEnabled) originalQueue.map { it.id } else emptyList(),
            index = c.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0),
            shuffleEnabled = shuffleEnabled,
            repeatMode = c.repeatMode,
        )
        // App scope, not viewModelScope: this also runs from onCleared.
        PulseApplication.get().appScope.launch {
            runCatching { prefs.setPlaybackSession(session) }
        }
    }

    private fun Song.withLike(): Song = if (liked == (id in likedIds)) this else copy(liked = id in likedIds)

    private fun List<Song>.withLikes(): List<Song> = map { it.withLike() }

    private fun applyLikedState() {
        currentQueue = currentQueue.withLikes()
        originalQueue = originalQueue.withLikes()
        val current = _state.value.currentSong?.withLike()
        _state.value = _state.value.copy(currentSong = current, queue = currentQueue)
    }

    /**
     * Keeps title/artist/artwork fresh for the items the notification and lock
     * screen are about to show. Limited to a window around the current track so
     * a 2,000-song queue doesn't mean 2,000 item replacements per metadata write.
     */
    private fun refreshMediaItemMetadataNearCurrent() {
        val c = controller ?: return
        if (c.mediaItemCount == 0 || c.mediaItemCount != currentQueue.size) return
        val current = c.currentMediaItemIndex.coerceAtLeast(0)
        val window = (current - 1).coerceAtLeast(0)..(current + 3).coerceAtMost(c.mediaItemCount - 1)
        for (index in window) {
            val song = currentQueue[index]
            val replacement = song.toMediaItem(metadataById[song.id])
            val display = replacement.displayKey()
            // The metadata flow fires on every write to any song, so skip
            // songs whose shown title/artist/artwork haven't changed. Each
            // replacement rebuilds the media notification.
            if (appliedDisplay[song.id] == display) continue
            val existing = c.getMediaItemAt(index)
            if (existing.mediaId != replacement.mediaId) continue
            if (existing.displayKey() != display) {
                c.replaceMediaItem(index, replacement)
            }
            appliedDisplay[song.id] = display
        }
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = viewModelFactory

        private val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = PulseApplication.get()
                return PlayerViewModel(app, app.repository, app.metadataRepository, app.userPreferences) as T
            }
        }
    }
}

/** Convert a Song into a Media3 MediaItem so it can be queued. */
private fun Song.toMediaItem(metadata: SongMetadata? = null): MediaItem {
    val display = displayWith(metadata)
    return MediaItem.Builder()
    .setUri(contentUri)
    .setMediaId(id.toString())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(display.title)
            .setArtist(display.artist)
            .setAlbumTitle(display.album)
            .setArtworkUri(metadata?.artworkUrl?.takeIf(String::isNotBlank)?.toUri() ?: albumArtUri)
            .build()
    )
    .build()
}

/** The fields the notification and lock screen show. URIs aren't compared: controller-side items may lack them. */
private fun MediaItem.displayKey(): List<Any?> = listOf(
    mediaMetadata.title?.toString(),
    mediaMetadata.artist?.toString(),
    mediaMetadata.albumTitle?.toString(),
    mediaMetadata.artworkUri,
)
