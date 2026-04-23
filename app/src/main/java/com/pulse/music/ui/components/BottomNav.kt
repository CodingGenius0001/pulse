package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song
import com.pulse.music.ui.theme.PulseColors

/**
 * The four bottom-nav destinations.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    ForYou("for_you", "For you", Icons.Outlined.Home),
    Library("library", "Library", Icons.Outlined.LibraryMusic),
    Search("search", "Search", Icons.Outlined.Search),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

/**
 * Custom bottom nav. Material3's NavigationBar uses indicators and elevation
 * that don't match the aesthetic — so we render our own pill-style row.
 */
@Composable
fun PulseBottomNav(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) PulseColors.PillSurfaceStrong else Color.Transparent)
                    .clickable { onNavigate(destination) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = if (selected) PulseColors.TextPrimary else PulseColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = destination.label,
                    color = if (selected) PulseColors.TextPrimary else PulseColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * The compact now-playing strip that sits above the tab bar.
 * Tapping the body expands to the full Now Playing screen.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PulseColors.PillSurfaceStrong)
            .clickable(onClick = onTap)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(
            song = song,
            cornerRadius = 8.dp,
            modifier = Modifier.size(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = PulseColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = PulseColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = PulseColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Tab bar container with a subtle top border.
 */
@Composable
fun BottomNavContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PulseColors.Line),
        )
        content()
    }
}

/** Height reserved at the bottom of scroll content so the tab bar doesn't cover items. */
val BottomBarContentPadding = PaddingValues(bottom = 160.dp)
