package com.pulse.music.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.pulse.music.PulseApplication
import com.pulse.music.data.Song
import com.pulse.music.data.SongMetadata
import com.pulse.music.lyrics.LrcLine
import com.pulse.music.lyrics.LyricsResult
import com.pulse.music.lyrics.parseLrc
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.CircleButton
import com.pulse.music.ui.components.PillGroup
import com.pulse.music.ui.components.PillIconButton
import com.pulse.music.ui.components.PlayPill
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val song = state.currentSong
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { PulseApplication.get() }

    BackHandler(enabled = true) { onBack() }

    if (song == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val metadata by produceState<SongMetadata?>(initialValue = null, song.id) {
        app.metadataRepository.observe(song.id).collect { value = it }
    }
    LaunchedEffect(song.id) {
        app.metadataRepository.resolve(song)
    }

    val displayTitle = metadata?.resolvedTitle?.takeIf(String::isNotBlank) ?: song.title
    val displayArtist = metadata?.resolvedArtist?.takeIf(String::isNotBlank) ?: song.artist
    val displayAlbum = metadata?.resolvedAlbum?.takeIf(String::isNotBlank) ?: song.album

    var overflowOpen by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var lyricsExpanded by remember(song.id) { mutableStateOf(false) }

    val lyricsResult by produceState<LyricsResult?>(initialValue = null, song.id, lyricsVisible) {
        value = if (lyricsVisible) app.lyricsRepository.lyricsFor(song) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseTheme.background)
            .statusBarsPadding(),
    ) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = displayAlbum,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(modifier = Modifier.size(38.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(6.dp))

            AlbumArt(
                song = song,
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f),
            )

            if (lyricsVisible) {
                Spacer(Modifier.height(14.dp))
                InlineLyricsSection(
                    result = lyricsResult,
                    positionMs = state.positionMs,
                    expanded = lyricsExpanded,
                    onToggleExpanded = { lyricsExpanded = !lyricsExpanded },
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$displayArtist · $displayAlbum",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = if (song.liked) PulseTheme.colors.accentPink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            WaveformScrubber(
                waveSeed = song.id.toInt() xor displayTitle.hashCode(),
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { vm.seekTo(it) },
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 14.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillGroup {
                    PillIconButton(onClick = { vm.previous() }) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                PlayPill(onClick = { vm.playOrPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(28.dp),
                    )
                }

                PillGroup {
                    PillIconButton(onClick = { vm.next() }) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next track",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    18.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomAction(
                    label = if (lyricsVisible) "Hide" else "Lyrics",
                    icon = Icons.Outlined.Lyrics,
                    active = lyricsVisible,
                    onClick = {
                        lyricsVisible = !lyricsVisible
                        if (!lyricsVisible) lyricsExpanded = false
                    },
                )
                BottomAction(
                    label = "Queue",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = onOpenQueue,
                )
                Box {
                    BottomAction(
                        label = "More",
                        icon = Icons.Filled.MoreHoriz,
                        onClick = { overflowOpen = true },
                    )
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (state.shuffleEnabled) "Shuffle · on" else "Shuffle") },
                            onClick = {
                                vm.toggleShuffle()
                                overflowOpen = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    tint = if (state.shuffleEnabled) PulseTheme.colors.accentViolet
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                val label = when (state.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> "Repeat"
                                    Player.REPEAT_MODE_ALL -> "Repeat · all"
                                    Player.REPEAT_MODE_ONE -> "Repeat · one"
                                    else -> "Repeat"
                                }
                                Text(label)
                            },
                            onClick = {
                                vm.toggleRepeat()
                                overflowOpen = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                                        Icons.Filled.RepeatOne
                                    } else {
                                        Icons.Filled.Repeat
                                    },
                                    contentDescription = null,
                                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                                        PulseTheme.colors.accentViolet
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                overflowOpen = false
                                shareSong(
                                    scope = scope,
                                    context = context,
                                    song = song,
                                    displayTitle = displayTitle,
                                    displayArtist = displayArtist,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun shareSong(
    scope: CoroutineScope,
    context: android.content.Context,
    song: Song,
    displayTitle: String,
    displayArtist: String,
) {
    val app = PulseApplication.get()
    scope.launch(Dispatchers.IO) {
        val cached = app.metadataRepository.getCached(song.id)
        val url = cached?.geniusUrl
        val shareText = if (url != null) {
            "$displayTitle - $displayArtist\n$url"
        } else {
            "$displayTitle - $displayArtist"
        }
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        }
    }
}

@Composable
private fun InlineLyricsSection(
    result: LyricsResult?,
    positionMs: Long,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PulseTheme.colors.pillSurfaceStrong.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lyrics",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (result is LyricsResult.Found) {
                Text(
                    text = if (expanded) "Show less" else "View lyrics",
                    color = PulseTheme.colors.accentViolet,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onToggleExpanded),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (val lyrics = result) {
            null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Loading lyrics…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is LyricsResult.Found -> {
                if (lyrics.synced) {
                    if (expanded) {
                        SyncedLyricsBody(
                            lrcText = lyrics.text,
                            positionMs = positionMs,
                            maxHeight = 178.dp,
                        )
                    } else {
                        SyncedLyricsPreview(
                            lrcText = lyrics.text,
                            positionMs = positionMs,
                        )
                    }
                } else {
                    if (expanded) {
                        PlainLyricsBody(text = lyrics.text, maxHeight = 178.dp)
                    } else {
                        PlainLyricsPreview(text = lyrics.text)
                    }
                }
            }

            is LyricsResult.NotFound -> {
                Text(
                    text = "No matching lyrics found for this track.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is LyricsResult.Error -> {
                Text(
                    text = lyrics.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsPreview(
    lrcText: String,
    positionMs: Long,
) {
    val lines = remember(lrcText) { parseLrc(lrcText) }
    if (lines.isEmpty()) {
        Text(
            text = "These lyrics could not be parsed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val activeIndex = remember(positionMs, lines) { activeLyricsIndex(lines, positionMs) }
    val startIndex = (activeIndex - 1).coerceAtLeast(0)
    val endIndex = (activeIndex + 2).coerceAtMost(lines.lastIndex)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (index in startIndex..endIndex) {
            val line = lines[index]
            val isActive = index == activeIndex
            Text(
                text = line.text,
                color = if (isActive) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isActive) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlainLyricsPreview(text: String) {
    val preview = remember(text) {
        text.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(4)
            .joinToString("\n")
    }
    Text(
        text = preview.ifBlank { "No readable lyric lines found." },
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlainLyricsBody(
    text: String,
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(scroll),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WaveformScrubber(
    waveSeed: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    var trackWidthPx by remember { mutableStateOf(1f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shownProgress = if (dragProgress >= 0f) dragProgress else progress

    val waveColor = Color(0xFFF3DAE8)
    val pillColor = Color.White
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    val waveParts = remember(waveSeed) {
        val random = kotlin.random.Random(waveSeed)
        List(6) {
            WavePart(
                cycles = random.nextDouble(0.85, 2.8).toFloat(),
                amplitude = random.nextDouble(0.22, 1.0).toFloat(),
                speed = random.nextDouble(0.55, 1.7).toFloat(),
                phase = random.nextDouble(0.0, PI * 2).toFloat(),
            )
        }
    }

    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.28f,
        animationSpec = tween(durationMillis = 320),
        label = "waveAmplitude",
    )
    val frameSeconds by produceState(initialValue = 0f) {
        while (true) {
            withFrameNanos { value = it / 1_000_000_000f }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { size: IntSize -> trackWidthPx = size.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val fraction = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            onSeek((fraction * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragProgress = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            dragProgress = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragProgress >= 0f && durationMs > 0) {
                                onSeek((dragProgress * durationMs).toLong())
                            }
                            dragProgress = -1f
                        },
                        onDragCancel = { dragProgress = -1f },
                    )
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                val width = size.width
                val centerY = size.height / 2f
                val trackHeight = 4.dp.toPx()
                val pillWidth = 10.dp.toPx()
                val pillHeight = 34.dp.toPx()
                val pillX = (width * shownProgress).coerceIn(pillWidth / 2f, width - pillWidth / 2f)

                val rightTrackStart = pillX + pillWidth / 2f + 2.dp.toPx()
                if (rightTrackStart < width) {
                    drawRoundRect(
                        color = inactiveColor,
                        topLeft = Offset(rightTrackStart, centerY - trackHeight / 2f),
                        size = Size(width - rightTrackStart, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                    )
                }

                val waveEnd = (pillX - pillWidth / 2f - 2.dp.toPx()).coerceAtLeast(0f)
                if (waveEnd > 6f) {
                    val path = Path()
                    val maxAmp = 7.8.dp.toPx() * amplitude
                    val step = 2.5f
                    var x = 0f
                    path.moveTo(0f, centerY)
                    while (x <= waveEnd) {
                        val normalized = x / waveEnd
                        val envelope = 0.52f + 0.48f * sin(normalized * PI.toFloat())
                        val offset = waveParts.sumOf { part ->
                            val angle =
                                (normalized * part.cycles * 2f * PI.toFloat()) +
                                    (frameSeconds * part.speed * 2.1f) +
                                    part.phase
                            (sin(angle) * part.amplitude).toDouble()
                        }.toFloat() / waveParts.size
                        path.lineTo(x, centerY + (offset * maxAmp * envelope))
                        x += step
                    }
                    drawPath(
                        path = path,
                        color = waveColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }

                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(pillX - pillWidth / 2f, centerY - pillHeight / 2f),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(
                    if (dragProgress >= 0f) (dragProgress * durationMs).toLong() else positionMs,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatDuration(durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class WavePart(
    val cycles: Float,
    val amplitude: Float,
    val speed: Float,
    val phase: Float,
)

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (active) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncedLyricsBody(
    lrcText: String,
    positionMs: Long,
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    val lines = remember(lrcText) { parseLrc(lrcText) }
    if (lines.isEmpty()) {
        Text(
            text = "These lyrics could not be parsed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val activeIndex = remember(positionMs, lines) { activeLyricsIndex(lines, positionMs) }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        val target = (activeIndex - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            val isActive = index == activeIndex
            Text(
                text = line.text,
                color = if (isActive) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isActive) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

private fun activeLyricsIndex(lines: List<LrcLine>, positionMs: Long): Int {
    var found = -1
    for (index in lines.indices) {
        if (lines[index].timestampMs <= positionMs) {
            found = index
        } else {
            break
        }
    }
    return found.coerceAtLeast(0)
}
