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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pulse.music.data.SongMetadata
import com.pulse.music.ui.AlbumEntry
import com.pulse.music.ui.ArtistEntry
import com.pulse.music.ui.LibraryIndex
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.LocalSongMetadata
import com.pulse.music.ui.components.SongListRow
import com.pulse.music.data.displayWith
import com.pulse.music.util.songCountLabel
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.BottomBarContentPadding
import com.pulse.music.ui.theme.PulseTheme

@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onSongTap: (List<Song>, Int) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val index by vm.libraryIndex.collectAsStateWithLifecycle()
    val recentSearches by vm.recentLibrarySearches.collectAsStateWithLifecycle()
    val metadata = LocalSongMetadata.current
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val results = remember(trimmed, allSongs, index, metadata) {
        if (trimmed.isEmpty()) SearchResults.empty() else filterLibrary(allSongs, index, metadata, trimmed)
    }

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
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                onSubmit = { vm.recordLibrarySearch(trimmed) },
            )
        }

        when {
            trimmed.isEmpty() -> EmptyPrompt(
                libraryCount = allSongs.size,
                recentSearches = recentSearches,
                onRecentSearchTap = { query = it },
            )
            results.isEmpty() -> NoResults(query = trimmed)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarContentPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (results.songs.isNotEmpty()) {
                        item { ResultSectionLabel("Songs") }
                        itemsIndexed(results.songs, key = { _, song -> "song_${song.id}" }) { position, song ->
                            SongListRow(song = song, onClick = {
                                vm.recordLibrarySearch(trimmed)
                                onSongTap(results.songs, position)
                            })
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { ResultSectionLabel("Albums") }
                        items(results.albums, key = { "album_${it.key}" }) { album ->
                            AlbumHit(
                                album = album,
                                onClick = {
                                    vm.recordLibrarySearch(trimmed)
                                    onOpenCollection(Routes.album(album.key))
                                },
                            )
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { ResultSectionLabel("Artists") }
                        items(results.artists, key = { "artist_${it.key}" }) { artist ->
                            ArtistHit(
                                artist = artist.name,
                                songCount = artist.songs.size,
                                onClick = {
                                    vm.recordLibrarySearch(trimmed)
                                    onOpenCollection(Routes.artist(artist.key))
                                },
                            )
                        }
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
    onSubmit: () -> Unit,
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
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
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
private fun EmptyPrompt(
    libraryCount: Int,
    recentSearches: List<String>,
    onRecentSearchTap: (String) -> Unit,
) {
    CenterState(
        title = if (libraryCount == 0) "Nothing to search yet" else "Start typing",
        subtitle = if (libraryCount == 0) {
            "Add music to the Pulse folder, then rescan."
        } else {
            "Search across $libraryCount songs by title, artist, or album."
        },
        recentSearches = recentSearches,
        onRecentSearchTap = onRecentSearchTap,
    )
}

@Composable
private fun NoResults(query: String) {
    CenterState(
        title = "No matches",
        subtitle = "Nothing in your library matches \"$query\".",
    )
}

@Composable
private fun CenterState(
    title: String,
    subtitle: String,
    recentSearches: List<String> = emptyList(),
    onRecentSearchTap: ((String) -> Unit)? = null,
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
            if (recentSearches.isNotEmpty() && onRecentSearchTap != null) {
                Text(
                    text = "Recent searches",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                RecentSearchList(
                    searches = recentSearches,
                    onTap = onRecentSearchTap,
                )
            }
        }
    }
}

@Composable
private fun RecentSearchList(
    searches: List<String>,
    onTap: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        searches.forEach { search ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PulseTheme.colors.surfaceSoft)
                    .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(20.dp))
                    .clickable { onTap(search) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = PulseTheme.colors.accentViolet,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = search,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
private fun AlbumHit(
    album: AlbumEntry,
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
        AlbumArt(song = album.coverSong, cornerRadius = 12.dp, modifier = Modifier.size(50.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = album.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "Album · ${album.artist}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(text = "Artist · ${songCountLabel(songCount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class SearchResults(
    val songs: List<Song>,
    val albums: List<AlbumEntry>,
    val artists: List<ArtistEntry>,
) {
    fun isEmpty(): Boolean = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        fun empty() = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

private fun filterLibrary(
    all: List<Song>,
    index: LibraryIndex,
    metadata: Map<Long, SongMetadata>,
    query: String,
): SearchResults {
    val q = query.lowercase()
    val matchingSongs = all
        .map { it to it.displayWith(metadata[it.id]) }
        .filter { (_, d) ->
            d.title.lowercase().contains(q) ||
                d.artist.lowercase().contains(q) ||
                d.album.lowercase().contains(q)
        }
        // Title matches first, then artist/album matches.
        .sortedBy { (_, d) -> if (d.title.lowercase().contains(q)) 0 else 1 }
        .map { it.first }

    return SearchResults(
        songs = matchingSongs.take(40),
        albums = index.albums.filter { it.title.lowercase().contains(q) }.take(10),
        artists = index.artists.filter { it.name.lowercase().contains(q) }.take(10),
    )
}
