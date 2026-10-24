package org.njarasoa.fijerena.core.network

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.work.CoroutineWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android's own foreground refusal is expected on a background start: logcat only, not Diagnostics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrySetForegroundRefusalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        val context = mockk<Context>()
        every { context.filesDir } returns tmp.root
        CrashLog.install(context)
        CrashLog.clear()
    }

    @Test
    fun androidsRefusalIsNotRecorded() =
        runTest {
            val worker = mockk<CoroutineWorker>()
            coEvery { worker.getForegroundInfo() } returns mockk()
            coEvery { worker.setForeground(any()) } throws ForegroundServiceStartNotAllowedException("not allowed")

            worker.trySetForeground("TestWorker")

            assertTrue(CrashLog.read().isEmpty())
        }
}
