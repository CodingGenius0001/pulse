package com.pulse.music.data

import com.pulse.music.network.GeniusApi

/**
 * Mediates between the UI and Genius, backed by Room.
 *
 * The rule is simple: once we've looked up a song (even a miss), we never
 * hit Genius again for that song. The first call pays the network cost; all
 * subsequent calls read from disk. This is why every result — match or miss —
 * is written to Room.
 *
 * Network calls happen lazily: [resolve] is suspending and runs on IO. The
 * UI fires it after scan completes (for fresh songs) or opportunistically
 * (e.g. when Now Playing opens) and reads cached data via [getCached].
 */
class MetadataRepository(
    private val metadataDao: MetadataDao,
) {

    /**
     * Get the cached metadata for a song if we have any. Doesn't touch the
     * network — safe to call on the main thread.
     *
     * Returns null if we've never resolved this song OR if resolution hit an
     * error (distinct from "resolved, no match found"). Use [resolve] to
     * actually populate the cache.
     */
    suspend fun getCached(songId: Long): SongMetadata? = metadataDao.get(songId)

    /**
     * Resolve metadata for a song. Checks cache first; on miss, hits Genius
     * /search, then /songs/{id} for richer details, then writes the result
     * back to the cache.
     *
     * Even on a miss (Genius returns no hits) we write an empty [SongMetadata]
     * row so we don't retry. That's why null-checks on the returned fields
     * are normal — a non-null return with all-null fields means "we checked
     * and Genius doesn't know this song".
     */
    suspend fun resolve(song: Song): SongMetadata {
        // Cache hit — short-circuit before any network call
        metadataDao.get(song.id)?.let { return it }

        val hit = GeniusApi.search(song.title, song.artist)
        if (hit == null) {
            // Miss OR network error — cache a "no match" row so we don't retry
            val empty = SongMetadata(songId = song.id)
            metadataDao.upsert(empty)
            return empty
        }

        // We have a top hit — try to enrich with /songs/{id} for album + release date
        val details = GeniusApi.getSong(hit.id)

        val record = SongMetadata(
            songId = song.id,
            geniusId = hit.id,
            geniusUrl = details?.url ?: hit.url,
            resolvedTitle = details?.title ?: hit.title,
            resolvedArtist = details?.artist ?: hit.artist,
            resolvedAlbum = details?.album,
            artworkUrl = details?.artworkUrl ?: hit.artworkUrl,
            releaseDate = details?.releaseDate,
        )
        metadataDao.upsert(record)
        return record
    }

    /** Force a re-resolve by clearing the cache and calling resolve again. */
    suspend fun refresh(song: Song): SongMetadata {
        metadataDao.delete(song.id)
        return resolve(song)
    }
}
