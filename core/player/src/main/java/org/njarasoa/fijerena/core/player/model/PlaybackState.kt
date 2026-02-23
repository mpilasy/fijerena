package org.njarasoa.fijerena.core.player.model

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

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val channelCount: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val mimeType: String, // e.g., "text/vtt", "application/x-subrip"
    val isSelected: Boolean
)

data class VideoQualityInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Float,
    val label: String, // e.g., "1080p (5.2 Mbps)"
    val isSelected: Boolean
)

data class ChapterInfo(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)
