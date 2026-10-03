package org.njarasoa.fijerena.core.player.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    @Test
    fun `xtream stream paths mask user and password`() {
        listOf("live", "movie", "series", "timeshift").forEach { kind ->
            assertEquals(
                "http://panel.example:8080/$kind/***/***/12345.ts",
                Redact.text("http://panel.example:8080/$kind/alice/s3cret/12345.ts"),
            )
        }
    }

    @Test
    fun `timeshift paths keep the segments after the password`() {
        assertEquals(
            "http://p.example/timeshift/***/***/60/2026-10-02:20-00/42.ts",
            Redact.text("http://p.example/timeshift/alice/s3cret/60/2026-10-02:20-00/42.ts"),
        )
    }

    @Test
    fun `secret query parameters are masked whatever their case`() {
        assertEquals(
            "http://p.example/player_api.php?username=***&password=***&action=get_live_streams",
            Redact.text("http://p.example/player_api.php?username=alice&password=s3cret&action=get_live_streams"),
        )
        assertEquals(
            "https://jf.example/Videos/1/stream?api_key=***&Static=true",
            Redact.text("https://jf.example/Videos/1/stream?api_key=abc123&Static=true"),
        )
        assertEquals("https://x.example/a?api-key=***", Redact.text("https://x.example/a?api-key=abc"))
        assertEquals("https://x.example/a?X-Emby-Token=***", Redact.text("https://x.example/a?X-Emby-Token=abc"))
        assertEquals("https://x.example/a?x-emby-token=***", Redact.text("https://x.example/a?x-emby-token=abc"))
        assertEquals("https://x.example/a?b=1&access_token=***#frag", Redact.text("https://x.example/a?b=1&access_token=abc#frag"))
        assertEquals("https://x.example/a?TOKEN=*** next", Redact.text("https://x.example/a?TOKEN=abc next"))
        assertEquals("https://x.example/a?PassWord=***", Redact.text("https://x.example/a?PassWord=abc"))
    }

    @Test
    fun `userinfo is masked`() {
        assertEquals("http://***@host.example:8080/get.php", Redact.text("http://alice:s3cret@host.example:8080/get.php"))
        assertEquals("smb://***@nas/share", Redact.text("smb://alice@nas/share"))
    }

    @Test
    fun `secrets inside a longer message and stack trace are masked`() {
        val trace =
            "java.io.IOException: Unexpected response for http://p.example/live/alice/s3cret/1.m3u8\n" +
                "\tat Foo.bar(Foo.kt:1)\n" +
                "Caused by: java.lang.Exception: GET http://p.example/get.php?username=alice&password=s3cret failed"
        val redacted = Redact.text(trace)
        assertFalse(redacted.contains("alice"))
        assertFalse(redacted.contains("s3cret"))
        assertEquals(trace.lines().size, redacted.lines().size)
    }

    @Test
    fun `redacting twice gives the same result`() {
        val input = "http://alice:pw@p.example/movie/alice/s3cret/9.mkv?token=abc&password=x"
        val once = Redact.text(input)
        assertEquals(once, Redact.text(once))
    }

    @Test
    fun `text and urls without secrets are unchanged`() {
        listOf(
            "",
            "uncaught on main",
            "java.lang.IllegalStateException: boom\n\tat Foo.bar(Foo.kt:12)",
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            "https://jf.example/Items/123/Images/Primary?maxWidth=300&tag=abc",
            "http://p.example/live/1234.ts",
            "http://p.example/series/info",
            "https://x.example/a?tokens=1&password=",
            "user@example.com sent mail",
        ).forEach { assertEquals(it, Redact.text(it)) }
    }
}
