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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.data.Song
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseTheme

/**
 * Search across the local library. Matches are substring, case-insensitive,
 * and applied to title, artist, and album fields. Results are grouped by type
 * (Songs / Albums / Artists) so duplicates don't clutter the list.
 *
 * Tapping a song plays that queue starting at the tapped item.
 * Tapping an album or artist plays every matching song.
 */
@Composable
fun SearchScreen(
    onSongTap: (List<Song>, Int) -> Unit,
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    // Filter results — recomputed on each keystroke, which is fine at this
    // scale (local library of a few hundred / thousand songs).
    val results = remember(trimmed, allSongs) {
        if (trimmed.isEmpty()) SearchResults.empty()
        else filterLibrary(allSongs, trimmed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header + text field
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "Search",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
            )
        }

        if (trimmed.isEmpty()) {
            EmptyPrompt(libraryCount = allSongs.size)
        } else if (results.isEmpty()) {
            NoResults(query = trimmed)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = BottomBarContentPadding.calculateBottomPadding(),
                ),
            ) {
                if (results.songs.isNotEmpty()) {
                    item { ResultSectionLabel("Songs · ${results.songs.size}") }
                    items(results.songs.size) { index ->
                        val song = results.songs[index]
                        SongRow(
                            song = song,
                            onClick = { onSongTap(results.songs, index) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                if (results.albums.isNotEmpty()) {
                    item { ResultSectionLabel("Albums · ${results.albums.size}") }
                    items(results.albums) { (albumName, songs) ->
                        AlbumHit(
                            albumName = albumName,
                            artist = songs.firstOrNull()?.artist.orEmpty(),
                            songCount = songs.size,
                            representative = songs.first(),
                            onClick = { onSongTap(songs, 0) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                if (results.artists.isNotEmpty()) {
                    item { ResultSectionLabel("Artists · ${results.artists.size}") }
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

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(PulseTheme.colors.pillSurface)
            .border(1.dp, PulseTheme.colors.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPrompt(libraryCount: Int) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (libraryCount == 0) "Nothing to search yet"
                else "Type to search $libraryCount songs",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (libraryCount == 0) "Add music to the Pulse folder, then rescan."
                else "Search by title, artist, or album.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No matches",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Nothing in your library matches \"$query\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PulseTheme.colors.textDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
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
        AlbumArt(song = song, cornerRadius = 6.dp, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist} · ${song.album}",
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
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(song = representative, cornerRadius = 8.dp, modifier = Modifier.size(48.dp))
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
private fun ArtistHit(
    artist: String,
    songCount: Int,
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink)
                    )
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

// ---------- Filtering ----------

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

/**
 * Non-composable pure function so `remember` can compute results without a
 * composable context. Matches are case-insensitive substring on any of
 * title / artist / album fields.
 */
private fun filterLibrary(all: List<Song>, query: String): SearchResults {
    val q = query.lowercase()

    val matchingSongs = all.filter { s ->
        s.title.lowercase().contains(q) ||
            s.artist.lowercase().contains(q) ||
            s.album.lowercase().contains(q)
    }

    // Distinct albums matching by name, with ALL songs from that album
    // (even if the album name match doesn't happen to match every song's title)
    val matchingAlbumNames = all
        .map { it.album }
        .filter { it.lowercase().contains(q) }
        .distinct()
    val albumGroups = matchingAlbumNames
        .map { name -> name to all.filter { it.album == name } }
        .filter { (_, songs) -> songs.isNotEmpty() }
        .take(10)

    val matchingArtistNames = all
        .map { it.artist }
        .filter { it.lowercase().contains(q) }
        .distinct()
    val artistGroups = matchingArtistNames
        .map { name -> name to all.filter { it.artist == name } }
        .filter { (_, songs) -> songs.isNotEmpty() }
        .take(10)

    return SearchResults(
        songs = matchingSongs.take(30),
        albums = albumGroups,
        artists = artistGroups,
    )
}
