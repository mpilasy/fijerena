package org.njarasoa.fijerena.core.player.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCallTrackerTest {
    private val request = Request.Builder().url("http://example.test/live/1.ts").build()
    private val background = mutableListOf<Runnable>()
    private val tracker = StreamCallTracker(runInBackground = { background += it })

    private fun factoryMaking(vararg calls: Call): Call.Factory {
        val queue = ArrayDeque(calls.toList())
        return Call.Factory { queue.removeFirst() }
    }

    @Test
    fun `cancelAll cancels every call the wrapped factory made, off the caller's thread`() {
        val first = mockk<Call>(relaxed = true)
        val second = mockk<Call>(relaxed = true)
        val factory = tracker.wrap(factoryMaking(first, second))
        factory.newCall(request)
        factory.newCall(request)

        tracker.cancelAll()
        verify(exactly = 0) { first.cancel() }

        background.single().run()
        verify { first.cancel() }
        verify { second.cancel() }
    }

    @Test
    fun `a call made after cancelAll is left alone`() {
        val old = mockk<Call>(relaxed = true)
        val next = mockk<Call>(relaxed = true)
        val factory = tracker.wrap(factoryMaking(old, next))
        factory.newCall(request)

        tracker.cancelAll()
        factory.newCall(request)
        background.single().run()

        verify { old.cancel() }
        verify(exactly = 0) { next.cancel() }
    }

    @Test
    fun `calls made elsewhere are never cancelled, and nothing runs with nothing open`() {
        val other = mockk<Call>(relaxed = true)
        every { other.request() } returns request

        tracker.cancelAll()

        assertEquals(0, background.size)
        verify(exactly = 0) { other.cancel() }
    }
}
