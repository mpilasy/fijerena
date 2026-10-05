package org.njarasoa.fijerena.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamStreamUrlTest {
    @Test
    fun `reads the username of each stream kind`() {
        assertEquals("main", XtreamStreamUrl.username("http://h:80/live/main/pw/12.m3u8"))
        assertEquals("main", XtreamStreamUrl.username("http://h/movie/main/pw/12.mkv"))
        assertEquals("main", XtreamStreamUrl.username("http://h/series/main/pw/99.mp4"))
        assertEquals("main", XtreamStreamUrl.username("http://h/timeshift/main/pw/60/2026-10-05:20-00/12.ts"))
    }

    @Test
    fun `decodes an encoded username`() {
        assertEquals("tahiry+2", XtreamStreamUrl.username("http://h/movie/tahiry%2B2/test/12.mkv"))
    }

    @Test
    fun `swaps the login and keeps the rest`() {
        assertEquals(
            "http://h:8080/movie/two/p%26w/12.mkv?x=1",
            XtreamStreamUrl.withLogin("http://h:8080/movie/main/pw/12.mkv?x=1", "two", "p&w"),
        )
    }

    @Test
    fun `the same stream on another login is the same stream`() {
        assertTrue(XtreamStreamUrl.sameStream("http://h/movie/main/pw/12.mkv", "http://h/movie/two/x/12.mkv"))
        assertFalse(XtreamStreamUrl.sameStream("http://h/movie/main/pw/12.mkv", "http://h/movie/main/pw/13.mkv"))
        assertTrue(XtreamStreamUrl.sameStream("https://j/Videos/a", "https://j/Videos/a"))
        assertFalse(XtreamStreamUrl.sameStream("https://j/Videos/a", "https://j/Videos/b"))
    }

    @Test
    fun `a URL without a login has none to read or swap`() {
        assertNull(XtreamStreamUrl.username("https://jellyfin.test/Videos/abc/stream?static=true"))
        assertNull(XtreamStreamUrl.withLogin("https://jellyfin.test/Videos/abc/stream", "two", "pw"))
    }
}
