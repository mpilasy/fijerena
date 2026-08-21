package org.njarasoa.fijerena.core.player.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ratings arrive as whatever string the provider chose to send, so the rounding has to be careful
 * about what it refuses to touch as well as what it rounds.
 */
class FormatRatingTest {
    @Test
    fun `rounds TMDB's three decimals to one`() {
        assertEquals("6.7", formatRating("6.666"))
        assertEquals("8.2", formatRating("8.234"))
        assertEquals("7.0", formatRating("6.95"))
    }

    @Test
    fun `keeps a value that is already one decimal`() {
        assertEquals("7.9", formatRating("7.9"))
    }

    @Test
    fun `gives a whole number a decimal place`() {
        assertEquals("8.0", formatRating("8"))
        assertEquals("10.0", formatRating("10"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals("6.7", formatRating(" 6.666 "))
    }

    @Test
    fun `leaves a rating combined with a certificate alone`() {
        // Real providers send this; parsing it as a number would drop the certificate.
        assertEquals("7.9 | PG-13", formatRating("7.9 | PG-13"))
    }

    @Test
    fun `leaves anything else alone`() {
        assertEquals("N/A", formatRating("N/A"))
        assertEquals("", formatRating(""))
    }
}
