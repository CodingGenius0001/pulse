package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import com.pulse.music.network.LrcLibApi
import kotlinx.coroutines.flow.Flow

/**
 * Mediates between the UI and remote metadata providers, backed by Room.
 *
 * Genius is still the only art source, but LRCLIB can recover artist/album
 * text for badly tagged files and give Genius a second, better-informed try.
 */
class MetadataRepository(
    private val metadataDao: MetadataDao,
) {

    suspend fun getCached(songId: Long): SongMetadata? = metadataDao.get(songId)

    fun observe(songId: Long): Flow<SongMetadata?> = metadataDao.observe(songId)

    suspend fun resolve(song: Song): SongMetadata {
        metadataDao.get(song.id)?.let { cached ->
            if (!shouldRetry(song, cached)) return cached
            metadataDao.delete(song.id)
        }

        val primaryOutcome = GeniusApi.searchForMetadata(song.title, song.artist)
        val fallbackInfo = if (primaryOutcome is GeniusSearchOutcome.Found) {
            null
        } else {
            LrcLibApi.findBestTrackInfo(
                trackName = song.title,
                artistName = song.artist,
                albumName = song.album,
                durationSeconds = song.durationMs / 1000,
            )
        }

        val primaryHit = when (primaryOutcome) {
            is GeniusSearchOutcome.Found -> primaryOutcome.hit
            GeniusSearchOutcome.NoMatch -> null
            GeniusSearchOutcome.Unavailable -> null
        }

        val retriedHit = if (primaryHit == null && fallbackInfo.hasUsefulArtistFor(song) && fallbackInfo.matchesSongContext(song)) {
            when (val retried = GeniusApi.searchForMetadata(song.title, fallbackInfo?.artist.orEmpty())) {
                is GeniusSearchOutcome.Found -> retried.hit
                else -> null
            }
        } else {
            null
        }

        val finalHit = retriedHit ?: primaryHit
        if (finalHit != null) {
            val details = GeniusApi.getSong(finalHit.id)
            val record = SongMetadata(
                songId = song.id,
                geniusId = finalHit.id,
                geniusUrl = details?.url ?: finalHit.url,
                resolvedTitle = details?.title ?: fallbackInfo?.title ?: finalHit.title,
                resolvedArtist = details?.artist ?: fallbackInfo?.artist ?: finalHit.artist,
                resolvedAlbum = details?.album ?: fallbackInfo?.album,
                artworkUrl = details?.artworkUrl ?: finalHit.artworkUrl,
                releaseDate = details?.releaseDate,
            )
            metadataDao.upsert(record)
            return record
        }

        if (fallbackInfo != null) {
            val record = SongMetadata(
                songId = song.id,
                resolvedTitle = fallbackInfo.title.takeIf { it.isNotBlank() },
                resolvedArtist = fallbackInfo.artist.takeIf { !it.isNullOrBlank() },
                resolvedAlbum = fallbackInfo.album.takeIf { !it.isNullOrBlank() },
            )
            metadataDao.upsert(record)
            return record
        }

        return when (primaryOutcome) {
            GeniusSearchOutcome.Unavailable -> SongMetadata(songId = song.id)
            else -> {
                val empty = SongMetadata(songId = song.id)
                metadataDao.upsert(empty)
                empty
            }
        }
    }

    suspend fun refresh(song: Song): SongMetadata {
        metadataDao.delete(song.id)
        return resolve(song)
    }

    private fun shouldRetry(song: Song, cached: SongMetadata): Boolean {
        val noResolvedInfo = cached.geniusId == null &&
            cached.geniusUrl.isNullOrBlank() &&
            cached.resolvedTitle.isNullOrBlank() &&
            cached.resolvedArtist.isNullOrBlank() &&
            cached.resolvedAlbum.isNullOrBlank() &&
            cached.artworkUrl.isNullOrBlank()
        if (!noResolvedInfo) return false
        return song.artist.isUnknownArtist()
    }

    private fun LrcLibApi.TrackInfo?.hasUsefulArtistFor(song: Song): Boolean =
        this != null &&
            !artist.isNullOrBlank() &&
            !artist.isUnknownArtist() &&
            (song.artist.isUnknownArtist() || !artist.equals(song.artist, ignoreCase = true))

    private fun LrcLibApi.TrackInfo?.matchesSongContext(song: Song): Boolean {
        if (this == null) return false

        val titleMatches = title.cleanKey() == song.title.cleanKey()
        if (!titleMatches) return false

        val albumTrusted = song.album.isUnknownAlbum() || album.cleanKey().isNotBlank() && album.cleanKey() == song.album.cleanKey()
        if (!albumTrusted) return false

        if (!song.artist.isUnknownArtist()) {
            return artist.cleanKey() == song.artist.cleanKey()
        }

        return !artist.isNullOrBlank() && !artist.isUnknownArtist()
    }

    private fun String?.isUnknownArtist(): Boolean =
        isNullOrBlank() ||
            equals("Unknown artist", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.isUnknownAlbum(): Boolean =
        isNullOrBlank() ||
            equals("Unknown album", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.cleanKey(): String =
        this.orEmpty()
            .lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^a-z0-9 ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
