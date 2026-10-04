package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import java.io.IOException

/**
 * [SyncManager]'s socket lifecycle against a fake socket factory ([SyncApi.openSocket]) on a
 * virtual clock.
 *
 * F-12: with a server whose HTTP API works but whose WebSocket doesn't (a reverse proxy that
 * doesn't upgrade), the socket reconnected every 5 s forever, each time with a full sync pass —
 * the pass succeeded, so the shared retry delay kept resetting. F-26: a half-open socket is failed
 * by OkHttp's protocol pings (SyncApiSocketTest) and must then reconnect through the same backoff.
 * See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-12, F-26.
 */
class SyncManagerSocketTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler = dispatcher.scheduler
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val engine = mockk<SyncEngine>()
    private val api = mockk<SyncApi>()

    /** Every socket opened, with the virtual time it was opened at. */
    private val sockets = mutableListOf<Pair<WebSocket, Long>>()
    private lateinit var listener: WebSocketListener
    private lateinit var manager: SyncManager

    @Before
    fun setup() {
        every { store.link } returns SyncAccountStore.Link("https://sync.test", "acc", "dev", "token", ByteArray(32))
        every { store.cursor } returns 0
        every { engine.isLinked } returns true
        coEvery { engine.syncNow(any()) } returns SyncEngine.Outcome(0, 0, 0, false)
        every { api.openSocket(any(), any(), any()) } answers {
            listener = thirdArg()
            mockk<WebSocket>(relaxed = true).also { sockets += it to scheduler.currentTime }
        }
        manager = SyncManager(mockk<Application>(relaxed = true), scope, store, engine, api)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun failLatest() = listener.onFailure(sockets.last().first, IOException("502 from the proxy"), null)

    @Test
    fun `a socket that keeps failing reconnects with a doubling delay and no sync passes`() {
        manager.onForeground()
        scheduler.runCurrent()
        assertEquals(1, sockets.size)

        for (expected in listOf(5_000L, 10_000L, 20_000L, 40_000L)) {
            val failedAt = scheduler.currentTime
            val before = sockets.size
            failLatest()
            advance(expected - 1)
            assertEquals("no reconnect before $expected ms", before, sockets.size)
            advance(1)
            assertEquals("reconnect after $expected ms", before + 1, sockets.size)
            assertEquals(failedAt + expected, sockets.last().second)
        }
        // Only the pass the foreground asked for: reconnects don't request one (the server's head,
        // sent when a socket opens, triggers the catch-up).
        coVerify(exactly = 1) { engine.syncNow(any()) }
    }

    @Test
    fun `the reconnect delay stops growing at five minutes`() {
        manager.onForeground()
        scheduler.runCurrent()
        val gaps =
            List(9) {
                val failedAt = scheduler.currentTime
                failLatest()
                advance(5 * 60_000L)
                sockets.last().second - failedAt
            }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L, 300_000L), gaps)
    }

    @Test
    fun `a socket that opens resets the delay, pings, and stops pinging once it fails`() {
        manager.onForeground()
        scheduler.runCurrent()
        failLatest()
        advance(5_000)
        failLatest()
        advance(10_000)
        assertEquals(3, sockets.size)

        val open = sockets.last().first
        listener.onOpen(open, mockk<Response>(relaxed = true))
        advance(30_000)
        verify(exactly = 1) { open.send("ping") }

        // The socket dies without a close (what a missed protocol pong turns into): back to 5 s.
        val failedAt = scheduler.currentTime
        failLatest()
        advance(5_000)
        assertEquals(4, sockets.size)
        assertEquals(failedAt + 5_000, sockets.last().second)
        advance(60_000)
        verify(exactly = 1) { open.send("ping") }
    }

    @Test
    fun `a failure from a socket already replaced is ignored`() {
        manager.onForeground()
        scheduler.runCurrent()
        val first = sockets.single().first
        manager.onBackground()
        manager.onForeground()
        scheduler.runCurrent()
        assertEquals(2, sockets.size)

        listener.onFailure(first, IOException("late failure of the old socket"), null)
        advance(10 * 60_000L)

        assertEquals(2, sockets.size)
        verify { first.close(1000, any()) }
    }

    @Test
    fun `no reconnect once revoked, or once in the background`() {
        manager.onForeground()
        scheduler.runCurrent()
        listener.onClosed(sockets.last().first, 4001, "revoked")
        advance(10 * 60_000L)
        assertEquals(1, sockets.size)

        manager.onBackground()
        manager.onForeground()
        scheduler.runCurrent()
        manager.onBackground()
        failLatest()
        advance(10 * 60_000L)
        assertEquals(2, sockets.size)
    }

    @Test
    fun `two foreground signals never open two sockets`() {
        manager.onForeground()
        manager.onForeground()
        scheduler.runCurrent()
        assertEquals(1, sockets.size)
    }
}
