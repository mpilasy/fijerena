package org.njarasoa.fijerena.core.player.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo

/**
 * The ways this provider says "nothing", each of which used to be recognised — or missed — at
 * whichever call site tripped over it first. Same Json configuration as XtreamApiService.
 */
class XtreamResponseTest {
    private val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private fun series(raw: String) = json.parseItemResponse(raw, 4080, "get_series_info", SeriesInfo::carriesNothing)

    private fun vod(raw: String) = json.parseItemResponse(raw, 149880, "get_vod_info", VodInfo::carriesNothing)

    @Test
    fun bareArrayMeansTheProviderHasNoSuchId() {
        val response = series("[]")

        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), response)
    }

    @Test
    fun objectWithNothingInItIsAlsoUnavailable() {
        // A proxy answering before it has the data: well-formed, no info, no episodes. Taken at
        // face value this renders as a real show with nothing in it.
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series("{}"))
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series("""{"seasons":[],"episodes":{}}"""))
    }

    @Test
    fun infoWithoutEpisodesStillReadsAsAShow() {
        // Unchanged from before this classification existed: a response carrying an info object
        // but no episodes is Ok, and the screen shows a show with nothing under it. Whether that
        // should count as nothing usable is a separate call, not one to make inside a refactor.
        val response = series("""{"info":{"name":"EN - Law & Order (1990) (US)"},"episodes":{}}""")

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun episodesWithoutInfoStillCount() {
        val raw =
            """
            {"episodes":{"1":[{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"}]}}
            """.trimIndent()

        val response = series(raw)

        assertTrue(response is XtreamResponse.Ok)
        assertEquals(1, (response as XtreamResponse.Ok).value.episodes.getValue("1").size)
    }

    @Test
    fun unreadableResponseIsMalformedNotUnavailable() {
        // Distinguishing these matters: unavailable is worth retrying and re-resolving by name,
        // a body we cannot parse is worth reporting as-is.
        val response = series("<html>gateway timeout</html>")

        assertTrue("expected Malformed, got $response", response is XtreamResponse.Malformed)
    }

    @Test
    fun movieWithNeitherInfoNorMovieDataIsUnavailable() {
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod("{}"))
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod("[]"))
    }

    @Test
    fun movieWithMovieDataIsOk() {
        val response = vod("""{"movie_data":{"stream_id":149880,"name":"BlacKkKlansman (2018)"}}""")

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun everyNonOkCaseCarriesSomethingToReport() {
        val unavailable = series("[]").asThrowable()
        assertEquals("Provider has no get_series_info for id 4080", unavailable.message)

        val malformed = series("not json").asThrowable()
        assertTrue(malformed.message?.isNotEmpty() == true)
    }
}
