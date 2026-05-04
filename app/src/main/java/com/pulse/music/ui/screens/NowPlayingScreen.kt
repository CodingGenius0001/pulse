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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.pulse.music.network.LrcLibApi
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
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
        song.id,
        metadata?.resolvedTitle,
        metadata?.resolvedArtist,
        metadata?.resolvedAlbum,
    ) {
        value = null
        value = app.lyricsRepository.lyricsFor(song)
    }
    val inlineLyric = remember(lyricsResult, state.positionMs) {
        extractInlineLyric(lyricsResult, state.positionMs)
    }

    var lyricsExpanded by remember(song.id) { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember(song.id) { mutableStateOf(false) }
    var showFixMatch by remember(song.id) { mutableStateOf(false) }
    var matchRefreshState by remember(song.id) { mutableStateOf<MatchRefreshState?>(null) }
    var matchRefreshJob by remember(song.id) { mutableStateOf<Job?>(null) }

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(34.dp))
                            .background(PulseTheme.colors.surfaceElevated)
                            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(34.dp))
                            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f),
                            ) {
                                AlbumArt(
                                    song = song,
                                    cornerRadius = 26.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            SingleLineLyric(line = inlineLyric)

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

                    CircleButton(
                        onClick = onBack,
                        size = 42.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 10.dp, y = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(PulseTheme.colors.surfaceElevated)
                        .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(28.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
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
                                text = { Text("Fix metadata / lyrics") },
                                onClick = {
                                    overflowOpen = false
                                    showFixMatch = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (lyricsExpanded) {
            BackHandler { lyricsExpanded = false }
            LyricsFullScreen(
                songTitle = displayTitle,
                result = lyricsResult,
                positionMs = state.positionMs,
                isPlaying = state.isPlaying,
                onPlayPause = { vm.playOrPause() },
                onPrevious = { vm.previous() },
                onNext = { vm.next() },
                onSeekTo = { vm.seekTo(it) },
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

        if (showFixMatch) {
            FixMatchDialog(
                initialTitle = displayTitle,
                initialArtist = displayArtist,
                initialAlbum = displayAlbum,
                onDismiss = { showFixMatch = false },
                onSave = { title, artist, album ->
                    showFixMatch = false
                    matchRefreshState = MatchRefreshState.Loading
                    matchRefreshJob = scope.launch {
                        try {
                            val refreshedMetadata = app.metadataRepository.correctMatch(song, title, artist, album)
                            val refreshedLyrics = app.lyricsRepository.refresh(song)
                            if (!isActive) return@launch
                            val message = when {
                                !refreshedMetadata.artworkUrl.isNullOrBlank() ->
                                    "Matched the song and refreshed artwork${lyricsSuffix(refreshedLyrics)}."
                                else ->
                                    "Saved the correction, but Pulse still could not confirm artwork for that match${lyricsSuffix(refreshedLyrics)}."
                            }
                            matchRefreshState = MatchRefreshState.Success(message)
                        } catch (_: java.util.concurrent.CancellationException) {
                            // User cancelled the in-flight refresh dialog.
                        } catch (error: Exception) {
                            if (!isActive) return@launch
                            matchRefreshState = MatchRefreshState.Error(
                                error.message ?: "Couldn't refresh metadata and lyrics right now.",
                            )
                        } finally {
                            matchRefreshJob = null
                        }
                    }
                },
                onSearch = { title, artist, album ->
                    app.lyricsRepository.searchCandidates(
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = song.durationMs / 1000,
                    )
                },
            )
        }

        when (val refresh = matchRefreshState) {
            MatchRefreshState.Loading -> MatchRefreshLoadingDialog(
                onCancel = {
                    matchRefreshJob?.cancel()
                    matchRefreshJob = null
                    matchRefreshState = null
                },
            )
            is MatchRefreshState.Success -> MatchRefreshMessageDialog(
                title = "Song updated",
                message = refresh.message,
                onDismiss = { matchRefreshState = null },
            )
            is MatchRefreshState.Error -> MatchRefreshMessageDialog(
                title = "Update failed",
                message = refresh.message,
                onDismiss = { matchRefreshState = null },
            )
            null -> Unit
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
    line: String?,
) {
    if (line == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }).togetherWith(fadeOut(tween(200)))
            },
            label = "singleLineLyric",
        ) { currentLine ->
            Text(
                text = currentLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LyricsFullScreen(
    songTitle: String,
    result: LyricsResult?,
    positionMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
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
                        maxLines = 2,
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

            Box(modifier = Modifier.weight(1f)) {
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
                            SyncedLyricsBody(
                                lrcText = result.text,
                                positionMs = positionMs,
                                onSeekTo = onSeekTo,
                            )
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

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(PulseTheme.colors.surfaceSoft)
                    .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillGroup {
                        PillIconButton(onClick = onPrevious) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous track",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    PlayPill(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = PulseTheme.colors.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    PillGroup {
                        PillIconButton(onClick = onNext) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next track",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp),
                            )
                        }
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
    onSeekTo: (Long) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                color = if (isActive) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isActive) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = if (isActive) 10.dp else 2.dp),
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
            style = MaterialTheme.typography.titleLarge,
            lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.42f,
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
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)

    val waveParts = remember(waveSeed) {
        val random = kotlin.random.Random(waveSeed)
        List(4) {
            WavePart(
                cycles = random.nextDouble(2.2, 4.6).toFloat(),
                amplitude = random.nextDouble(1.0, 1.85).toFloat(),
                speed = random.nextDouble(0.18, 0.34).toFloat(),
                phase = random.nextDouble(0.0, PI * 2).toFloat(),
            )
        }
    }

    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 460),
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
                val pillWidth = 10.dp.toPx()
                val pillHeight = 34.dp.toPx()
                val tailPadding = 4.dp.toPx()
                val crestSpan = 196.dp.toPx()
                val pillX = (width * shownProgress).coerceIn(pillWidth / 2f, width - pillWidth / 2f)
                val waveInset = 4.dp.toPx()
                val maxAmp = 11.8.dp.toPx() * amplitude
                val step = 1.15f
                val travel = frameSeconds * 9.4f
                val path = Path()
                var hasPoint = false
                val activeWaveEnd = (pillX - pillWidth / 2f - tailPadding).coerceAtLeast(waveInset)
                val inactiveTrackStart = (pillX + pillWidth / 2f + tailPadding).coerceAtMost(width)

                if (!isPlaying) {
                    if (activeWaveEnd > waveInset) {
                        drawLine(
                            color = waveColor,
                            start = Offset(waveInset, centerY),
                            end = Offset(activeWaveEnd, centerY),
                            strokeWidth = 3.3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                } else {
                    fun amplitudeAt(x: Float): Float {
                        val distanceFromPill = abs(pillX - x)
                        val nearPill = (1f - (distanceFromPill / crestSpan).coerceIn(0f, 1f))
                        val crestBoost = 0.88f + 0.22f * sin(nearPill * PI.toFloat() / 2f)
                        return crestBoost
                    }

                    var x = waveInset
                    while (x <= activeWaveEnd) {
                        val sourceX = (x * 0.92f) - travel
                        val offset = waveParts.sumOf { part ->
                            val wavelength = (98f / part.cycles).coerceAtLeast(28f)
                            val angle = ((sourceX / wavelength) * 2f * PI.toFloat() * (0.82f + (part.speed * 0.48f))) + part.phase
                            (sin(angle) * part.amplitude).toDouble()
                        }.toFloat() / waveParts.size
                        val y = centerY + (offset * maxAmp * amplitudeAt(x))
                        if (!hasPoint) {
                            path.moveTo(x, y)
                            hasPoint = true
                        } else {
                            path.lineTo(x, y)
                        }
                        x += step
                    }

                    if (hasPoint) {
                        drawPath(
                            path = path,
                            color = waveColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }

                if (inactiveTrackStart < width - waveInset) {
                    drawLine(
                        color = inactiveTrackColor,
                        start = Offset(inactiveTrackStart, centerY),
                        end = Offset(width - waveInset, centerY),
                        strokeWidth = 3.3.dp.toPx(),
                        cap = StrokeCap.Round,
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

private fun lyricsSuffix(result: LyricsResult): String = when (result) {
    is LyricsResult.Found -> " and lyrics"
    LyricsResult.NotFound -> ", but lyrics were still not found"
    is LyricsResult.Error -> ", but lyrics could not be refreshed"
}

private fun extractInlineLyric(
    result: LyricsResult?,
    positionMs: Long,
): String? = when (result) {
    is LyricsResult.Found -> {
        if (result.synced) {
            val lines = parseLrc(result.text)
            val idx = activeLyricsIndex(lines, positionMs)
            lines.getOrNull(idx)?.text?.takeIf { it.isNotBlank() }
        } else {
            result.text.lines().firstOrNull { it.isNotBlank() }
        }
    }

    else -> null
}

private sealed interface MatchRefreshState {
    data object Loading : MatchRefreshState
    data class Success(val message: String) : MatchRefreshState
    data class Error(val message: String) : MatchRefreshState
}

@Composable
private fun MatchRefreshLoadingDialog(
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Refreshing song", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = PulseTheme.colors.accentViolet,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Looking up the corrected metadata, artwork, and lyrics.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = PulseTheme.colors.surface,
    )
}

@Composable
private fun MatchRefreshMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PulseTheme.colors.accentViolet)
            }
        },
        containerColor = PulseTheme.colors.surface,
    )
}

@Composable
private fun FixMatchDialog(
    initialTitle: String,
    initialArtist: String,
    initialAlbum: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onSearch: suspend (String, String, String) -> List<LrcLibApi.TrackInfo>,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var artist by remember(initialArtist) { mutableStateOf(initialArtist) }
    var album by remember(initialAlbum) { mutableStateOf(initialAlbum) }
    var candidates by remember { mutableStateOf<List<LrcLibApi.TrackInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix metadata and lyrics", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Search by song title and optionally artist, then pick the matching track from LRCLIB.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                MatchField(value = title, onValueChange = { title = it.take(120) }, placeholder = "Song title")
                MatchField(value = artist, onValueChange = { artist = it.take(120) }, placeholder = "Artist (optional)")
                MatchField(value = album, onValueChange = { album = it.take(120) }, placeholder = "Album (optional)")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PulseTheme.colors.surfaceSoft)
                        .clickable(enabled = !isSearching && title.isNotBlank()) {
                            isSearching = true
                            errorMessage = null
                            scope.launch {
                                runCatching { onSearch(title.trim(), artist.trim(), album.trim()) }
                                    .onSuccess { candidates = it }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Search failed."
                                    }
                                isSearching = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = PulseTheme.colors.accentViolet,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = PulseTheme.colors.accentViolet,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (isSearching) "Searching..." else "Find matches",
                        color = if (title.isNotBlank()) PulseTheme.colors.accentViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (candidates.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(candidates) { _, candidate ->
                            MatchCandidateRow(
                                candidate = candidate,
                                onClick = {
                                    onSave(
                                        candidate.title,
                                        candidate.artist?.takeIf { it.isNotBlank() } ?: artist.trim(),
                                        candidate.album?.takeIf { it.isNotBlank() } ?: album.trim(),
                                    )
                                },
                            )
                        }
                    }
                } else if (!isSearching && title.isNotBlank()) {
                    Text(
                        text = "No candidates yet. Run a search to see possible lyric matches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PulseTheme.colors.accentViolet)
            }
        },
        containerColor = PulseTheme.colors.surface,
    )
}

@Composable
private fun MatchCandidateRow(
    candidate: LrcLibApi.TrackInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = candidate.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(candidate.artist, candidate.album).joinToString(" - ").ifBlank { "Unknown album" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Use",
            color = PulseTheme.colors.accentViolet,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MatchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PulseTheme.colors.surfaceElevated)
            .border(1.dp, PulseTheme.colors.line2, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
