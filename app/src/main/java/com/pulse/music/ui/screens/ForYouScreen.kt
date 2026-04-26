package com.pulse.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.update.UpdateState
import com.pulse.music.update.UpdateViewModel

@Composable
fun ForYouScreen(
    vm: LibraryViewModel,
    updateVm: UpdateViewModel,
    onSongTap: (List<Song>, Int) -> Unit,
    onHeroPlay: (List<Song>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val topPlayed by vm.topPlayed.collectAsStateWithLifecycle()
    val folderState by vm.folderState.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentPadding = PaddingValues(top = 12.dp, bottom = BottomBarContentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { AppBar(userName = userName, onOpenSettings = onOpenSettings) }
        item { HeroCard(topPlayed.firstOrNull() ?: allSongs.firstOrNull(), topPlayed.ifEmpty { allSongs.take(12) }, onHeroPlay) }
        item { UpdateBanner(updateVm = updateVm, onOpenSettings = onOpenSettings) }

        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeading("Recently played", "Return to what still feels current.") }
            item { SongCarousel(songs = recentlyPlayed, onTap = { index -> onSongTap(recentlyPlayed, index) }) }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeading("Recently added", "New arrivals waiting to settle in.") }
            item { SongCarousel(songs = recentlyAdded, onTap = { index -> onSongTap(recentlyAdded, index) }) }
        }

        if (allSongs.isNotEmpty()) {
            item { SectionHeading("Curated from your library", "Quiet mixes built from what you already keep close.") }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MadeForYouCard(
                        title = "All music",
                        subtitle = "${allSongs.size} songs",
                        detail = "Everything you have imported.",
                        thumbnailSongs = allSongs.take(4),
                        seed = "all_music",
                        onPlay = { onSongTap(allSongs, 0) },
                    )
                    if (recentlyAdded.size >= 4) {
                        MadeForYouCard(
                            title = "Fresh mix",
                            subtitle = "${recentlyAdded.size} songs",
                            detail = "The newest additions to your collection.",
                            thumbnailSongs = recentlyAdded.take(4),
                            seed = "fresh_mix",
                            onPlay = { onSongTap(recentlyAdded, 0) },
                        )
                    }
                    if (topPlayed.size >= 4) {
                        MadeForYouCard(
                            title = "On repeat",
                            subtitle = "${topPlayed.size} songs",
                            detail = "Your most-played run lately.",
                            thumbnailSongs = topPlayed.take(4),
                            seed = "top_played",
                            onPlay = { onSongTap(topPlayed, 0) },
                        )
                    }
                }
            }
        }

        if (allSongs.isEmpty()) {
            item {
                EmptyState(
                    folderExists = folderState.exists,
                    folderPath = folderState.displayPath,
                    onCreateFolder = { vm.createPulseFolder() },
                    onRescan = { vm.rescan() },
                )
            }
        }
    }
}

@Composable
private fun AppBar(userName: String, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Pulse",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "A cleaner room for your collection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userName.firstOrNull()?.uppercase() ?: "P",
                    color = PulseTheme.colors.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            CircleButton(onClick = onOpenSettings, size = 42.dp) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Open settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    hero: Song?,
    heroSongs: List<Song>,
    onPlay: (List<Song>) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .aspectRatio(1f / 1.18f)
            .clip(RoundedCornerShape(32.dp))
            .background(PulseTheme.colors.surfaceElevated),
    ) {
        if (hero != null) {
            AlbumArt(
                song = hero,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.26f),
                            Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "FEATURED SELECTION",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = hero?.album ?: "Your library",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (heroSongs.isEmpty()) "Import music to begin."
                else "${heroSongs.size} tracks arranged from your most-played run.",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = if (heroSongs.isEmpty()) 0.3f else 0.94f))
                    .clickable(enabled = heroSongs.isNotEmpty()) { onPlay(heroSongs) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = PulseTheme.colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Play selection",
                    color = PulseTheme.colors.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SongCarousel(songs: List<Song>, onTap: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(songs.size) { index ->
            val song = songs[index]
            Column(
                modifier = Modifier.width(118.dp).clickable { onTap(index) },
            ) {
                AlbumArt(
                    song = song,
                    cornerRadius = 18.dp,
                    modifier = Modifier.size(118.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MadeForYouCard(
    title: String,
    subtitle: String,
    detail: String,
    thumbnailSongs: List<Song>,
    seed: String,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumMosaic(
            songs = thumbnailSongs,
            seed = seed,
            modifier = Modifier.size(68.dp),
            cornerRadius = 16.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = subtitle,
                color = PulseTheme.colors.accentViolet,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PulseTheme.colors.accentCream),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = PulseTheme.colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(
    folderExists: Boolean,
    folderPath: String,
    onCreateFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (folderExists) "Your Pulse folder is ready for music"
            else "Set up the Pulse folder first",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = if (folderExists)
                "Drop MP3, FLAC, or M4A files into $folderPath, then rescan the library."
            else
                "Pulse keeps a dedicated source folder so the library stays tidy. It will live at $folderPath.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(PulseTheme.colors.accentCream)
                .clickable(onClick = if (folderExists) onRescan else onCreateFolder)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(
                text = if (folderExists) "Rescan library" else "Create folder",
                color = PulseTheme.colors.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun UpdateBanner(updateVm: UpdateViewModel, onOpenSettings: () -> Unit) {
    val state by updateVm.state.collectAsStateWithLifecycle()
    val info = (state as? UpdateState.Available)?.info ?: return

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onOpenSettings)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(PulseTheme.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = PulseTheme.colors.accentViolet,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Update available",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Build #${info.buildNumber} is ready to download.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Open",
            color = PulseTheme.colors.accentViolet,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
