package com.pulse.music.lyrics

/**
 * Outcome of asking for lyrics. Sealed so the UI can exhaustively handle
 * every case (found vs not found vs still loading — loading is modelled
 * elsewhere as `null`).
 */
sealed interface LyricsResult {
    /**
     * We have lyrics. [synced] tells the UI whether to enable the
     * karaoke-style line highlighter or just render plain text.
     *
     * When [synced] is true, [text] is in LRC format:
     *   `[00:12.34] First line of lyrics`
     *   `[00:15.67] Second line`
     */
    data class Found(val text: String, val synced: Boolean) : LyricsResult

    /**
     * Lyrics provider checked and came up empty, or we hit an error and
     * cached the miss. Either way, UI shows a friendly fallback message.
     */
    data object NotFound : LyricsResult
}

/**
 * Parses a single LRC timestamp like "[01:23.45]" into milliseconds.
 * Returns null if the line doesn't start with a valid timestamp. Lines
 * without timestamps (e.g. `[ti:Title]` metadata tags or blank lines)
 * are excluded from the synced line list.
 */
fun parseLrcTimestamp(line: String): Long? {
    // Expect format [mm:ss.ff] or [mm:ss.fff] or [mm:ss] at the very start
    if (line.length < 5 || line[0] != '[') return null
    val close = line.indexOf(']')
    if (close <= 0) return null
    val inside = line.substring(1, close)
    val parts = inside.split(":")
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val secondsStr = parts[1]
    val secondsDouble = secondsStr.toDoubleOrNull() ?: return null
    return ((minutes * 60 + secondsDouble) * 1000).toLong()
}

/**
 * Splits an LRC string into a list of (timestampMs, text) pairs, sorted by
 * timestamp. Lines without a timestamp are dropped. Used by the Lyrics
 * dialog to highlight the current line based on playback position.
 */
data class LrcLine(val timestampMs: Long, val text: String)

fun parseLrc(lrc: String): List<LrcLine> {
    val out = mutableListOf<LrcLine>()
    lrc.lines().forEach { raw ->
        val ts = parseLrcTimestamp(raw) ?: return@forEach
        val close = raw.indexOf(']')
        val text = raw.substring(close + 1).trim()
        if (text.isNotEmpty()) out.add(LrcLine(ts, text))
    }
    return out.sortedBy { it.timestampMs }
}
