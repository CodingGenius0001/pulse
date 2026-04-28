package com.pulse.music.data

import com.pulse.music.lyrics.LyricsResult
import com.pulse.music.network.LrcLibApi

/**
 * Provides lyrics for a song. Found lyrics are cached in Room. Misses are
 * intentionally retried because user tags are often incomplete and lookup
 * heuristics improve over time.
 */
class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val metadataDao: MetadataDao,
) {

    suspend fun lyricsFor(song: Song): LyricsResult {
        val metadata = metadataDao.get(song.id)
        lyricsDao.get(song.id)?.let { cached ->
            if (!cached.notFound) return cached.toResult()
            lyricsDao.delete(song.id)
        }
        val requests = buildLookupRequests(song, metadata)

        var sawNetworkError = false
        var response: LrcLibApi.LrcLibResponse = LrcLibApi.LrcLibResponse.NotFound
        for (request in requests) {
            response = LrcLibApi.fetch(
                trackName = request.title,
                artistName = request.artist,
                albumName = request.album,
                durationSeconds = song.durationMs / 1000,
            )
            when (response) {
                is LrcLibApi.LrcLibResponse.Found -> break
                LrcLibApi.LrcLibResponse.Error -> sawNetworkError = true
                LrcLibApi.LrcLibResponse.NotFound -> Unit
            }
        }

        val attemptedAt = System.currentTimeMillis()
        val record = when (response) {
            is LrcLibApi.LrcLibResponse.Found -> SongLyrics(
                songId = song.id,
                syncedLyrics = response.syncedLyrics,
                plainLyrics = response.plainLyrics,
                instrumental = response.instrumental,
                notFound = false,
            )
            LrcLibApi.LrcLibResponse.NotFound -> SongLyrics(
                songId = song.id,
                notFound = true,
            )
            LrcLibApi.LrcLibResponse.Error -> {
                return LyricsResult.Error("Couldn't reach LRCLIB. Check your connection and try again.")
            }
        }

        if (record.notFound && sawNetworkError) {
            markLyricsAttempt(
                songId = song.id,
                attemptedAt = attemptedAt,
                resolved = false,
            )
            return LyricsResult.Error("Couldn't reach LRCLIB. Check your connection and try again.")
        }

        markLyricsAttempt(
            songId = song.id,
            attemptedAt = attemptedAt,
            resolved = !record.notFound,
        )
        if (!record.notFound) {
            lyricsDao.upsert(record)
        }
        return record.toResult()
    }

    /** Force a re-fetch, bypassing cache. */
    suspend fun refresh(song: Song): LyricsResult {
        val existing = lyricsDao.get(song.id)
        lyricsDao.delete(song.id)
        val refreshed = lyricsFor(song)
        return when {
            refreshed is LyricsResult.Found -> refreshed
            existing != null && !existing.notFound -> {
                lyricsDao.upsert(existing)
                existing.toResult()
            }
            else -> refreshed
        }
    }

    suspend fun needsBackgroundFetch(song: Song, metadata: SongMetadata?): Boolean {
        val cached = lyricsDao.get(song.id)
        if (cached != null && !cached.notFound) return false

        val now = System.currentTimeMillis()
        val hasResolvedIdentity = metadata.hasResolvedIdentity() ||
            song.artist.isKnownArtist()
        if (!hasResolvedIdentity) return false

        if ((metadata?.lyricsResolvedAt ?: 0L) > 0L) return false
        val attemptedAt = metadata?.lyricsAttemptedAt ?: 0L
        return attemptedAt == 0L || now - attemptedAt >= LYRICS_RETRY_WINDOW_MS
    }

    suspend fun searchCandidates(
        title: String,
        artist: String = "",
        album: String = "",
        durationSeconds: Long = 0,
    ): List<LrcLibApi.TrackInfo> {
        return LrcLibApi.searchCandidates(
            trackName = title,
            artistName = artist,
            albumName = album,
            durationSeconds = durationSeconds,
        )
    }

    private fun SongLyrics.toResult(): LyricsResult {
        syncedLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = true)
        }
        plainLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = false)
        }
        return LyricsResult.NotFound
    }

    private fun buildLookupRequests(song: Song, metadata: SongMetadata?): List<LookupRequest> {
        val primary = LookupRequest(
            title = metadata?.resolvedTitle?.takeIf(String::isNotBlank) ?: song.title,
            artist = metadata?.resolvedArtist?.takeIf(String::isNotBlank) ?: song.artist,
            album = metadata?.resolvedAlbum?.takeIf(String::isNotBlank) ?: song.album,
        )
        val fallback = LookupRequest(
            title = song.title,
            artist = song.artist,
            album = song.album,
        )
        val titleOnly = LookupRequest(
            title = metadata?.resolvedTitle?.takeIf(String::isNotBlank) ?: song.title,
            artist = "",
            album = "",
        )
        val rawTitleOnly = LookupRequest(
            title = song.title,
            artist = "",
            album = "",
        )
        return listOf(primary, fallback, titleOnly, rawTitleOnly)
            .filter { it.title.isNotBlank() }
            .distinct()
    }

    private data class LookupRequest(
        val title: String,
        val artist: String,
        val album: String,
    )

    private suspend fun markLyricsAttempt(
        songId: Long,
        attemptedAt: Long,
        resolved: Boolean,
    ) {
        val metadata = metadataDao.get(songId) ?: SongMetadata(songId = songId)
        metadataDao.upsert(
            metadata.copy(
                lyricsAttemptedAt = attemptedAt,
                lyricsResolvedAt = if (resolved) attemptedAt else metadata.lyricsResolvedAt,
            )
        )
    }

    private fun SongMetadata?.hasResolvedIdentity(): Boolean =
        this != null &&
            (
                geniusId != null ||
                    !geniusUrl.isNullOrBlank() ||
                    (
                        !resolvedTitle.isNullOrBlank() &&
                            resolvedArtist.isKnownArtist()
                        ) ||
                    (
                        resolvedArtist.isKnownArtist() &&
                            resolvedAlbum.isKnownAlbum()
                        )
                )

    private fun String?.isKnownArtist(): Boolean =
        !isNullOrBlank() &&
            !equals("Unknown artist", ignoreCase = true) &&
            !equals("<unknown>", ignoreCase = true)

    private fun String?.isKnownAlbum(): Boolean =
        !isNullOrBlank() &&
            !equals("Unknown album", ignoreCase = true) &&
            !equals("<unknown>", ignoreCase = true)

    private companion object {
        const val LYRICS_RETRY_WINDOW_MS = 6 * 60 * 60 * 1000L
    }
}
