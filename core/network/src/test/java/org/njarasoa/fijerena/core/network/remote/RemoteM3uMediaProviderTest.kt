package org.njarasoa.fijerena.core.network.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

/**
 * A source's playlist URL changed (here, or synced from another device) kept serving the old
 * playlist for up to 6 hours, and Refresh re-served the cached copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteM3uMediaProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var server: HttpServer
    private val playlists = mutableMapOf<String, String>()

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}/$path"

    private fun playlist(vararg titles: String) =
        "#EXTM3U\n" + titles.joinToString("") { "#EXTINF:-1 group-title=\"News\",$it\nhttp://example.test/${it.lowercase()}.ts\n" }

    private fun titles(provider: RemoteM3uMediaProvider) =
        runBlocking {
            val category = provider.getCategories(ContentType.LIVE_TV).getOrThrow().single()
            provider.getItems(category.id, ContentType.LIVE_TV).getOrThrow().map { it.name }
        }

    @Before
    fun setup() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = playlists[exchange.requestURI.path.removePrefix("/")]?.toByteArray()
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        context.cacheDir.listFiles { f -> f.name.startsWith("remote_m3u_") }?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `a changed URL loads the new playlist at once, and the old copy goes`() {
        playlists["old.m3u"] = playlist("Old One")
        playlists["new.m3u"] = playlist("New One")
        assertEquals(listOf("Old One"), titles(RemoteM3uMediaProvider(7L, url("old.m3u"), context)))

        // What a URL edit does: the provider is rebuilt with the new URL, same source id.
        assertEquals(listOf("New One"), titles(RemoteM3uMediaProvider(7L, url("new.m3u"), context)))

        val cached = context.cacheDir.listFiles { f -> f.name.startsWith("remote_m3u_7_") }!!.toList()
        assertEquals(1, cached.size)
    }

    @Test
    fun `the cached copy is used within its lifetime, and refreshCatalog downloads again`() {
        playlists["list.m3u"] = playlist("First")
        assertEquals(listOf("First"), titles(RemoteM3uMediaProvider(8L, url("list.m3u"), context)))

        playlists["list.m3u"] = playlist("First", "Second")
        val provider = RemoteM3uMediaProvider(8L, url("list.m3u"), context)
        assertEquals(listOf("First"), titles(provider))

        assertTrue(runBlocking { provider.refreshCatalog() }.isSuccess)
        assertEquals(listOf("First", "Second"), titles(provider))
    }

    @Test
    fun `a playlist cached under the pre-URL name is cleared by the next download`() {
        val legacy = File(context.cacheDir, "remote_m3u_9.m3u").apply { writeText(playlist("Legacy")) }
        playlists["list.m3u"] = playlist("Current")

        assertEquals(listOf("Current"), titles(RemoteM3uMediaProvider(9L, url("list.m3u"), context)))
        assertFalse(legacy.exists())
    }
}
