package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Test

/** [EpgFileManager.isLowMemory]: a Chromecast HD counts as low on memory, a Shield or Bravia does not. */
class LowMemoryDeviceTest {
    private val mb = 1024L * 1024

    @Test
    fun `a Chromecast HD with 1,4 GB is low on memory`() {
        assertEquals(true, EpgFileManager.isLowMemory(1_439_364L * 1024))
    }

    @Test
    fun `a Shield or a Bravia with 3 GB is not`() {
        assertEquals(false, EpgFileManager.isLowMemory(3_016_708L * 1024))
        assertEquals(false, EpgFileManager.isLowMemory(2_997_848L * 1024))
    }

    @Test
    fun `2 GB exactly is not, and an unknown size is not either`() {
        assertEquals(false, EpgFileManager.isLowMemory(2048 * mb))
        assertEquals(false, EpgFileManager.isLowMemory(0))
    }
}
