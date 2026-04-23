package com.pulse.music.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A user-created playlist. Songs are joined via PlaylistSongCrossRef below.
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Marks system-managed playlists (like "Liked songs") so the UI can render
     * them differently and prevent deletion.
     */
    val systemType: String? = null,
)

/** Many-to-many join between songs and playlists. */
@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)
