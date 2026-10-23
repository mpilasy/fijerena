package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Some panels omit `tmdb` from get_series_info while the series listing has it. */
class ResolveSeriesTmdbIdTest {
    @Test
    fun `the info block wins`() {
        assertEquals(4238, resolveSeriesTmdbId("4238", "999"))
    }

    @Test
    fun `falls back to the stored id when info has none`() {
        assertEquals(4238, resolveSeriesTmdbId(null, "4238"))
        assertEquals(4238, resolveSeriesTmdbId("", "4238"))
        assertEquals(4238, resolveSeriesTmdbId("0", "4238"))
    }

    @Test
    fun `no usable id anywhere gives none`() {
        assertNull(resolveSeriesTmdbId(null, null))
        assertNull(resolveSeriesTmdbId("0", "abc"))
    }
}
