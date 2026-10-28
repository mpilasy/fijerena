package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rail's groups (the admin settings plan): Your Profile, Admin (only with Expert mode) and
 * About (a child profile has no Settings on TV). Only Admin asks the PIN on the way in,
 * and its page holds the parts the Expert groups had, in their rail order.
 */
class SettingsGroupTest {
    @Test
    fun `an Expert sees Your Profile, Admin and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.ADMIN, SettingsGroup.ABOUT),
            SettingsGroup.visibleFor(admin = true),
        )
    }

    @Test
    fun `a grown-up without Expert mode sees Your Profile and About`() {
        assertEquals(
            listOf(SettingsGroup.PROFILES, SettingsGroup.ABOUT),
            SettingsGroup.visibleFor(admin = false),
        )
    }

    @Test
    fun `only Admin asks the PIN`() {
        assertEquals(listOf(SettingsGroup.ADMIN), SettingsGroup.entries.filter { it.adminOnly })
    }

    @Test
    fun `Admin's parts come in the old groups' order`() {
        assertEquals(
            listOf(
                AdminPart.PROFILES,
                AdminPart.PARENTAL_CONTROLS,
                AdminPart.THIS_DEVICE,
                AdminPart.SOURCE_GUIDE,
                AdminPart.SYNC_BACKUP,
            ),
            AdminPart.entries,
        )
    }
}
