package org.njarasoa.fijerena.core.player.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.PlaybackServiceConnection
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val serviceConnection = PlaybackServiceConnection(context)

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMetadata = MutableStateFlow(PlayerMetadata())
    val currentMetadata: StateFlow<PlayerMetadata> = _currentMetadata.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    init {
        viewModelScope.launch {
            startService()
            connectToService()
        }
    }

    private fun startService() {
        val intent = Intent(context, StreamingPlaybackService::class.java)
        context.startService(intent)
    }

    private suspend fun connectToService() {
        serviceConnection.connect().collect { controller ->
            _controller.value = controller
        }
    }

    fun playStream(metadata: PlayerMetadata) {
        _currentMetadata.value = metadata
        viewModelScope.launch {
            val controller = _controller.value
            if (controller != null) {
                // Load and play the media
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(metadata.streamUrl)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(metadata.title)
                            .setDisplayTitle(metadata.channelName)
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.playWhenReady = true
                controller.prepare()
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            _controller.value?.pause()
        }
    }

    fun resume() {
        viewModelScope.launch {
            _controller.value?.play()
        }
    }

    fun stop() {
        viewModelScope.launch {
            _controller.value?.stop()
            _playbackState.value = PlaybackState.Idle
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            _controller.value?.seekTo(position)
        }
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection.disconnect()
        viewModelScope.launch {
            _controller.value?.stop()
            _controller.value?.release()
        }
    }
}
