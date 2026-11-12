package org.njarasoa.fijerena.core.network.local

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture

/**
 * The parser on a real public playlist: `m3u/rec-index.m3u` is the iptv-org playlist
 * (https://iptv-org.github.io/iptv/index.m3u, the Unlicense), recorded 2026-11-11 with
 * scripts/record-provider-fixtures.py and trimmed to one entry of each shape: an `http-user-agent`
 * attribute with commas in it, an `http-referrer`, `#EXTVLCOPT` lines, and HLS, DASH and PHP stream
 * URLs. Every entry of this playlist is a live channel.
 */
class M3uRecordedPlaylistTest {
    private val entries = M3uParser.parse(fixture("m3u/rec-index.m3u").reader().buffered()).toList()

    @Test
    fun `every entry reads with its name, group, logo and guide id`() {
        assertEquals(
            listOf("00s Replay", "1+1 International", "1+1 Ukraina (1080p)", "2M Monde (360p)", "2TV (1080p)"),
            entries.map { it.name },
        )
        assertEquals(listOf("Movies", "General", "General", "General", "Music"), entries.map { it.groupTitle })
        assertEquals("https://img.invalid/LOY0rtp.png", entries[1].logo)
        assertEquals("2MMonde.ma@SD", entries[3].tvgId)
    }

    @Test
    fun `the stream URL is the line after the options`() {
        assertEquals("http://stream.invalid/georgia_play.php?id=1plus1international", entries[1].url)
        assertEquals("https://stream.invalid/live/eds/2m_monde/hls_video_ts_tuhawxpiemz257adfc/2m_monde.m3u8", entries[3].url)
    }

    @Test
    fun `every entry reads as live, the PHP page too`() {
        assertEquals(listOf(true, true, true, true, true), entries.map { it.isLive })
    }
}
