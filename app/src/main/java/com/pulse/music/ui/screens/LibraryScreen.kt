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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import com.pulse.music.ui.AlbumEntry
import com.pulse.music.ui.ArtistEntry
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.FilterPill
import com.pulse.music.ui.components.LocalSongMetadata
import com.pulse.music.ui.components.SongListRow
import com.pulse.music.data.displayWith
import com.pulse.music.ui.components.isKnownArtistName
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.util.songCountLabel
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) {
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Songs("Songs"),
}

private enum class SongSort(val label: String) {
    Title("Title"),
    Artist("Artist"),
    RecentlyAdded("Recently added"),
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onSongTap: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistCounts by vm.playlistSongCounts.collectAsStateWithLifecycle()
    val likedSongs by vm.likedSongs.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val index by vm.libraryIndex.collectAsStateWithLifecycle()
    val metadata = LocalSongMetadata.current

    val scope = rememberCoroutineScope()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.Playlists) }
    var songSort by rememberSaveable { mutableStateOf(SongSort.Title) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val query = searchQuery.trim().lowercase()

    val visiblePlaylists = remember(playlists, query) {
        playlists.filter { it.systemType == null && (query.isBlank() || it.name.lowercase().contains(query)) }
    }
    val showLikedSongs = query.isBlank() || "liked songs".contains(query)
    val showRecentlyAdded = query.isBlank() || "recently added".contains(query)
    val visibleAlbums = remember(index, query) {
        index.albums.filter { album ->
            query.isBlank() || album.title.lowercase().contains(query) || album.artist.lowercase().contains(query)
        }
    }
    val visibleArtists = remember(index, query) {
        index.artists.filter { artist -> query.isBlank() || artist.name.lowercase().contains(query) }
    }
    val visibleSongs = remember(allSongs, metadata, query, songSort) {
        val displayed = allSongs.map { it to it.displayWith(metadata[it.id]) }
        displayed
            .filter { (_, display) ->
                query.isBlank() ||
                    display.title.lowercase().contains(query) ||
                    display.artist.lowercase().contains(query) ||
                    display.album.lowercase().contains(query)
            }
            .let { list ->
                when (songSort) {
                    SongSort.Title -> list.sortedBy { it.second.title.lowercase() }
                    SongSort.Artist -> list.sortedWith(
                        compareBy({ !it.second.artist.isKnownArtistName() }, { it.second.artist.lowercase() }, { it.second.title.lowercase() }),
                    )
                    SongSort.RecentlyAdded -> list.sortedByDescending { it.first.dateAdded }
                }
            }
            .map { it.first }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = "${songCountLabel(allSongs.size)} · ${index.albums.size} albums · ${index.artists.size} artists",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CircleButton(onClick = { showNewPlaylistDialog = true }, size = 44.dp) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New playlist",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
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
                        item(key = "liked") {
                            SystemPlaylistRow(
                                title = "Liked songs",
                                subtitle = songCountLabel(likedSongs.size),
                                icon = Icons.Filled.Favorite,
                                onClick = { onOpenCollection(Routes.LIKED) },
                            )
                        }
                    }
                    if (showRecentlyAdded) {
                        item(key = "recent") {
                            SystemPlaylistRow(
                                title = "Recently added",
                                subtitle = songCountLabel(recentlyAdded.size),
                                icon = Icons.Filled.History,
                                onClick = { onOpenCollection(Routes.RECENTLY_ADDED) },
                            )
                        }
                    }
                    items(visiblePlaylists, key = { "playlist_${it.id}" }) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            songCount = playlistCounts[playlist.id] ?: 0,
                            vm = vm,
                            onClick = { onOpenCollection(Routes.playlist(playlist.id)) },
                        )
                    }
                    if (visiblePlaylists.isEmpty()) {
                        item(key = "playlist_hint") {
                            if (query.isBlank()) {
                                CreatePlaylistPrompt(onClick = { showNewPlaylistDialog = true })
                            } else if (!showLikedSongs && !showRecentlyAdded) {
                                EmptyLibraryMessage("No playlists match that search.")
                            }
                        }
                    }
                }

                LibraryFilter.Albums -> {
                    val rows = visibleAlbums.chunked(2)
                    items(rows, key = { row -> "albums_" + row.first().key }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { album ->
                                AlbumTile(
                                    album = album,
                                    onClick = { onOpenCollection(Routes.album(album.key)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (visibleAlbums.isEmpty()) {
                        item { EmptyLibraryMessage(if (query.isBlank()) "No tagged albums yet." else "No albums match that search.") }
                    }
                }

                LibraryFilter.Artists -> {
                    items(visibleArtists, key = { "artist_" + it.key }) { artist ->
                        ArtistRow(
                            artist = artist,
                            onClick = { onOpenCollection(Routes.artist(artist.key)) },
                        )
                    }
                    if (visibleArtists.isEmpty()) {
                        item { EmptyLibraryMessage("No artists match that search.") }
                    }
                }

                LibraryFilter.Songs -> {
                    if (visibleSongs.isNotEmpty()) {
                        item(key = "songs_header") {
                            SongsHeader(
                                count = visibleSongs.size,
                                sort = songSort,
                                onSortChange = { songSort = it },
                                onShuffle = { onShuffle(visibleSongs) },
                            )
                        }
                    }
                    itemsIndexed(visibleSongs, key = { _, song -> song.id }) { position, song ->
                        SongListRow(
                            song = song,
                            onClick = { onSongTap(visibleSongs, position) },
                        )
                    }
                    if (visibleSongs.isEmpty()) {
                        item { EmptyLibraryMessage(if (query.isBlank()) "No songs yet. Add music to your Pulse folder, then rescan from Settings." else "No songs match that search.") }
                    }
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onCreate = { name ->
                showNewPlaylistDialog = false
                scope.launch {
                    val id = vm.createPlaylist(name)
                    onOpenCollection(Routes.playlist(id))
                }
            },
        )
    }
}

@Composable
private fun SongsHeader(
    count: Int,
    sort: SongSort,
    onSortChange: (SongSort) -> Unit,
    onShuffle: () -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)))
                .clickable(onClick = onShuffle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, tint = PulseTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
            Text("Shuffle all", color = PulseTheme.colors.onPrimary, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = songCountLabel(count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { sortOpen = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(sort.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SongSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = if (option == sort) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onBackground,
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            sortOpen = false
                        },
                    )
                }
            }
        }
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
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = value.isNotEmpty()) { onValueChange("") },
            contentAlignment = Alignment.Center,
        ) {
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
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
                    text = "Name it now. You can add songs from the playlist page or from any song's ⋮ menu.",
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.isNotBlank()) onCreate(input.trim()) },
                enabled = input.isNotBlank(),
            ) {
                Text(
                    "Create",
                    color = if (input.isNotBlank()) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    icon: ImageVector,
    onClick: () -> Unit,
) {
    LibraryRow(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(PulseTheme.colors.accentPink, PulseTheme.colors.accentViolet))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PulseTheme.colors.onPrimary, modifier = Modifier.size(22.dp))
        }
        RowText(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        RowChevron()
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    songCount: Int,
    vm: LibraryViewModel,
    onClick: () -> Unit,
) {
    var thumbnailSongs by remember(playlist.id) { mutableStateOf<List<Song>>(emptyList()) }
    // Re-read the cover whenever the playlist's contents change.
    LaunchedEffect(playlist.id, songCount) {
        thumbnailSongs = vm.getPlaylistThumbnailArt(playlist.id)
    }

    LibraryRow(onClick = onClick) {
        AlbumMosaic(
            songs = thumbnailSongs,
            seed = "playlist_${playlist.id}",
            modifier = Modifier.size(58.dp),
            cornerRadius = 16.dp,
        )
        RowText(title = playlist.name, subtitle = songCountLabel(songCount), modifier = Modifier.weight(1f))
        RowChevron()
    }
}

@Composable
private fun CreatePlaylistPrompt(onClick: () -> Unit) {
    LibraryRow(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = PulseTheme.colors.accentViolet, modifier = Modifier.size(22.dp))
        }
        RowText(
            title = "Create a playlist",
            subtitle = "Collect songs for a mood, a drive, anything",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlbumTile(
    album: AlbumEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        AlbumArt(
            song = album.coverSong,
            cornerRadius = 16.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = album.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = album.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntry, onClick: () -> Unit) {
    LibraryRow(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artist.name.firstOrNull()?.uppercase() ?: "?",
                color = PulseTheme.colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        val subtitle = buildList {
            if (artist.albumCount > 0) add(if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums")
            add(songCountLabel(artist.songs.size))
        }.joinToString(" · ")
        RowText(title = artist.name, subtitle = subtitle, modifier = Modifier.weight(1f))
        RowChevron()
    }
}

@Composable
private fun LibraryRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun RowText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
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
