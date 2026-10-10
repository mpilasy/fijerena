package org.njarasoa.fijerena.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamApiServiceTest {
    @Test
    fun testBuildStreamUrl_encodesCredentials() {
        val service =
            XtreamApiService(
                baseUrl = "http://example.com",
                username = "user/name",
                password = "pass/word",
            )

        val url = service.buildStreamUrl(123)
        assertEquals("http://example.com/live/user%2Fname/pass%2Fword/123.m3u8", url)
    }

    @Test
    fun testBuildVodStreamUrl_encodesCredentials() {
        val service =
            XtreamApiService(
                baseUrl = "http://example.com",
                username = "user@test.com",
                password = "p@ssw:rd",
            )

        val url = service.buildVodStreamUrl(456, "mkv")
        assertEquals("http://example.com/movie/user%40test.com/p%40ssw%3Ard/456.mkv", url)
    }

    @Test
    fun testBuildSeriesStreamUrl_encodesExtension() {
        val service =
            XtreamApiService(
                baseUrl = "http://example.com",
                username = "user",
                password = "pass",
            )

        // Extension with special chars (unlikely but good to cover)
        val url = service.buildSeriesStreamUrl(789, "mp4/bad")
        assertEquals("http://example.com/series/user/pass/789.mp4%2Fbad", url)
    }

    @Test
    fun testBuildEpisodeStreamUrl_encodesEpisodeId() {
        val service =
            XtreamApiService(
                baseUrl = "http://example.com",
                username = "user",
                password = "pass",
            )

        val url = service.buildEpisodeStreamUrl("ep/123", "mp4")
        assertEquals("http://example.com/series/user/pass/ep%2F123.mp4", url)
    }

    @Test
    fun testBuildTimeshiftUrl_pathFormInTheLiveOutputFormat() {
        val hls = XtreamApiService(baseUrl = "http://example.com/", username = "user", password = "p/w")
        val ts = XtreamApiService(baseUrl = "example.com", username = "user", password = "pw", streamOutputFormat = "ts")

        assertEquals(
            "http://example.com/timeshift/user/p%2Fw/62/2026-10-10:17-13/386405.m3u8",
            hls.buildTimeshiftUrl(386405, "2026-10-10:17-13", 62),
        )
        assertEquals(
            "http://example.com/timeshift/user/pw/62/2026-10-10:17-13/386405.ts",
            ts.buildTimeshiftUrl(386405, "2026-10-10:17-13", 62),
        )
    }
}
