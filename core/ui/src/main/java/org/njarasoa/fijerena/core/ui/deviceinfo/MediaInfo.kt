package org.njarasoa.fijerena.core.ui.deviceinfo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText
import kotlin.math.roundToInt

/**
 * The Video decoders and Audio output sections of Device info
 * (docs/plans/archive/20261004_device-info-screen-plan.md → P2). Call it off the main thread; a value the
 * device won't give shows "—".
 */
suspend fun mediaSections(context: Context): List<DeviceInfoSection> {
    val decoders = readOrNull(::readDecoders)
    currentCoroutineContext().ensureActive()
    val audio = readOrNull { readAudioOutput(context) }
    return listOf(
        DeviceInfoSection(R.string.device_info_section_video_decoders, decoderRows(decoders)),
        DeviceInfoSection(R.string.device_info_section_audio, audioRows(audio)),
    )
}

/** The video types listed, in order: short name (the row label) and MIME type. */
internal val VIDEO_TYPES =
    listOf(
        "AVC" to MediaFormat.MIMETYPE_VIDEO_AVC,
        "HEVC" to MediaFormat.MIMETYPE_VIDEO_HEVC,
        "AV1" to MediaFormat.MIMETYPE_VIDEO_AV1,
        "VP9" to MediaFormat.MIMETYPE_VIDEO_VP9,
        "Dolby Vision" to MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
    )

/** The passthrough formats checked, in order. */
internal val PASSTHROUGH_ENCODINGS =
    listOf(
        "AC3" to AudioFormat.ENCODING_AC3,
        "EAC3" to AudioFormat.ENCODING_E_AC3,
        "EAC3-JOC" to AudioFormat.ENCODING_E_AC3_JOC,
        "DTS" to AudioFormat.ENCODING_DTS,
        "DTS-HD" to AudioFormat.ENCODING_DTS_HD,
        "TrueHD" to AudioFormat.ENCODING_DOLBY_TRUEHD,
    )

private val HDR_PROFILE_NAMES =
    mapOf(
        MediaFormat.MIMETYPE_VIDEO_HEVC to
            listOf(
                CodecProfileLevel.HEVCProfileMain10 to "Main10",
                CodecProfileLevel.HEVCProfileMain10HDR10 to "HDR10",
                CodecProfileLevel.HEVCProfileMain10HDR10Plus to "HDR10+",
            ),
        MediaFormat.MIMETYPE_VIDEO_AV1 to
            listOf(
                CodecProfileLevel.AV1ProfileMain10 to "Main10",
                CodecProfileLevel.AV1ProfileMain10HDR10 to "HDR10",
                CodecProfileLevel.AV1ProfileMain10HDR10Plus to "HDR10+",
            ),
        MediaFormat.MIMETYPE_VIDEO_VP9 to
            listOf(
                CodecProfileLevel.VP9Profile2 to "Profile2",
                CodecProfileLevel.VP9Profile2HDR to "Profile2 HDR",
                CodecProfileLevel.VP9Profile2HDR10Plus to "Profile2 HDR10+",
            ),
    )

/** Dolby Vision profile constants and the profile number they stand for. */
private val DOLBY_VISION_PROFILES =
    listOf(
        CodecProfileLevel.DolbyVisionProfileDvavPer to 0,
        CodecProfileLevel.DolbyVisionProfileDvavPen to 1,
        CodecProfileLevel.DolbyVisionProfileDvheDer to 2,
        CodecProfileLevel.DolbyVisionProfileDvheDen to 3,
        CodecProfileLevel.DolbyVisionProfileDvheDtr to 4,
        CodecProfileLevel.DolbyVisionProfileDvheStn to 5,
        CodecProfileLevel.DolbyVisionProfileDvheDth to 6,
        CodecProfileLevel.DolbyVisionProfileDvheDtb to 7,
        CodecProfileLevel.DolbyVisionProfileDvheSt to 8,
        CodecProfileLevel.DolbyVisionProfileDvavSe to 9,
        CodecProfileLevel.DolbyVisionProfileDvav110 to 10,
    )

/** One decoder for one video type; null where the decoder doesn't say. */
internal data class DecoderInfo(
    val name: String,
    val mime: String,
    val hardware: Boolean,
    val maxWidth: Int?,
    val maxHeight: Int?,
    val maxFps: Int?,
    val profiles: List<String>,
)

/** What the current audio output takes without decoding: format names from [PASSTHROUGH_ENCODINGS]. */
internal data class AudioOutputInfo(
    val passthrough: List<String>,
    val maxChannels: Int,
)

/** A row per decoder under its type's short name; "No decoder" for a type without one, "—" per type if the list couldn't be read. */
internal fun decoderRows(decoders: List<DecoderInfo>?): List<DeviceInfoRow> =
    VIDEO_TYPES.flatMap { (label, mime) ->
        val forType = decoders?.filter { it.mime == mime }
        when {
            forType == null -> {
                listOf(DeviceInfoRow(UiText.DynamicString(label), missingValue()))
            }

            forType.isEmpty() -> {
                listOf(
                    DeviceInfoRow(UiText.DynamicString(label), UiText.StringResource(R.string.device_info_decoder_none)),
                )
            }

            else -> {
                forType.map { DeviceInfoRow(UiText.DynamicString(label), decoderValue(it)) }
            }
        }
    }

internal fun decoderValue(decoder: DecoderInfo): UiText =
    UiText.StringResource(
        if (decoder.hardware) R.string.device_info_decoder_hardware_format else R.string.device_info_decoder_software_format,
        decoder.name,
        decoderDetails(decoder),
    )

/** "3840×2160 @ 60 fps · Main10, HDR10": the technical part of a decoder's value. */
internal fun decoderDetails(decoder: DecoderInfo): String {
    val size =
        if (decoder.maxWidth != null && decoder.maxHeight != null) {
            "${decoder.maxWidth}×${decoder.maxHeight}" + decoder.maxFps?.let { " @ $it fps" }.orEmpty()
        } else {
            null
        }
    return listOfNotNull(size, decoder.profiles.joinToString(", ").ifEmpty { null })
        .joinToString(" · ")
        .ifEmpty { "—" }
}

/** Short names of the HDR and Dolby Vision profiles in [profiles] (`CodecProfileLevel.profile` values) for [mime]. */
internal fun hdrProfiles(
    mime: String,
    profiles: List<Int>,
): List<String> =
    if (mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) {
        DOLBY_VISION_PROFILES
            .filter { (profile, _) -> profile in profiles }
            .map { it.second }
            .let { numbers -> if (numbers.isEmpty()) emptyList() else listOf("DV " + numbers.joinToString(", ")) }
    } else {
        HDR_PROFILE_NAMES[mime]
            .orEmpty()
            .filter { (profile, _) -> profile in profiles }
            .map { it.second }
    }

internal fun audioRows(audio: AudioOutputInfo?): List<DeviceInfoRow> =
    listOf(
        infoRow(R.string.device_info_audio_passthrough, passthroughValue(audio)),
        infoRow(R.string.device_info_audio_max_channels, audio?.maxChannels?.toString()),
    )

private fun passthroughValue(audio: AudioOutputInfo?): UiText =
    when {
        audio == null -> missingValue()
        audio.passthrough.isEmpty() -> UiText.StringResource(R.string.device_info_audio_passthrough_none)
        else -> UiText.DynamicString(audio.passthrough.joinToString(", "))
    }

private fun readDecoders(): List<DecoderInfo> =
    MediaCodecList(MediaCodecList.REGULAR_CODECS)
        .codecInfos
        .filter { !it.isEncoder && !it.isAlias }
        .flatMap { codec ->
            VIDEO_TYPES.mapNotNull { (_, mime) ->
                codec.supportedTypes
                    .firstOrNull { it.equals(mime, ignoreCase = true) }
                    ?.let { type -> readDecoder(codec, type, mime) }
            }
        }

private fun readDecoder(
    codec: MediaCodecInfo,
    type: String,
    mime: String,
): DecoderInfo {
    val capabilities = readOrNull { codec.getCapabilitiesForType(type) }
    val video = capabilities?.videoCapabilities
    val size = video?.let { readOrNull { largestSize(it) } }
    val fps = size?.let { (width, height) -> readOrNull { video.getSupportedFrameRatesFor(width, height).upper.roundToInt() } }
    return DecoderInfo(
        name = codec.name,
        mime = mime,
        hardware = codec.isHardwareAccelerated,
        maxWidth = size?.first,
        maxHeight = size?.second,
        maxFps = fps,
        profiles = hdrProfiles(mime, capabilities?.profileLevels?.map { it.profile }.orEmpty()),
    )
}

/**
 * The widest and tallest size the decoder reports. A decoder that also takes portrait video
 * reports e.g. 4096 for both, a size it can't decode: then the tallest height at the widest width.
 */
private fun largestSize(video: MediaCodecInfo.VideoCapabilities): Pair<Int, Int> {
    val width = video.supportedWidths.upper
    val height = video.supportedHeights.upper
    return if (video.isSizeSupported(width, height)) width to height else width to video.getSupportedHeightsFor(width).upper
}

/** Media3's view of the output, the one the player uses to choose passthrough. */
@OptIn(UnstableApi::class)
private fun readAudioOutput(context: Context): AudioOutputInfo {
    val capabilities = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
    return AudioOutputInfo(
        passthrough = PASSTHROUGH_ENCODINGS.filter { capabilities.supportsEncoding(it.second) }.map { it.first },
        maxChannels = capabilities.maxChannelCount,
    )
}
