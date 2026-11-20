package org.njarasoa.fijerena.core.player.api

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveStreamFormatTest {
    @After
    fun tearDown() = LiveStreamFormat.clearForTest()

    @Test
    fun `mobile data gets HLS when the server allows it`() {
        assertEquals("m3u8", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf("m3u8", "ts", "rtmp"), panel = "h:80"))
        assertEquals("m3u8", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf("TS", "M3U8"), panel = "h:80"))
    }

    @Test
    fun `Wi-Fi gets MPEG-TS`() {
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = false, allowedFormats = listOf("m3u8", "ts"), panel = "h:80"))
    }

    @Test
    fun `mobile data gets MPEG-TS when the server does not list HLS`() {
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf("ts"), panel = "h:80"))
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = true, allowedFormats = emptyList(), panel = "h:80"))
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf(null), panel = "h:80"))
        // Before a login.
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = true, allowedFormats = null, panel = "h:80"))
    }

    @Test
    fun `a panel whose HLS failed gets MPEG-TS, other panels keep HLS`() {
        LiveStreamFormat.markHlsFailed("h:80")
        assertEquals("ts", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf("m3u8"), panel = "h:80"))
        assertEquals("m3u8", LiveStreamFormat.choose(onMobileData = true, allowedFormats = listOf("m3u8"), panel = "other:80"))
    }

    @Test
    fun `a live HLS address falls back to the same stream as MPEG-TS`() {
        assertEquals("http://h:80/live/u/p/12.ts", LiveStreamFormat.tsFallbackFor("http://h:80/live/u/p/12.m3u8"))
        assertEquals("http://h/live/u/p%2Fw/12.ts?token=1", LiveStreamFormat.tsFallbackFor("http://h/live/u/p%2Fw/12.M3U8?token=1"))
    }

    @Test
    fun `no fallback for other addresses`() {
        assertNull(LiveStreamFormat.tsFallbackFor("http://h/live/u/p/12.ts"))
        assertNull(LiveStreamFormat.tsFallbackFor("http://h/timeshift/u/p/60/2026-10-05:20-00/12.m3u8"))
        assertNull(LiveStreamFormat.tsFallbackFor("http://h/movie/u/p/12.mkv"))
        assertNull(LiveStreamFormat.tsFallbackFor("https://cdn.example.com/channel/index.m3u8"))
    }

    @Test
    fun `the panel is the host and port`() {
        assertEquals("h.example:8080", LiveStreamFormat.panelOf("http://H.example:8080/live/u/p/12.m3u8"))
        assertEquals("h.example", LiveStreamFormat.panelOf("http://h.example/live/u/p/12.m3u8"))
    }

    @Test
    fun `an Auto source builds MPEG-TS on Wi-Fi and before a login`() {
        // NetworkMonitor is not set up in a unit test: Wi-Fi, its default.
        val service = XtreamApiService(baseUrl = "http://h:80", username = "u", password = "p", streamOutputFormat = LiveStreamFormat.AUTO)
        assertEquals("http://h:80/live/u/p/12.ts", service.buildStreamUrl(12))
    }
}
