package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

class EpgChannelMatcherTest {

    private fun createStream(id: Int, name: String, epgId: String? = null): XtreamStreamEntity {
        return XtreamStreamEntity(
            streamId = id,
            name = name,
            categoryId = "1",
            providerId = 1L,
            epgChannelId = epgId,
            streamType = "live",
            streamIcon = "",
            num = id,
            added = "0",
            type = XtreamStreamEntity.TYPE_LIVE
        )
    }

    @Test
    fun `match by exact epgChannelId`() {
        val streams = listOf(createStream(1, "Channel 1", "epg_1"))
        val matcher = EpgChannelMatcher(streams)

        val match = matcher.match("epg_1", "Unknown Name")
        assertNotNull(match)
        assertEquals(1, match?.streamId)
    }

    @Test
    fun `match by exact stream name`() {
        val streams = listOf(createStream(1, "Channel 1"))
        val matcher = EpgChannelMatcher(streams)

        val match = matcher.match("epg_unknown", "Channel 1")
        assertNotNull(match)
        assertEquals(1, match?.streamId)
    }

    @Test
    fun `match by normalized name`() {
        val streams = listOf(createStream(1, "UK: Channel 1 HD"))
        val matcher = EpgChannelMatcher(streams)

        // ChannelNameNormalizer removes UK: and HD and spaces => "channel1"
        val match = matcher.match("epg_unknown", "channel 1")
        assertNotNull(match)
        assertEquals(1, match?.streamId)
    }

    @Test
    fun `match by contains`() {
        val streams = listOf(createStream(1, "Sports Network Extra"))
        val matcher = EpgChannelMatcher(streams)

        // Normalized stream: "sportsnetworkextra"
        // Normalized query: "sportsnetwork"
        val match = matcher.match("epg_unknown", "Sports Network")
        assertNotNull(match)
        assertEquals(1, match?.streamId)

        // Reverse contains
        // Normalized query: "sportsnetworkextrahd"
        val match2 = matcher.match("epg_unknown", "Sports Network Extra HD")
        assertNotNull(match2)
        assertEquals(1, match2?.streamId)
    }

    @Test
    fun `does not match by contains if length less than 4`() {
        val streams = listOf(createStream(1, "ABC"))
        val matcher = EpgChannelMatcher(streams)

        val match = matcher.match("epg_unknown", "ABC Extra")
        assertNull(match)
    }
}
