package org.njarasoa.fijerena.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The admin's first run (the settings flow review, E1). */
class FirstSourceTest {
    @Test
    fun `an Admin with no source starts on Add Source`() {
        assertTrue(startsOnAddSource(hasSource = false, admin = true))
    }

    @Test
    fun `a profile that is not Admin starts on Home with no source`() {
        assertFalse(startsOnAddSource(hasSource = false, admin = false))
    }

    @Test
    fun `with a source nobody starts on Add Source`() {
        assertFalse(startsOnAddSource(hasSource = true, admin = true))
        assertFalse(startsOnAddSource(hasSource = true, admin = false))
    }

    @Test
    fun `the first source saved opens Home`() {
        assertTrue(opensHomeAfterSave(editId = Screen.AddProvider().editId, sourcesBefore = 0))
    }

    @Test
    fun `a later source goes back as before`() {
        assertFalse(opensHomeAfterSave(editId = Screen.AddProvider().editId, sourcesBefore = 1))
    }

    @Test
    fun `an edit goes back as before`() {
        assertFalse(opensHomeAfterSave(editId = 3L, sourcesBefore = 0))
        assertFalse(opensHomeAfterSave(editId = 3L, sourcesBefore = 2))
    }
}
