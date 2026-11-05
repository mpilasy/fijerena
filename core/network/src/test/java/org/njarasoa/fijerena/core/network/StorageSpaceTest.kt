package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Storage is low below 5% free, at most 500 MB, as Android counts it for WorkManager. */
class StorageSpaceTest {
    private val mb = 1024L * 1024

    @Test
    fun `small storage is low below 5 percent free`() {
        // A 4.3 GB Chromecast HD: low below about 220 MB.
        val total = 4400 * mb
        assertEquals(220 * mb, StorageSpace.lowThresholdBytes(total))
        assertTrue(StorageSpace.isLow(200 * mb, total))
        assertFalse(StorageSpace.isLow(900 * mb, total))
    }

    @Test
    fun `large storage is low below 500 MB free`() {
        val total = 64_000 * mb
        assertEquals(500 * mb, StorageSpace.lowThresholdBytes(total))
        assertTrue(StorageSpace.isLow(499 * mb, total))
        assertFalse(StorageSpace.isLow(600 * mb, total))
    }

    @Test
    fun `the storage error reads back as itself`() {
        val error = StoredError(ErrorKind.STORAGE_LOW, catalogue = true, raw = StorageSpace.rawText(150 * mb))
        assertEquals(error, StoredError.decode(error.encode()))
        assertEquals("Storage low: 150 MB free", error.raw)
    }
}
