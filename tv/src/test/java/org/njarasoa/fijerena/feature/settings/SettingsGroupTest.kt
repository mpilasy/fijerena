package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rail's groups: the Expert groups (Parental controls just after Profiles, This Device, Sources
 * & guide, Sync & backup, Advanced) only with Expert mode; a child profile sees only its own page
 * and About. An Expert group, and Profiles with Expert mode, asks the PIN on the way in.
 */
class SettingsGroupTest {
    @Test
    fun `without Expert mode the rail has no Expert group`() {
        val groups = SettingsGroup.visibleFor(admin = false)
        assertTrue(groups.none { it.adminOnly })
        assertFalse(SettingsGroup.PARENTAL_CONTROLS in groups)
        assertFalse(SettingsGroup.THIS_DEVICE in groups)
        assertFalse(SettingsGroup.ADVANCED in groups)
    }

    @Test
    fun `with Expert mode Parental controls comes just after Profiles`() {
        val groups = SettingsGroup.visibleFor(admin = true)
        assertEquals(SettingsGroup.PARENTAL_CONTROLS, groups[groups.indexOf(SettingsGroup.PROFILES) + 1])
    }

    @Test
    fun `an Expert sees every group, About last`() {
        assertEquals(
            listOf(
                SettingsGroup.PROFILES,
                SettingsGroup.PARENTAL_CONTROLS,
                SettingsGroup.PLAYBACK,
                SettingsGroup.DISPLAY,
                SettingsGroup.THIS_DEVICE,
                SettingsGroup.SOURCE_GUIDE,
                SettingsGroup.SYNC_BACKUP,
                SettingsGroup.ADVANCED,
                SettingsGroup.ABOUT,
            ),
            SettingsGroup.visibleFor(admin = true),
        )
    }

    @Test
    fun `a child profile sees only its own page and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.ABOUT),
            SettingsGroup.visibleFor(admin = false, child = true),
        )
    }

    @Test
    fun `a grown-up without Expert mode keeps Playback and Display`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.PLAYBACK, SettingsGroup.DISPLAY, SettingsGroup.ABOUT),
            SettingsGroup.visibleFor(admin = false),
        )
    }

    @Test
    fun `the Expert groups and an Expert's Profiles ask the PIN, the others never`() {
        assertEquals(
            listOf(
                SettingsGroup.PROFILES,
                SettingsGroup.PARENTAL_CONTROLS,
                SettingsGroup.THIS_DEVICE,
                SettingsGroup.SOURCE_GUIDE,
                SettingsGroup.SYNC_BACKUP,
                SettingsGroup.ADVANCED,
            ),
            SettingsGroup.entries.filter { it.asksPin(admin = true) },
        )
        // Without Expert mode Profiles is the viewer's own page: no PIN.
        assertTrue(SettingsGroup.visibleFor(admin = false).none { it.asksPin(admin = false) })
    }
}
