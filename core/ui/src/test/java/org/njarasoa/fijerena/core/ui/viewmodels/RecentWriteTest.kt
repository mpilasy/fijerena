package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

/** A live channel opened full screen from a preview goes into Recent at once, not after the watch delay. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentWriteTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val recentWrite = RecentWrite(scope, dispatcher)
    private var writes = 0
    private val write: suspend () -> Unit = { writes++ }

    @Test
    fun `the write waits for the delay`() {
        recentWrite.schedule(write, DELAY)
        scope.advanceTimeBy(DELAY - 1)
        assertEquals(0, writes)
        scope.advanceTimeBy(2)
        assertEquals(1, writes)
    }

    @Test
    fun `now writes a waiting write at once, and only once`() {
        recentWrite.schedule(write, DELAY)
        recentWrite.now()
        scope.runCurrent()
        assertEquals(1, writes)
        scope.advanceTimeBy(DELAY * 2)
        assertEquals(1, writes)
    }

    @Test
    fun `now before the load is ready makes that load write at once`() {
        recentWrite.loadStarted()
        recentWrite.now()
        recentWrite.schedule(write, DELAY)
        scope.runCurrent()
        assertEquals(1, writes)
    }

    @Test
    fun `a new load drops a now asked for the load before`() {
        recentWrite.now()
        recentWrite.loadStarted()
        recentWrite.schedule(write, DELAY)
        scope.runCurrent()
        assertEquals(0, writes)
    }

    @Test
    fun `cancel drops the waiting write`() {
        recentWrite.schedule(write, DELAY)
        recentWrite.cancel()
        recentWrite.now()
        scope.advanceTimeBy(DELAY * 2)
        assertEquals(0, writes)
    }

    @Test
    fun `a load cancelled before it schedules writes nothing`() {
        val load = Job()
        load.cancel()
        recentWrite.schedule(write, DELAY, load)
        scope.advanceTimeBy(DELAY * 2)
        assertEquals(0, writes)
    }

    @Test
    fun `schedules from two threads at once leave one write`() {
        repeat(ROUNDS) {
            val start = CyclicBarrier(2)
            val threads =
                List(2) {
                    thread {
                        start.await()
                        recentWrite.schedule(write, DELAY)
                    }
                }
            threads.forEach { it.join() }
        }
        scope.advanceTimeBy(DELAY * 2)
        assertEquals(1, writes)
    }

    private companion object {
        const val DELAY = 10_000L
        const val ROUNDS = 200
    }
}
