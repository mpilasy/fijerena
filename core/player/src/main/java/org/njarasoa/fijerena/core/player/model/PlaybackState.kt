package org.njarasoa.fijerena.core.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

sealed class PlaybackState {
    data object Idle : PlaybackState()

    data object Buffering : PlaybackState()

    data class Playing(
        val position: Long,
        val duration: Long,
    ) : PlaybackState()

    data class Paused(
        val position: Long,
        val duration: Long,
    ) : PlaybackState()

    /**
     * Playback reached the natural end of the stream. Carries the duration so the session can be
     * finalized as *completed* — the player is torn down moments later, so reading position off it
     * at exit time yields 0 and the item silently loses both its resume point and its watched mark.
     */
    data class Ended(
        val duration: Long = 0L,
    ) : PlaybackState()

    data class Error(
        val message: String,
        val exception: Exception? = null,
    ) : PlaybackState()
}

/** `headers` is built at construction and only ever read — see the note on [MediaItem]. */
@Immutable
@Serializable
data class PlayerMetadata(
    val title: String = "",
    val channelName: String = "",
    val description: String? = null,
    val streamUrl: String = "",
    val isLive: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val channelCount: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val isSelected: Boolean,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val mimeType: String, // e.g., "text/vtt", "application/x-subrip"
    val isSelected: Boolean,
)

data class VideoQualityInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Float,
    val label: String, // e.g., "1080p (5.2 Mbps)"
    val isSelected: Boolean,
)

data class ChapterInfo(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)
