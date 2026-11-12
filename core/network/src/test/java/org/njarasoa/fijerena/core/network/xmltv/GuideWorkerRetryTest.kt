package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import java.io.File

/**
 * A guide worker that hits a full disk finishes with the stored error and is not tried again (the
 * low storage plan, LS-Q5).
 */
class GuideWorkerRetryTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

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

    // The low storage plan, Phase 2: a rebuild needs 0.5 × epg_index.db and the 50 MB margin free.
    @Test
    fun `a search index rebuild with not enough space does not start and is not tried again`() =
        runTest {
            val mb = 1024L * 1024
            val indexer = mockk<EpgIndexer>(relaxed = true)
            every { indexer.isFtsStale() } returns true
            mockkObject(EpgIndexer.Companion)
            every { EpgIndexer.getInstance(any()) } returns indexer
            // A 200 MB guide index, so the rebuild needs 150 MB; 149 MB free on a storage that is not low.
            val context =
                mockk<Context>(relaxed = true).also {
                    every { it.filesDir } returns
                        mockk<File> {
                            every { totalSpace } returns 2048 * mb
                            every { usableSpace } returns 149 * mb
                        }
                    every { it.getDatabasePath(any()) } returns mockk<File> { every { length() } returns 200 * mb }
                }
            val worker = spyk(EpgFtsRebuildWorker(context, mockk<WorkerParameters>(relaxed = true)))
            coEvery { worker.getForegroundInfo() } returns mockk()
            coEvery { worker.setForeground(any()) } returns Unit

            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
            coVerify(exactly = 0) { indexer.rebuildFtsAndUpdateState() }
        }
}
