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
}
