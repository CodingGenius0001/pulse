package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song
import com.pulse.music.util.gradientFor

/**
 * Composes 2x2 album art tiles into a single thumbnail.
 *
 * If [songs] has fewer than 4 items we either pad the remaining tiles with
 * deterministic gradient swatches (for a consistent look) or, if empty,
 * render a single uniform gradient.
 */
@Composable
fun AlbumMosaic(
    songs: List<Song>,
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(cornerRadius))) {
        when {
            songs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientFor(seed)),
                )
            }
            songs.size == 1 -> {
                AlbumArt(
                    song = songs.first(),
                    cornerRadius = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                val padded = padToFour(songs, seed)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        AlbumArt(
                            song = padded[0],
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                        AlbumArt(
                            song = padded[1],
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        AlbumArt(
                            song = padded[2],
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                        AlbumArt(
                            song = padded[3],
                            cornerRadius = 0.dp,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pads the song list to exactly 4. If fewer than 4 songs exist, we repeat
 * the available ones so the mosaic always looks complete.
 */
private fun padToFour(songs: List<Song>, @Suppress("UNUSED_PARAMETER") seed: String): List<Song> {
    if (songs.size >= 4) return songs.take(4)
    val repeated = mutableListOf<Song>()
    var i = 0
    while (repeated.size < 4) {
        repeated.add(songs[i % songs.size])
        i++
    }
    return repeated
}

/**
 * Gradient-only fallback for playlists that have zero songs.
 */
@Composable
fun GradientThumbnail(
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradientFor(seed)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}

/**
 * Solid-color fallback for system playlists (Liked, Recently added) that
 * want a distinct visual treatment from user playlists.
 */
@Composable
fun SolidThumbnail(
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(color),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}
