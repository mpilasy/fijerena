package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncEngine

/**
 * [SyncManager] never runs two passes at once, and a request made while a pass runs gets a pass
 * after it. Thread races plan → 10: a request that came between the last loop check and the clear
 * of the running flag was lost; the flag is now read again after the clear.
 */
class SyncManagerPassTest {
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val engine = mockk<SyncEngine>()
    private var scope: CoroutineScope? = null

    @After
    fun tearDown() {
        scope?.cancel()
    }

    private fun manager(scope: CoroutineScope): SyncManager {
        this.scope = scope
        every { store.link } returns null
        every { engine.isLinked } returns true
        return SyncManager(
            mockk<Application>(relaxed = true),
            scope,
            store,
            engine,
            mockk<SyncApi>(relaxed = true),
            nowPlaying = MutableStateFlow(null),
            pendingPush = { SyncManager.PendingPush.NONE },
        )
    }

    @Test
    fun `a request made while a pass runs gets one more pass after it`() {
        val dispatcher = StandardTestDispatcher()
        val manager = manager(CoroutineScope(SupervisorJob() + dispatcher))
        val firstPassEnds = CompletableDeferred<Unit>()
        var passes = 0
        coEvery { engine.syncNow(any()) } coAnswers {
            if (++passes == 1) firstPassEnds.await()
            SyncEngine.Outcome(0, 0, 0, false)
        }

        manager.requestSync(0)
        dispatcher.scheduler.runCurrent()
        manager.requestSync(0)
        manager.requestSync(0)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, passes)

        firstPassEnds.complete(Unit)
        dispatcher.scheduler.runCurrent()
        // The two requests made during the first pass share one pass.
        assertEquals(2, passes)
    }
}
