package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Any field of an Xtream answer may come as null. One null must never fail the answer: a field
 * takes its default, and only an entry with nothing to play (no id) is dropped.
 */
class XtreamNullFieldsTest {
    // As XtreamApiService reads the panel.
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a list with a null entry or an entry without an id keeps the rest`() {
        val body = """[null,{"stream_id":null,"name":"No id"},{"stream_id":7,"name":"Film","category_id":null,"num":null}]"""

        val streams = json.decodeFromString<List<XtreamStream?>>(body).mapNotNull { it?.withName() }

        assertEquals(listOf(7), streams.map { it.streamId })
        assertEquals("", streams.single().categoryId)
    }

    @Test
    fun `a series list entry without an id is dropped`() {
        val body = """[{"series_id":null,"name":"No id"},{"series_id":3,"name":"Show","backdrop_path":[null]}]"""

        assertEquals(listOf(3), json.decodeFromString<List<XtreamSeries?>>(body).mapNotNull { it?.withName() }.map { it.seriesId })
    }

    @Test
    fun `a category with null fields reads`() {
        val category = json.decodeFromString<List<XtreamCategory?>>("""[{"category_id":"1","category_name":null,"parent_id":null},null]""")

        assertEquals(XtreamCategory(categoryId = "1"), category.first())
    }

    @Test
    fun `series info with null seasons, null season fields and null episode fields reads`() {
        val body =
            """
            {"info":null,"seasons":[null,{"season_number":null,"name":null}],
            "episodes":{"1":[{"id":"10","episode_num":null,"title":null,"container_extension":"mkv","info":null},
            {"id":null,"container_extension":"mkv"},{"id":"12","container_extension":null}]}}
            """.trimIndent()

        val info = json.decodeFromString<SeriesInfo>(body)

        assertEquals(listOf(null, Season()), info.seasons)
        assertEquals(listOf(Episode(id = "10", containerExtension = "mkv")), info.episodes["1"])
    }

    @Test
    fun `a login answer with null blocks reads`() {
        val auth =
            json.decodeFromString<XtreamAuthResponse>(
                """{"user_info":null,"server_info":{"url":null,"port":null,"server_protocol":null}}""",
            )

        assertEquals(0, auth.userInfo.auth)
        assertEquals("", auth.serverInfo?.url)
    }

    @Test
    fun `an EPG answer with a null listing keeps the rest`() {
        val answer = json.decodeFromString<XtreamEpgAnswer>("""{"epg_listings":[null,{"id":null,"title":null}]}""")

        assertTrue(answer.toEpgResponse().listings.size == 1)
    }
}
