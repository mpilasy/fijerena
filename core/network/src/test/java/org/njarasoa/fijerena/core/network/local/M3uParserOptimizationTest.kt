package org.njarasoa.fijerena.core.network.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class M3uParserOptimizationTest {
    @Test
    fun `parse correctly parses valid m3u content`() {
        val content =
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="CNN.us" tvg-name="CNN" tvg-logo="http://example.com/cnn.png" group-title="News",CNN
            http://example.com/cnn.m3u8
            #EXTINF:-1,BBC World
            http://example.com/bbc.m3u8
            #EXTINF:-1 group-title="Sports",ESPN
            http://example.com/espn.m3u8
            """.trimIndent()

        val entries = M3uParser.parse(content.reader().buffered()).toList()

        assertEquals(3, entries.size)

        with(entries[0]) {
            assertEquals("CNN", name)
            assertEquals("News", groupTitle)
            assertEquals("http://example.com/cnn.png", logo)
            assertEquals("CNN.us", tvgId)
            assertEquals("http://example.com/cnn.m3u8", url)
            assertTrue(isLive)
        }

        with(entries[1]) {
            assertEquals("BBC World", name)
            assertEquals("Uncategorized", groupTitle)
            assertEquals(null, logo)
            assertEquals(null, tvgId)
            assertEquals("http://example.com/bbc.m3u8", url)
        }

        with(entries[2]) {
            assertEquals("ESPN", name)
            assertEquals("Sports", groupTitle)
            assertEquals(null, logo)
            assertEquals(null, tvgId)
            assertEquals("http://example.com/espn.m3u8", url)
        }
    }

    @Test
    fun `parse handles quoted attributes correctly`() {
        val content =
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="channel 1" group-title="My Group",Channel 1
            http://example.com/1.m3u8
            """.trimIndent()

        val entries = M3uParser.parse(content.reader().buffered()).toList()
        assertEquals(1, entries.size)
        assertEquals("channel 1", entries[0].tvgId)
        assertEquals("My Group", entries[0].groupTitle)
    }

    @Test
    fun `parse benchmark`() {
        // Generate a large M3U file
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        repeat(5000) { i ->
            sb.append(
                "#EXTINF:-1 tvg-id=\"id$i\" tvg-name=\"Channel $i\" tvg-logo=\"http://logo.com/$i.png\" group-title=\"Group ${i % 10}\",Channel $i\n",
            )
            sb.append("http://stream.com/$i.m3u8\n")
        }
        val content = sb.toString()

        // Warmup
        repeat(5) { M3uParser.parse(content.reader().buffered()).toList() }

        val time =
            measureTimeMillis {
                repeat(10) {
                    M3uParser.parse(content.reader().buffered()).toList()
                }
            }
    }
}
