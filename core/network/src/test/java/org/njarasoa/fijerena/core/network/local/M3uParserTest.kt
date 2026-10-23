package org.njarasoa.fijerena.core.network.local

import org.junit.Test
import java.io.File

class M3uParserTest {
    @Test
    fun testCorrectness() {
        val content =
            """
            #EXTM3U
            #EXTINF:-1 group-title="News",Channel 1
            http://example.com/1

            #EXTINF:-1,Channel 2
            #EXTVLCOPT:foo=bar
            http://example.com/2
            """.trimIndent()

        val entries = M3uParser.parse(content.reader().buffered()).toList()

        assert(entries.size == 2)
        assert(entries[0].name == "Channel 1")
        assert(entries[0].groupTitle == "News")
        assert(entries[0].url == "http://example.com/1")

        assert(entries[1].name == "Channel 2")
        assert(entries[1].groupTitle == "Uncategorized")
        assert(entries[1].url == "http://example.com/2")
    }

    @Test
    fun testLargeFile() {
        // Verify that the parser can handle a reasonably large file without error
        val entriesCount = 10000
        val file = File.createTempFile("test_m3u", ".m3u")
        file.deleteOnExit()

        file.bufferedWriter().use { writer ->
            writer.write("#EXTM3U\n")
            for (i in 1..entriesCount) {
                writer.write("#EXTINF:-1 tvg-id=\"id$i\" tvg-logo=\"logo$i\" group-title=\"Group $i\",Channel $i\n")
                writer.write("http://example.com/stream$i\n")
            }
        }

        val entries = file.bufferedReader().use { M3uParser.parse(it).toList() }
        assert(entries.size == entriesCount)
        assert(entries.last().name == "Channel $entriesCount")
    }
}
