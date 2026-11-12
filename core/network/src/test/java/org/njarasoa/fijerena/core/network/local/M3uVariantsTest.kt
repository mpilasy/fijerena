package org.njarasoa.fijerena.core.network.local

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture

/**
 * [M3uParser] on the hand-written `m3u/var-playlist-*` files. Each one shows a quirk of playlists
 * in the field (the extended M3U format has no formal standard; the iptv-org playlists and VLC set
 * the common form): a byte-order mark, CRLF line ends, no `#EXTM3U` header, a guide URL in the
 * header, commas inside quotes and in the name, an empty `group-title`, `#EXTVLCOPT` lines, and live
 * stream URLs with a query.
 */
class M3uVariantsTest {
    private fun read(name: String) = M3uParser.parse(fixture("m3u/$name").reader().buffered()).toList()

    private val twoChannels = listOf("News Channel", "Music Channel")

    @Test
    fun `a byte-order mark before the header`() {
        assertEquals(twoChannels, read("var-playlist-bom.m3u").map { it.name })
    }

    @Test
    fun `a byte-order mark before the first entry, with no header`() {
        assertEquals(listOf("News Channel"), read("var-playlist-bom-no-header.m3u").map { it.name })
    }

    @Test
    fun `CRLF line ends leave no CR in the URL`() {
        val entries = read("var-playlist-crlf.m3u")
        assertEquals(twoChannels, entries.map { it.name })
        assertEquals(listOf("https://stream.invalid/news/index.m3u8", "https://stream.invalid/music/index.m3u8"), entries.map { it.url })
    }

    @Test
    fun `a playlist without the EXTM3U header`() {
        val entries = read("var-playlist-no-header.m3u")
        assertEquals(twoChannels, entries.map { it.name })
        assertEquals(listOf("News", "Music"), entries.map { it.groupTitle })
    }

    @Test
    fun `a guide URL in the header does not change the entries`() {
        val entries = read("var-playlist-x-tvg-url.m3u")
        assertEquals(twoChannels, entries.map { it.name })
        assertEquals(listOf("news.example", "music.example"), entries.map { it.tvgId })
    }

    @Test
    fun `a comma inside quotes is not the start of the name`() {
        val news = read("var-playlist-attributes.m3u").first()
        assertEquals("News, Weather and Sport", news.name)
        assertEquals("News, Weather", news.groupTitle)
        assertEquals("news.example", news.tvgId)
    }

    @Test
    fun `an empty group-title is Uncategorized`() {
        assertEquals("Uncategorized", read("var-playlist-attributes.m3u")[1].groupTitle)
    }

    @Test
    fun `EXTVLCOPT lines between the entry and its URL`() {
        val film = read("var-playlist-attributes.m3u")[2]
        assertEquals("Film Channel", film.name)
        assertEquals("https://img.invalid/film.png", film.logo)
        assertEquals("https://stream.invalid/film/index.m3u8", film.url)
    }

    @Test
    fun `a PHP page or a stream URL with a query is live, a film file is not`() {
        val entries = read("var-playlist-live-urls.m3u")
        assertEquals(
            listOf(
                "PHP Page" to true,
                "HLS With Query" to true,
                "TS With Query" to true,
                "Film File" to false,
                "Film File With Query" to false,
            ),
            entries.map { it.name to it.isLive },
        )
    }
}
