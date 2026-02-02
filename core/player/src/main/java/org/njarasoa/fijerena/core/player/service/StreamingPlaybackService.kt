package org.njarasoa.fijerena.core.player.service

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.device.DeviceDetector
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.source.StreamingMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StreamingPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playerListener: PlayerListener? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMetadata = MutableStateFlow(PlayerMetadata())
    val currentMetadata: StateFlow<PlayerMetadata> = _currentMetadata.asStateFlow()


    override fun onCreate() {
        super.onCreate()
        instance = this
        initializePlayer()
        acquireWakeLock()
    }

    private fun initializePlayer(contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD) {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setLoadControl(PlayerConfigFactory.createLoadControl(contentType))
            .setTrackSelector(PlayerConfigFactory.createTrackSelector(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        playerListener = PlayerListener(
            onStateChanged = { newState ->
                _playbackState.value = newState
            },
            onWakeLockRequired = {
                acquireWakeLock()
            },
            player = player
        )
        player.addListener(playerListener!!)
    }

    fun setContentType(contentType: PlayerConfigFactory.ContentType) {
        // Release old player and create new one with different LoadControl
        mediaSession?.run {
            player.removeListener(playerListener!!)
            player.release()
            release()
        }
        mediaSession = null
        initializePlayer(contentType)
    }

    fun playStream(metadata: PlayerMetadata) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        // Reset error state on new stream
        playerListener?.resetErrorState()
        _currentMetadata.value = metadata

        // Use StreamingMediaSourceFactory for proper HLS/DASH/MPEG-TS detection
        val mediaSource = StreamingMediaSourceFactory.createMediaSource(
            context = this,
            streamUrl = metadata.streamUrl,
            headers = metadata.headers
        )

        player.setMediaSource(mediaSource)
        player.playWhenReady = true
        player.prepare()
        _playbackState.value = PlaybackState.Buffering
    }

    fun pause() {
        mediaSession?.player?.pause()
    }

    fun resume() {
        mediaSession?.player?.play()
    }

    fun stop() {
        mediaSession?.player?.stop()
        _playbackState.value = PlaybackState.Idle
        releaseWakeLock()
    }

    fun seekTo(position: Long) {
        mediaSession?.player?.seekTo(position)
    }

    fun getPlayer(): androidx.media3.common.Player? {
        return mediaSession?.player
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StreamingPlayback:WakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playerListener!!)
            player.release()
            release()
        }
        mediaSession = null
        releaseWakeLock()
        instance = null
        super.onDestroy()
    }

    private class PlayerListener(
        private val onStateChanged: (PlaybackState) -> Unit,
        private val onWakeLockRequired: () -> Unit,
        private val player: Player
    ) : Player.Listener {
        private var isInErrorState = false

        fun resetErrorState() {
            isInErrorState = false
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady) {
                onWakeLockRequired()
            }
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onWakeLockRequired()
            }
            updatePlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            isInErrorState = true
            val errorMessage = parsePlaybackError(error)
            onStateChanged(PlaybackState.Error(errorMessage, error))
        }

        private fun parsePlaybackError(error: PlaybackException): String {
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> {
                    // Codec/decoder errors
                    val codecInfo = extractCodecInfo(error.message ?: "")
                    if (codecInfo.isNotEmpty()) {
                        "Video codec not supported on this device: $codecInfo"
                    } else {
                        "Video format not supported on this device"
                    }
                }
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    "Network connection failed. Check your internet connection."
                }
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    "Stream unavailable (HTTP error). The content may have been removed."
                }
                PlaybackException.ERROR_CODE_TIMEOUT -> {
                    "Playback timeout. The stream may be too slow or unavailable."
                }
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    "Stream not found or access denied."
                }
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    "Invalid stream format. The stream may be corrupted."
                }
                else -> {
                    "Playback error: ${error.errorCodeName}"
                }
            }
        }

        private fun extractCodecInfo(message: String): String {
            // Extract codec info from error message like "video/hevc" or "hvc1.2.4.H150.B0"
            val codecRegex = Regex("video/(\\w+)|format=(\\w+)")
            val match = codecRegex.find(message)
            return match?.value?.replace("video/", "")?.uppercase() ?: ""
        }

        private fun updatePlaybackState() {
            // Don't overwrite error state
            if (isInErrorState) return

            val state = when (player.playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> {
                    if (player.playWhenReady) {
                        PlaybackState.Playing(
                            position = player.currentPosition,
                            duration = player.duration.coerceAtLeast(0L)
                        )
                    } else {
                        PlaybackState.Paused(
                            position = player.currentPosition,
                            duration = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
            onStateChanged(state)
        }
    }

    companion object {
        @Volatile
        private var instance: StreamingPlaybackService? = null

        fun getInstance(): StreamingPlaybackService? = instance

        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> {
            return service.playbackState
        }

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> {
            return service.currentMetadata
        }
    }
}
