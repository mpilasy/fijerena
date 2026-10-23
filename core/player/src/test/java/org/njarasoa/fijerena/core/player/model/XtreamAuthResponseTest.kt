package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same Json settings as XtreamApiService. */
class XtreamAuthResponseTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a refused login parses, so the app can say the login is wrong`() {
        val response = json.decodeFromString<XtreamAuthResponse>("""{"user_info":{"auth":0}}""")

        assertEquals(0, response.userInfo.auth)
        assertNull(response.serverInfo)
    }

    @Test
    fun `an accepted login keeps its fields`() {
        val body =
            """
            {"user_info":{"username":"u","password":"p","auth":1,"status":"Active"},
             "server_info":{"url":"panel.example","port":"80","server_protocol":"http","timezone":"Europe/London"}}
            """.trimIndent()

        val response = json.decodeFromString<XtreamAuthResponse>(body)

        assertEquals("Active", response.userInfo.status)
        assertEquals("Europe/London", response.serverInfo?.timezone)
    }
}
