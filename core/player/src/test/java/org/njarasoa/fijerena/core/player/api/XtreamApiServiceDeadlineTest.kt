package org.njarasoa.fijerena.core.player.api

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.XtreamStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * R-14: a panel that trickles bytes never trips OkHttp's per-read timeout, so a metadata call held
 * its screen on a spinner forever. Metadata calls now have an overall deadline; the catalogue
 * downloads keep only the per-read timeouts. Run against a local server that sends its body one
 * byte every [BYTE_GAP_MS], longer in all than the deadline. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-14.
 */
class XtreamApiServiceDeadlineTest {
    private val server = ServerSocket(0)
    private val service =
        XtreamApiService(
            baseUrl = "http://127.0.0.1:${server.localPort}",
            username = "user",
            password = "pass",
            metadataTimeoutMs = DEADLINE_MS,
        )

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client =
                    try {
                        server.accept()
                    } catch (_: IOException) {
                        null
                    }
                client?.let { thread(isDaemon = true) { trickle(it) } }
            }
        }
    }

    private fun trickle(socket: Socket) {
        socket.use {
            try {
                val input = it.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) Unit
                val out = it.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${BODY.length}\r\n\r\n".toByteArray())
                out.flush()
                for (byte in BODY.toByteArray()) {
                    Thread.sleep(BYTE_GAP_MS)
                    out.write(byte.toInt())
                    out.flush()
                }
            } catch (_: IOException) {
                // The client gave up: what the deadline is for.
            }
        }
    }

    @After
    fun tearDown() {
        service.close()
        server.close()
    }

    @Test
    fun `a metadata call that outruns its deadline fails as a timeout`() {
        assertThrows(HttpRequestTimeoutException::class.java) {
            runBlocking { service.getVodCategories() }
        }
    }

    @Test
    fun `a catalogue download has no deadline`() {
        val streams = runBlocking { service.getVodStreams() }

        assertEquals(emptyList<XtreamStream>(), streams)
    }

    private companion object {
        const val DEADLINE_MS = 1_000L
        const val BYTE_GAP_MS = 500L

        /** Four bytes, two seconds: twice the deadline, far within the 30 s read timeout. */
        const val BODY = "[  ]"
    }
}
