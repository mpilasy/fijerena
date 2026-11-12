package org.njarasoa.fijerena.core.player.api

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.fixture
import org.njarasoa.fijerena.core.player.model.XtreamStream
import java.net.InetSocketAddress

/**
 * The client against answers in the shape of the local jellyxtream bridge
 * (tools/jellyfin-xtream/xtream_bridge.py), served from a local socket: login, categories, the
 * three lists (also read as a stream), film and show details and the short guide.
 *
 * Source: the `var-<action>-bridge.json` files are written by hand from the bridge's code
 * (2026-11-11), with the placeholders scripts/record-provider-fixtures.py gives. No bridge login
 * was at hand for a recording. A recording (`rec-<action>.json`) replaces them.
 */
class XtreamBridgeAnswersTest {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/player_api.php") { exchange ->
                val action =
                    exchange.requestURI.query
                        .split("&")
                        .firstOrNull { it.startsWith("action=") }
                        ?.substringAfter("=") ?: "login"
                val body = fixture("xtream/var-$action-bridge.json").toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    private val service = XtreamApiService("http://127.0.0.1:${server.address.port}", "user", "pass")

    @After
    fun tearDown() {
        service.close()
        server.stop(0)
    }

    @Test
    fun `the login reads with the account and the panel clock`() {
        val login = runBlocking { service.authenticate() }

        assertEquals(1, login.userInfo.auth)
        assertEquals(5, login.userInfo.maxConnections?.toIntOrNull())
        assertEquals(listOf("m3u8", "ts"), login.userInfo.allowedOutputFormats)
        assertEquals(1767225600L, login.serverInfo?.timestampNow)
    }

    @Test
    fun `the categories read`() {
        assertEquals(listOf("1", "2"), runBlocking { service.getVodCategories() }.map { it.categoryId })
        assertEquals(listOf("Category 3"), runBlocking { service.getSeriesCategories() }.map { it.categoryName })
    }

    @Test
    fun `every film reads, as a list and as a stream`() {
        val films = runBlocking { service.getVodStreams() }
        val streamed = mutableListOf<XtreamStream>()
        runBlocking { service.getVodStreamsStreaming { streamed += it } }

        assertEquals(listOf(3, 4, 5, 6), films.map { it.streamId })
        assertEquals(listOf("Film 1", "Film 2", "Film 3", "Film 4"), films.map { it.name })
        assertEquals(films, streamed)
    }

    @Test
    fun `every show reads with its backdrops`() {
        val shows = runBlocking { service.getSeries() }

        assertEquals(listOf(10, 11, 12), shows.map { it.seriesId })
        assertEquals(listOf(1, 0, 1), shows.map { it.backdropPath.orEmpty().size })
        assertEquals(4.2, shows.first().rating5based)
    }

    @Test
    fun `every channel reads with its guide id`() {
        val channels = runBlocking { service.getStreams() }

        assertEquals(listOf(30, 31, 32), channels.map { it.streamId })
        assertEquals("0000000000000000000000000000001e", channels.first().epgChannelId)
    }

    @Test
    fun `a film's details read with the stream data`() {
        val response = runBlocking { service.getVodInfo(3) }

        assertTrue("expected Ok, got $response", response is XtreamResponse.Ok)
        val info = (response as XtreamResponse.Ok).value
        assertEquals("mkv", info.movieData?.containerExtension)
        assertEquals(1080, info.info?.video?.height)
        assertEquals("10001", info.info?.tmdbId)
    }

    @Test
    fun `a show's details read with every episode`() {
        val response = runBlocking { service.getSeriesInfo(10) }

        assertTrue("expected Ok, got $response", response is XtreamResponse.Ok)
        val info = (response as XtreamResponse.Ok).value
        assertEquals(mapOf("1" to listOf("20", "21"), "2" to listOf("22")), info.episodes.mapValues { (_, e) -> e.map { it.id } })
        assertEquals(listOf(1, 2), info.seasons.map { it?.seasonNumber })
        assertEquals("Show 1", info.info?.name)
    }

    @Test
    fun `the short guide reads as plain text and epoch times`() {
        val guide = runBlocking { service.getShortEpg(30) }.listings

        assertEquals(listOf("Programme 1", "Programme 2", "Programme 3"), guide.map { it.title })
        assertEquals("1767229200", guide.first().start)
        assertEquals("1767232800", guide.first().end)
        assertEquals("", guide.last().description)
        assertNull(guide.first().language?.takeIf { it.isNotEmpty() })
    }
}
