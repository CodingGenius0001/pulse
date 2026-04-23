package com.pulse.music.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pulse.music.MainActivity

/**
 * MediaSessionService is the modern foreground service for music playback.
 *
 * It gives us:
 *  - Background playback that keeps running when the app is backgrounded
 *  - Lock-screen media controls
 *  - Notification media controls
 *  - Automatic audio focus handling (pauses for calls, ducks for nav, etc.)
 *
 * UI talks to this service via a MediaController, not directly.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug
            .build()

        // When the user taps the notification, open MainActivity
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** When the task is swiped away, stop playback if paused (standard music-app behavior). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
