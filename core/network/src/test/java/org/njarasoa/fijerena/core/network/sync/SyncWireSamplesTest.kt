package org.njarasoa.fijerena.core.network.sync

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture
import java.net.InetSocketAddress

/**
 * [SyncApi] and [SyncWire] against the sample messages under `fixtures/sync/`, served from a local
 * socket. The server's vitest suite (server/test/samples.test.ts) checks the same files against the
 * server, so the app and the server cannot drift (the provider data tests plan, PD-Q6). The samples
 * are written by hand from server/README.md and server/src/account.ts (2026-11-11).
 */
class SyncWireSamplesTest {
    private var pushedBody: String? = null

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                val name =
                    when {
                        path == "/info" -> "var-info-sample.json"
                        path == "/changes" && exchange.requestMethod == "POST" -> "var-push-response-sample.json"
                        path == "/changes" -> "var-pull-response-sample.json"
                        else -> "var-handoff-collected-sample.json"
                    }
                if (exchange.requestMethod == "POST") pushedBody = exchange.requestBody.readBytes().decodeToString()
                val body = sample(name).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    private val url = "http://127.0.0.1:${server.address.port}"
    private val api = SyncApi(OkHttpClient())

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun sample(name: String) = fixture("sync/$name")

    private fun tree(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `info reads the protocol`() {
        val info = runBlocking { api.info(url) }

        assertEquals("fijerena-sync", info.service)
        assertEquals(SyncWire.PROTOCOL, info.protocol)
        assertFalse(info.setupSecretRequired)
    }

    @Test
    fun `a push sends the sample request and reads the sample answer`() {
        val records = Json.decodeFromString<SyncWire.PushRequest>(sample("var-push-request-sample.json")).records

        val answer = runBlocking { api.push(url, "device-token", records) }

        assertEquals(tree(sample("var-push-request-sample.json")), tree(pushedBody!!))
        assertEquals(SyncWire.PushResponse(head = 3, accepted = 3, rejected = emptyList()), answer)
    }

    @Test
    fun `a pull reads every record with its tags, sequence and deletion`() {
        val page = runBlocking { api.pull(url, "device-token", since = 0) }

        assertEquals(3L, page.head)
        assertFalse(page.more)
        assertEquals(0L, page.remaining)
        assertFalse(page.resync)
        assertEquals(listOf("key-favourite", "key-setting", "key-history"), page.records.map { it.key })
        assertEquals(listOf(1L, 2L, 3L), page.records.map { it.seq })
        assertEquals(listOf(false, false, true), page.records.map { it.deleted })
        assertNull(page.records[1].providerTag)
        assertEquals("profile-tag-1", page.records[2].profileTag)
        assertEquals("sealed-history", page.records[2].payload)
    }

    @Test
    fun `the socket's head message reads`() {
        assertEquals(3L, SyncWire.parseHead(sample("var-head-sample.json")))
    }

    @Test
    fun `a collected handoff reads its sealed secret`() {
        val handoff = runBlocking { api.collectHandoff(url, "0123456789abcdef0123456789abcdef") }

        assertTrue(handoff.ready)
        assertEquals("sealed-pairing-code-and-account-key", handoff.sealed)
        assertEquals("sender-public-key", handoff.senderKey)
    }
}
