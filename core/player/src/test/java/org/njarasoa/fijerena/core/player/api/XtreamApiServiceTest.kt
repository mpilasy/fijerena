package org.njarasoa.fijerena.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamApiServiceTest {

    @Test
    fun testBuildStreamUrl_encodesCredentials() {
        val service = XtreamApiService(
            baseUrl = "http://example.com",
            username = "user/name",
            password = "pass/word"
        )

        val url = service.buildStreamUrl(123)
        // Expected URL encoding for "user/name" -> "user%2Fname"
        // Expected URL encoding for "pass/word" -> "pass%2Fword"
        assertEquals("http://example.com/live/user%2Fname/pass%2Fword/123.m3u8", url)
    }

    @Test
    fun testBuildVodStreamUrl_encodesCredentials() {
        val service = XtreamApiService(
            baseUrl = "http://example.com",
            username = "user@test.com",
            password = "p@ssw:rd"
        )

        val url = service.buildVodStreamUrl(456, "mkv")
        // "user@test.com" -> "user%40test.com"
        // "p@ssw:rd" -> "p%40ssw%3Ard"
        assertEquals("http://example.com/movie/user%40test.com/p%40ssw%3Ard/456.mkv", url)
    }

    @Test
    fun testBuildSeriesStreamUrl_encodesExtension() {
        val service = XtreamApiService(
            baseUrl = "http://example.com",
            username = "user",
            password = "pass"
        )

        // Extension with special chars (unlikely but good to cover)
        val url = service.buildSeriesStreamUrl(789, "mp4/bad")
        // "mp4/bad" -> "mp4%2Fbad"
        assertEquals("http://example.com/series/user/pass/789.mp4%2Fbad", url)
    }

    @Test
    fun testBuildEpisodeStreamUrl_encodesEpisodeId() {
        val service = XtreamApiService(
            baseUrl = "http://example.com",
            username = "user",
            password = "pass"
        )

        // Episode ID with special chars
        val url = service.buildEpisodeStreamUrl("ep/123", "mp4")
        // "ep/123" -> "ep%2F123"
        assertEquals("http://example.com/series/user/pass/ep%2F123.mp4", url)
    }
}
