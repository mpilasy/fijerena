package org.njarasoa.fijerena.core.player.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wait for Android to open the app's network, just after the device wakes. */
class NetworkAccessTest {
    @Test
    fun `an open network ends the wait at once`() =
        runTest {
            assertTrue(NetworkAccess.awaitOpen(MutableStateFlow(false), timeoutMs = 10_000))
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun `a blocked network ends the wait when it opens`() =
        runTest {
            val blocked = MutableStateFlow(true)
            var result: Boolean? = null
            launch { result = NetworkAccess.awaitOpen(blocked, timeoutMs = 10_000) }
            advanceTimeBy(800)
            runCurrent()
            assertNull(result)

            blocked.value = false
            runCurrent()
            assertEquals(true, result)
            assertEquals(800L, testScheduler.currentTime)
        }

    @Test
    fun `a network that stays blocked ends the wait after the limit`() =
        runTest {
            assertFalse(NetworkAccess.awaitOpen(MutableStateFlow(true), timeoutMs = 10_000))
            assertEquals(10_000L, testScheduler.currentTime)
        }

    @Test
    fun `a status stream that ends with no value ends the wait`() =
        runTest {
            assertFalse(NetworkAccess.awaitOpen(emptyFlow(), timeoutMs = 10_000))
        }
}
