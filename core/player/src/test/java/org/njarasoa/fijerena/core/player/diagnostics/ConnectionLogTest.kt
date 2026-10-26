package org.njarasoa.fijerena.core.player.diagnostics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A failed server call shows in Diagnostics, logins masked, the last [ConnectionLog.MAX_ENTRIES] kept. */
class ConnectionLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setup() {
        ConnectionLog.installAt(tmp.newFile("connections.log"))
    }

    @After
    fun tearDown() {
        ConnectionLog.clear()
    }

    @Test
    fun `a call nobody answers is recorded with its address, the password masked`() {
        val client = HttpClient(OkHttp) { recordFailedCalls() }

        runBlocking { runCatching { client.get("http://127.0.0.1:1/player_api.php?username=u&password=secret&action=get_vod_info") } }

        val entry = ConnectionLog.read().single()
        assertTrue(entry, entry.contains("No answer from 127.0.0.1"))
        assertTrue(entry, entry.contains("action=get_vod_info"))
        assertFalse(entry, entry.contains("secret"))
    }

    @Test
    fun `only the newest entries are kept, newest first`() {
        repeat(ConnectionLog.MAX_ENTRIES + 5) { i ->
            ConnectionLog.record("http://panel.example/player_api.php", java.io.IOException("try $i"), nowMs = i.toLong())
        }

        val entries = ConnectionLog.read()
        assertEquals(ConnectionLog.MAX_ENTRIES, entries.size)
        assertTrue(entries.first().contains("try ${ConnectionLog.MAX_ENTRIES + 4}"))
    }

    @Test
    fun `a file past twice the limit is trimmed back to the newest entries`() {
        val file = tmp.newFile("trimmed.log")
        ConnectionLog.installAt(file)
        repeat(ConnectionLog.MAX_ENTRIES * 2 + 1) { i ->
            ConnectionLog.record("http://panel.example/player_api.php", java.io.IOException("try $i"), nowMs = i.toLong())
        }

        val stored = file.readText().split(ConnectionLog.ENTRY_SEPARATOR).filter { it.isNotBlank() }
        assertEquals(ConnectionLog.MAX_ENTRIES, stored.size)
        assertTrue(ConnectionLog.read().first().contains("try ${ConnectionLog.MAX_ENTRIES * 2}"))
    }

    @Test
    fun `the stack trace follows the short part, capped and masked`() {
        val error = java.io.IOException("refused http://panel.example/live/user/secret/1.ts")
        ConnectionLog.record("http://panel.example/player_api.php?username=u&password=secret", error)

        val (short, trace) = ConnectionLog.read().single().split(ConnectionLog.TRACE_SEPARATOR)
        assertTrue(short, short.endsWith("IOException: refused http://panel.example/live/***/***/1.ts"))
        assertTrue(trace, trace.startsWith("java.io.IOException"))
        assertTrue(trace, trace.contains("ConnectionLogTest"))
        assertTrue(trace.lines().size <= ConnectionLog.MAX_TRACE_LINES)
        assertFalse(trace, trace.contains("secret"))
    }
}
