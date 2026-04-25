package com.pulse.music.player

import android.content.ComponentName
import android.content.Context
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
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the current playback state for the UI to render.
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
)

/**
 * Owns the MediaController and exposes playback state + commands to the UI.
 *
 * One instance lives for the whole app (scoped to the activity). It connects
 * to [PlayerService] on start and disconnects in onCleared.
 */
class PlayerViewModel(
    application: android.app.Application,
    private val repository: MusicRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var currentQueue: List<Song> = emptyList()

    /** Track position updates so the progress bar ticks in real time. */
    private var positionTickerRunning = false

    init {
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
                try {
                    val c = controllerFuture.get()
                    controller = c
                    attachListener(c)
                    refreshFromController(c)
                } catch (e: Exception) {
                    // Service not ready yet — UI will show empty state
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshFromController(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFromController(c)
                // Mark the song as played so recently-played + play counts are accurate
                val currentId = mediaItem?.mediaId?.toLongOrNull() ?: return
                viewModelScope.launch { repository.markPlayed(currentId) }
            }
        })
        startPositionTicker()
    }

    private fun refreshFromController(player: Player) {
        val currentItem = player.currentMediaItem
        val currentId = currentItem?.mediaId?.toLongOrNull()
        val song = currentQueue.firstOrNull { it.id == currentId }
            ?: _state.value.currentSong?.takeIf { it.id == currentId }

        _state.value = _state.value.copy(
            currentSong = song,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            currentIndex = player.currentMediaItemIndex,
        )
    }

    private fun startPositionTicker() {
        if (positionTickerRunning) return
        positionTickerRunning = true
        viewModelScope.launch {
            while (positionTickerRunning) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.coerceAtLeast(0),
                    )
                }
                delay(500)
            }
        }
    }

    // -------- Playback commands --------

    /**
     * Play a list of songs starting at [startIndex].
     * Dedupes by song ID to guard against the same track being queued twice.
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        val deduped = songs.distinctBy { it.id }
        val adjustedStart = startIndex.coerceIn(0, (deduped.size - 1).coerceAtLeast(0))
        currentQueue = deduped
        val items = deduped.map { it.toMediaItem() }
        c.setMediaItems(items, adjustedStart, 0L)
        c.prepare()
        c.play()
        _state.value = _state.value.copy(queue = deduped, currentIndex = adjustedStart)
    }

    fun playOrPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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

    /** Jumps 10 seconds forward, clamped to track duration. */
    fun seekForward10() {
        val c = controller ?: return
        val target = (c.currentPosition + 10_000L).coerceAtMost(
            if (c.duration > 0) c.duration else Long.MAX_VALUE
        )
        c.seekTo(target)
    }

    /** Jumps 10 seconds back, clamped to 0. */
    fun seekBack10() {
        val c = controller ?: return
        val target = (c.currentPosition - 10_000L).coerceAtLeast(0)
        c.seekTo(target)
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
        c.moveMediaItem(fromIndex, toIndex)

        // Also update our local cache so PlaybackState reflects the new order
        val reordered = currentQueue.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex.coerceAtMost(size), item)
        }
        currentQueue = reordered
        _state.value = _state.value.copy(
            queue = reordered,
            currentIndex = c.currentMediaItemIndex,
        )
    }

    /** Remove a queue item at the given index. Cannot remove the currently playing one. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index == c.currentMediaItemIndex) return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
        val reordered = currentQueue.toMutableList().apply { removeAt(index) }
        currentQueue = reordered
        _state.value = _state.value.copy(
            queue = reordered,
            currentIndex = c.currentMediaItemIndex,
        )
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleLike() {
        val song = _state.value.currentSong ?: return
        viewModelScope.launch { repository.toggleLike(song) }
        _state.value = _state.value.copy(
            currentSong = song.copy(liked = !song.liked),
        )
    }

    override fun onCleared() {
        positionTickerRunning = false
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = viewModelFactory

        private val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = PulseApplication.get()
                return PlayerViewModel(app, app.repository) as T
            }
        }
    }
}

/** Convert a Song into a Media3 MediaItem so it can be queued. */
private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(contentUri)
    .setMediaId(id.toString())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()
    )
    .build()
