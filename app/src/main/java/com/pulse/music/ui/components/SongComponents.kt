package com.pulse.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song
import com.pulse.music.data.SongDisplay
import com.pulse.music.data.displayWith
import com.pulse.music.data.SongMetadata
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.util.formatDuration

/**
 * Every song's cached metadata, keyed by song id. Provided once at the app
 * root from a single Room subscription, so rows don't each open their own.
 */
val LocalSongMetadata = compositionLocalOf<Map<Long, SongMetadata>> { emptyMap() }

@Composable
fun Song.display(): SongDisplay = displayWith(LocalSongMetadata.current[id])

fun String.isKnownArtistName(): Boolean =
    isNotBlank() && !equals("Unknown artist", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

fun String.isKnownAlbumName(): Boolean =
    isNotBlank() && !equals("Unknown album", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

/** Song-level actions every list offers. Provided at the app root. */
@Stable
class SongActions(
    val play: (songs: List<Song>, index: Int) -> Unit,
    val openMenu: (song: Song, extras: List<SongMenuItem>) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val toggleLike: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val openAlbum: (Song) -> Unit,
    val openArtist: (Song) -> Unit,
)

val LocalSongActions = staticCompositionLocalOf<SongActions?> { null }

/** The id of the song currently loaded in the player, for "now playing" highlights. */
val LocalCurrentSongId = compositionLocalOf<Long?> { null }

/** A screen-specific entry for the song menu, e.g. "Remove from this playlist". */
@Immutable
data class SongMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The standard song row. Tap plays, long-press or the ⋮ button opens the song menu.
 * [leadingNumber] swaps the artwork for a track number (album pages).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuExtras: List<SongMenuItem> = emptyList(),
    leadingNumber: Int? = null,
    showAlbum: Boolean = true,
) {
    val actions = LocalSongActions.current
    val display = song.display()
    val isCurrent = LocalCurrentSongId.current == song.id
    val subtitle = buildList {
        add(display.artist)
        if (showAlbum && display.album.isKnownAlbumName()) add(display.album)
    }.joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrent) PulseTheme.colors.surfaceSoft else PulseTheme.colors.surfaceElevated)
            .combinedClickable(
                onClick = onClick,
                onLongClick = actions?.let { { it.openMenu(song, menuExtras) } },
                onLongClickLabel = "Song options",
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingNumber != null) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = "Now playing",
                        tint = PulseTheme.colors.accentViolet,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "$leadingNumber",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        } else {
            AlbumArt(song = song, cornerRadius = 12.dp, modifier = Modifier.size(50.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display.title,
                color = if (isCurrent) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
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
        if (song.liked) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint = PulseTheme.colors.accentPink,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (actions != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { actions.openMenu(song, menuExtras) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options for ${display.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Bottom sheet with the standard song actions plus any screen-specific [extras]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    extras: List<SongMenuItem>,
    actions: SongActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val display = song.display()

    fun run(block: () -> Unit) {
        block()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PulseTheme.colors.surface,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlbumArt(song = song, cornerRadius = 12.dp, modifier = Modifier.size(52.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = display.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = display.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetAction("Play next", Icons.AutoMirrored.Filled.PlaylistPlay) { run { actions.playNext(song) } }
            SheetAction("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { run { actions.addToQueue(song) } }
            SheetAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { run { actions.addToPlaylist(song) } }
            SheetAction(
                label = if (song.liked) "Remove from Liked songs" else "Add to Liked songs",
                icon = if (song.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            ) { run { actions.toggleLike(song) } }
            if (display.album.isKnownAlbumName()) {
                SheetAction("Go to album", Icons.Filled.Album) { run { actions.openAlbum(song) } }
            }
            if (display.artist.isKnownArtistName()) {
                SheetAction("Go to artist", Icons.Filled.Person) { run { actions.openArtist(song) } }
            }
            extras.forEach { extra ->
                SheetAction(extra.label, extra.icon) { run(extra.onClick) }
            }
        }
    }
}

/** Convenience for screens that offer "Remove from this playlist". */
fun removeFromPlaylistItem(onRemove: () -> Unit) =
    SongMenuItem("Remove from this playlist", Icons.Filled.RemoveCircleOutline, onRemove)

@Composable
private fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
