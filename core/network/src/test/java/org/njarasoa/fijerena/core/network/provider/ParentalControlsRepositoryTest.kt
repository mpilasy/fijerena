package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An entry as a full-text phrase, for the first pass of match counts and exceptions. */
class ParentalControlsRepositoryTest {
    @Test
    fun `an entry becomes a phrase of its words, open at the end after a star`() {
        assertEquals("\"one piece\"", ParentalControlsRepository.ftsQuery("one piece"))
        assertEquals("\"Yu Gi Oh\"", ParentalControlsRepository.ftsQuery("Yu-Gi-Oh"))
        assertEquals("\"barn*\"", ParentalControlsRepository.ftsQuery("barn*"))
        assertEquals("\"18\"", ParentalControlsRepository.ftsQuery("+18"))
        assertNull(ParentalControlsRepository.ftsQuery(" * "))
    }
}
