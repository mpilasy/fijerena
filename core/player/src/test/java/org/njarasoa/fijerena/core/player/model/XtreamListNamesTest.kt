package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A list entry with `"name": null` (gr8iptv's film list, 2026-10-09) used to fail the whole list.
 * Same Json settings as XtreamApiService.
 */
class XtreamListNamesTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a film with a null name no longer fails the list, and is dropped when it has no title`() {
        val body =
            """
            [{"num":1,"name":"Alpha","stream_type":"movie","stream_id":10,"category_id":"5"},
             {"num":64175,"name":null,"title":null,"year":null,"stream_type":"movie","stream_id":11,"category_id":"5"},
             {"num":3,"name":"","title":"Gamma","stream_type":"movie","stream_id":12,"category_id":"5"}]
            """.trimIndent()

        val names = json.decodeFromString<List<XtreamStream>>(body).mapNotNull { it.withName() }.map { it.name }

        assertEquals(listOf("Alpha", "Gamma"), names)
    }

    @Test
    fun `a title that isn't text is ignored, not a failure`() {
        val body = """[{"num":1,"name":"Alpha","title":42,"stream_type":"movie","stream_id":10,"category_id":"5"}]"""

        assertEquals(listOf("Alpha"), json.decodeFromString<List<XtreamStream>>(body).mapNotNull { it.withName() }.map { it.name })
    }

    @Test
    fun `a show with a null name takes its title, or is dropped`() {
        val body =
            """
            [{"num":1,"name":null,"title":"Delta","series_id":20,"category_id":"7"},
             {"num":2,"name":null,"series_id":21,"category_id":"7"},
             {"num":3,"name":"Echo","series_id":22,"category_id":"7"}]
            """.trimIndent()

        val names = json.decodeFromString<List<XtreamSeries>>(body).mapNotNull { it.withName() }.map { it.name }

        assertEquals(listOf("Delta", "Echo"), names)
    }

    @Test
    fun `a missing category id or stream type no longer fails the list`() {
        val body = """[{"num":1,"name":"Alpha","stream_id":10,"category_id":null}]"""

        val stream = json.decodeFromString<List<XtreamStream>>(body).single()

        assertEquals("" to "", stream.categoryId to stream.streamType)
    }
}
