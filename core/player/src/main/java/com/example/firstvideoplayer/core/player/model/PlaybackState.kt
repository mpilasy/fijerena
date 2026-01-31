package com.example.firstvideoplayer.core.player.model

import kotlinx.serialization.Serializable

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Buffering : PlaybackState()
    data class Playing(val position: Long, val duration: Long) : PlaybackState()
    data class Paused(val position: Long, val duration: Long) : PlaybackState()
    data object Ended : PlaybackState()
    data class Error(val message: String, val exception: Exception? = null) : PlaybackState()
}

@Serializable
data class PlayerMetadata(
    val title: String = "",
    val channelName: String = "",
    val streamUrl: String = "",
    val isLive: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class StreamQuality(
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Int = 0
)
