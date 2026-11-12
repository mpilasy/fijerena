package org.njarasoa.fijerena.core.network.xmltv

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.StoredError

/**
 * A guide worker that hits a full disk finishes with the stored error and is not tried again (the
 * low storage plan, LS-Q5).
 */
class GuideWorkerRetryTest {
    @Test
    fun `a full disk is not tried again`() {
        assertEquals(ListenableWorker.Result.failure(), failedRunResult(diskFull = true, runAttemptCount = 0, maxRetries = 5))
    }

    @Test
    fun `another failure is tried again until the last try`() {
        assertEquals(ListenableWorker.Result.retry(), failedRunResult(diskFull = false, runAttemptCount = 0, maxRetries = 5))
        assertEquals(ListenableWorker.Result.failure(), failedRunResult(diskFull = false, runAttemptCount = 5, maxRetries = 5))
    }

    @Test
    fun `a stored storage-low error among the sources counts as a full disk`() {
        val storageLow = StoredError(ErrorKind.STORAGE_LOW, raw = "write failed: ENOSPC").encode()
        val network = StoredError(ErrorKind.NETWORK).encode()

        assertTrue(EpgSyncWorker.anyStorageLow(listOf(network, storageLow)))
        assertFalse(EpgSyncWorker.anyStorageLow(listOf(network, null)))
        assertFalse(EpgSyncWorker.anyStorageLow(listOf("connection reset")))
    }
}
