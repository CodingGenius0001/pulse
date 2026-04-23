package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pulse.music.data.Song
import com.pulse.music.util.gradientFor

/**
 * Album art. Tries embedded art from MediaStore; if that fails, falls back
 * to a gradient tile with a music-note icon centered on top.
 *
 * The gradient is seeded by album+artist so the same album always gets the
 * same color across the UI — consistent visual identity without the bland
 * "every song looks identical" look a uniform music-note icon would have.
 */
@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (song == null) {
            MusicNoteFallback(
                seed = "empty",
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.albumArtUri)
                .crossfade(true)
                .build(),
        )
        val state = painter.state

        val loadFailed = state is AsyncImagePainter.State.Error ||
                state is AsyncImagePainter.State.Empty

        if (loadFailed) {
            MusicNoteFallback(
                seed = song.album + song.artist,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = "${song.album} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Gradient background + centered music-note icon — the fallback used when
 * a song has no embedded album art (or the art fails to load).
 */
@Composable
private fun MusicNoteFallback(
    seed: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(gradientFor(seed)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            // Icon size scales naturally with the container via fillMaxSize+padding
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        )
    }
}
