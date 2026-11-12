package org.njarasoa.fijerena.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.fixture
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo

/**
 * The ways this provider says "nothing", classified in one place.
 *
 * Source: the `var-get_series_info-*` and `var-get_vod_info-*` files under fixtures/xtream/ that
 * this test reads are bodies real panels sent (field reports, kept as inline strings before
 * 2026-11-11). Each one cost a debugging session: feed a parsing change these, not a body shaped
 * like the model.
 */
class XtreamResponseTest {
    private val json = XtreamJson

    private fun series(raw: String) = json.parseItemResponse(raw, 4080, "get_series_info", SeriesInfo::carriesNothing)

    private fun vod(raw: String) = json.parseItemResponse(raw, 149880, "get_vod_info", VodInfo::carriesNothing)

    @Test
    fun bareArrayMeansTheProviderHasNoSuchId() {
        val response = series(fixture("xtream/var-get_series_info-empty-array.json"))

        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), response)
    }

    @Test
    fun objectWithNothingInItIsAlsoUnavailable() {
        // A proxy that answers before it has the data: well-formed, no info, no episodes. Taken as
        // is, it shows as a real show with nothing in it.
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series(fixture("xtream/var-get_series_info-empty-object.json")))
        assertEquals(XtreamResponse.Unavailable(4080, "get_series_info"), series(fixture("xtream/var-get_series_info-no-episodes.json")))
    }

    @Test
    fun infoWithoutEpisodesStillReadsAsAShow() {
        // An info object but no episodes is Ok, and the screen shows a show with nothing under it.
        // Whether that counts as nothing usable is a separate decision.
        val response = series(fixture("xtream/var-get_series_info-info-only.json"))

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun episodesWithoutInfoStillCount() {
        val response = series(fixture("xtream/var-get_series_info-episodes-only.json"))

        assertTrue(response is XtreamResponse.Ok)
        assertEquals(
            1,
            (response as XtreamResponse.Ok)
                .value.episodes
                .getValue("1")
                .size,
        )
    }

    @Test
    fun seriesInfoWithoutANameKeepsItsEpisodes() {
        // The name was the model's one required field, so a provider that leaves it out (or sends
        // it as "title") lost the whole episode list.
        val response = series(fixture("xtream/var-get_series_info-info-without-name.json"))

        assertTrue("expected Ok, got $response", response is XtreamResponse.Ok)
        assertEquals(
            1,
            (response as XtreamResponse.Ok)
                .value.episodes
                .getValue("1")
                .size,
        )
    }

    @Test
    fun oneUnreadableEpisodeCostsOnlyItself() {
        // The map decode stopped at the first episode it could not read, and the series came back
        // with no episodes.
        val response = series(fixture("xtream/var-get_series_info-one-episode-malformed.json"))

        assertTrue("expected the readable episode to survive, got $response", response is XtreamResponse.Ok)
        val episodes = (response as XtreamResponse.Ok).value.episodes.getValue("1")
        assertEquals(1, episodes.size)
        assertEquals("242136", episodes.single().id)
    }

    @Test
    fun unreadableResponseIsMalformedNotUnavailable() {
        // Unavailable is worth a retry and a lookup by name; a body we cannot parse is worth
        // reporting as is.
        val response = series(fixture("xtream/var-get_series_info-not-json.html"))

        assertTrue("expected Malformed, got $response", response is XtreamResponse.Malformed)
    }

    @Test
    fun movieWithNeitherInfoNorMovieDataIsUnavailable() {
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod(fixture("xtream/var-get_vod_info-empty-object.json")))
        assertEquals(XtreamResponse.Unavailable(149880, "get_vod_info"), vod(fixture("xtream/var-get_vod_info-empty-array.json")))
    }

    @Test
    fun movieWithMovieDataIsOk() {
        val response = vod(fixture("xtream/var-get_vod_info-movie_data-only.json"))

        assertTrue(response is XtreamResponse.Ok)
    }

    @Test
    fun everyNonOkCaseCarriesSomethingToReport() {
        val unavailable = series(fixture("xtream/var-get_series_info-empty-array.json")).asThrowable()
        assertEquals("Provider has no get_series_info for id 4080", unavailable.message)

        val malformed = series(fixture("xtream/var-get_series_info-not-json.html")).asThrowable()
        assertTrue(malformed.message?.isNotEmpty() == true)
    }
}
