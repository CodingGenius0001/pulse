package com.pulse.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.PillGroup
import com.pulse.music.ui.components.PillIconButton
import com.pulse.music.ui.components.PlayPill
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.formatDuration

@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val song = state.currentSong

    // System back dismisses the now-playing overlay rather than closing the app
    BackHandler(enabled = true) { onBack() }

    if (song == null) {
        // Nothing playing — just bounce out
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Canvas)
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
                    tint = PulseColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM",
                    color = PulseColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = song.album,
                    color = PulseColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            CircleButton(onClick = { /* TODO overflow */ }, size = 38.dp) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = PulseColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            // Album art
            AlbumArt(
                song = song,
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(28.dp))

            // Title + like
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = PulseColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${song.artist} · ${song.album}",
                        color = PulseColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { vm.toggleLike() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (song.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (song.liked) "Unlike" else "Like",
                        tint = if (song.liked) PulseColors.AccentPink else PulseColors.TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress
            ProgressBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { vm.seekTo(it) },
            )

            Spacer(Modifier.height(20.dp))

            // Transport: 3 pill groups — prev/next | PLAY | shuffle/repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left pill: prev/next
                PillGroup {
                    PillIconButton(onClick = { vm.previous() }) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = PulseColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    PillIconButton(onClick = { vm.next() }) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = PulseColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Center: wide play pill
                PlayPill(onClick = { vm.playOrPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = PulseColors.Canvas,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Right pill: shuffle/repeat
                PillGroup {
                    PillIconButton(onClick = { vm.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleEnabled) PulseColors.AccentViolet else PulseColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    PillIconButton(onClick = { vm.toggleRepeat() }) {
                        val repeatIcon = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        }
                        val repeatTint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                            PulseColors.AccentViolet
                        } else {
                            PulseColors.TextMuted
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Repeat",
                            tint = repeatTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom subtle actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    24.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomAction("Lyrics")
                DotSeparator()
                BottomAction("Queue")
                DotSeparator()
                BottomAction("Output")
            }
        }
    }
}

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        // Using Material Slider but restyled to match the thin-bar + vertical-pill look.
        // We override track + thumb via custom rendering using Compose primitives isn't
        // straightforward, so we use Slider with matching colors.
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { new ->
                if (durationMs > 0) onSeek((new * durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = PulseColors.TextPrimary,
                activeTrackColor = PulseColors.TextPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionMs),
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatDuration(durationMs),
                color = PulseColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BottomAction(label: String) {
    Text(
        text = label,
        color = PulseColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun DotSeparator() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(PulseColors.TextDim),
    )
}
