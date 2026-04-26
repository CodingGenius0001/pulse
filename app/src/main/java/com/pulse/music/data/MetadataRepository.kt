package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Mediates between the UI and Genius, backed by Room.
 *
 * Real no-match responses are cached so the app does not ask Genius for the
 * same missing song forever. Transient failures, missing tokens, and API
 * errors are not cached; a later scan can try again.
 */
class MetadataRepository(
    private val metadataDao: MetadataDao,
) {

    suspend fun getCached(songId: Long): SongMetadata? = metadataDao.get(songId)

    fun observe(songId: Long): Flow<SongMetadata?> = metadataDao.observe(songId)

    /**
     * Resolve metadata for a song. Checks cache first; on miss, hits Genius
     * /search, then /songs/{id} for richer details, then writes the result
     * back to the cache when the lookup produced a stable answer.
     */
    suspend fun resolve(song: Song): SongMetadata {
        metadataDao.get(song.id)?.let { return it }

        val hit = when (val outcome = GeniusApi.searchForMetadata(song.title, song.artist)) {
            is GeniusSearchOutcome.Found -> outcome.hit
            GeniusSearchOutcome.NoMatch -> {
                val empty = SongMetadata(songId = song.id)
                metadataDao.upsert(empty)
                return empty
            }
            GeniusSearchOutcome.Unavailable -> {
                return SongMetadata(songId = song.id)
            }
        }

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
