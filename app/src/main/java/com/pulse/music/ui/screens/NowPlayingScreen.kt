package com.pulse.music.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.pulse.music.PulseApplication
import com.pulse.music.data.Song
import com.pulse.music.lyrics.LyricsResult
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

    BackHandler(enabled = true) { onBack() }

    if (song == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseTheme.background)
            .statusBarsPadding(),
    ) {
        // Top bar — back arrow + song context, no more overflow button
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
                    text = song.album,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Spacer so the title stays perfectly centered
            Box(modifier = Modifier.size(38.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            AlbumArt(
                song = song,
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(24.dp))

            // Title + like
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${song.artist} · ${song.album}",
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

            PillScrubber(
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { vm.seekTo(it) },
            )

            Spacer(Modifier.height(24.dp))

            // Transport: prev / PLAY / next — clean and symmetric
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

            // Bottom action row — Lyrics · Queue · More (dropdown for shuffle/repeat/share)
            // The overflow button is now in thumb reach at the bottom instead of the
            // top-right corner. Pulling its companion actions (shuffle/repeat/share)
            // down with it keeps everything ergonomic.
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
                    label = "Lyrics",
                    icon = Icons.Outlined.Lyrics,
                    onClick = { lyricsOpen = true },
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
                            text = {
                                Text(
                                    if (state.shuffleEnabled) "Shuffle · on" else "Shuffle",
                                )
                            },
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
                                val icon = when (state.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                                    else -> Icons.Filled.Repeat
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) PulseTheme.colors.accentViolet
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                overflowOpen = false
                                shareSong(scope, context, song)
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

    if (lyricsOpen) {
        LyricsDialog(
            song = song,
            positionMs = state.positionMs,
            onDismiss = { lyricsOpen = false },
        )
    }
}

/**
 * Shares the current song via the Android system share sheet. If we have a
 * cached Genius URL for the song, we share that so the recipient gets an
 * actual working link. Otherwise we fall back to just the title + artist.
 *
 * The dialog action is launched from the current composition scope, keeping
 * the coroutine tied to the visible Now Playing surface.
 * The disk read is a single primary-key Room lookup.
 */
private fun shareSong(scope: CoroutineScope, context: android.content.Context, song: Song) {
    val app = PulseApplication.get()
    scope.launch(Dispatchers.IO) {
        val cached = app.metadataRepository.getCached(song.id)
        val url = cached?.geniusUrl
        val shareText = if (url != null) {
            "${song.title} — ${song.artist}\n$url"
        } else {
            "${song.title} — ${song.artist}"
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
private fun PillScrubber(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var trackWidthPx by remember { mutableStateOf(1f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shownProgress = if (dragProgress >= 0f) dragProgress else progress

    val activeColor = if (isPlaying) PulseTheme.colors.accentPink else MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { size: IntSize -> trackWidthPx = size.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            onSeek((frac * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset: Offset ->
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
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f
                val trackHeight = 4.dp.toPx()
                val thumbWidth = 7.dp.toPx()
                val thumbHeight = 32.dp.toPx()
                val thumbX = (w * shownProgress).coerceIn(thumbWidth / 2f, w - thumbWidth / 2f)
                val trackRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)

                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(w, trackHeight),
                    cornerRadius = trackRadius,
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                    cornerRadius = trackRadius,
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
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
                    if (dragProgress >= 0f) (dragProgress * durationMs).toLong() else positionMs
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

/**
 * Combined wave + scrubber. Continuous flowing wave on the played side,
 * thin track on the unplayed side, circular knob at the playhead.
 *
 *   ┌─────────────────── 48dp tall drag area ───────────────────────────┐
 *   │   ╭╮╭─╮╭──╮╭──╮╭╮╭─╮╭   ●   ──────────────────────────────────────  │
 *   │   continuous wavy curve  knob       thin muted track (unplayed)    │
 *   └────────────────────────────────────────────────────────────────────┘
 *      0:18                                                         2:56
 *
 * The wave is drawn as a smooth Path stroked along the played portion.
 * Y values come from summing two sine waves of different frequencies and
 * phases — gives the curve organic-looking variation without the rigidity
 * of a single sine, so peaks and troughs differ in height and spacing.
 *
 * When playback pauses, amplitude animates from 1 → 0 over 600ms. The
 * shape stays sampled (the path is still drawn) so it gracefully flattens
 * rather than abruptly disappearing. Same on resume in reverse.
 */
@Composable
private fun WavyScrubber(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var trackWidthPx by remember { mutableStateOf(1f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shownProgress = if (dragProgress >= 0f) dragProgress else progress

    // Phase animation — drives the leftward scroll while playing.
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    // Amplitude — animates between 0 (paused, flat) and 1 (playing, full).
    val amplitudeTarget = if (isPlaying) 1f else 0f
    val amplitude by animateFloatAsState(
        targetValue = amplitudeTarget,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "amplitude",
    )

    val activeColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { size: IntSize -> trackWidthPx = size.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            onSeek((frac * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset: Offset ->
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
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                val w = size.width
                val h = size.height
                val midY = h / 2
                val splitX = w * shownProgress.coerceIn(0f, 1f)

                // Wave geometry
                val maxAmplitudePx = 7f.dp.toPx()         // peak height at full amplitude
                val knobRadiusPx = 7f.dp.toPx()           // round knob, replaces the old vertical pill
                val mutedStrokeWidth = 2f.dp.toPx()
                val activeStrokeWidth = 2.5f.dp.toPx()

                // The wave curve, sampled left → right in 2px steps.
                // We sum two sines of incommensurate frequencies to break the
                // periodicity — the result looks like an organic waveform.
                if (splitX > knobRadiusPx) {
                    val curvePath = androidx.compose.ui.graphics.Path()
                    val step = 2f
                    var x = 0f
                    val pathEnd = splitX - knobRadiusPx  // stop before the knob to keep it visually clean

                    val freq1 = 6f * kotlin.math.PI.toFloat() / w
                    val freq2 = 13f * kotlin.math.PI.toFloat() / w

                    curvePath.moveTo(0f, midY)
                    while (x <= pathEnd) {
                        // Two sines, summed and normalised. Phase scrolls the
                        // shape leftward over time.
                        val s1 = kotlin.math.sin(x * freq1 + phase)
                        val s2 = kotlin.math.sin(x * freq2 - phase * 0.6f) * 0.5f
                        val y = midY + (s1 + s2) * 0.7f * maxAmplitudePx * amplitude
                        curvePath.lineTo(x, y)
                        x += step
                    }
                    drawPath(
                        path = curvePath,
                        color = activeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = activeStrokeWidth,
                            cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                        ),
                    )
                }

                // Thin muted track on the unplayed side — starts after the knob.
                val unplayedStart = splitX + knobRadiusPx
                if (unplayedStart < w) {
                    drawLine(
                        color = mutedColor,
                        start = Offset(unplayedStart, midY),
                        end = Offset(w, midY),
                        strokeWidth = mutedStrokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                // Circular knob at the playhead.
                drawCircle(
                    color = activeColor,
                    radius = knobRadiusPx,
                    center = Offset(splitX, midY),
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
                    if (dragProgress >= 0f) (dragProgress * durationMs).toLong() else positionMs
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

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Lyrics dialog. Fetches synced or plain lyrics from the LyricsRepository
 * (LRCLIB-backed, cached in Room). When a synced version is available, the
 * current line is highlighted based on playback position.
 *
 * Synced lyrics are stored in LRC format (`[mm:ss.ff] line text`). We parse
 * once on first render, then the current-line lookup is a binary-search-
 * grade operation against [positionMs], which is cheap enough to do every
 * 500ms tick.
 */
@Composable
private fun LyricsDialog(
    song: Song,
    positionMs: Long,
    onDismiss: () -> Unit,
) {
    val repo = remember { PulseApplication.get().lyricsRepository }

    val result by produceState<LyricsResult?>(initialValue = null, song.id) {
        value = repo.lyricsFor(song)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                when (val r = result) {
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
                                text = "Looking for lyrics…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    is LyricsResult.Found -> {
                        if (r.synced) {
                            SyncedLyricsBody(lrcText = r.text, positionMs = positionMs)
                        } else {
                            // Plain text — scrollable column, no highlighting
                            val scroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(scroll),
                            ) {
                                Text(
                                    text = r.text,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    is LyricsResult.NotFound -> {
                        Text(
                            text = "We didn't find lyrics for this one :(",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Lyrics come from LRCLIB — a free community database. If this track isn't there yet, you can drop a matching .lrc file next to the audio file in your PulseApp folder (feature coming soon).",
                            color = PulseTheme.colors.textDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    is LyricsResult.Error -> {
                        Text(
                            text = r.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onBackground)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * Renders synced LRC lyrics with the line corresponding to [positionMs]
 * highlighted. Auto-scrolls so the current line stays near the center
 * of the visible area.
 */
@Composable
private fun SyncedLyricsBody(lrcText: String, positionMs: Long) {
    val lines = remember(lrcText) { com.pulse.music.lyrics.parseLrc(lrcText) }
    if (lines.isEmpty()) {
        Text(
            text = "We have lyrics for this song but they couldn't be parsed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    // Find the active line — the last line whose timestamp has passed.
    val activeIndex = remember(positionMs, lines) {
        var found = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= positionMs) found = i
            else break
        }
        found.coerceAtLeast(0)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        // Keep the active line roughly centered. Offset by a few items so
        // the user sees upcoming lyrics, not just what's already been sung.
        val target = (activeIndex - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lines.size) { i ->
            val line = lines[i]
            val isActive = i == activeIndex
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
