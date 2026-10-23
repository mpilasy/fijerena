package org.njarasoa.fijerena.core.network.tmdb

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The user-entered TMDB key (D4, the public release plan): no
 * key and a refused key both turn TMDB off, before any request is made.
 */
class TmdbKeyTest {
    @Test
    fun `no key, blank key - TMDB is off`() {
        assertEquals(TmdbKeyState.None, TmdbKeyState.of(null, null))
        assertEquals(TmdbKeyState.None, TmdbKeyState.of("", null))
        assertEquals(TmdbKeyState.None, TmdbKeyState.of("  ", "  "))
    }

    @Test
    fun `a key TMDB refused stays off until another key is entered`() {
        assertEquals(TmdbKeyState.Rejected, TmdbKeyState.of("abc", "abc"))
        assertEquals(TmdbKeyState.Active("def"), TmdbKeyState.of("def", "abc"))
        assertEquals(TmdbKeyState.Active("abc"), TmdbKeyState.of("abc", null))
    }

    @Test
    fun `without a key nothing is requested and hasApiKey is false`() =
        runTest {
            val tmdb = TmdbApiService(activeKey = { null })
            assertFalse(tmdb.hasApiKey())
            // The HTTP client is lazy: reaching it here would build it, and the call would need a
            // network. The missing key has to stop the call first.
            try {
                tmdb.getSeason(1, 1)
                fail("expected TmdbKeyMissingException")
            } catch (expected: TmdbKeyMissingException) {
                // TMDB off: same failure every caller already treats as "no TMDB data".
            }
        }

    @Test
    fun `a rejected key reads as no key`() {
        var key: String? = "abc"
        var rejected: String? = null
        val tmdb =
            TmdbApiService(
                activeKey = { (TmdbKeyState.of(key, rejected) as? TmdbKeyState.Active)?.key },
                onRejected = { rejected = it },
            )
        assertTrue(tmdb.hasApiKey())
        rejected = "abc"
        assertFalse(tmdb.hasApiKey())
        key = "new"
        assertTrue(tmdb.hasApiKey())
    }

    @Test
    fun `key format - v4 tokens go as Bearer, v3 keys as a query parameter`() {
        assertTrue(TmdbApiService.isV4Token("eyJhbGciOiJIUzI1NiJ9.e30.x"))
        assertFalse(TmdbApiService.isV4Token("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `language follows the app, except where TMDB has no text`() {
        assertEquals("fr-FR", TmdbApiService.tmdbLanguage("fr"))
        assertEquals("en-US", TmdbApiService.tmdbLanguage("en"))
        assertEquals("en-US", TmdbApiService.tmdbLanguage("mg"))
    }

    @Test
    fun `Test button verdicts`() {
        assertEquals(TmdbKeyCheck.VALID, TmdbApiService.keyCheckResult(HttpStatusCode.OK))
        assertEquals(TmdbKeyCheck.REJECTED, TmdbApiService.keyCheckResult(HttpStatusCode.Unauthorized))
        assertEquals(TmdbKeyCheck.UNREACHABLE, TmdbApiService.keyCheckResult(HttpStatusCode.ServiceUnavailable))
    }

    @Test
    fun `a blank candidate is refused without a request`() =
        runTest {
            assertEquals(TmdbKeyCheck.REJECTED, TmdbApiService(activeKey = { null }).checkKey("  "))
        }
}
