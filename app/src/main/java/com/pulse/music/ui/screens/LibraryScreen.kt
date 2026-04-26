package com.pulse.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.FilterPill
import com.pulse.music.ui.components.PlaylistDetailDialog
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

    val albumsGrouped = remember(allSongs) { allSongs.groupBy { it.album }.entries.toList() }
    val artistsGrouped = remember(allSongs) { allSongs.groupBy { it.artist }.entries.toList() }

    val scope = rememberCoroutineScope()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.Playlists) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val query = searchQuery.trim().lowercase()
    val visiblePlaylists = remember(playlists, likedSongs, recentlyAdded, query) {
        playlists.filter { it.systemType == null && (query.isBlank() || it.name.lowercase().contains(query)) }
    }
    val showLikedSongs = query.isBlank() || "liked songs".contains(query) || likedSongs.any {
        it.title.lowercase().contains(query) || it.artist.lowercase().contains(query) || it.album.lowercase().contains(query)
    }
    val showRecentlyAdded = query.isBlank() || "recently added".contains(query) || recentlyAdded.any {
        it.title.lowercase().contains(query) || it.artist.lowercase().contains(query) || it.album.lowercase().contains(query)
    }
    val visibleAlbums = remember(albumsGrouped, query) {
        albumsGrouped.filter { (albumName, songs) ->
            query.isBlank() ||
                albumName.lowercase().contains(query) ||
                songs.any { it.artist.lowercase().contains(query) }
        }
    }
    val visibleArtists = remember(artistsGrouped, query) {
        artistsGrouped.filter { (artistName, _) ->
            query.isBlank() || artistName.lowercase().contains(query)
        }
    }
    val visibleSongs = remember(allSongs, query) {
        allSongs.filter { song ->
            query.isBlank() ||
                song.title.lowercase().contains(query) ||
                song.artist.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Library",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = "${allSongs.size} songs arranged cleanly.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CircleButton(onClick = { showNewPlaylistDialog = true }, size = 42.dp) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New playlist",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries) { filter ->
                FilterPill(
                    label = filter.label,
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LibrarySearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(60) },
            placeholder = when (selectedFilter) {
                LibraryFilter.Playlists -> "Search playlists"
                LibraryFilter.Albums -> "Search albums or artists"
                LibraryFilter.Artists -> "Search artists"
                LibraryFilter.Songs -> "Search songs, artists, albums"
            },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarContentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (selectedFilter) {
                LibraryFilter.Playlists -> {
                    if (showLikedSongs) {
                        item {
                            SystemPlaylistRow(
                                title = "Liked songs",
                                subtitle = "${likedSongs.size} tracks",
                                gradient = listOf(PulseTheme.colors.accentPink, PulseTheme.colors.accentViolet),
                                icon = { Icon(Icons.Filled.Favorite, null, tint = PulseTheme.colors.onPrimary, modifier = Modifier.size(20.dp)) },
                                onClick = { if (likedSongs.isNotEmpty()) onSongTap(likedSongs, 0) },
                            )
                        }
                    }
                    if (showRecentlyAdded) {
                        item {
                            SystemPlaylistRow(
                                title = "Recently added",
                                subtitle = "${recentlyAdded.size} tracks",
                                gradient = listOf(Color(0xFF556270), Color(0xFF8E9EAB)),
                                icon = { Icon(Icons.Filled.History, null, tint = PulseTheme.colors.onPrimary, modifier = Modifier.size(20.dp)) },
                                onClick = { if (recentlyAdded.isNotEmpty()) onSongTap(recentlyAdded, 0) },
                            )
                        }
                    }
                    items(visiblePlaylists) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            vm = vm,
                            onPlay = {
                                scope.launch {
                                    val songs = vm.getSongsInPlaylist(playlist.id)
                                    if (songs.isEmpty()) {
                                        selectedPlaylistId = playlist.id
                                    } else {
                                        onSongTap(songs, 0)
                                    }
                                }
                            },
                            onEdit = { selectedPlaylistId = playlist.id },
                        )
                    }
                    if (!showLikedSongs && !showRecentlyAdded && visiblePlaylists.isEmpty()) {
                        item {
                            EmptyLibraryMessage(
                                if (query.isBlank()) "No user playlists yet." else "No playlists match that search.",
                            )
                        }
                    }
                }

                LibraryFilter.Albums -> {
                    items(visibleAlbums) { (albumName, songs) ->
                        AlbumRow(
                            albumName = albumName,
                            artist = songs.firstOrNull()?.artist.orEmpty(),
                            songCount = songs.size,
                            representativeSong = songs.first(),
                            onClick = { onSongTap(songs, 0) },
                        )
                    }
                    if (visibleAlbums.isEmpty()) {
                        item { EmptyLibraryMessage("No albums match that search.") }
                    }
                }

                LibraryFilter.Artists -> {
                    items(visibleArtists) { (artistName, songs) ->
                        ArtistRow(
                            artist = artistName,
                            songCount = songs.size,
                            onClick = { onSongTap(songs, 0) },
                        )
                    }
                    if (visibleArtists.isEmpty()) {
                        item { EmptyLibraryMessage("No artists match that search.") }
                    }
                }

                LibraryFilter.Songs -> {
                    items(visibleSongs.size) { index ->
                        SongRow(
                            song = visibleSongs[index],
                            onClick = { onSongTap(visibleSongs, index) },
                        )
                    }
                    if (visibleSongs.isEmpty()) {
                        item { EmptyLibraryMessage("No songs match that search.") }
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

    selectedPlaylist?.let { playlist ->
        val playlistSongs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
            vm.observeSongsInPlaylist(playlist.id).collect { value = it }
        }
        PlaylistDetailDialog(
            playlist = playlist,
            playlistSongs = playlistSongs,
            allSongs = allSongs,
            onDismiss = { selectedPlaylistId = null },
            onSongTap = onSongTap,
            onPlayPlaylist = { songs -> onSongTap(songs, 0) },
            onRenamePlaylist = { name -> vm.renamePlaylist(playlist.id, name) },
            onDeletePlaylist = { vm.deletePlaylist(playlist.id) },
            onAddSong = { songId -> vm.addSongToPlaylist(playlist.id, songId) },
            onRemoveSong = { songId -> vm.removeSongFromPlaylist(playlist.id, songId) },
            launchSuspend = { block -> scope.launch { block() } },
        )
    }
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PulseTheme.colors.surfaceSoft)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Name the collection first. You can sort songs into it after.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(18.dp))
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
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (input.isNotBlank()) onCreate(input.trim()) }) {
                Text("Create", color = PulseTheme.colors.accentViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = PulseTheme.colors.surface,
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
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SolidThumbnail(
            color = Color.Transparent,
            modifier = Modifier.size(58.dp).background(Brush.linearGradient(gradient), RoundedCornerShape(16.dp)),
            cornerRadius = 16.dp,
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = "Open", color = PulseTheme.colors.accentViolet, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    vm: LibraryViewModel,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
) {
    val thumbnailSongs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
        value = vm.getPlaylistThumbnailArt(playlist.id)
    }
    val songCount by produceState(initialValue = 0, playlist.id) {
        value = vm.getPlaylistSongCount(playlist.id)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumMosaic(
            songs = thumbnailSongs,
            seed = "playlist_${playlist.id}",
            modifier = Modifier.size(58.dp),
            cornerRadius = 16.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$songCount songs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Edit",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(song = song, cornerRadius = 12.dp, modifier = Modifier.size(50.dp))
        Column(modifier = Modifier.weight(1f)) {
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
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(song = representativeSong, cornerRadius = 14.dp, modifier = Modifier.size(58.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = albumName, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$artist - $songCount songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ArtistRow(artist: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artist.firstOrNull()?.uppercase() ?: "?",
                color = PulseTheme.colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = artist, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$songCount songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyLibraryMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
