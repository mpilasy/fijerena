package org.njarasoa.fijerena.core.network.utils

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cancelled [await] used to resume with the response anyway, which nobody read or closed, so its
 * pooled connection leaked. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-14.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpExtTest {
    private val callback = slot<Callback>()
    private val call =
        mockk<Call> {
            every { enqueue(capture(callback)) } just runs
            every { cancel() } just runs
        }
    private var bodyClosed = false

    private fun response(): Response {
        val source =
            object : ForwardingSource(Buffer().writeUtf8("{}")) {
                override fun close() {
                    bodyClosed = true
                    super.close()
                }
            }.buffer()
        val body =
            object : ResponseBody() {
                override fun contentType(): MediaType? = null

                override fun contentLength(): Long = 2

                override fun source(): BufferedSource = source
            }
        return Response
            .Builder()
            .request(Request.Builder().url("http://panel.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    @Test
    fun `a response that arrives after cancellation is closed`() =
        runTest(UnconfinedTestDispatcher()) {
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { call.await() }
            waiting.cancel()
            verify { call.cancel() }

            callback.captured.onResponse(call, response())

            assertTrue(bodyClosed)
        }

    @Test
    fun `a response that arrives in time is handed over open`() =
        runTest(UnconfinedTestDispatcher()) {
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { call.await() }
            val response = response()

            callback.captured.onResponse(call, response)

            assertSame(response, waiting.await())
            assertFalse(bodyClosed)
            response.close()
        }
}
