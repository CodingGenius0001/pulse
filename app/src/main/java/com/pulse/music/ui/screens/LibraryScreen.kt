package com.pulse.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.FilterPill
import com.pulse.music.ui.components.SolidThumbnail
import com.pulse.music.ui.theme.PulseTheme
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) {
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Songs("Songs"),
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onSongTap: (List<Song>, Int) -> Unit,
) {
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val likedSongs by vm.likedSongs.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()

    // Pre-group songs here (where @Composable context is explicit) so the
    // lazy list just consumes the result. Grouping inside the when-branches
    // below sits in a non-composable lambda and can't call remember().
    val albumsGrouped = remember(allSongs) { allSongs.groupBy { it.album }.entries.toList() }
    val artistsGrouped = remember(allSongs) { allSongs.groupBy { it.artist }.entries.toList() }

    val scope = rememberCoroutineScope()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    var selectedFilter by remember { mutableStateOf(LibraryFilter.Playlists) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseTheme.background),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
            )
            CircleButton(onClick = { showNewPlaylistDialog = true }, size = 36.dp) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New playlist",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Filter pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries.size) { idx ->
                val filter = LibraryFilter.entries[idx]
                FilterPill(
                    label = filter.label,
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = BottomBarContentPadding.calculateBottomPadding(),
            ),
        ) {
            when (selectedFilter) {
                LibraryFilter.Playlists -> {
                    // System: Liked songs
                    item {
                        SystemPlaylistRow(
                            title = "Liked songs",
                            subtitle = "${likedSongs.size} songs",
                            gradient = listOf(PulseTheme.colors.accentPink, Color(0xFFEC4899)),
                            icon = { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
                            onClick = { if (likedSongs.isNotEmpty()) onSongTap(likedSongs, 0) },
                        )
                    }
                    // System: Recently added
                    item {
                        SystemPlaylistRow(
                            title = "Recently added",
                            subtitle = "${recentlyAdded.size} songs · Auto",
                            gradient = listOf(Color(0xFF60A5FA), Color(0xFF3B82F6)),
                            icon = { Icon(Icons.Filled.History, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
                            onClick = { if (recentlyAdded.isNotEmpty()) onSongTap(recentlyAdded, 0) },
                        )
                    }
                    // User playlists
                    items(playlists.filter { it.systemType == null }) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            vm = vm,
                            onClick = { /* TODO detail screen */ },
                        )
                    }
                    if (playlists.none { it.systemType == null }) {
                        item {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = "No user playlists yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                LibraryFilter.Albums -> {
                    items(albumsGrouped) { (albumName, songs) ->
                        AlbumRow(
                            albumName = albumName,
                            artist = songs.firstOrNull()?.artist.orEmpty(),
                            songCount = songs.size,
                            representativeSong = songs.first(),
                            onClick = { onSongTap(songs, 0) },
                        )
                    }
                }
                LibraryFilter.Artists -> {
                    items(artistsGrouped) { (artistName, songs) ->
                        ArtistRow(
                            artist = artistName,
                            songCount = songs.size,
                            onClick = { onSongTap(songs, 0) },
                        )
                    }
                }
                LibraryFilter.Songs -> {
                    items(allSongs.size) { index ->
                        val song = allSongs[index]
                        SongRow(
                            song = song,
                            onClick = { onSongTap(allSongs, index) },
                        )
                    }
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onCreate = { name ->
                scope.launch { vm.createPlaylist(name) }
                showNewPlaylistDialog = false
            },
        )
    }
}

@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                Text(
                    text = "Give your playlist a name. You can add songs to it later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PulseTheme.colors.pillSurface)
                        .border(1.dp, PulseTheme.colors.line, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Playlist name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it.take(60) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onCreate(input.trim()) },
            ) {
                Text("Create", color = MaterialTheme.colorScheme.onBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SystemPlaylistRow(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SolidThumbnail(
            color = Color.Transparent,
            modifier = Modifier
                .size(52.dp)
                .background(Brush.linearGradient(gradient), RoundedCornerShape(10.dp)),
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = "⋯",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    vm: LibraryViewModel,
    onClick: () -> Unit,
) {
    // Fetch thumbnail art lazily
    val thumbnailSongs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
        value = vm.getPlaylistThumbnailArt(playlist.id)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumMosaic(
            songs = thumbnailSongs,
            seed = "playlist_${playlist.id}",
            modifier = Modifier.size(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${thumbnailSongs.size} songs · You",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = "⋯",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(
            song = song,
            cornerRadius = 6.dp,
            modifier = Modifier.size(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AlbumRow(
    albumName: String,
    artist: String,
    songCount: Int,
    representativeSong: Song,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(
            song = representativeSong,
            cornerRadius = 8.dp,
            modifier = Modifier.size(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$artist · $songCount songs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.linearGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artist.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$songCount songs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
