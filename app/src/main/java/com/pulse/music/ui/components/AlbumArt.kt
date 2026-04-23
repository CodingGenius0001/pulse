package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pulse.music.data.Song
import com.pulse.music.util.gradientFor

/**
 * Renders album art for a song. If Coil successfully loads the embedded art,
 * the bitmap is shown. Otherwise we fall back to a deterministic gradient
 * seeded by the song's album — so a single album always gets the same color
 * even when art is missing.
 *
 * The fallback also shows the first letter of the album as a subtle marker.
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2D)),
            )
            return@Box
        }

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.albumArtUri)
                .crossfade(true)
                .build(),
        )
        // painter.state is backed by mutableStateOf, so reading it in a
        // composable triggers recomposition when it changes.
        val state = painter.state

        val loadFailed = state is AsyncImagePainter.State.Error ||
                state is AsyncImagePainter.State.Empty

        if (loadFailed) {
            // Gradient fallback seeded by album (not title) so an album stays consistent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientFor(song.album + song.artist)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = song.album.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
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
