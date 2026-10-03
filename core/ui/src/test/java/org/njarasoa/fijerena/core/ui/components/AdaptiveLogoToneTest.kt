package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A title logo's tone (does it need the light plate?) and the per-URL cache that remembers it. */
class AdaptiveLogoToneTest {
    private fun argb(
        alpha: Int,
        grey: Int,
    ) = (alpha shl 24) or (grey shl 16) or (grey shl 8) or grey

    @Test
    fun `a near-black opaque logo is dark, a white one is not`() {
        assertTrue(isDarkPixels(IntArray(256) { argb(0xFF, 0x10) }))
        assertFalse(isDarkPixels(IntArray(256) { argb(0xFF, 0xF0) }))
    }

    @Test
    fun `transparent pixels don't pull a light wordmark toward dark`() {
        val mostlyEmpty = IntArray(256) { if (it < 16) argb(0xFF, 0xF0) else argb(0x00, 0x00) }
        assertFalse(isDarkPixels(mostlyEmpty))
    }

    @Test
    fun `a fully transparent image is not dark`() {
        assertFalse(isDarkPixels(IntArray(256) { argb(0x10, 0x00) }))
    }

    @Test
    fun `the cache returns the stored tone and nothing for an unseen logo`() {
        LogoToneCache.put("https://img/dark-logo.png", true)
        LogoToneCache.put("https://img/light-logo.png", false)
        assertEquals(true, LogoToneCache.get("https://img/dark-logo.png"))
        assertEquals(false, LogoToneCache.get("https://img/light-logo.png"))
        assertNull(LogoToneCache.get("https://img/never-seen.png"))
    }

    @Test
    fun `the cache drops the least recently used logo past its bound`() {
        LogoToneCache.put("https://img/kept.png", true)
        repeat(300) { i ->
            LogoToneCache.put("https://img/filler-$i.png", false)
            if (i % 100 == 0) LogoToneCache.get("https://img/kept.png")
        }
        assertEquals(true, LogoToneCache.get("https://img/kept.png"))
        assertNull(LogoToneCache.get("https://img/filler-0.png"))
        assertEquals(false, LogoToneCache.get("https://img/filler-299.png"))
    }
}
