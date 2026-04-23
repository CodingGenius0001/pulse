package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * If [songs] has fewer than 4 items we pad by repeating available songs.
 * If empty, renders a single gradient swatch.
 *
 * We use fillMaxHeight/fillMaxWidth fractions rather than weight() because
 * weight() is a RowScope/ColumnScope extension and can trip over internal
 * parent-data types when chained through nested scopes.
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
                val padded = padToFour(songs)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        AlbumArt(
                            song = padded[0],
                            cornerRadius = 0.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f),
                        )
                        AlbumArt(
                            song = padded[1],
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        AlbumArt(
                            song = padded[2],
                            cornerRadius = 0.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f),
                        )
                        AlbumArt(
                            song = padded[3],
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pads a song list to exactly 4 by repeating available entries.
 */
private fun padToFour(songs: List<Song>): List<Song> {
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
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Solid-color fallback used for system playlists (Liked songs, Recently added).
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
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
