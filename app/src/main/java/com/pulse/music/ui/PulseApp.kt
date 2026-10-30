package com.pulse.music.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulse.music.PulseApplication
import com.pulse.music.data.Song
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.AddToPlaylistDialog
import com.pulse.music.ui.components.LocalCurrentSongId
import com.pulse.music.ui.components.LocalSongActions
import com.pulse.music.ui.components.LocalSongMetadata
import com.pulse.music.ui.components.SongActions
import com.pulse.music.ui.components.SongActionsSheet
import com.pulse.music.ui.components.SongMenuItem
import com.pulse.music.ui.screens.CollectionRef
import com.pulse.music.ui.screens.CollectionScreen
import com.pulse.music.ui.components.BottomNavContainer
import com.pulse.music.ui.components.Destination
import com.pulse.music.ui.components.MiniPlayer
import com.pulse.music.ui.components.PulseBottomNav
import com.pulse.music.ui.screens.ForYouScreen
import com.pulse.music.ui.screens.LibraryScreen
import com.pulse.music.ui.screens.NowPlayingScreen
import com.pulse.music.ui.screens.QueueScreen
import com.pulse.music.ui.screens.SearchScreen
import com.pulse.music.ui.screens.SettingsScreen
import com.pulse.music.ui.theme.LocalPulseBackgroundTint
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.update.UpdateRepository
import com.pulse.music.update.UpdateState
import com.pulse.music.update.UpdateViewModel
import com.pulse.music.util.ArtworkColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root composable. Owns the nav controller, shared ViewModels, bottom nav,
 * mini-player, Now Playing / Queue overlays, and the song menu.
 */
@Composable
fun PulseApp(
    launchRoute: String? = null,
    onLaunchRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val updateVm: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
    // Only the fields the root needs, so position ticks don't recompose every screen.
    val currentSong by remember(playerVm) {
        playerVm.state.map { it.currentSong }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerVm) {
        playerVm.state.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val metadataById by libraryVm.metadataById.collectAsStateWithLifecycle()
    val playlists by libraryVm.playlists.collectAsStateWithLifecycle()
    val app = remember { PulseApplication.get() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val artworkUrl = currentSong?.let { metadataById[it.id] }?.artworkUrl?.takeIf(String::isNotBlank)
    var artworkColor by remember(currentSong?.id, artworkUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(currentSong?.id, artworkUrl) {
        artworkColor = currentSong?.let { ArtworkColorExtractor.dominantColor(context, it, artworkUrl) }
    }
    val backgroundTint by animateColorAsState(
        targetValue = artworkColor
            ?.copy(alpha = if (isPlaying) 0.16f else 0.09f)
            ?: Color.Transparent,
        label = "backgroundTint",
    )

    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var changelogDismissed by remember { mutableStateOf(false) }
    var showAppOpenUpdatePrompt by remember { mutableStateOf(false) }
    var pendingChangelog by remember { mutableStateOf<UpdateRepository.PendingChangelog?>(null) }
    var songMenu by remember { mutableStateOf<Pair<Song, List<SongMenuItem>>?>(null) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(Unit) {
        pendingChangelog = app.updateRepository.pendingChangelogForCurrentInstall()
    }
    val dismissedUpdatePromptBuild by app.userPreferences.dismissedUpdatePromptBuild.collectAsStateWithLifecycle(initialValue = 0)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Detail pages (albums, playlists...) keep the tab they were opened from highlighted.
    var selectedTabRoute by remember { mutableStateOf(Destination.ForYou.route) }
    LaunchedEffect(currentRoute) {
        if (Destination.entries.any { it.route == currentRoute }) selectedTabRoute = currentRoute!!
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openCollection(route: String) {
        showNowPlaying = false
        showQueue = false
        navController.navigate(route) { launchSingleTop = true }
    }

    val songActions = remember(playerVm, libraryVm) {
        SongActions(
            play = { songs, index ->
                playerVm.playQueue(songs, index)
            },
            openMenu = { song, extras -> songMenu = song to extras },
            playNext = { song ->
                playerVm.playNext(song)
                toast("Playing next")
            },
            addToQueue = { song ->
                if (playerVm.isQueued(song.id)) {
                    toast("Already in the queue")
                } else {
                    playerVm.addToQueue(song)
                    toast("Added to queue")
                }
            },
            toggleLike = { song -> playerVm.toggleLike(song) },
            addToPlaylist = { song -> addToPlaylistSong = song },
            openAlbum = { song ->
                val album = LibraryIndex.albumNameFor(song, libraryVm.metadataById.value[song.id])
                openCollection(Routes.album(LibraryIndex.albumKey(album)))
            },
            openArtist = { song ->
                val artist = LibraryIndex.artistNameFor(song, libraryVm.metadataById.value[song.id])
                openCollection(Routes.artist(LibraryIndex.artistKey(artist)))
            },
        )
    }

    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) {
            app.updateRepository.checkForAppOpenUpdate()
        }
        if (info != null) {
            updateVm.showAvailable(info)
        }
    }

    LaunchedEffect(launchRoute) {
        if (!launchRoute.isNullOrBlank()) {
            Destination.entries.firstOrNull { it.route == launchRoute }?.let { destination ->
                if (destination.route != currentRoute) {
                    navigateTo(navController, destination)
                }
                if (destination == Destination.Settings) {
                    updateVm.checkForUpdate()
                }
            }
            onLaunchRouteConsumed()
        }
    }

    LaunchedEffect(updateState, dismissedUpdatePromptBuild, currentRoute) {
        val available = updateState as? UpdateState.Available ?: return@LaunchedEffect
        showAppOpenUpdatePrompt =
            available.info.buildNumber > dismissedUpdatePromptBuild &&
                currentRoute != Destination.Settings.route
    }

    val onSongTap: (List<Song>, Int) -> Unit = remember(playerVm) {
        { songs, index -> playerVm.playQueue(songs, index) }
    }
    val onShuffle: (List<Song>) -> Unit = remember(playerVm) {
        { songs -> playerVm.playShuffled(songs) }
    }

    PulseTheme(accentColor = artworkColor) {
        CompositionLocalProvider(
            LocalPulseBackgroundTint provides backgroundTint,
            LocalSongMetadata provides metadataById,
            LocalSongActions provides songActions,
            LocalCurrentSongId provides currentSong?.id,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PulseTheme.background,
                                PulseTheme.colors.surface,
                                PulseTheme.background,
                            ),
                        ),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    PulseTheme.colors.accentViolet.copy(alpha = 0.14f),
                                    Color.Transparent,
                                ),
                                radius = 1200f,
                            ),
                        ),
                )
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = Destination.ForYou.route,
                        ) {
                            composable(Destination.ForYou.route) {
                                ForYouScreen(
                                    vm = libraryVm,
                                    updateVm = updateVm,
                                    onSongTap = onSongTap,
                                    onShuffle = onShuffle,
                                    onOpenSettings = {
                                        navigateTo(navController, Destination.Settings)
                                    },
                                    onOpenCollection = { route -> openCollection(route) },
                                )
                            }
                            composable(Destination.Library.route) {
                                LibraryScreen(
                                    vm = libraryVm,
                                    onSongTap = onSongTap,
                                    onShuffle = onShuffle,
                                    onOpenCollection = { route -> openCollection(route) },
                                )
                            }
                            composable(Destination.Search.route) {
                                SearchScreen(
                                    vm = libraryVm,
                                    onSongTap = onSongTap,
                                    onOpenCollection = { route -> openCollection(route) },
                                )
                            }
                            composable(Destination.Settings.route) {
                                SettingsScreen(
                                    vm = libraryVm,
                                    updateVm = updateVm,
                                )
                            }
                            composable(Routes.ALBUM) { entry ->
                                CollectionRoute(
                                    ref = CollectionRef.Album(entry.arguments?.getString("key").orEmpty()),
                                    libraryVm = libraryVm,
                                    navController = navController,
                                    onSongTap = onSongTap,
                                    onShuffle = onShuffle,
                                )
                            }
                            composable(Routes.ARTIST) { entry ->
                                CollectionRoute(
                                    ref = CollectionRef.Artist(entry.arguments?.getString("key").orEmpty()),
                                    libraryVm = libraryVm,
                                    navController = navController,
                                    onSongTap = onSongTap,
                                    onShuffle = onShuffle,
                                )
                            }
                            composable(
                                Routes.PLAYLIST,
                                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            ) { entry ->
                                CollectionRoute(
                                    ref = CollectionRef.Playlist(entry.arguments?.getLong("id") ?: -1L),
                                    libraryVm = libraryVm,
                                    navController = navController,
                                    onSongTap = onSongTap,
                                    onShuffle = onShuffle,
                                )
                            }
                            composable(Routes.LIKED) {
                                CollectionRoute(CollectionRef.Liked, libraryVm, navController, onSongTap, onShuffle)
                            }
                            composable(Routes.RECENTLY_ADDED) {
                                CollectionRoute(CollectionRef.RecentlyAdded, libraryVm, navController, onSongTap, onShuffle)
                            }
                        }
                    }

                    Column(modifier = Modifier.navigationBarsPadding()) {
                        AnimatedVisibility(
                            visible = currentSong != null,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            currentSong?.let { song ->
                                MiniPlayerHost(
                                    playerVm = playerVm,
                                    song = song,
                                    isPlaying = isPlaying,
                                    onTap = { showNowPlaying = true },
                                )
                            }
                        }
                        BottomNavContainer {
                            PulseBottomNav(
                                currentRoute = selectedTabRoute,
                                onNavigate = { destination ->
                                    navigateTo(navController, destination)
                                },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showNowPlaying && currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    NowPlayingScreen(
                        onBack = { showNowPlaying = false },
                        onOpenQueue = { showQueue = true },
                    )
                }

                AnimatedVisibility(
                    visible = showQueue && currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    QueueScreen(onBack = { showQueue = false })
                }

                songMenu?.let { (song, extras) ->
                    // Show the freshest copy (e.g. liked state) of the song.
                    val liveSong = libraryVm.allSongs.collectAsStateWithLifecycle().value
                        .firstOrNull { it.id == song.id } ?: song
                    SongActionsSheet(
                        song = liveSong,
                        extras = extras,
                        actions = songActions,
                        onDismiss = { songMenu = null },
                    )
                }

                addToPlaylistSong?.let { song ->
                    AddToPlaylistDialog(
                        playlists = playlists.filter { it.systemType == null },
                        onDismiss = { addToPlaylistSong = null },
                        onCreatePlaylist = { name -> libraryVm.createPlaylist(name) },
                        onAddToPlaylist = { playlistId ->
                            libraryVm.addSongToPlaylist(playlistId, song.id)
                            val name = playlists.firstOrNull { it.id == playlistId }?.name
                            toast(if (name != null) "Added to $name" else "Added to playlist")
                        },
                        launchSuspend = { block -> scope.launch { block() } },
                    )
                }

                val changelog = pendingChangelog
                if (changelog != null && !changelogDismissed) {
                    PostUpdateChangelogDialog(
                        changelog = changelog,
                        onDismiss = {
                            changelogDismissed = true
                            scope.launch {
                                app.updateRepository.clearPendingChangelog()
                            }
                        },
                    )
                }

                val available = updateState as? UpdateState.Available
                if (available != null && showAppOpenUpdatePrompt) {
                    AppOpenUpdateDialog(
                        buildNumber = available.info.buildNumber,
                        onOpen = {
                            showAppOpenUpdatePrompt = false
                            navigateTo(navController, Destination.Settings)
                        },
                        onDismiss = {
                            showAppOpenUpdatePrompt = false
                            scope.launch {
                                app.userPreferences.setDismissedUpdatePromptBuild(available.info.buildNumber)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Routes for the detail pages. Tab routes live on [Destination]. */
object Routes {
    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val PLAYLIST = "playlist/{id}"
    const val LIKED = "collection/liked"
    const val RECENTLY_ADDED = "collection/recent"

    fun album(key: String) = "album/${Uri.encode(key)}"
    fun artist(key: String) = "artist/${Uri.encode(key)}"
    fun playlist(id: Long) = "playlist/$id"
}

@Composable
private fun CollectionRoute(
    ref: CollectionRef,
    libraryVm: LibraryViewModel,
    navController: NavHostController,
    onSongTap: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
) {
    CollectionScreen(
        ref = ref,
        vm = libraryVm,
        onBack = { navController.popBackStack() },
        onPlay = onSongTap,
        onShuffle = onShuffle,
        onOpenAlbum = { key -> navController.navigate(Routes.album(key)) },
    )
}

/** Collects playback position here so ticks only recompose the mini player. */
@Composable
private fun MiniPlayerHost(
    playerVm: PlayerViewModel,
    song: Song,
    isPlaying: Boolean,
    onTap: () -> Unit,
) {
    val state by playerVm.state.collectAsStateWithLifecycle()
    MiniPlayer(
        song = song,
        isPlaying = isPlaying,
        progress = {
            if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        },
        onTap = onTap,
        onPlayPause = { playerVm.playOrPause() },
        onNext = { playerVm.next() },
    )
}

private fun navigateTo(navController: NavHostController, destination: Destination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppOpenUpdateDialog(
    buildNumber: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Update available",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Text(
                text = "Build #$buildNumber is ready. Open Settings to download and install it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PulseTheme.colors.accentCream,
                    contentColor = PulseTheme.colors.onPrimary,
                ),
            ) {
                Text("Open updates")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = PulseTheme.colors.surface,
    )
}

@Composable
private fun PostUpdateChangelogDialog(
    changelog: UpdateRepository.PendingChangelog,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "What's new in v${changelog.versionName} (build ${changelog.buildNumber})",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = changelog.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
