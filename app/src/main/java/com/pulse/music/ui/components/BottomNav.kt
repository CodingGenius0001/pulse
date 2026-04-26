package com.pulse.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song
import com.pulse.music.ui.theme.PulseTheme

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

@Composable
fun PulseBottomNav(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(PulseTheme.colors.surface)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (selected) PulseTheme.colors.surfaceElevated else Color.Transparent,
                    )
                    .clickable { onNavigate(destination) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    listOf(PulseTheme.colors.accentViolet, PulseTheme.colors.accentPink),
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent),
                                )
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = if (selected) PulseTheme.colors.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = destination.label,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward10: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(24.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(
            song = song,
            cornerRadius = 12.dp,
            modifier = Modifier.size(48.dp),
        )
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
        MiniPlayerAction(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            emphasized = true,
            onClick = onPlayPause,
        )
        MiniPlayerAction(
            icon = Icons.Filled.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = onSkipForward10,
        )
        MiniPlayerAction(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next track",
            onClick = onNext,
        )
    }
}

@Composable
private fun MiniPlayerAction(
    icon: ImageVector,
    contentDescription: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (emphasized) 40.dp else 36.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) PulseTheme.colors.accentCream else PulseTheme.colors.surfaceSoft,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (emphasized) PulseTheme.colors.onPrimary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun BottomNavContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color.Transparent, PulseTheme.colors.canvas.copy(alpha = 0.9f)),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PulseTheme.colors.line),
        )
        content()
    }
}

val BottomBarContentPadding = PaddingValues(bottom = 176.dp)
