package com.pulse.music.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A song. Backed by MediaStore but cached in Room so the library survives
 * offline / no-permission moments and queries stay fast.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long, // MediaStore _ID
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dataPath: String, // absolute file path
    val dateAdded: Long, // seconds since epoch (MediaStore convention)
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0, // ms since epoch; 0 = never
    val liked: Boolean = false,
    val isAvailable: Boolean = true,
    val lastSeenAt: Long = System.currentTimeMillis(),
) {
    /** URI to play via MediaStore. */
    val contentUri: Uri
        get() = "content://media/external/audio/media/$id".toUri()

    /** URI for the cached album art thumbnail. */
    val albumArtUri: Uri
        get() = "content://media/external/audio/albumart/$albumId".toUri()
}
