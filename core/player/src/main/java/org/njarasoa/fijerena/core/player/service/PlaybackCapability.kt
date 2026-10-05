package org.njarasoa.fijerena.core.player.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

/**
 * The video format when a stream has video but no decoder for any of its video tracks (Dolby
 * Vision profile 5 on a device without a Dolby Vision decoder): Media3 then selects no video
 * track and "plays" audio only, or nothing, without an error. Null when the stream has no video
 * (radio), when a video track is selected, or when one is merely deselected but playable.
 * See docs/plans/20261004_playback-capability-errors-plan.md → P1.
 */
@OptIn(UnstableApi::class)
internal fun unplayableVideoFormat(tracks: Tracks): Format? {
    if (!tracks.containsType(C.TRACK_TYPE_VIDEO)) return null
    if (tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) return null
    if (tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, true)) return null
    return tracks.groups.first { it.type == C.TRACK_TYPE_VIDEO }.getTrackFormat(0)
}

/** The Dolby Vision profile in a `dvhe.05.09` / `dvh1.08.06` style codecs string, else null. */
internal fun dolbyVisionProfile(codecs: String?): Int? {
    val parts = codecs?.split('.') ?: return null
    if (parts.size < 2 || parts[0] !in DOLBY_VISION_CODEC_TAGS) return null
    return parts[1].toIntOrNull()
}

/** Codec and resolution for an error message, e.g. "HEVC 7680×4320". */
internal fun codecLabel(format: Format): String {
    val mime = format.sampleMimeType ?: format.containerMimeType
    val codec = CODEC_NAMES[mime] ?: mime?.substringAfter('/')?.uppercase() ?: "?"
    return if (format.width > 0 && format.height > 0) "$codec ${format.width}×${format.height}" else codec
}

private val DOLBY_VISION_CODEC_TAGS = setOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

private val CODEC_NAMES =
    mapOf(
        "video/hevc" to "HEVC",
        "video/avc" to "H.264",
        "video/av01" to "AV1",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/x-vnd.on2.vp8" to "VP8",
        "video/dolby-vision" to "Dolby Vision",
        "video/mpeg2" to "MPEG-2",
        "video/mp4v-es" to "MPEG-4",
    )
