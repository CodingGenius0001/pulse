package com.pulse.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pulse.music.PulseApplication
import com.pulse.music.data.Song
import com.pulse.music.data.SongMetadata
import com.pulse.music.util.gradientFor

/**
 * Album art with a three-level fallback chain:
 * 1. Resolved remote artwork URL from the Room cache.
 * 2. Embedded artwork from the audio file only while remote identity is still unresolved.
 * 3. Deterministic gradient tile.
 *
 * The metadata row is observed, not read once, so covers appear as soon as
 * background enrichment finishes.
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
            MusicNoteFallback(seed = "empty", modifier = Modifier.fillMaxSize())
            return@Box
        }

        val metadata by produceState<SongMetadata?>(initialValue = null, song.id) {
            PulseApplication.get()
                .metadataRepository
                .observe(song.id)
                .collect { value = it }
        }
        val remoteArtUrl = metadata?.artworkUrl?.takeIf(String::isNotBlank)
        val remoteIdentityResolved =
            !metadata?.resolvedTitle.isNullOrBlank() || !metadata?.resolvedArtist.isNullOrBlank()

        LaunchedEffect(song.id, remoteArtUrl) {
            if (remoteArtUrl.isNullOrBlank()) {
                PulseApplication.get().metadataRepository.resolve(song)
            }
        }

        var useEmbeddedFallback by remember(song.id, remoteArtUrl, remoteIdentityResolved) { mutableStateOf(false) }
        val model: Any? = when {
            !remoteArtUrl.isNullOrBlank() && !useEmbeddedFallback -> remoteArtUrl
            !remoteIdentityResolved -> song.albumArtUri
            else -> null
        }

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
        )
        val state = painter.state

        LaunchedEffect(state, remoteArtUrl, remoteIdentityResolved, useEmbeddedFallback) {
            if (state is AsyncImagePainter.State.Error &&
                !remoteArtUrl.isNullOrBlank() &&
                !remoteIdentityResolved &&
                !useEmbeddedFallback
            ) {
                useEmbeddedFallback = true
            }
        }

        MusicNoteFallback(
            seed = song.album + song.artist,
            modifier = Modifier.fillMaxSize(),
        )

        if (model != null && state !is AsyncImagePainter.State.Error) {
            Image(
                painter = painter,
                contentDescription = "${song.album} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MusicNoteFallback(
    seed: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(gradientFor(seed)),
        contentAlignment = Alignment.Center,
    ) {
        val minSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val iconSize = (minSide * 0.34f).coerceIn(18.dp, 104.dp)
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.48f),
            modifier = Modifier.size(iconSize),
        )
    }
}
