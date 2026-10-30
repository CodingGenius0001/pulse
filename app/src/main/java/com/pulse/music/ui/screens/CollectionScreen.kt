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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Song
import com.pulse.music.ui.AlbumEntry
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AlbumMosaic
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.ConfirmDeletePlaylistDialog
import com.pulse.music.ui.components.RenamePlaylistDialog
import com.pulse.music.ui.components.SongListRow
import com.pulse.music.ui.components.SongPickerDialog
import com.pulse.music.ui.components.removeFromPlaylistItem
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.util.formatTotalDuration
import com.pulse.music.util.songCountLabel
import kotlinx.coroutines.launch

/** Which collection a [CollectionScreen] shows. Keys come from [com.pulse.music.ui.LibraryIndex]. */
sealed interface CollectionRef {
    data class Album(val key: String) : CollectionRef
    data class Artist(val key: String) : CollectionRef
    data class Playlist(val id: Long) : CollectionRef
    data object Liked : CollectionRef
    data object RecentlyAdded : CollectionRef
}

/**
 * One page for every song collection: albums, artists, user playlists and
 * the built-in Liked songs / Recently added lists.
 */
@Composable
fun CollectionScreen(
    ref: CollectionRef,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val index by vm.libraryIndex.collectAsStateWithLifecycle()
    val likedSongs by vm.likedSongs.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val playlist = (ref as? CollectionRef.Playlist)?.let { r -> playlists.firstOrNull { it.id == r.id } }
    val playlistSongs by remember(ref) {
        if (ref is CollectionRef.Playlist) vm.observeSongsInPlaylist(ref.id) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val album = (ref as? CollectionRef.Album)?.let { index.album(it.key) }
    val artist = (ref as? CollectionRef.Artist)?.let { index.artist(it.key) }
    val artistAlbums: List<AlbumEntry> = remember(artist, index) {
        val artistSongIds = artist?.songs?.map { it.id }?.toSet().orEmpty()
        index.albums.filter { entry -> entry.songs.any { it.id in artistSongIds } }
    }

    val songs: List<Song> = when (ref) {
        is CollectionRef.Album -> album?.songs.orEmpty()
        is CollectionRef.Artist -> artist?.songs.orEmpty()
        is CollectionRef.Playlist -> playlistSongs
        CollectionRef.Liked -> likedSongs
        CollectionRef.RecentlyAdded -> recentlyAdded
    }
    val title = when (ref) {
        is CollectionRef.Album -> album?.title ?: "Album"
        is CollectionRef.Artist -> artist?.name ?: "Artist"
        is CollectionRef.Playlist -> playlist?.name ?: "Playlist"
        CollectionRef.Liked -> "Liked songs"
        CollectionRef.RecentlyAdded -> "Recently added"
    }
    val kind = when (ref) {
        is CollectionRef.Album -> "Album"
        is CollectionRef.Artist -> "Artist"
        is CollectionRef.Playlist -> "Playlist"
        CollectionRef.Liked, CollectionRef.RecentlyAdded -> "Collection"
    }
    val details = buildList {
        if (ref is CollectionRef.Album && album != null) add(album.artist)
        if (ref is CollectionRef.Artist && artist != null && artist.albumCount > 0) {
            add(if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums")
        }
        add(songCountLabel(songs.size))
        if (songs.isNotEmpty()) add(formatTotalDuration(songs.sumOf { it.durationMs }))
    }.joinToString(" · ")

    // A deleted playlist or an album whose songs all vanished: leave the page.
    val missing = when (ref) {
        is CollectionRef.Playlist -> playlists.isNotEmpty() && playlist == null
        // The index is built off the main thread; only judge once it exists.
        is CollectionRef.Album -> index.artists.isNotEmpty() && album == null
        is CollectionRef.Artist -> index.artists.isNotEmpty() && artist == null
        else -> false
    }
    if (missing) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    }

    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = BottomBarContentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                CircleButton(onClick = onBack, size = 42.dp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CollectionArtwork(ref = ref, songs = songs, title = title, coverSong = album?.coverSong)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = kind.uppercase(),
                    color = PulseTheme.colors.accentViolet,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = details,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryAction(
                        label = "Play",
                        icon = Icons.Filled.PlayArrow,
                        enabled = songs.isNotEmpty(),
                        onClick = { onPlay(songs, 0) },
                    )
                    SecondaryAction(
                        label = "Shuffle",
                        icon = Icons.Filled.Shuffle,
                        enabled = songs.size > 1,
                        onClick = { onShuffle(songs) },
                    )
                }
                if (ref is CollectionRef.Playlist && playlist != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmallAction("Add songs", Icons.Filled.Add) { showPicker = true }
                        SmallAction("Rename", Icons.Filled.Edit) { showRename = true }
                        SmallAction("Delete", Icons.Filled.Delete) { showDelete = true }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (ref is CollectionRef.Artist && artistAlbums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artistAlbums, key = { it.key }) { entry ->
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenAlbum(entry.key) },
                        ) {
                            AlbumArt(song = entry.coverSong, cornerRadius = 16.dp, modifier = Modifier.size(120.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = entry.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = songCountLabel(entry.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Songs",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        if (songs.isEmpty()) {
            item {
                EmptyCollectionMessage(
                    text = when (ref) {
                        is CollectionRef.Playlist -> "This playlist is empty. Use Add songs, or the ⋮ menu on any song."
                        CollectionRef.Liked -> "Songs you like show up here. Tap the heart in the player, or use the ⋮ menu on any song."
                        else -> "Nothing here yet."
                    },
                )
            }
        }

        itemsIndexed(songs, key = { _, song -> song.id }) { position, song ->
            SongListRow(
                song = song,
                onClick = { onPlay(songs, position) },
                leadingNumber = if (ref is CollectionRef.Album) {
                    (song.trackNumber % 1000).takeIf { it > 0 } ?: (position + 1)
                } else {
                    null
                },
                showAlbum = ref !is CollectionRef.Album,
                menuExtras = if (ref is CollectionRef.Playlist) {
                    listOf(removeFromPlaylistItem { scope.launch { vm.removeSongFromPlaylist(ref.id, song.id) } })
                } else {
                    emptyList()
                },
            )
        }
    }

    if (playlist != null) {
        if (showPicker) {
            val inPlaylist = remember(playlistSongs) { playlistSongs.map { it.id }.toSet() }
            SongPickerDialog(
                title = "Add to ${playlist.name}",
                songs = allSongs.filterNot { it.id in inPlaylist },
                onDismiss = { showPicker = false },
                onAddSong = { song -> scope.launch { vm.addSongToPlaylist(playlist.id, song.id) } },
            )
        }
        if (showRename) {
            RenamePlaylistDialog(
                currentName = playlist.name,
                onDismiss = { showRename = false },
                onSave = { name ->
                    showRename = false
                    scope.launch { vm.renamePlaylist(playlist.id, name) }
                },
            )
        }
        if (showDelete) {
            ConfirmDeletePlaylistDialog(
                playlistName = playlist.name,
                onDismiss = { showDelete = false },
                onDelete = {
                    showDelete = false
                    scope.launch {
                        vm.deletePlaylist(playlist.id)
                        onBack()
                    }
                },
            )
        }
    }
}

@Composable
private fun CollectionArtwork(ref: CollectionRef, songs: List<Song>, title: String, coverSong: Song? = null) {
    val size = 210.dp
    when (ref) {
        is CollectionRef.Album -> AlbumArt(song = coverSong ?: songs.firstOrNull(), cornerRadius = 28.dp, modifier = Modifier.size(size))
        is CollectionRef.Playlist -> AlbumMosaic(
            songs = songs.take(4),
            seed = "playlist_${ref.id}",
            cornerRadius = 28.dp,
            modifier = Modifier.size(size),
        )
        is CollectionRef.Artist -> Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                color = PulseTheme.colors.onPrimary,
                style = MaterialTheme.typography.displayLarge,
            )
        }
        CollectionRef.Liked, CollectionRef.RecentlyAdded -> Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(PulseTheme.colors.accentPink, PulseTheme.colors.accentViolet))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (ref == CollectionRef.Liked) Icons.Filled.Favorite else Icons.Filled.History,
                contentDescription = null,
                tint = PulseTheme.colors.onPrimary,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun PrimaryAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)),
                alpha = if (enabled) 1f else 0.4f,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = PulseTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
        Text(text = label, color = PulseTheme.colors.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SmallAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyCollectionMessage(text: String) {
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
