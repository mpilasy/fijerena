package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Search the guide" opened from a TV Guide keeps the results on that guide's channels
 * (UX overhaul plan Part III, GD5 — "In <category> only").
 */
class FilterToStreamsTest {
    private fun airing(
        channelId: String,
        streamId: Int?,
    ) = EpgBrowserAiring(
        channelId = channelId,
        channelName = channelId,
        channelIconUrl = null,
        startEpoch = 0L,
        endEpoch = 60L,
        matchedStream = streamId?.let { EpgBrowserMatchedStream(streamId = it, streamName = channelId, categoryId = "1") },
    )

    private fun program(
        id: String,
        vararg airings: EpgBrowserAiring,
    ) = EpgBrowserProgram(id = id, title = id, description = null, category = null, airings = airings.toList())

    private fun group(
        label: String,
        vararg programs: EpgBrowserProgram,
    ) = EpgBrowserDateGroup(dateLabel = label, dayStartEpoch = 0L, programs = programs.toList())

    @Test
    fun `keeps only the airings on the guide's channels`() {
        val groups =
            listOf(
                group("today", program("news", airing("tf1", 1), airing("fr2", 2), airing("unmatched", null))),
            )

        val filtered = filterToStreams(groups, GuideChannels(setOf("1")))

        assertEquals(
            listOf("tf1"),
            filtered
                .single()
                .programs
                .single()
                .airings
                .map { it.channelId },
        )
    }

    @Test
    fun `drops programmes and days left with no airing`() {
        val groups =
            listOf(
                group("today", program("news", airing("tf1", 1)), program("film", airing("fr2", 2))),
                group("tomorrow", program("sport", airing("fr3", 3))),
            )

        val filtered = filterToStreams(groups, GuideChannels(setOf("1")))

        assertEquals(listOf("today"), filtered.map { it.dateLabel })
        assertEquals(listOf("news"), filtered.single().programs.map { it.id })
    }

    @Test
    fun `an empty channel set keeps nothing`() {
        val groups = listOf(group("today", program("news", airing("tf1", 1))))

        assertTrue(filterToStreams(groups, GuideChannels(emptySet())).isEmpty())
    }

    @Test
    fun `an airing matched to another stream on a guide channel plays the guide's stream`() {
        val fr4k = EpgBrowserMatchedStream(streamId = 7, streamName = "FR: CANAL+ 4K", categoryId = "fr")
        val groups = listOf(group("today", program("film", airing("CanalPlus.pl", 99), airing("other", 5))))

        val filtered = filterToStreams(groups, GuideChannels(setOf("7"), mapOf("canalplus.pl" to fr4k)))

        assertEquals(
            listOf(fr4k),
            filtered
                .single()
                .programs
                .single()
                .airings
                .map { it.matchedStream },
        )
    }
}
