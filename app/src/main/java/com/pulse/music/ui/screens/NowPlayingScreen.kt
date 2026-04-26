package com.pulse.music.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.pulse.music.ui.LibraryViewModel
import com.pulse.music.ui.components.AlbumArt
import com.pulse.music.ui.components.AddToPlaylistDialog
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
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val playlists by libraryVm.playlists.collectAsStateWithLifecycle()
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

    val lyricsResult by produceState<LyricsResult?>(
        initialValue = null,
        key1 = song.id,
        key2 = metadata?.resolvedTitle,
        key3 = metadata?.resolvedArtist,
        key4 = metadata?.resolvedAlbum,
    ) {
        value = null
        value = app.lyricsRepository.lyricsFor(song)
    }

    var lyricsExpanded by remember(song.id) { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember(song.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PulseTheme.colors.surface, PulseTheme.background, PulseTheme.colors.surface),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(PulseTheme.colors.accentViolet.copy(alpha = 0.16f), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
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
                        text = "NOW PLAYING",
                        color = PulseTheme.colors.accentViolet,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = displayAlbum,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.size(42.dp))
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(34.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(34.dp))
                        .padding(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AlbumArt(
                            song = song,
                            cornerRadius = 26.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .aspectRatio(1f),
                        )

                        SingleLineLyric(result = lyricsResult, positionMs = state.positionMs)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                    text = "$displayArtist - $displayAlbum",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(PulseTheme.colors.surfaceSoft)
                                    .clickable { vm.toggleLike() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (song.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (song.liked) "Unlike" else "Like",
                                    tint = if (song.liked) PulseTheme.colors.accentPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        WaveformScrubber(
                            waveSeed = song.id.toInt() xor displayTitle.hashCode(),
                            isPlaying = state.isPlaying,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            onSeek = { vm.seekTo(it) },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PillGroup {
                                PillIconButton(onClick = { vm.previous() }) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "Previous track",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            PlayPill(onClick = { vm.playOrPause() }) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = PulseTheme.colors.onPrimary,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            PillGroup {
                                PillIconButton(onClick = { vm.next() }) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Next track",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BottomAction(
                        modifier = Modifier.weight(1f),
                        label = "Lyrics",
                        icon = Icons.Outlined.Lyrics,
                        active = lyricsExpanded,
                        onClick = { lyricsExpanded = true },
                    )
                    BottomAction(
                        modifier = Modifier.weight(1f),
                        label = "Queue",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        onClick = onOpenQueue,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        BottomAction(
                            modifier = Modifier.fillMaxWidth(),
                            label = "More",
                            icon = Icons.Filled.MoreHoriz,
                            onClick = { overflowOpen = true },
                        )
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.shuffleEnabled) "Shuffle - on" else "Shuffle") },
                                onClick = { vm.toggleShuffle(); overflowOpen = false },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Shuffle,
                                        contentDescription = null,
                                        tint = if (state.shuffleEnabled) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    val label = when (state.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> "Repeat"
                                        Player.REPEAT_MODE_ALL -> "Repeat - all"
                                        Player.REPEAT_MODE_ONE -> "Repeat - one"
                                        else -> "Repeat"
                                    }
                                    Text(label)
                                },
                                onClick = { vm.toggleRepeat(); overflowOpen = false },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                        contentDescription = null,
                                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = {
                                    overflowOpen = false
                                    showAddToPlaylist = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    overflowOpen = false
                                    shareSong(scope, context, song, displayTitle, displayArtist)
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

        if (lyricsExpanded) {
            BackHandler { lyricsExpanded = false }
            LyricsFullScreen(
                songTitle = displayTitle,
                result = lyricsResult,
                positionMs = state.positionMs,
                onClose = { lyricsExpanded = false },
            )
        }

        if (showAddToPlaylist) {
            AddToPlaylistDialog(
                playlists = playlists.filter { it.systemType == null },
                onDismiss = { showAddToPlaylist = false },
                onCreatePlaylist = { name -> libraryVm.createPlaylist(name) },
                onAddToPlaylist = { playlistId -> libraryVm.addSongToPlaylist(playlistId, song.id) },
                launchSuspend = { block -> scope.launch { block() } },
            )
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
        val shareText = if (url != null) "$displayTitle - $displayArtist\n$url" else "$displayTitle - $displayArtist"
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
private fun SingleLineLyric(
    result: LyricsResult?,
    positionMs: Long,
) {
    val currentLine: String? = when (result) {
        is LyricsResult.Found -> {
            if (result.synced) {
                val lines = remember(result.text) { parseLrc(result.text) }
                val idx = remember(positionMs, lines) { activeLyricsIndex(lines, positionMs) }
                lines.getOrNull(idx)?.text?.takeIf { it.isNotBlank() }
            } else {
                remember(result.text) { result.text.lines().firstOrNull { it.isNotBlank() } }
            }
        }

        else -> null
    }

    AnimatedContent(
        targetState = currentLine,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }).togetherWith(fadeOut(tween(200)))
        },
        label = "singleLineLyric",
    ) { line ->
        if (line != null) {
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun LyricsFullScreen(
    songTitle: String,
    result: LyricsResult?,
    positionMs: Long,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseTheme.background.copy(alpha = 0.98f))
            .statusBarsPadding()
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(PulseTheme.colors.surfaceElevated)
                .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(32.dp))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LYRICS",
                        color = PulseTheme.colors.accentViolet,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = songTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleButton(onClick = onClose, size = 42.dp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close lyrics",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when (result) {
                    null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = PulseTheme.colors.accentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Loading lyrics...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    is LyricsResult.Found -> {
                        if (result.synced) {
                            SyncedLyricsBody(lrcText = result.text, positionMs = positionMs)
                        } else {
                            PlainLyricsBody(text = result.text)
                        }
                    }

                    is LyricsResult.NotFound -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "No lyrics found",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Lyrics come from LRCLIB. If this track is missing, you can place a matching .lrc file beside the audio file.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    is LyricsResult.Error -> {
                        Text(
                            text = result.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsBody(
    lrcText: String,
    positionMs: Long,
) {
    val lines = remember(lrcText) { parseLrc(lrcText) }
    if (lines.isEmpty()) {
        Text(
            text = "Lyrics could not be parsed.",
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(lines) { line ->
            val isActive = lines.indexOf(line) == activeIndex
            Text(
                text = line.text,
                color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isActive) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PlainLyricsBody(text: String) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f,
        )
        Spacer(Modifier.height(80.dp))
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
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var trackWidthPx by remember { mutableStateOf(1f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shownProgress = if (dragProgress >= 0f) dragProgress else progress

    val waveColor = PulseTheme.colors.accentViolet
    val pillColor = PulseTheme.colors.accentCream
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
                        onDragStart = { offset -> dragProgress = (offset.x / trackWidthPx).coerceIn(0f, 1f) },
                        onDrag = { change, _ -> dragProgress = (change.position.x / trackWidthPx).coerceIn(0f, 1f) },
                        onDragEnd = {
                            if (dragProgress >= 0f && durationMs > 0) onSeek((dragProgress * durationMs).toLong())
                            dragProgress = -1f
                        },
                        onDragCancel = { dragProgress = -1f },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                            val angle = (normalized * part.cycles * 2f * PI.toFloat()) + (frameSeconds * part.speed * 2.1f) + part.phase
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragProgress >= 0f) (dragProgress * durationMs).toLong() else positionMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatDuration(durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
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
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (active) PulseTheme.colors.surfaceSoft else PulseTheme.colors.surfaceElevated
    val tint = if (active) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun activeLyricsIndex(lines: List<LrcLine>, positionMs: Long): Int {
    var found = -1
    for (index in lines.indices) {
        if (lines[index].timestampMs <= positionMs) found = index else break
    }
    return found.coerceAtLeast(0)
}
