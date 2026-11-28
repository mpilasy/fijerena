package org.njarasoa.fijerena.core.player.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4K and the codec order come from the hardware decoders, not from the device type. */
class CapabilitiesOfTest {
    private val avc1080 = VideoDecoder("video/avc", decodes4K = false)
    private val hevc1080 = VideoDecoder("video/hevc", decodes4K = false)
    private val av11080 = VideoDecoder("video/av01", decodes4K = false)
    private val hevc4K = VideoDecoder("video/hevc", decodes4K = true)
    private val av14K = VideoDecoder("video/av01", decodes4K = true)

    @Test
    fun `a Chromecast HD with HEVC and AV1 up to 1080p stays at 1080p`() {
        val caps = capabilitiesOf(DeviceType.CHROMECAST_TV, listOf(avc1080, hevc1080, av11080))

        assertFalse(caps.supports4K)
        assertEquals(1920 to 1080, caps.maxResolution)
        assertEquals(listOf("video/av01", "video/hevc", "video/avc"), caps.preferredCodecs)
    }

    @Test
    fun `a box the app does not know gets 4K and AV1 first when its decoders do`() {
        val caps = capabilitiesOf(DeviceType.GENERIC_TV, listOf(avc1080, hevc4K, av14K))

        assertTrue(caps.supports4K)
        assertEquals(3840 to 2160, caps.maxResolution)
        assertEquals(listOf("video/av01", "video/hevc", "video/avc"), caps.preferredCodecs)
    }

    @Test
    fun `a Shield with no AV1 decoder prefers HEVC`() {
        val caps = capabilitiesOf(DeviceType.NVIDIA_SHIELD, listOf(avc1080, hevc4K))

        assertTrue(caps.supports4K)
        assertFalse(caps.supportsAv1)
        assertEquals(listOf("video/hevc", "video/avc"), caps.preferredCodecs)
    }

    @Test
    fun `a Bravia with a hardware AV1 decoder prefers AV1`() {
        val caps = capabilitiesOf(DeviceType.SONY_BRAVIA, listOf(avc1080, hevc4K, av14K))

        assertEquals(listOf("video/av01", "video/hevc", "video/avc"), caps.preferredCodecs)
    }

    @Test
    fun `a phone stays at 1080p and H264 even with 4K decoders`() {
        val caps = capabilitiesOf(DeviceType.GENERIC_MOBILE, listOf(avc1080, hevc4K, av14K))

        assertFalse(caps.supports4K)
        assertEquals(1920 to 1080, caps.maxResolution)
        assertEquals(listOf("video/avc"), caps.preferredCodecs)
    }

    @Test
    fun `no hardware decoder found leaves 1080p and H264`() {
        val caps = capabilitiesOf(DeviceType.GENERIC_TV, emptyList())

        assertFalse(caps.supports4K)
        assertEquals(1920 to 1080, caps.maxResolution)
        assertEquals(listOf("video/avc"), caps.preferredCodecs)
    }
}
