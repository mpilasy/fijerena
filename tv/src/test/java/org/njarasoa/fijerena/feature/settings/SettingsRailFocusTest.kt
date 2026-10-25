package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → the rail of groups (`scripts/focus-walks/settings.txt`, its first lines): the
 * groups an Expert, a grown-up and a child see, in order. Up and Down never leave the rail.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRailFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert sees every group`() {
        open(SettingsFixture.ADMIN)
        walk.assertFocused("Profiles")
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("This Device"),
            down("Sources & Guide"),
            down("Sync & Backup"),
            down("Advanced"),
            down("About"),
            // Down at the end of the rail stays put.
            down("About"),
            up("Advanced"),
        )
    }

    @Test
    fun `a grown-up sees its own page, Playback, Display and About`() {
        open(SettingsFixture.GUEST)
        walk.assertFocused("Your Profile")
        walk.run(
            // No Expert group: no Parental Controls, This Device, Sources & Guide, Sync & Backup
            // or Advanced.
            down("Playback"),
            down("Display"),
            down("About"),
            down("About"),
            up("Display"),
            up("Playback"),
            up("Your Profile"),
            // Up at the top of the rail stays put.
            up("Your Profile"),
        )
    }

    @Test
    fun `a child sees its own page and About only`() {
        open(SettingsFixture.KID)
        walk.assertFocused("Your Profile")
        walk.run(
            down("About"),
            down("About"),
            up("Your Profile"),
            up("Your Profile"),
        )
    }
}
