package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A child's own page has no Name and no PIN rows; an Expert on the child's page keeps both. */
class ProfileNameAndPinRowsTest {
    private val child = ProfileSettings(devMode = false, autoplayNextEpisode = false, child = true)
    private val grownUp = ProfileSettings(devMode = false, autoplayNextEpisode = false)

    @Test
    fun `a child's own page hides Name and PIN`() {
        assertFalse(child.showsNameAndPin(adminView = false))
    }

    @Test
    fun `an Expert on a child's page still edits Name and PIN`() {
        assertTrue(child.showsNameAndPin(adminView = true))
    }

    @Test
    fun `a grown-up's own page keeps Name and PIN`() {
        assertTrue(grownUp.showsNameAndPin(adminView = false))
    }
}
