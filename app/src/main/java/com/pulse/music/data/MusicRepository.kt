package com.pulse.music.data

import com.pulse.music.scanner.MusicScanner
import kotlinx.coroutines.flow.Flow

/**
 * Repository fronts the DAO and orchestrates scanning.
 *
 * Anything that needs music data goes through here — UI layer never talks
 * directly to Room or MediaStore.
 */
class MusicRepository(
    private val dao: MusicDao,
    private val scanner: MusicScanner,
) {
    // -------- Scanning --------

    /**
     * Triggers a full rescan. Reads fresh data from MediaStore, merges it
     * into Room (preserving liked/playCount state), and prunes deleted files.
     */
    suspend fun rescan(folderFilter: String? = "/Pulse/"): Int {
        val scanned = scanner.scanAll(folderFilter)
        if (scanned.isEmpty()) return 0

        // Preserve user-state (likes, play counts) from existing rows
        val existing = scanned.map { it.id }.let { ids ->
            ids.mapNotNull { dao.getSong(it) }
        }.associateBy { it.id }

        val merged = scanned.map { s ->
            val prev = existing[s.id]
            s.copy(
                playCount = prev?.playCount ?: 0,
                lastPlayedAt = prev?.lastPlayedAt ?: 0L,
                liked = prev?.liked ?: false,
            )
        }

        dao.upsertSongs(merged)
        dao.deleteSongsNotIn(merged.map { it.id })

        // Ensure the Liked system playlist exists
        ensureSystemPlaylist(SYSTEM_LIKED, "Liked songs")

        return merged.size
    }

    private suspend fun ensureSystemPlaylist(type: String, name: String) {
        if (dao.getSystemPlaylist(type) == null) {
            dao.insertPlaylist(Playlist(name = name, systemType = type))
        }
    }

    // -------- Songs --------

    fun observeAllSongs(): Flow<List<Song>> = dao.observeAllSongs()
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<Song>> = dao.observeRecentlyAdded(limit)
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<Song>> = dao.observeRecentlyPlayed(limit)
    fun observeLikedSongs(): Flow<List<Song>> = dao.observeLikedSongs()
    fun observeTopPlayed(limit: Int = 12): Flow<List<Song>> = dao.observeTopPlayed(limit)

    suspend fun getSong(id: Long): Song? = dao.getSong(id)

    suspend fun toggleLike(song: Song) {
        dao.setLiked(song.id, !song.liked)
        // Sync with the Liked songs system playlist
        val liked = dao.getSystemPlaylist(SYSTEM_LIKED)
        if (liked != null) {
            if (!song.liked) {
                dao.addSongToPlaylist(PlaylistSongCrossRef(liked.id, song.id))
            } else {
                dao.removeSongFromPlaylist(liked.id, song.id)
            }
        }
    }

    suspend fun markPlayed(songId: Long) {
        dao.markPlayed(songId)
    }

    // -------- Playlists --------

    fun observePlaylists(): Flow<List<Playlist>> = dao.observePlaylists()

    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Song>> =
        dao.observeSongsInPlaylist(playlistId)

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(Playlist(name = name))

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun getThumbnailSongs(playlistId: Long): List<Song> =
        dao.getTopFourSongsForPlaylist(playlistId)

    companion object {
        const val SYSTEM_LIKED = "liked"
    }
}
