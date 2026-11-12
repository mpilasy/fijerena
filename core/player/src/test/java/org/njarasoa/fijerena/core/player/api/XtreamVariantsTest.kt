package org.njarasoa.fijerena.core.player.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeToSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.fixture
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamEpgAnswer
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.player.model.toEpgResponse
import org.njarasoa.fijerena.core.player.model.withName

/**
 * The Xtream quirks that panels send, each read as the client reads it: lists as a stream with
 * [withName], single items through [parseItemResponse], and the login and the panel guide with
 * [XtreamJson].
 *
 * Source: the `var-` files under fixtures/xtream/ are written by hand (2026-11-11) from the quirks
 * the provider data tests plan lists from panels in the field: numbers as text, `""` in a number
 * field, `null` against `""`, `[]` or `{}` for an empty object or list, and a text where a list
 * belongs. None of them may fail a whole list, a film's details, a show's episodes or a login.
 */
@OptIn(ExperimentalSerializationApi::class)
class XtreamVariantsTest {
    private fun streams(name: String) =
        XtreamJson.decodeToSequence<XtreamStream?>(fixture("xtream/$name").byteInputStream()).mapNotNull { it?.withName() }.toList()

    private fun series(name: String) =
        XtreamJson.decodeToSequence<XtreamSeries?>(fixture("xtream/$name").byteInputStream()).mapNotNull { it?.withName() }.toList()

    private fun vodInfo(name: String) = XtreamJson.parseItemResponse(fixture("xtream/$name"), 1, "get_vod_info", VodInfo::carriesNothing)

    private fun seriesInfo(name: String) =
        XtreamJson.parseItemResponse(fixture("xtream/$name"), 1, "get_series_info", SeriesInfo::carriesNothing)

    private fun login(name: String) = XtreamJson.decodeFromString<XtreamAuthResponse>(fixture("xtream/$name"))

    private fun guide(name: String) = XtreamJson.decodeFromString<XtreamEpgAnswer>(fixture("xtream/$name")).toEpgResponse().listings

    private fun <T> ok(response: XtreamResponse<T>): T {
        assertTrue("expected Ok, got $response", response is XtreamResponse.Ok)
        return (response as XtreamResponse.Ok).value
    }

    @Test
    fun `numbers sent as text read as numbers`() {
        val films = streams("var-get_vod_streams-numbers-as-strings.json")

        assertEquals(listOf(12, 13, 14), films.map { it.streamId })
        assertEquals(listOf(1, 2, 3), films.map { it.num })
        assertEquals("5", films.first().categoryId)
    }

    @Test
    fun `an empty number field reads as no number, and an empty id drops only its entry`() {
        val channels = streams("var-get_live_streams-empty-number-fields.json")

        assertEquals(listOf(31, 32), channels.map { it.streamId })
        assertEquals(listOf(0, 1), channels.map { it.tvArchive })
        assertEquals(listOf(0, 7), channels.map { it.tvArchiveDuration })
        assertEquals(listOf(0, 2), channels.map { it.num })
    }

    @Test
    fun `null and an empty text in the same field read alike`() {
        val (withNulls, withEmpty) = streams("var-get_live_streams-null-and-empty.json")

        assertEquals(withNulls.tvArchive, withEmpty.tvArchive)
        assertEquals(withNulls.tvArchiveDuration, withEmpty.tvArchiveDuration)
        assertEquals(withNulls.num, withEmpty.num)
        assertEquals(listOf(41, 42), listOf(withNulls.streamId, withEmpty.streamId))
    }

    @Test
    fun `a backdrop sent as one text, an empty text or an empty object keeps the show`() {
        val shows = series("var-get_series-backdrop-path-not-a-list.json")

        assertEquals(listOf(51, 52, 53, 54), shows.map { it.seriesId })
        assertEquals(listOf("https://img.invalid/51-backdrop.jpg"), shows[0].backdropPath)
        assertEquals(listOf(0, 0, 1), shows.drop(1).map { it.backdropPath.orEmpty().size })
    }

    @Test
    fun `a show list with empty number fields reads`() {
        val shows = series("var-get_series-empty-number-fields.json")

        assertEquals(listOf(61, 62, 63), shows.map { it.seriesId })
        assertEquals(listOf(null, 4.5, 3.0), shows.map { it.rating5based })
        assertEquals(listOf(null, 2, 3), shows.map { it.num })
    }

    @Test
    fun `categories with a number id or an empty parent read`() {
        val categories =
            XtreamJson
                .decodeFromString<List<XtreamCategory?>>(fixture("xtream/var-get_vod_categories-numbers.json"))
                .filterNotNull()
                .filter { it.categoryId.isNotBlank() }

        assertEquals(listOf("1", "2", "3"), categories.map { it.categoryId })
        assertEquals(listOf(0, 0, 0), categories.map { it.parentId })
    }

    @Test
    fun `film details with an empty array for info or movie data still read`() {
        val noInfo = ok(vodInfo("var-get_vod_info-info-empty-array.json"))
        val noMovieData = ok(vodInfo("var-get_vod_info-movie_data-empty-array.json"))

        assertNull(noInfo.info)
        assertEquals(149881, noInfo.movieData?.streamId)
        assertEquals("Film 1", noMovieData.info?.name)
        assertNull(noMovieData.movieData)
    }

    @Test
    fun `film details with empty number fields read`() {
        val info = ok(vodInfo("var-get_vod_info-empty-number-fields.json"))

        assertEquals(149882, info.movieData?.streamId)
        assertEquals(1080, info.info?.video?.height)
        assertNull(info.info?.video?.width)
    }

    @Test
    fun `a show with an empty array for info keeps its episodes`() {
        val info = ok(seriesInfo("var-get_series_info-info-empty-array.json"))

        assertNull(info.info)
        assertEquals(listOf("71", "72"), info.episodes.getValue("1").map { it.id })
    }

    @Test
    fun `a show with an empty object for its seasons keeps its episodes`() {
        val info = ok(seriesInfo("var-get_series_info-seasons-empty-object.json"))

        assertEquals(emptyList<Any>(), info.seasons)
        assertEquals(listOf("81"), info.episodes.getValue("1").map { it.id })
    }

    @Test
    fun `a show with empty number fields keeps every episode`() {
        val info = ok(seriesInfo("var-get_series_info-empty-number-fields.json"))
        val episodes = info.episodes.getValue("1")

        assertEquals(listOf("91", "92"), episodes.map { it.id })
        assertEquals(listOf(1, 0), episodes.map { it.episodeNum })
        assertEquals(listOf(1, null), episodes.map { it.season })
        assertEquals(listOf(null, 2520), episodes.map { it.info?.durationSecs })
        assertEquals(1, info.seasons.single()?.seasonNumber)
        assertNull(info.seasons.single()?.episodeCount)
        assertNull(info.info?.rating5based)
        assertEquals(listOf("https://img.invalid/91-backdrop.jpg"), info.info?.backdropPath)
    }

    @Test
    fun `a refused login reads, so the app can say the login is wrong`() {
        val refused = login("var-login-refused.json")

        assertEquals(0, refused.userInfo.auth)
        assertNull(refused.serverInfo)
    }

    @Test
    fun `a login with numbers and text swapped reads`() {
        val auth = login("var-login-numbers-as-strings.json")

        assertEquals(1, auth.userInfo.auth)
        assertEquals("1", auth.userInfo.activeCons)
        assertEquals("2", auth.userInfo.maxConnections)
        assertEquals(listOf("m3u8"), auth.userInfo.allowedOutputFormats)
        assertEquals("80", auth.serverInfo?.port)
        assertEquals(1767225600L, auth.serverInfo?.timestampNow)
    }

    @Test
    fun `a login with empty fields reads`() {
        val auth = login("var-login-empty-fields.json")

        assertEquals(1, auth.userInfo.auth)
        assertNull(auth.userInfo.maxConnections?.toIntOrNull())
        assertEquals(emptyList<String?>(), auth.userInfo.allowedOutputFormats)
        assertNull(auth.serverInfo?.timestampNow)
    }

    @Test
    fun `a login read with quirks is stored and read back the same`() {
        // AccountManager stores the login answer as JSON and reads it at the next start.
        listOf("var-login-numbers-as-strings.json", "var-login-empty-fields.json", "var-login-refused.json").forEach { name ->
            val auth = login(name)

            assertEquals(name, auth, XtreamJson.decodeFromString<XtreamAuthResponse>(XtreamJson.encodeToString(auth)))
        }
    }

    @Test
    fun `a guide answer with no listings, in any form, is empty`() {
        listOf("empty-array", "empty-object", "missing").forEach { form ->
            assertEquals(form, emptyList<Any>(), guide("var-get_short_epg-listings-$form.json"))
        }
    }

    @Test
    fun `guide text that is not base64 stays as it is, and the times read in every form`() {
        val listings = guide("var-get_short_epg-bad-base64.json")

        assertEquals(listOf("Not base64!", "Programme 2"), listings.map { it.title })
        assertEquals(listOf("Plain text, not base64.", null), listings.map { it.description })
        assertEquals(listOf("1767229200", "1767232800"), listings.map { it.start })
        assertEquals(listOf(null, 1), listings.map { it.hasArchive })
    }
}
