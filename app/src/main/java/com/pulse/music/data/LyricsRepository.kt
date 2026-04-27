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
        lyricsDao.get(song.id)?.let { cached ->
            if (!cached.notFound) return cached.toResult()
            lyricsDao.delete(song.id)
        }

        val metadata = metadataDao.get(song.id)
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
            return LyricsResult.Error("Couldn't reach LRCLIB. Check your connection and try again.")
        }

        if (!record.notFound) {
            lyricsDao.upsert(record)
        }
        return record.toResult()
    }

    /** Force a re-fetch, bypassing cache. */
    suspend fun refresh(song: Song): LyricsResult {
        lyricsDao.delete(song.id)
        return lyricsFor(song)
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
}
