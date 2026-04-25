package com.pulse.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached lyrics for a song.
 *
 * [syncedLyrics] is an LRC-format string like:
 *   [00:12.34] Line 1
 *   [00:15.67] Line 2
 * If present, the Now Playing dialog can highlight the current line.
 *
 * [plainLyrics] is the fallback when no synced version exists.
 *
 * When both are null and [notFound] is true, we've confirmed LRCLIB had no
 * match — don't re-query.
 */
@Entity(tableName = "song_lyrics")
data class SongLyrics(
    @PrimaryKey val songId: Long,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val instrumental: Boolean = false,
    val notFound: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
)
