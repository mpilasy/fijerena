package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamEpgListingTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    // One bears `get_simple_data_table` listing (2026-10-10), as it comes.
    private val bears =
        """
        {"epg_listings":[{"id":"1","epg_id":"7","title":"T3V2aXIgUXVlbSBWw6ogTnVtIE1pbnV0bw==","lang":"pt",
        "start":"2026-10-10 15:15:00","end":"2026-10-10 15:30:00","description":"VW0gcHJvZ3JhbWEu",
        "channel_id":"Rtp1.pt","start_timestamp":"1791645300","stop_timestamp":"1791646200",
        "now_playing":0,"has_archive":1}]}
        """.trimIndent()

    @Test
    fun `times come from the timestamps and text is decoded`() {
        val program =
            json
                .decodeFromString<XtreamEpgAnswer>(bears)
                .toEpgResponse()
                .listings
                .single()
        assertEquals(1791645300L, program.startTime)
        assertEquals(1791646200L, program.endTime)
        assertEquals("Ouvir Quem Vê Num Minuto", program.title)
        assertEquals("Um programa.", program.description)
        assertEquals(1, program.hasArchive)
    }

    @Test
    fun `without timestamps the text times are read as UTC`() {
        val answer =
            """{"epg_listings":[{"id":"1","title":"","start":"2026-10-10 15:15:00","end":"2026-10-10 15:30:00"}]}"""
        val program =
            json
                .decodeFromString<XtreamEpgAnswer>(answer)
                .toEpgResponse()
                .listings
                .single()
        assertEquals(1791645300L, program.startTime)
        assertNull(program.hasArchive)
    }

    @Test
    fun `plain text that is not base64 stays as it is`() {
        assertEquals("News", xtreamText("News"))
        assertEquals("Le journal de 20h", xtreamText("Le journal de 20h"))
        assertEquals("", xtreamText(""))
    }
}
