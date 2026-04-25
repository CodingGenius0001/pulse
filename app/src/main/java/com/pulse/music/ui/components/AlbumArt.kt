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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.pulse.music.util.gradientFor

/**
 * Album art with a three-level fallback chain:
 *   1. Genius-resolved artwork URL (remote, best quality)
 *   2. Embedded ID3/MP4 artwork from the audio file itself
 *   3. Gradient tile + centered music-note icon
 *
 * The Genius URL is looked up from the Room cache — no network call here,
 * just a disk read. Background enrichment (in LibraryViewModel) populates
 * the cache after a scan, so the more the user uses the app, the more
 * tracks get pretty art.
 *
 * The gradient is seeded by album+artist so the same album gets the same
 * color across the UI — albums stay visually distinct even without real art.
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

        // Look up a cached Genius artwork URL for this song. produceState
        // gives us a reactive value without blocking the composition.
        val geniusArtUrl by produceState<String?>(initialValue = null, song.id) {
            value = PulseApplication.get()
                .metadataRepository
                .getCached(song.id)
                ?.artworkUrl
        }

        val primaryModel: Any = geniusArtUrl ?: song.albumArtUri
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(primaryModel)
                .crossfade(true)
                .build(),
        )
        val state = painter.state

        val loadFailed = state is AsyncImagePainter.State.Error ||
            state is AsyncImagePainter.State.Empty

        if (loadFailed) {
            // If the primary source failed AND it was a Genius URL, we could
            // in theory try the embedded art next. Doing that requires a second
            // painter + additional state handling; for v0.4 we just drop to
            // the gradient fallback. Tracks that Genius resolves correctly
            // essentially always render fine, so this is a cheap tradeoff.
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
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        )
    }
}
