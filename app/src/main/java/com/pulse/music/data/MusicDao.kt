package com.pulse.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // -------- Songs --------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<Song>)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotIn(ids: List<Long>)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeLikedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSong(id: Long): Song?

    @Query("UPDATE songs SET liked = :liked WHERE id = :id")
    suspend fun setLiked(id: Long, liked: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun markPlayed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    fun observeTopPlayed(limit: Int = 12): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun songCount(): Int

    // -------- Playlists --------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylist(id: Long): Playlist?

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists WHERE systemType = :type LIMIT 1")
    suspend fun getSystemPlaylist(type: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun playlistContainsSong(playlistId: Long, songId: Long): Int

    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref
            ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.addedAt DESC
        """
    )
    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref
            ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.addedAt DESC
        """
    )
    suspend fun getSongsInPlaylist(playlistId: Long): List<Song>

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    /**
     * For building playlist thumbnails: grab album art from the 4 most recent songs.
     */
    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref
            ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.addedAt DESC
        LIMIT 4
        """
    )
    suspend fun getTopFourSongsForPlaylist(playlistId: Long): List<Song>
}
