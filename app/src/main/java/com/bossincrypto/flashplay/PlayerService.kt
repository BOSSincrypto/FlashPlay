package com.bossincrypto.flashplay

import android.content.Intent
import android.os.Process
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlayerService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var prefs: PlaybackPrefs
    private var currentPlaybackFailed = false
    private val persistenceHandler = Handler(Looper.getMainLooper())
    private val persistProgress = object : Runnable {
        override fun run() {
            persistPosition()
            if (player.isPlaying) {
                persistenceHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PlaybackPrefs(this)

        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)

        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Sync seeks keep scrubbing responsive; exact seek remains available to integrations.
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                setWakeMode(C.WAKE_MODE_NETWORK)
                setPlaybackParameters(PlaybackParameters(prefs.speed))
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentPlaybackFailed = false
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        persistenceHandler.removeCallbacks(persistProgress)
                        if (isPlaying) {
                            persistenceHandler.postDelayed(persistProgress, POSITION_SAVE_INTERVAL_MS)
                        } else {
                            persistPosition()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        currentPlaybackFailed = true
                        persistPosition()
                    }

                })
            }

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val isOwnApp = controllerInfo.uid == Process.myUid() || controllerInfo.packageName == packageName
        return if (isOwnApp || controllerInfo.isTrusted) session else null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep a playing session alive for PiP/background controls; persist when paused or destroyed.
        if (!player.isPlaying) persistPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistenceHandler.removeCallbacks(persistProgress)
        persistPosition()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun persistPosition() {
        val item = player.currentMediaItem
        val uri = item?.localConfiguration?.uri
        prefs.savePosition(uri, player.currentPosition)
        prefs.saveLastUri(uri.takeIf { item?.localConfiguration?.tag == true && !currentPlaybackFailed })
    }

    companion object {
        const val SKIP_MS = 10_000L
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}
