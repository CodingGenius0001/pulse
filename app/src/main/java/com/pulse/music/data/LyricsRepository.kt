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
) {

    suspend fun lyricsFor(song: Song): LyricsResult {
        lyricsDao.get(song.id)?.let { cached ->
            if (!cached.notFound) return cached.toResult()
            lyricsDao.delete(song.id)
        }

        val response = LrcLibApi.fetch(
            trackName = song.title,
            artistName = song.artist,
            albumName = song.album,
            durationSeconds = song.durationMs / 1000,
        )

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

    private fun SongLyrics.toResult(): LyricsResult {
        syncedLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = true)
        }
        plainLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = false)
        }
        return LyricsResult.NotFound
    }
}
