package com.pulse.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.PulseApplication
import com.pulse.music.data.Song
import com.pulse.music.data.SongMetadata
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.theme.PulseTheme

@Composable
fun QueueScreen(
    onBack: () -> Unit,
) {
    val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseTheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onClick = onBack, size = 42.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "QUEUE",
                    color = PulseTheme.colors.accentViolet,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "${state.queue.size} tracks",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(modifier = Modifier.size(42.dp))
        }

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(30.dp))
                    .background(PulseTheme.colors.surfaceElevated)
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Queue is empty",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return
        }

        val currentSong = state.currentSong
        val currentMetadata by produceState<SongMetadata?>(initialValue = null, currentSong?.id) {
            if (currentSong == null) {
                value = null
            } else {
                PulseApplication.get().metadataRepository.observe(currentSong.id).collect { value = it }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentSong != null) {
                item {
                    QueueReturnCard(
                        song = currentSong,
                        title = currentMetadata?.resolvedTitle?.takeIf(String::isNotBlank) ?: currentSong.title,
                        artist = currentMetadata?.resolvedArtist?.takeIf(String::isNotBlank) ?: currentSong.artist,
                        onClick = onBack,
                    )
                }
            }

            items(state.queue.size) { index ->
                val song = state.queue[index]
                val isCurrent = index == state.currentIndex
                QueueRow(
                    index = index,
                    title = song.title,
                    artist = song.artist,
                    isCurrent = isCurrent,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.queue.size - 1,
                    canRemove = !isCurrent,
                    albumContent = {
                        AlbumArt(
                            song = song,
                            cornerRadius = 14.dp,
                            modifier = Modifier.size(54.dp),
                        )
                    },
                    onTap = { vm.jumpToQueueIndex(index) },
                    onMoveUp = { vm.moveQueueItem(index, index - 1) },
                    onMoveDown = { vm.moveQueueItem(index, index + 1) },
                    onRemove = { vm.removeFromQueue(index) },
                )
            }
        }
    }
}

@Composable
private fun QueueReturnCard(
    song: Song,
    title: String,
    artist: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song = song, cornerRadius = 14.dp, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Return to now playing",
                color = PulseTheme.colors.accentViolet,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(PulseTheme.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = "Return to now playing",
                tint = PulseTheme.colors.accentViolet,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    title: String,
    artist: String,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    albumContent: @Composable () -> Unit,
    onTap: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (isCurrent) PulseTheme.colors.surfaceSoft else PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(22.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCurrent) PulseTheme.colors.accentCream else PulseTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Now playing",
                    tint = PulseTheme.colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        albumContent()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconSmall(icon = Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", enabled = canMoveUp, onClick = onMoveUp)
            IconSmall(icon = Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", enabled = canMoveDown, onClick = onMoveDown)
            IconSmall(icon = Icons.Filled.Close, contentDescription = "Remove", enabled = canRemove, onClick = onRemove)
        }
    }
}

@Composable
private fun IconSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(PulseTheme.colors.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else PulseTheme.colors.textDim.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}
