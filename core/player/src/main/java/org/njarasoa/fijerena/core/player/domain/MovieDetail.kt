package org.njarasoa.fijerena.core.player.domain

data class MovieDetail(
    val id: String,
    val name: String,
    val metadata: MediaMetadata = MediaMetadata(),
    val coverUrl: String? = null,
    val extension: String? = null,
    val videoInfo: VideoTechInfo? = null,
    val audioTracks: List<AudioTechInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTechInfo> = emptyList()
)

data class VideoTechInfo(
    val width: Int? = null,
    val height: Int? = null,
    val codecName: String? = null,
    val bitrate: Int? = null,
    val videoRange: String? = null,
    val displayTitle: String? = null
)

data class AudioTechInfo(
    val codecName: String? = null,
    val language: String? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val displayTitle: String? = null,
    val isDefault: Boolean = false
)

data class SubtitleTechInfo(
    val codecName: String? = null,
    val language: String? = null,
    val displayTitle: String? = null,
    val isDefault: Boolean = false
)
