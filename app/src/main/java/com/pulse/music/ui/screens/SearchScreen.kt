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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseTheme

@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onSongTap: (List<Song>, Int) -> Unit,
) {
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var importQuery by remember { mutableStateOf<String?>(null) }
    val trimmed = query.trim()
    val results = remember(trimmed, allSongs) {
        if (trimmed.isEmpty()) SearchResults.empty() else filterLibrary(allSongs, trimmed)
    }
    val songImportState by vm.songImportState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "Search",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Find a song, artist, or album without noise.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.padding(top = 12.dp))
            SearchField(query = query, onQueryChange = { query = it }, onClear = { query = "" })
        }

        when {
            trimmed.isEmpty() -> EmptyPrompt(libraryCount = allSongs.size)
            results.isEmpty() -> NoResults(
                query = trimmed,
                onImport = { importQuery = trimmed },
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarContentPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (results.songs.isNotEmpty()) {
                        item { ResultSectionLabel("Songs") }
                        items(results.songs.size) { index ->
                            SongRow(song = results.songs[index], onClick = { onSongTap(results.songs, index) })
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { ResultSectionLabel("Albums") }
                        items(results.albums) { (albumName, songs) ->
                            AlbumHit(
                                albumName = albumName,
                                artist = songs.firstOrNull()?.artist.orEmpty(),
                                songCount = songs.size,
                                representative = songs.first(),
                                onClick = { onSongTap(songs, 0) },
                            )
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { ResultSectionLabel("Artists") }
                        items(results.artists) { (artistName, songs) ->
                            ArtistHit(
                                artist = artistName,
                                songCount = songs.size,
                                onClick = { onSongTap(songs, 0) },
                            )
                        }
                    }
                }
            }
        }
    }

    val activeImportQuery = importQuery
    if (activeImportQuery != null) {
        ImportSongDialog(
            state = songImportState,
            onDismiss = {
                importQuery = null
                vm.resetSongImportState()
            },
            onSearch = vm::searchImportCandidates,
            onImport = vm::importCandidate,
            initialQuery = activeImportQuery,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(PulseTheme.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = PulseTheme.colors.accentViolet,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Songs, artists, albums",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PulseTheme.colors.surfaceSoft)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPrompt(libraryCount: Int) {
    CenterState(
        title = if (libraryCount == 0) "Nothing to search yet" else "Start typing",
        subtitle = if (libraryCount == 0) {
            "Add music to the Pulse folder, then rescan."
        } else {
            "Search across $libraryCount songs by title, artist, or album."
        },
    )
}

@Composable
private fun NoResults(
    query: String,
    onImport: () -> Unit,
) {
    CenterState(
        title = "No matches",
        subtitle = "Nothing in your library matches \"$query\".",
        actionLabel = "Download this song",
        actionIcon = Icons.Filled.Download,
        onAction = onImport,
        secondaryText = "Search YouTube Music and import it into Pulse instead.",
    )
}

@Composable
private fun CenterState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onAction: (() -> Unit)? = null,
    secondaryText: String? = null,
) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(PulseTheme.colors.surfaceElevated)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (!secondaryText.isNullOrBlank()) {
                Text(
                    text = secondaryText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && actionIcon != null && onAction != null) {
                SearchImportButton(
                    label = actionLabel,
                    icon = actionIcon,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
private fun SearchImportButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PulseTheme.colors.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = PulseTheme.colors.onPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ResultSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PulseTheme.colors.accentViolet,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
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
            Text(text = song.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "${song.artist} - ${song.album}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumHit(
    albumName: String,
    artist: String,
    songCount: Int,
    representative: Song,
    onClick: () -> Unit,
) {
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
        AlbumArt(song = representative, cornerRadius = 12.dp, modifier = Modifier.size(50.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = albumName, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$artist - $songCount songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ArtistHit(artist: String, songCount: Int, onClick: () -> Unit) {
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
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artist.firstOrNull()?.uppercase() ?: "?",
                color = PulseTheme.colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = artist, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$songCount songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class SearchResults(
    val songs: List<Song>,
    val albums: List<Pair<String, List<Song>>>,
    val artists: List<Pair<String, List<Song>>>,
) {
    fun isEmpty(): Boolean = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        fun empty() = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

private fun filterLibrary(all: List<Song>, query: String): SearchResults {
    val q = query.lowercase()
    val matchingSongs = all.filter { s ->
        s.title.lowercase().contains(q) ||
            s.artist.lowercase().contains(q) ||
            s.album.lowercase().contains(q)
    }

    val matchingAlbumNames = all.map { it.album }.filter { it.lowercase().contains(q) }.distinct()
    val albumGroups = matchingAlbumNames.map { name -> name to all.filter { it.album == name } }.filter { it.second.isNotEmpty() }.take(10)

    val matchingArtistNames = all.map { it.artist }.filter { it.lowercase().contains(q) }.distinct()
    val artistGroups = matchingArtistNames.map { name -> name to all.filter { it.artist == name } }.filter { it.second.isNotEmpty() }.take(10)

    return SearchResults(
        songs = matchingSongs.take(30),
        albums = albumGroups,
        artists = artistGroups,
    )
}
