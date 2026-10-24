package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Parental controls is admin-only: the rail lists it only with Expert mode, just after Profiles. A
 * child profile sees only its own page and About (no device-wide Playback or Display).
 */
class SettingsGroupTest {
    @Test
    fun `without Expert mode the rail has no Parental controls`() {
        assertFalse(SettingsGroup.PARENTAL_CONTROLS in SettingsGroup.visibleFor(admin = false))
    }

    @Test
    fun `with Expert mode Parental controls comes just after Profiles`() {
        val groups = SettingsGroup.visibleFor(admin = true)
        assertEquals(SettingsGroup.PARENTAL_CONTROLS, groups[groups.indexOf(SettingsGroup.PROFILES) + 1])
    }

    @Test
    fun `a child profile sees only its own page and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.ABOUT_ADVANCED),
            SettingsGroup.visibleFor(admin = false, child = true),
        )
    }

    @Test
    fun `a grown-up without Expert mode keeps Playback and Display`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.PLAYBACK, SettingsGroup.DISPLAY, SettingsGroup.ABOUT_ADVANCED),
            SettingsGroup.visibleFor(admin = false),
        )
    }
}
