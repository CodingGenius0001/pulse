package com.pulse.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulse.music.PulseApplication
import com.pulse.music.player.PlayerViewModel
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
import com.pulse.music.update.UpdateViewModel
import com.pulse.music.util.ArtworkColorExtractor

/**
 * Root composable. Owns the nav controller, shared ViewModels, bottom nav,
 * mini-player, and Now Playing / Queue overlays.
 */
@Composable
fun PulseApp() {
    val navController = rememberNavController()
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val updateVm: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
    val playbackState by playerVm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val currentSong = playbackState.currentSong
    val artworkUrl by produceState<String?>(initialValue = null, currentSong?.id) {
        val song = currentSong
        if (song == null) {
            value = null
        } else {
            PulseApplication.get()
                .metadataRepository
                .observe(song.id)
                .collect { value = it?.artworkUrl?.takeIf(String::isNotBlank) }
        }
    }
    val artworkColor by produceState<Color?>(initialValue = null, currentSong?.id, artworkUrl) {
        value = currentSong?.let { ArtworkColorExtractor.dominantColor(context, it, artworkUrl) }
    }
    val backgroundTint by animateColorAsState(
        targetValue = artworkColor
            ?.copy(alpha = if (playbackState.isPlaying) 0.13f else 0.07f)
            ?: Color.Transparent,
        label = "backgroundTint",
    )

    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(LocalPulseBackgroundTint provides backgroundTint) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PulseTheme.background),
        ) {
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
                                onSongTap = { songs, index ->
                                    playerVm.playQueue(songs, index)
                                    showNowPlaying = true
                                },
                                onHeroPlay = { songs ->
                                    playerVm.playQueue(songs, 0)
                                    showNowPlaying = true
                                },
                                onOpenSettings = {
                                    navigateTo(navController, Destination.Settings)
                                },
                            )
                        }
                        composable(Destination.Library.route) {
                            LibraryScreen(
                                vm = libraryVm,
                                onSongTap = { songs, index ->
                                    playerVm.playQueue(songs, index)
                                    showNowPlaying = true
                                },
                            )
                        }
                        composable(Destination.Search.route) {
                            SearchScreen(
                                vm = libraryVm,
                                onSongTap = { songs, index ->
                                    playerVm.playQueue(songs, index)
                                    showNowPlaying = true
                                },
                            )
                        }
                        composable(Destination.Settings.route) {
                            SettingsScreen(
                                vm = libraryVm,
                                updateVm = updateVm,
                            )
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
                            MiniPlayer(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                onTap = { showNowPlaying = true },
                                onPlayPause = { playerVm.playOrPause() },
                                onSkipForward10 = { playerVm.seekForward10() },
                                onNext = { playerVm.next() },
                            )
                        }
                    }
                    BottomNavContainer {
                        PulseBottomNav(
                            currentRoute = currentRoute,
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
        }
    }
}

private fun navigateTo(navController: NavHostController, destination: Destination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
