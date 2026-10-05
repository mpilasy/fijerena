package org.njarasoa.fijerena.core.ui.deviceinfo

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText

class MediaInfoTest {
    // UiText.StringResource has no equals: compare a readable form of it.
    private fun UiText.text(): String =
        when (this) {
            is UiText.DynamicString -> value
            is UiText.StringResource -> "res:$resId" + args.joinToString("") { "|$it" }
        }

    private fun res(
        id: Int,
        vararg args: Any,
    ): String = UiText.StringResource(id, *args).text()

    private fun DeviceInfoRow.text(): Pair<String, String> = label.text() to value.text()

    private val shieldHevc =
        DecoderInfo(
            name = "OMX.Nvidia.h265.decode",
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
            hardware = true,
            maxWidth = 3840,
            maxHeight = 2160,
            maxFps = 60,
            profiles = listOf("Main10", "HDR10"),
        )
    private val softwareHevc =
        DecoderInfo(
            name = "c2.android.hevc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
            hardware = false,
            maxWidth = 4096,
            maxHeight = 2304,
            maxFps = null,
            profiles = emptyList(),
        )
    private val avc =
        DecoderInfo(
            name = "OMX.Nvidia.h264.decode",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            hardware = true,
            maxWidth = 4096,
            maxHeight = 2160,
            maxFps = 30,
            profiles = emptyList(),
        )
    private val dolbyVision =
        DecoderInfo(
            name = "OMX.Nvidia.DOVI.decode",
            mime = MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
            hardware = true,
            maxWidth = 3840,
            maxHeight = 2160,
            maxFps = 60,
            profiles = listOf("DV 5, 8"),
        )

    @Test
    fun `decoder rows list each decoder under its type in a fixed order, and a type with none says so`() {
        val rows = decoderRows(listOf(dolbyVision, softwareHevc, shieldHevc, avc)).map { it.text() }

        val none = res(R.string.device_info_decoder_none)
        assertEquals(
            listOf(
                "AVC" to res(R.string.device_info_decoder_hardware_format, "OMX.Nvidia.h264.decode", "4096×2160 @ 30 fps"),
                "HEVC" to res(R.string.device_info_decoder_software_format, "c2.android.hevc.decoder", "4096×2304"),
                "HEVC" to res(R.string.device_info_decoder_hardware_format, "OMX.Nvidia.h265.decode", "3840×2160 @ 60 fps · Main10, HDR10"),
                "AV1" to none,
                "VP9" to none,
                "Dolby Vision" to
                    res(R.string.device_info_decoder_hardware_format, "OMX.Nvidia.DOVI.decode", "3840×2160 @ 60 fps · DV 5, 8"),
            ),
            rows,
        )
    }

    @Test
    fun `an unreadable codec list shows the missing value for every type`() {
        val rows = decoderRows(null).map { it.text() }

        val missing = missingValue().text()
        assertEquals(listOf("AVC", "HEVC", "AV1", "VP9", "Dolby Vision").map { it to missing }, rows)
    }

    @Test
    fun `decoder details without a size show the profiles, and with nothing a dash`() {
        assertEquals("HDR10", decoderDetails(shieldHevc.copy(maxWidth = null, profiles = listOf("HDR10"))))
        assertEquals("—", decoderDetails(softwareHevc.copy(maxHeight = null)))
    }

    @Test
    fun `HEVC profiles keep only Main10 and the HDR ones`() {
        val profiles =
            listOf(
                CodecProfileLevel.HEVCProfileMain,
                CodecProfileLevel.HEVCProfileMain10HDR10Plus,
                CodecProfileLevel.HEVCProfileMain10,
                CodecProfileLevel.HEVCProfileMain10HDR10,
                CodecProfileLevel.HEVCProfileMain10,
            )

        assertEquals(listOf("Main10", "HDR10", "HDR10+"), hdrProfiles(MediaFormat.MIMETYPE_VIDEO_HEVC, profiles))
    }

    @Test
    fun `AV1 and VP9 HDR profiles get their own names`() {
        assertEquals(
            listOf("Main10", "HDR10"),
            hdrProfiles(
                MediaFormat.MIMETYPE_VIDEO_AV1,
                listOf(CodecProfileLevel.AV1ProfileMain8, CodecProfileLevel.AV1ProfileMain10, CodecProfileLevel.AV1ProfileMain10HDR10),
            ),
        )
        assertEquals(
            listOf("Profile2", "Profile2 HDR"),
            hdrProfiles(
                MediaFormat.MIMETYPE_VIDEO_VP9,
                listOf(CodecProfileLevel.VP9Profile0, CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile2HDR),
            ),
        )
    }

    @Test
    fun `Dolby Vision profiles become one DV token with the profile numbers in order`() {
        val profiles =
            listOf(
                CodecProfileLevel.DolbyVisionProfileDvheSt,
                CodecProfileLevel.DolbyVisionProfileDvheStn,
                CodecProfileLevel.DolbyVisionProfileDvheDtr,
            )

        assertEquals(listOf("DV 4, 5, 8"), hdrProfiles(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, profiles))
        assertEquals(emptyList<String>(), hdrProfiles(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, emptyList()))
    }

    @Test
    fun `AVC lists no HDR profiles`() {
        assertEquals(emptyList<String>(), hdrProfiles(MediaFormat.MIMETYPE_VIDEO_AVC, listOf(CodecProfileLevel.AVCProfileHigh10)))
    }

    @Test
    fun `audio rows list the passthrough formats and the channel count`() {
        val rows = audioRows(AudioOutputInfo(listOf("AC3", "EAC3", "EAC3-JOC"), 8)).map { it.text() }

        assertEquals(
            listOf(
                res(R.string.device_info_audio_passthrough) to "AC3, EAC3, EAC3-JOC",
                res(R.string.device_info_audio_max_channels) to "8",
            ),
            rows,
        )
    }

    @Test
    fun `audio rows say none without passthrough and dash when unreadable`() {
        assertEquals(
            res(R.string.device_info_audio_passthrough_none),
            audioRows(AudioOutputInfo(emptyList(), 2))[0].value.text(),
        )
        assertEquals(
            listOf(missingValue().text(), missingValue().text()),
            audioRows(null).map { it.value.text() },
        )
    }
}
