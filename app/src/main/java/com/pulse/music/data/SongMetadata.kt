package com.pulse.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached song metadata from Genius.
 *
 * Keyed by Song.id (MediaStore _ID) so each scanned audio file has exactly
 * one row. Once fetched we never hit the network for this song again — even
 * if fields are blank (null means "couldn't resolve a match").
 *
 * [fetchedAt] lets us invalidate stale entries in the future if we want to
 * re-resolve after, say, 30 days. Not used yet but cheap to store.
 */
@Entity(tableName = "song_metadata")
data class SongMetadata(
    @PrimaryKey val songId: Long,
    val geniusId: Long? = null,           // Genius's song ID (null = no match found)
    val geniusUrl: String? = null,        // canonical genius.com URL (used for Share)
    val resolvedTitle: String? = null,    // title as Genius knows it
    val resolvedArtist: String? = null,
    val resolvedAlbum: String? = null,
    val artworkUrl: String? = null,       // remote URL — Coil fetches + caches it
    val releaseDate: String? = null,      // e.g. "2017-01-06"
    val overrideTitle: String? = null,    // user-corrected title, if any
    val overrideArtist: String? = null,
    val overrideAlbum: String? = null,
    val overrideAppliedAt: Long = 0,
    val identityResolvedAt: Long = 0,
    val artworkAttemptedAt: Long = 0,
    val artworkResolvedAt: Long = 0,
    val lyricsAttemptedAt: Long = 0,
    val lyricsResolvedAt: Long = 0,
    val fetchedAt: Long = System.currentTimeMillis(),
)
