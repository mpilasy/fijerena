package org.njarasoa.fijerena.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The admin's first run (the settings flow review, E1). */
class FirstSourceTest {
    @Test
    fun `an Expert with no source starts on Add Source`() {
        assertTrue(startsOnAddSource(hasSource = false, expert = true))
    }

    @Test
    fun `a profile that is not Expert starts on Home with no source`() {
        assertFalse(startsOnAddSource(hasSource = false, expert = false))
    }

    @Test
    fun `with a source nobody starts on Add Source`() {
        assertFalse(startsOnAddSource(hasSource = true, expert = true))
        assertFalse(startsOnAddSource(hasSource = true, expert = false))
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
