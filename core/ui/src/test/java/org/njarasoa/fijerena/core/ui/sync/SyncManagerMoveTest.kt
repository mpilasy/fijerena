package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.WebSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.MoveState
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncApiException
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import org.njarasoa.fijerena.core.network.sync.SyncServerMove

/**
 * [SyncManager.startMove] (the sync server move plan → App interface): the move runs with the
 * socket closed and inside [SyncEngine.exclusive], so no pass runs during it; after the switch the
 * socket opens to the new server and a pass runs.
 */
class SyncManagerMoveTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler = dispatcher.scheduler
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val engine = mockk<SyncEngine>()
    private val api = mockk<SyncApi>()
    private val mover = mockk<SyncServerMove>()
    private val sockets = mutableListOf<Pair<String, WebSocket>>()
    private var serverUrl = "https://old.test"
    private val moveEnds = CompletableDeferred<Unit>()
    private var inExclusive = false
    private val passesInExclusive = mutableListOf<Boolean>()
    private lateinit var manager: SyncManager

    @Before
    fun setup() {
        every { store.link } answers { SyncAccountStore.Link(serverUrl, "acc", "dev", "token", ByteArray(32)) }
        every { store.previousServer } returns null
        every { store.cursor } returns 0
        every { engine.isLinked } returns true
        coEvery { engine.syncNow(any()) } answers {
            passesInExclusive += inExclusive
            SyncEngine.Outcome(0, 0, 0, false)
        }
        coEvery { engine.exclusive<Unit>(any()) } coAnswers {
            inExclusive = true
            try {
                firstArg<suspend () -> Unit>().invoke()
            } finally {
                inExclusive = false
            }
        }
        coEvery { api.devices(any(), any()) } returns emptyList()
        every { api.openSocket(any(), any(), any()) } answers {
            mockk<WebSocket>(relaxed = true).also { sockets += firstArg<String>() to it }
        }
        every { mover.unfinished } returns null
        manager =
            SyncManager(
                mockk<Application>(relaxed = true),
                scope,
                store,
                engine,
                api,
                nowPlaying = kotlinx.coroutines.flow.MutableStateFlow(null),
                pendingPush = { SyncManager.PendingPush.NONE },
                mover = mover,
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the move runs with the socket closed, then the socket opens to the new server and a pass runs`() {
        coEvery { mover.run("https://new.test", "secret", any()) } coAnswers {
            val onState = thirdArg<(MoveState) -> Unit>()
            onState(MoveState.Running(MoveState.Stage.COPYING, 10, 20))
            moveEnds.await()
            serverUrl = "https://new.test"
            onState(MoveState.Done)
        }
        manager.onForeground()
        scheduler.runCurrent()
        assertEquals(listOf("https://old.test"), sockets.map { it.first })

        manager.startMove("https://new.test", "secret")
        scheduler.runCurrent()

        verify { sockets.single().second.close(any(), any()) }
        assertEquals(MoveState.Running(MoveState.Stage.COPYING, 10, 20), manager.move.value)
        // A second start while the move runs does nothing.
        manager.startMove("https://other.test", null)
        scheduler.runCurrent()
        coVerify(exactly = 1) { mover.run(any(), any(), any()) }

        moveEnds.complete(Unit)
        scheduler.runCurrent()

        assertEquals(MoveState.Done, manager.move.value)
        assertEquals("https://new.test", sockets.last().first)
        assertTrue(passesInExclusive.none { it })
        coVerify(exactly = 2) { engine.syncNow(any()) }
    }

    @Test
    fun `a move that stops shows its error`() {
        coEvery { mover.run(any(), any(), any()) } throws SyncApiException(0, "Sync server new.test unreachable")

        manager.startMove("https://new.test", null)
        scheduler.runCurrent()

        assertEquals(MoveState.Failed("Sync server new.test unreachable"), manager.move.value)
    }
}
