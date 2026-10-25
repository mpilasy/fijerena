package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private companion object {
        const val DELAY = 10_000L
    }
}
