package com.pulse.music.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.util.gradientFor

@Composable
fun ForYouScreen(
    onSongTap: (List<Song>, Int) -> Unit,
    onHeroPlay: (List<Song>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val topPlayed by vm.topPlayed.collectAsStateWithLifecycle()
    val folderState by vm.folderState.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = BottomBarContentPadding.calculateBottomPadding(),
        ),
    ) {
        item { AppBar(userName = userName, onOpenSettings = onOpenSettings) }

        item {
            Spacer(Modifier.height(4.dp))
            HeroCard(
                hero = topPlayed.firstOrNull() ?: allSongs.firstOrNull(),
                heroSongs = topPlayed.ifEmpty { allSongs.take(12) },
                onPlay = onHeroPlay,
            )
            Spacer(Modifier.height(24.dp))
        }

        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently played",
                    subtitle = "Pick up where you left off",
                )
                Spacer(Modifier.height(12.dp))
                SongCarousel(
                    songs = recentlyPlayed,
                    onTap = { index -> onSongTap(recentlyPlayed, index) },
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently added",
                    subtitle = "Fresh in your library",
                )
                Spacer(Modifier.height(12.dp))
                SongCarousel(
                    songs = recentlyAdded,
                    onTap = { index -> onSongTap(recentlyAdded, index) },
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        if (allSongs.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Made for you",
                    subtitle = "Built from your library",
                )
                Spacer(Modifier.height(12.dp))
                MadeForYouCard(
                    title = "All music",
                    subtitle = "${allSongs.size} songs · Everything in your library",
                    thumbnailSongs = allSongs.take(4),
                    seed = "all_music",
                    onPlay = { onSongTap(allSongs, 0) },
                )
                Spacer(Modifier.height(8.dp))
                if (recentlyAdded.size >= 4) {
                    MadeForYouCard(
                        title = "Fresh mix",
                        subtitle = "${recentlyAdded.size} songs · Recently added",
                        thumbnailSongs = recentlyAdded.take(4),
                        seed = "fresh_mix",
                        onPlay = { onSongTap(recentlyAdded, 0) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (topPlayed.size >= 4) {
                    MadeForYouCard(
                        title = "On repeat",
                        subtitle = "${topPlayed.size} songs · Your most-played",
                        thumbnailSongs = topPlayed.take(4),
                        seed = "top_played",
                        onPlay = { onSongTap(topPlayed, 0) },
                    )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Pulse",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)
                        )
                    )
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userName.firstOrNull()?.uppercase() ?: "Y",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
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
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(1f / 1.15f)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        if (hero != null) {
            // Full-bleed album art backdrop
            AlbumArt(
                song = hero,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientFor("hero")),
            )
        }
        // Dark gradient for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to Color.Black.copy(alpha = 0.4f),
                            1f to Color.Black.copy(alpha = 0.85f),
                        )
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(PulseTheme.colors.accentViolet),
                )
                Text(
                    text = "ON REPEAT · THIS WEEK",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = hero?.album ?: "Your music",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (heroSongs.isEmpty()) "Import music to get started"
                else "The ${heroSongs.size} songs you've been looping lately",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .clickable(enabled = heroSongs.isNotEmpty()) { onPlay(heroSongs) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Play now",
                    color = MaterialTheme.colorScheme.background,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SongCarousel(songs: List<Song>, onTap: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(songs.size) { index ->
            val song = songs[index]
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .clickable { onTap(index) },
            ) {
                AlbumArt(
                    song = song,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(100.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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
    thumbnailSongs: List<Song>,
    seed: String,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PulseTheme.colors.pillSurface)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumMosaic(
            songs = thumbnailSongs,
            seed = seed,
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PulseTheme.colors.pillSurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(14.dp),
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
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (folderExists) "Pulse folder is empty"
            else "Let's set up your music folder",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = if (folderExists)
                "Drop MP3, FLAC, or M4A files into:\n$folderPath\nthen tap Rescan."
            else
                "Pulse looks for music in a dedicated folder so your library stays clean. We'll create it at:\n$folderPath",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable(onClick = if (folderExists) onRescan else onCreateFolder)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (folderExists) "Rescan library" else "Create Pulse folder",
                color = MaterialTheme.colorScheme.background,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
