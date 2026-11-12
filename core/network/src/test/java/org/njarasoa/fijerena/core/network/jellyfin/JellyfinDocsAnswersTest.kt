package org.njarasoa.fijerena.core.network.jellyfin

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture
import java.net.InetSocketAddress

/**
 * [JellyfinApiService] against answers in the shape of the Jellyfin API reference
 * (https://api.jellyfin.org, Jellyfin 10.10), served from a local socket: sign-in, the libraries,
 * a library's items (also read as a stream), a show's seasons and episodes, and one item's details.
 * The `var-<endpoint>-docs.json` files are written by hand from the reference (2026-11-11), with
 * placeholder ids, names and plots and no access token (PD-Q1 (a), PD-Q3 (a)).
 */
class JellyfinDocsAnswersTest {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                val name =
                    when {
                        path == "/Users/AuthenticateByName" -> "var-users_authenticatebyname-docs.json"
                        path.endsWith("/Views") -> "var-users_views-docs.json"
                        path.endsWith("/Items") -> "var-users_items-docs.json"
                        path.contains("/Items/") -> "var-users_items_id-docs.json"
                        path.endsWith("/Seasons") -> "var-shows_seasons-docs.json"
                        path.endsWith("/Episodes") -> "var-shows_episodes-docs.json"
                        else -> null
                    }
                val body = name?.let { fixture("jellyfin/$it").toByteArray() }
                if (body == null) {
                    exchange.sendResponseHeaders(204, -1)
                } else {
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val api = JellyfinApiService("http://127.0.0.1:${server.address.port}", "device")

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun signIn() = runBlocking { api.authenticate("user", "pass") }.getOrThrow()

    @Test
    fun `the sign-in reads the user and the server`() {
        val auth = signIn()

        assertEquals("user", auth.user.name)
        assertEquals("00000000000000000000000000000b01", auth.user.id)
        assertEquals("00000000000000000000000000000a01", auth.serverId)
        assertEquals("00000000000000000000000000000b01", api.getUserId())
        assertTrue(api.isAuthenticated())
    }

    @Test
    fun `the libraries read with their kind`() {
        signIn()
        val libraries = runBlocking { api.getLibraries() }.getOrThrow()

        assertEquals(listOf("Films", "Shows"), libraries.map { it.name })
        assertEquals(listOf("movies", "tvshows"), libraries.map { it.collectionType })
    }

    @Test
    fun `a library's items read with their streams and watch state, as a list and as a stream`() {
        signIn()
        val items = runBlocking { api.getItems(parentId = "00000000000000000000000000000d01") }.getOrThrow()
        val streamed = mutableListOf<JellyfinItem>()
        runBlocking { api.getItemsStreaming(parentId = "00000000000000000000000000000d01") { streamed += it } }

        assertEquals(listOf("Movie", "Movie", "Series"), items.map { it.type })
        assertEquals(items, streamed)
        val film = items.first()
        assertEquals(2004, film.productionYear)
        assertEquals(72_000_000_000L, film.runTimeTicks)
        assertEquals(
            listOf("Video", "Audio", "Subtitle"),
            film.mediaSources
                .single()
                .mediaStreams
                .map { it.type },
        )
        assertEquals(
            "HDR10",
            film.mediaSources
                .single()
                .mediaStreams
                .first()
                .videoRangeType,
        )
        assertEquals(18_000_000_000L, film.userData?.playbackPositionTicks)
        assertTrue(film.userData!!.isFavorite)
        assertEquals("00000000000000000000000000006002", film.imageTags["Logo"])
        assertEquals(listOf("Actor", "Director"), film.people.map { it.type })
        // A film with no details keeps the defaults.
        assertNull(items[1].productionYear)
        assertTrue(items[1].userData!!.played)
        assertEquals("Ended", items[2].status)
    }

    @Test
    fun `a show's seasons and episodes read in order`() {
        signIn()
        val seasons = runBlocking { api.getSeasons("00000000000000000000000000002003") }.getOrThrow()
        val episodes = runBlocking { api.getEpisodes("00000000000000000000000000002003", seasons.first().id) }.getOrThrow()

        assertEquals(listOf(1, 0), seasons.map { it.indexNumber })
        assertEquals(listOf(1, 2, 3), episodes.map { it.indexNumber })
        assertEquals(listOf(1, 1, 1), episodes.map { it.parentIndexNumber })
        assertEquals(listOf(true, false, null), episodes.map { it.userData?.played })
        assertNull(episodes.last().runTimeTicks)
    }

    @Test
    fun `one item's details read with its facts and ids`() {
        signIn()
        val film = runBlocking { api.getItemById("00000000000000000000000000002001") }.getOrThrow()

        assertEquals("Film 1 Original", film.originalTitle)
        assertEquals(listOf("A neutral tagline."), film.taglines)
        assertEquals(listOf("United States of America", "Germany"), film.productionLocations)
        assertEquals("1000001", film.providerIds["Tmdb"])
        assertEquals("Trailer", film.remoteTrailers.single().name)
    }
}
