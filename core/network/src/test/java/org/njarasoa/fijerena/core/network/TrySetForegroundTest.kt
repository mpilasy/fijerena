package org.njarasoa.fijerena.core.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.njarasoa.fijerena.core.network.xmltv.EpgFtsRebuildWorker
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * A refused foreground start must not fail a worker's run
 * (docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-11).
 */
class TrySetForegroundTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val refused = IllegalStateException("ForegroundServiceStartNotAllowedException")

    @Before
    fun setUp() {
        val context = mockk<Context>()
        every { context.filesDir } returns tmp.root
        CrashLog.install(context)
        CrashLog.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun aRefusedForegroundStartIsRecordedNotThrown() =
        runTest {
            val worker = mockk<CoroutineWorker>()
            coEvery { worker.getForegroundInfo() } returns mockk()
            coEvery { worker.setForeground(any()) } throws refused

            worker.trySetForeground("TestWorker")

            assertTrue(CrashLog.read().single().contains("TestWorker setForeground"))
        }

    @Test
    fun cancellationIsRethrown() =
        runTest {
            val worker = mockk<CoroutineWorker>()
            coEvery { worker.getForegroundInfo() } returns mockk()
            coEvery { worker.setForeground(any()) } throws CancellationException("stopped")

            try {
                worker.trySetForeground("TestWorker")
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                assertTrue(CrashLog.read().isEmpty())
            }
        }

    @Test
    fun ftsRebuildWorkerStillRebuildsWhenSetForegroundIsRefused() =
        runTest {
            val indexer = mockk<EpgIndexer>(relaxed = true)
            mockkObject(EpgIndexer.Companion)
            every { EpgIndexer.getInstance(any()) } returns indexer
            val worker = spyk(EpgFtsRebuildWorker(mockk(relaxed = true), mockk<WorkerParameters>(relaxed = true)))
            coEvery { worker.getForegroundInfo() } returns mockk()
            coEvery { worker.setForeground(any()) } throws refused

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { indexer.rebuildFtsAndUpdateState() }
        }
}
