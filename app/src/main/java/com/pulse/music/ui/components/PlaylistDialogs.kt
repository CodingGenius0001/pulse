package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pulse.music.data.Playlist
import com.pulse.music.data.Song
import com.pulse.music.ui.theme.PulseTheme

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: suspend (String) -> Long,
    onAddToPlaylist: suspend (Long) -> Unit,
    launchSuspend: (suspend () -> Unit) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (playlists.isEmpty()) {
                    Text(
                        text = "Create your first playlist below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PulseTheme.colors.surfaceElevated)
                                .clickable {
                                    launchSuspend {
                                        onAddToPlaylist(playlist.id)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Add here",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = PulseTheme.colors.accentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    if (newPlaylistName.isEmpty()) {
                        Text(
                            text = "New playlist name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    BasicTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it.take(60) },
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
            TextButton(
                onClick = {
                    val name = newPlaylistName.trim()
                    if (name.isNotEmpty()) {
                        launchSuspend {
                            val playlistId = onCreatePlaylist(name)
                            onAddToPlaylist(playlistId)
                            onDismiss()
                        }
                    }
                },
            ) {
                Text("Create and add", color = PulseTheme.colors.accentViolet)
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
fun PlaylistDetailDialog(
    playlist: Playlist,
    playlistSongs: List<Song>,
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onSongTap: (List<Song>, Int) -> Unit,
    onAddSong: suspend (Long) -> Unit,
    onRemoveSong: suspend (Long) -> Unit,
    launchSuspend: (suspend () -> Unit) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val playlistSongIds = remember(playlistSongs) { playlistSongs.map { it.id }.toSet() }
    val addableSongs = remember(allSongs, playlistSongIds) { allSongs.filterNot { it.id in playlistSongIds } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(PulseTheme.colors.surface)
                .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlistSongs.size} songs",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                CircleButton(onClick = onDismiss, size = 40.dp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close playlist",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(PulseTheme.colors.accentCream)
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = PulseTheme.colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Add songs",
                    color = PulseTheme.colors.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (playlistSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No songs here yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlistSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(PulseTheme.colors.surfaceElevated)
                                .clickable {
                                    val index = playlistSongs.indexOfFirst { it.id == song.id }
                                    if (index >= 0) onSongTap(playlistSongs, index)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AlbumArt(song = song, cornerRadius = 12.dp, modifier = Modifier.size(46.dp))
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
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PulseTheme.colors.surfaceSoft)
                                    .clickable {
                                        launchSuspend { onRemoveSong(song.id) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove song",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        SongPickerDialog(
            title = "Add songs to ${playlist.name}",
            songs = addableSongs,
            onDismiss = { showPicker = false },
            onAddSong = { song ->
                launchSuspend { onAddSong(song.id) }
            },
        )
    }
}

@Composable
private fun SongPickerDialog(
    title: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onAddSong: (Song) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(28.dp))
                .background(PulseTheme.colors.surface)
                .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CircleButton(onClick = onDismiss, size = 40.dp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close song picker",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Everything in your library is already here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(songs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(PulseTheme.colors.surfaceElevated)
                                .clickable { onAddSong(song) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AlbumArt(song = song, cornerRadius = 12.dp, modifier = Modifier.size(46.dp))
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
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PulseTheme.colors.surfaceSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add song",
                                    tint = PulseTheme.colors.accentViolet,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
