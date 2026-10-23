package org.njarasoa.fijerena.core.player.config

import org.junit.Assert.assertEquals
import org.junit.Test

/** The VOD byte cap scales with the device's heap, within a floor and a ceiling. */
class VodBufferBytesTest {
    private val mb = 1024 * 1024

    @Test
    fun `a 512 MB large heap gets a 128 MB cap`() {
        assertEquals(128 * mb, NetworkBufferProfile.vodTargetBufferBytes(512))
    }

    @Test
    fun `a small heap keeps the 64 MB floor`() {
        assertEquals(NetworkBufferProfile.VOD_TARGET_BUFFER_BYTES, NetworkBufferProfile.vodTargetBufferBytes(192))
    }

    @Test
    fun `a huge heap stops at the ceiling`() {
        assertEquals(NetworkBufferProfile.VOD_TARGET_BUFFER_BYTES_MAX, NetworkBufferProfile.vodTargetBufferBytes(4096))
    }

    @Test
    fun `VOD keeps no back buffer, which would eat the byte cap`() {
        assertEquals(0, NetworkBufferProfile.WIFI_VOD_BACK_BUFFER_MS)
        assertEquals(0, NetworkBufferProfile.CELLULAR_VOD_BACK_BUFFER_MS)
    }
}
