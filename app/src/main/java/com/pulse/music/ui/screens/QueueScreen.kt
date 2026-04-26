package com.pulse.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.theme.PulseTheme

/**
 * The current playback queue. Tapping an item jumps playback to it. The
 * up/down arrows move the item one position; the × removes it (unless it's
 * the currently-playing track). We chose explicit arrow buttons over
 * drag-to-reorder because true reorder gestures in Compose require a third-
 * party library (reorderable) that adds meaningful weight for what is
 * ultimately a secondary surface.
 */
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
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onClick = onBack, size = 38.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Queue",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
            )
            // Spacer to balance the back button
            Box(modifier = Modifier.size(38.dp))
        }

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Queue is empty",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
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
                            cornerRadius = 8.dp,
                            modifier = Modifier.size(48.dp),
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

// items() above is the standard LazyListScope.items(count, itemContent) extension.

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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) PulseTheme.colors.pillSurfaceStrong
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Now-playing indicator
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Now playing",
                    tint = PulseTheme.colors.accentViolet,
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = PulseTheme.colors.textDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        albumContent()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCurrent) PulseTheme.colors.accentViolet
                else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Reorder / remove controls
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconSmall(
                icon = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move up",
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            IconSmall(
                icon = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move down",
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
            IconSmall(
                icon = Icons.Filled.Close,
                contentDescription = "Remove",
                enabled = canRemove,
                onClick = onRemove,
            )
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
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else PulseTheme.colors.textDim.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}
