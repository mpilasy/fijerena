package org.njarasoa.fijerena.core.player.domain

data class MovieDetail(
    val id: String,
    val name: String,
    val metadata: MediaMetadata = MediaMetadata(),
    val coverUrl: String? = null,
    val extension: String? = null,
    val videoInfo: VideoTechInfo? = null,
    val audioInfo: AudioTechInfo? = null
)

data class VideoTechInfo(
    val width: Int? = null,
    val height: Int? = null,
    val codecName: String? = null
)

data class AudioTechInfo(
    val codecName: String? = null,
    val language: String? = null
)
