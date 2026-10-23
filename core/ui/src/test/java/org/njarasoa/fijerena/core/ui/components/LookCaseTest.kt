package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Console shows labels in capitals; titles, headings, body text and the other Looks keep the case. */
class LookCaseTest {
    @Test
    fun `labels go to capitals in a caps Look`() {
        assertEquals("PLAY", lookCase("Play", TextRole.Label, caps = true, Locale.UK))
    }

    @Test
    fun `titles, headings and body keep their case`() {
        assertEquals("The Godfather", lookCase("The Godfather", TextRole.Title, caps = true, Locale.UK))
        assertEquals("The Matrix", lookCase("The Matrix", TextRole.Heading, caps = true, Locale.UK))
        assertEquals("A long synopsis.", lookCase("A long synopsis.", TextRole.Body, caps = true, Locale.UK))
    }

    @Test
    fun `other Looks keep the case`() {
        assertEquals("Continue watching", lookCase("Continue watching", TextRole.Label, caps = false, Locale.UK))
    }

    @Test
    fun `capitals follow the app's language`() {
        assertEquals("ÉPISODES", lookCase("épisodes", TextRole.Label, caps = true, Locale.FRANCE))
    }
}
