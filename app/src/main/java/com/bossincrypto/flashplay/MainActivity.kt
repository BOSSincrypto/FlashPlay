package com.bossincrypto.flashplay

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.media.AudioManager
import android.util.Rational
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var openButton: Button
    private lateinit var speedButton: Button
    private lateinit var emptyText: TextView
    private lateinit var controlsBar: LinearLayout
    private lateinit var gestureFeedback: TextView
    private lateinit var gestureDetector: GestureDetector
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: PlaybackPrefs
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingUri: Uri? = null
    private var currentUriCanPersist = false

    private val openVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant a one-shot URI only; playback still works in this session.
        }
        currentUriCanPersist = contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        }
        play(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PlaybackPrefs(this)
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.player_view)
        openButton = findViewById(R.id.open_button)
        speedButton = findViewById(R.id.speed_button)
        emptyText = findViewById(R.id.empty_text)
        controlsBar = findViewById(R.id.controls_bar)
        gestureFeedback = findViewById(R.id.gesture_feedback)
        audioManager = getSystemService(AudioManager::class.java)
        gestureDetector = GestureDetector(this, PlayerGestures())
        playerView.setOnClickListener {
            if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
        }
        playerView.videoSurfaceView?.setOnTouchListener { surface, event ->
            val handled = gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) surface.performClick()
            handled
        }
        openButton.setOnClickListener { showOpenMenu(openButton) }
        speedButton.setOnClickListener { showSpeedMenu(speedButton) }
        speedButton.text = speedLabel(prefs.speed)
        connectController()
        handleIntent(intent)
        updatePipParams()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldEnterPip()) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::openButton.isInitialized) {
            controlsBar.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
            if (isInPictureInPictureMode) {
                emptyText.visibility = View.GONE
            } else {
                updateUiForMedia(controller?.currentMediaItem)
            }
        }
        playerView.useController = !isInPictureInPictureMode
    }

    override fun onDestroy() {
        playerView.player = null
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onDestroy()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    controller = future.get().also { connected ->
                        playerView.player = connected
                        connected.addListener(object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                currentUriCanPersist = mediaItem?.localConfiguration?.uri?.let(::canRestoreUri) == true
                                updateUiForMedia(mediaItem)
                                updatePipParams()
                            }

                            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                                prefs.speed = playbackParameters.speed
                                speedButton.text = speedLabel(playbackParameters.speed)
                            }

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                updatePipParams()
                            }

                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                updatePipParams()
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                emptyText.visibility = View.VISIBLE
                                emptyText.text = getString(R.string.playback_error)
                            }
                        })
                        connected.currentMediaItem?.let(::updateUiForMedia)
                        if (connected.currentMediaItem == null && pendingUri == null) {
                            prefs.lastUri()?.takeIf(::canRestoreUri)?.let { lastUri ->
                                val restoredItem = MediaItem.Builder()
                                    .setUri(lastUri)
                                    .setMediaId(lastUri.toString())
                                    .setTag(true)
                                    .build()
                                connected.setMediaItem(restoredItem, prefs.positionFor(lastUri))
                                connected.prepare()
                            }
                        }
                    }
                    pendingUri?.let { uri ->
                        pendingUri = null
                        play(uri)
                    }
                } catch (_: Exception) {
                    emptyText.text = getString(R.string.player_unavailable)
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun play(uri: Uri) {
        val player = controller
        if (player == null) {
            pendingUri = uri
            return
        }
        player.currentMediaItem?.localConfiguration?.uri?.let { previousUri ->
            prefs.savePosition(previousUri, player.currentPosition)
        }
        val shouldPersist = uri.scheme == "file" || currentUriCanPersist
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setTag(shouldPersist)
            .build()
        player.setMediaItem(item, prefs.positionFor(uri))
        player.setPlaybackParameters(PlaybackParameters(prefs.speed))
        player.prepare()
        player.play()
        updateUiForMedia(item)
    }

    private fun canRestoreUri(uri: Uri): Boolean {
        if (uri.scheme == "file") return true
        if (uri.scheme != "content") return false
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        currentUriCanPersist = false
        uri?.let(::play)
    }

    private fun updateUiForMedia(item: MediaItem?) {
        val hasMedia = item != null
        emptyText.visibility = if (hasMedia) View.GONE else View.VISIBLE
        openButton.contentDescription = getString(if (hasMedia) R.string.open_another else R.string.open_video)
    }

    private fun showSpeedMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        SPEEDS.forEach { speed ->
            popup.menu.add(speedLabel(speed)).setOnMenuItemClickListener {
                prefs.speed = speed
                controller?.setPlaybackParameters(PlaybackParameters(speed))
                speedButton.text = speedLabel(speed)
                true
            }
        }
        popup.show()
    }

    private fun showOpenMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.open_file).setOnMenuItemClickListener {
                openVideo.launch(arrayOf("video/*"))
                true
            }
            menu.add(R.string.open_url).setOnMenuItemClickListener {
                showUrlDialog()
                true
            }
            show()
        }
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.url_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setPadding(48, 16, 48, 16)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.open_url)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.play, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uri = input.text.toString().trim().toUri()
                if (uri.scheme == "https") {
                    dialog.dismiss()
                    play(uri)
                } else {
                    input.error = getString(R.string.invalid_url)
                }
            }
        }
        dialog.show()
    }

    private fun speedLabel(speed: Float): String {
        val value = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')
        return getString(R.string.speed_format, value)
    }

    private fun showGestureFeedback(text: String) {
        gestureFeedback.text = text
        gestureFeedback.visibility = View.VISIBLE
        gestureFeedback.removeCallbacks(hideGestureFeedback)
        gestureFeedback.postDelayed(hideGestureFeedback, 650L)
    }

    private val hideGestureFeedback = Runnable { gestureFeedback.visibility = View.GONE }

    private inner class PlayerGestures : GestureDetector.SimpleOnGestureListener() {
        private var initialBrightness = 0.5f
        private var initialVolume = 0

        override fun onDown(event: MotionEvent): Boolean {
            initialBrightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            val player = controller ?: return false
            val delta = if (event.x < playerView.width / 2f) -PlayerService.SKIP_MS else PlayerService.SKIP_MS
            player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
            showGestureFeedback(getString(R.string.seek_feedback, delta / 1_000L))
            return true
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            playerView.performClick()
            return true
        }

        override fun onScroll(start: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            start ?: return false
            if (kotlin.math.abs(distanceY) < kotlin.math.abs(distanceX)) return false
            val fraction = (start.y - current.y) / playerView.height.coerceAtLeast(1)
            if (start.x < playerView.width / 2f) {
                val brightness = (initialBrightness + fraction).coerceIn(0.05f, 1f)
                window.attributes = window.attributes.apply { screenBrightness = brightness }
                showGestureFeedback(getString(R.string.brightness_feedback, (brightness * 100).toInt()))
            } else {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val volume = (initialVolume + fraction * max).toInt().coerceIn(0, max)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                showGestureFeedback(getString(R.string.volume_feedback, volume * 100 / max))
            }
            return true
        }
    }

    private fun shouldEnterPip(): Boolean = controller?.isPlaying == true &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip() {
        updatePipParams()
        enterPictureInPictureMode(buildPipParams())
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val videoSize = controller?.videoSize
        var width = videoSize?.width?.takeIf { it > 0 } ?: 16
        var height = videoSize?.height?.takeIf { it > 0 } ?: 9
        val ratio = width.toFloat() / height
        if (ratio !in MIN_PIP_RATIO..MAX_PIP_RATIO) {
            width = 16
            height = 9
        }
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(width, height))
        val sourceRect = Rect()
        if (::playerView.isInitialized && playerView.getGlobalVisibleRect(sourceRect)) {
            builder.setSourceRectHint(sourceRect)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(shouldEnterPip())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) builder.setSeamlessResizeEnabled(true)
        return builder.build()
    }

    companion object {
        private val SPEEDS = (1..16).map { it * PlaybackPrefs.STEP }
        private const val MIN_PIP_RATIO = 1f / 2.39f
        private const val MAX_PIP_RATIO = 2.39f
    }
}
