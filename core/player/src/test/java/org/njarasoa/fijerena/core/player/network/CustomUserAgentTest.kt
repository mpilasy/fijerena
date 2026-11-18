package org.njarasoa.fijerena.core.player.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

/** A Nerd's custom User-Agent replaces each call's own; without one, each call keeps its own. */
class CustomUserAgentTest {
    @After
    fun reset() = CustomUserAgent.set(null)

    // What a call built from the app's client sends: the request after its interceptors, no network.
    private fun sentUserAgent(own: String?): String? {
        var sent: Request? = null
        val client =
            NetworkModule.okHttpClient
                .newBuilder()
                .addInterceptor { chain ->
                    sent = chain.request()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                }.build()
        val request =
            Request
                .Builder()
                .url("http://192.0.2.1/")
                .apply { own?.let { header("User-Agent", it) } }
                .build()
        client.newCall(request).execute().close()
        return sent?.header("User-Agent")
    }

    @Test
    fun `without a custom User-Agent streams keep the player's and every other call sends the app's`() {
        assertEquals(CustomUserAgent.STREAM, sentUserAgent(CustomUserAgent.STREAM))
        assertEquals(CustomUserAgent.app, sentUserAgent("ktor-client"))
        assertEquals(CustomUserAgent.app, sentUserAgent(null))
    }

    @Test
    fun `the app's User-Agent names the installed version`() {
        assertEquals("Fijerena/0.9.20261118004847 (Android)", CustomUserAgent.appUserAgent("0.9.20261118004847"))
        assertEquals("Fijerena (Android)", CustomUserAgent.appUserAgent(null))
    }

    @Test
    fun `a custom User-Agent replaces each call's own`() {
        CustomUserAgent.set("MyPlayer/2.0")
        assertEquals("MyPlayer/2.0", sentUserAgent("MediaPlayer/1.0 (Linux; Android)"))
        assertEquals("MyPlayer/2.0", sentUserAgent(null))
    }

    @Test
    fun `a URL connection sends the app's, or the custom one`() {
        val plain = URL("http://192.0.2.1/").openConnection()
        CustomUserAgent.apply(plain)
        assertEquals(CustomUserAgent.app, plain.getRequestProperty("User-Agent"))
        CustomUserAgent.set("MyPlayer/2.0")
        val custom = URL("http://192.0.2.1/").openConnection()
        CustomUserAgent.apply(custom)
        assertEquals("MyPlayer/2.0", custom.getRequestProperty("User-Agent"))
    }

    @Test
    fun `what a header can't carry is dropped, and nothing left means the defaults`() {
        CustomUserAgent.set("  Lecteur é/1.0\n ")
        assertEquals("Lecteur /1.0", CustomUserAgent.value)
        CustomUserAgent.set(" é ")
        assertNull(CustomUserAgent.value)
        CustomUserAgent.set("x".repeat(400))
        assertEquals(CustomUserAgent.MAX_LENGTH, CustomUserAgent.value?.length)
    }
}
