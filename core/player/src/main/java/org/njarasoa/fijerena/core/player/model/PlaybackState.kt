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
     * Playback reached the natural end of the stream. Carries the duration so the session is
     * finalised as completed: the player is torn down moments later, so its position at exit is 0
     * and the item would lose its resume point and its watched mark.
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
    // Series name for episode playback: when set, the OSD shows it as the big title and [title]
    // (the episode name) as a subtitle line after [episodeLabel]. Null for films and Live TV.
    val showTitle: String? = null,
    // "S{season}:E{episode}", for example "S3:E1"; only used with [showTitle].
    val episodeLabel: String? = null,
    // TMDB's transparent-PNG wordmark for this title, if any: the OSD shows it in place of
    // [showTitle] / [title] as text.
    val logoUrl: String? = null,
    // Live TV: the EPG programme on air, once the guide lookup resolves and as it rolls over.
    // Null for VOD and for channels without guide data. Only live sync's "now playing" reads it.
    val programTitle: String? = null,
    // Catch-up (a programme from a live channel's archive, played like a film): the line under the
    // programme title, "Channel · Yesterday 17:15". Null for everything else. A catch-up stream
    // that can't start fails at once, without retries. See the catchup plan.
    val catchupLabel: String? = null,
    // The source the stream plays from. Only live sync's "now playing" reads it.
    val sourceName: String? = null,
    // The audio languages Media3 prefers when this stream starts (the profile's track languages;
    // see preferredAudioLanguages). Empty: English, as before.
    val preferredAudioLanguages: List<String> = emptyList(),
)

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    // For display: the stream's code, or the translated word "Unknown".
    val language: String,
    val label: String,
    val channelCount: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val isSelected: Boolean,
    // The raw Format data that the profile's track languages read (TrackLanguage.kt).
    val languageCode: String? = null,
    val roleFlags: Int = 0,
    val selectionFlags: Int = 0,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    // For display: the stream's code, or the translated word "Unknown".
    val language: String,
    val label: String,
    val mimeType: String, // e.g., "text/vtt", "application/x-subrip"
    val isSelected: Boolean,
    // The raw Format data that the profile's track languages read (TrackLanguage.kt).
    val languageCode: String? = null,
    val roleFlags: Int = 0,
    val selectionFlags: Int = 0,
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
