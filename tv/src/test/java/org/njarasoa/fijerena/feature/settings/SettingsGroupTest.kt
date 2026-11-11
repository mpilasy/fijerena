package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rail's groups (the Admin sections plan): Your Profile, the five
 * Admin sections (only for an Admin) and About (a child profile has no Settings on TV). Only the
 * Admin sections ask the PIN on the way in, in the order the Admin list had.
 */
class SettingsGroupTest {
    private val adminSections =
        listOf(
            SettingsGroup.ADMIN_PROFILES,
            SettingsGroup.PARENTAL_CONTROLS,
            SettingsGroup.THIS_DEVICE,
            SettingsGroup.SOURCE_GUIDE,
            SettingsGroup.SYNC_BACKUP,
        )

    @Test
    fun `an Admin sees Your Profile, the Admin sections and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES) + adminSections + SettingsGroup.ABOUT,
            SettingsGroup.visibleFor(admin = true),
        )
    }

    @Test
    fun `a grown-up who is not an Admin sees Your Profile and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.ABOUT),
            SettingsGroup.visibleFor(admin = false),
        )
    }

    @Test
    fun `only the Admin sections ask the PIN`() {
        assertEquals(adminSections, SettingsGroup.entries.filter { it.adminOnly })
    }
}
