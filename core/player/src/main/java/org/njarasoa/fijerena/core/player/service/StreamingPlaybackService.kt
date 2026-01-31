package org.njarasoa.fijerena.core.player.service

import android.content.Intent
import android.os.PowerManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
        initializePlayer()
        acquireWakeLock()
    }

    private fun initializePlayer() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setLoadControl(PlayerConfigFactory.createLoadControl())
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
            }
        )
        player.addListener(playerListener!!)
    }

    fun playStream(metadata: PlayerMetadata) {
        val player = mediaSession?.player ?: return
        _currentMetadata.value = metadata

        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(metadata.streamUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(metadata.title)
                    .setDisplayTitle(metadata.channelName)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
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
        super.onDestroy()
    }

    private class PlayerListener(
        private val onStateChanged: (PlaybackState) -> Unit,
        private val onWakeLockRequired: () -> Unit
    ) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> PlaybackState.Idle
                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
            onStateChanged(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onWakeLockRequired()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady) {
                onWakeLockRequired()
            }
        }
    }

    companion object {
        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> {
            return service.playbackState
        }

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> {
            return service.currentMetadata
        }
    }
}
