package org.njarasoa.fijerena.core.player.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [Redact.report]: a device report for the sync group names no server and no token (the device diagnostics sync plan). */
class RedactReportTest {
    @Test
    fun `the host of a URL is masked, the path keeps its own masks`() {
        assertEquals(
            "Source error https://***/live/***/***/1.ts?password=***",
            Redact.report("Source error https://panel.example:8080/live/alice/s3cret/1.ts?password=s3cret"),
        )
    }

    @Test
    fun `a URL with userinfo and no path loses its host too`() {
        val masked = Redact.report("GET http://alice:s3cret@10.0.0.2:8096 failed")

        assertEquals("GET http://*** failed", masked)
        assertFalse(masked, masked.contains("10.0.0.2"))
    }

    @Test
    fun `a Bearer token is masked`() {
        assertEquals("Authorization: Bearer *** refused", Redact.report("Authorization: Bearer eyJhbGciOi.payload.sig refused"))
        assertEquals("bearer ***", Redact.report("bearer abc123"))
    }

    @Test
    fun `masking twice gives the same text`() {
        val once = Redact.report("http://p.example/movie/a/b/2.mkv Bearer t0ken https://x.example/?token=1")

        assertEquals(once, Redact.report(once))
    }

    @Test
    fun `text with no URL and no token is unchanged`() {
        val text = "SocketTimeoutException: timeout after 30000 ms (guide refresh failed)"

        assertEquals(text, Redact.report(text))
    }

    @Test
    fun `a host the network stack names outside a URL is masked`() {
        assertEquals(
            "UnknownHostException: Unable to resolve host \"***\": No address associated with hostname",
            Redact.report("UnknownHostException: Unable to resolve host \"panel.example\": No address associated with hostname"),
        )
        assertEquals(
            "ConnectException: Failed to connect to ***/*** (port 8080)",
            Redact.report("ConnectException: Failed to connect to panel.example/203.0.113.7:8080 (port 8080)"),
        )
        assertEquals(
            "SSLPeerUnverifiedException: Hostname *** not verified",
            Redact.report("SSLPeerUnverifiedException: Hostname panel.example not verified"),
        )
    }

    @Test
    fun `an IPv4 address is masked, a version number is not`() {
        assertEquals("connect timed out to *** after 10 s", Redact.report("connect timed out to 192.168.68.21:5555 after 10 s"))
        assertEquals("Fijerena 0.9.20261112015711 · Media3 1.9.0", Redact.report("Fijerena 0.9.20261112015711 · Media3 1.9.0"))
    }
}
