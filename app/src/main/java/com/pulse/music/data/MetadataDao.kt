package com.pulse.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SongMetadata)

    @Query("SELECT * FROM song_metadata WHERE songId = :songId LIMIT 1")
    suspend fun get(songId: Long): SongMetadata?

    @Query("SELECT * FROM song_metadata WHERE songId = :songId LIMIT 1")
    fun observe(songId: Long): Flow<SongMetadata?>

    @Query("DELETE FROM song_metadata WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("UPDATE OR REPLACE song_metadata SET songId = :newSongId WHERE songId = :oldSongId")
    suspend fun moveSongId(oldSongId: Long, newSongId: Long)
}

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lyrics: SongLyrics)

    @Query("SELECT * FROM song_lyrics WHERE songId = :songId LIMIT 1")
    suspend fun get(songId: Long): SongLyrics?

    @Query("DELETE FROM song_lyrics WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("UPDATE OR REPLACE song_lyrics SET songId = :newSongId WHERE songId = :oldSongId")
    suspend fun moveSongId(oldSongId: Long, newSongId: Long)
}
