package org.njarasoa.fijerena.core.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-15. */
class RetryWhenOnlineTest {
    @Test
    fun `offline then online returns`() =
        runTest {
            flowOf(false, false, true).awaitBackOnline()
        }

    @Test
    fun `online, lost, then back returns`() =
        runTest {
            flowOf(true, true, false, true).awaitBackOnline()
        }

    @Test
    fun `staying online never returns`() =
        runTest {
            val states = MutableSharedFlow<Boolean>()
            var returned = false
            val job = launch { states.awaitBackOnline().also { returned = true } }
            runCurrent()
            states.emit(true)
            states.emit(true)
            runCurrent()
            assertFalse(returned)
            states.emit(false)
            runCurrent()
            assertFalse(returned)
            states.emit(true)
            runCurrent()
            assertTrue(returned)
            job.cancel()
        }
}
