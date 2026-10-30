package com.pulse.music.data

/** What the app shows for a song: resolved metadata when we have it, file tags otherwise. */
data class SongDisplay(
    val title: String,
    val artist: String,
    val album: String,
)

fun Song.displayWith(metadata: SongMetadata?): SongDisplay = SongDisplay(
    title = preferResolved(metadata?.resolvedTitle, title),
    artist = preferResolved(metadata?.resolvedArtist, artist),
    album = preferResolved(metadata?.resolvedAlbum, album),
)

/**
 * Providers sometimes return a correct match in different casing
 * ("bohemian rhapsody"). When it only differs from the tag by case, keep the
 * tag's spelling.
 */
private fun preferResolved(resolved: String?, tag: String): String {
    val value = resolved?.trim()?.takeIf { it.isNotEmpty() } ?: return tag
    return if (value.equals(tag.trim(), ignoreCase = true)) tag else value
}
