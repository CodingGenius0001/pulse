package com.pulse.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulse.music.player.PlayerViewModel
import com.pulse.music.ui.components.BottomNavContainer
import com.pulse.music.ui.components.Destination
import com.pulse.music.ui.components.MiniPlayer
import com.pulse.music.ui.components.PulseBottomNav
import com.pulse.music.ui.screens.ForYouScreen
import com.pulse.music.ui.screens.LibraryScreen
import com.pulse.music.ui.screens.NowPlayingScreen
import com.pulse.music.ui.screens.SearchScreen
import com.pulse.music.ui.screens.SettingsScreen

/**
 * Root composable. Owns the nav controller, bottom nav, and mini-player overlay.
 * Now Playing is rendered as a full-height modal sliding up from the bottom.
 */
@Composable
fun PulseApp() {
    val navController = rememberNavController()
    val playerVm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
    val playbackState by playerVm.state.collectAsStateWithLifecycle()

    var showNowPlaying by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.ForYou.route,
                ) {
                    composable(Destination.ForYou.route) {
                        ForYouScreen(
                            onSongTap = { songs, index ->
                                playerVm.playQueue(songs, index)
                                showNowPlaying = true
                            },
                            onHeroPlay = { songs ->
                                playerVm.playQueue(songs, 0)
                                showNowPlaying = true
                            },
                        )
                    }
                    composable(Destination.Library.route) {
                        LibraryScreen(
                            onSongTap = { songs, index ->
                                playerVm.playQueue(songs, index)
                                showNowPlaying = true
                            },
                        )
                    }
                    composable(Destination.Search.route) {
                        SearchScreen(
                            onSongTap = { songs, index ->
                                playerVm.playQueue(songs, index)
                                showNowPlaying = true
                            },
                        )
                    }
                    composable(Destination.Settings.route) {
                        SettingsScreen()
                    }
                }
            }

            Column(modifier = Modifier.navigationBarsPadding()) {
                val currentSong = playbackState.currentSong
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
            visible = showNowPlaying && playbackState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NowPlayingScreen(
                onBack = { showNowPlaying = false },
            )
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
