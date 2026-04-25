package com.pulse.music.data

import com.pulse.music.lyrics.LyricsResult
import com.pulse.music.network.LrcLibApi

/**
 * Provides lyrics for a song. Reads Room cache first; on miss, queries
 * LRCLIB and writes the result (whether match or miss) back to cache so we
 * never repeat a lookup.
 *
 * Synced lyrics (LRC format) are preferred when available — the UI can
 * highlight lines as the song plays. Otherwise we fall back to plain text.
 */
class LyricsRepository(
    private val lyricsDao: LyricsDao,
) {

    suspend fun lyricsFor(song: Song): LyricsResult {
        // 1. Cache check
        lyricsDao.get(song.id)?.let { cached ->
            return cached.toResult()
        }

        // 2. LRCLIB fetch
        val durationSec = song.durationMs / 1000
        val response = LrcLibApi.fetch(
            trackName = song.title,
            artistName = song.artist,
            albumName = song.album,
            durationSeconds = durationSec,
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
                // Don't cache errors — transient network issue shouldn't lock
                // the user out of retrying once they're back on wifi.
                return LyricsResult.NotFound
            }
        }

        lyricsDao.upsert(record)
        return record.toResult()
    }

    /** Force a re-fetch, bypassing cache. */
    suspend fun refresh(song: Song): LyricsResult {
        lyricsDao.delete(song.id)
        return lyricsFor(song)
    }

    private fun SongLyrics.toResult(): LyricsResult {
        // Prefer synced if present — the UI gets karaoke-style highlighting.
        syncedLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = true)
        }
        plainLyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Found(text = it, synced = false)
        }
        return LyricsResult.NotFound
    }
}
