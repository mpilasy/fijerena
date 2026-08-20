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
        val response = series(XtreamPayloads.EMPTY_ARRAY)

        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), response)
    }

    @Test
    fun objectWithNothingInItIsAlsoUnavailable() {
        // A proxy answering before it has the data: well-formed, no info, no episodes. Taken at
        // face value this renders as a real show with nothing in it.
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series(XtreamPayloads.EMPTY_OBJECT))
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series(XtreamPayloads.SERIES_NO_EPISODES))
    }

    @Test
    fun infoWithoutEpisodesStillReadsAsAShow() {
        // Unchanged from before this classification existed: a response carrying an info object
        // but no episodes is Ok, and the screen shows a show with nothing under it. Whether that
        // should count as nothing usable is a separate call, not one to make inside a refactor.
        val response = series(XtreamPayloads.SERIES_INFO_ONLY)

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun episodesWithoutInfoStillCount() {
        val response = series(XtreamPayloads.SERIES_EPISODES_ONLY)

        assertTrue(response is XtreamResponse.Ok)
        assertEquals(1, (response as XtreamResponse.Ok).value.episodes.getValue("1").size)
    }

    @Test
    fun seriesInfoWithoutANameKeepsItsEpisodes() {
        // The name was the model's one required field, so a provider omitting it — or sending it
        // as "title" — used to throw the whole episode list away.
        val response = series(XtreamPayloads.SERIES_INFO_WITHOUT_NAME)

        assertTrue("expected Ok, got $response", response is XtreamResponse.Ok)
        assertEquals(1, (response as XtreamResponse.Ok).value.episodes.getValue("1").size)
    }

    @Test
    fun oneUnreadableEpisodeCostsTheWholeList() {
        // Current behaviour, pinned rather than endorsed: EpisodesMapSerializer catches its own
        // decode failure and returns an empty map, so a single episode missing a required field
        // takes every other episode of that show with it — and the show then reads as unavailable.
        val response = series(XtreamPayloads.SERIES_ONE_EPISODE_MALFORMED)

        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), response)
    }

    @Test
    fun unreadableResponseIsMalformedNotUnavailable() {
        // Distinguishing these matters: unavailable is worth retrying and re-resolving by name,
        // a body we cannot parse is worth reporting as-is.
        val response = series(XtreamPayloads.NOT_JSON)

        assertTrue("expected Malformed, got $response", response is XtreamResponse.Malformed)
    }

    @Test
    fun movieWithNeitherInfoNorMovieDataIsUnavailable() {
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod(XtreamPayloads.EMPTY_OBJECT))
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod(XtreamPayloads.EMPTY_ARRAY))
    }

    @Test
    fun movieWithMovieDataIsOk() {
        val response = vod(XtreamPayloads.VOD_MOVIE_DATA_ONLY)

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun everyNonOkCaseCarriesSomethingToReport() {
        val unavailable = series(XtreamPayloads.EMPTY_ARRAY).asThrowable()
        assertEquals("Provider has no get_series_info for id 4080", unavailable.message)

        val malformed = series(XtreamPayloads.NOT_JSON).asThrowable()
        assertTrue(malformed.message?.isNotEmpty() == true)
    }
}
