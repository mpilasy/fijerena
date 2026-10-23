package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Xtream panels send `""` or the literal `"0"` — not `null` — for a title with no TMDB match.
 * Both must normalize to `null` so the TMDB-dedup joins in `XtreamStreamDao`/`XtreamSeriesDao`/
 * `XtreamEpisodeDao` (guarded only by `IS NOT NULL`) don't treat every unmatched title as sharing
 * one fake tmdbId.
 */
class NormalizeTmdbIdTest {
    @Test
    fun `null is left alone`() {
        assertNull(null.normalizeTmdbId())
    }

    @Test
    fun `empty string becomes null`() {
        assertNull("".normalizeTmdbId())
    }

    @Test
    fun `literal zero becomes null`() {
        assertNull("0".normalizeTmdbId())
    }

    @Test
    fun `blank string becomes null`() {
        assertNull("  ".normalizeTmdbId())
    }

    @Test
    fun `a real tmdbId is left alone`() {
        assertEquals("603", "603".normalizeTmdbId())
    }

    @Test
    fun `a tmdbId beginning with zero is left alone`() {
        // "0" is the panel's placeholder; "0123" is a real (if oddly formatted) id, not the same value.
        assertEquals("0123", "0123".normalizeTmdbId())
    }
}
