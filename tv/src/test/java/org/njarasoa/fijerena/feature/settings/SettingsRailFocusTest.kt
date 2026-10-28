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
 * groups an Expert and a grown-up see, in order (a child has no Settings). Up and Down never leave the rail.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRailFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert sees Your Profile, Admin and About`() {
        open(SettingsFixture.ADMIN)
        walk.assertFocused("Your Profile")
        walk.run(
            down("Admin"),
            down("About"),
            // Down at the end of the rail stays put.
            down("About"),
            up("Admin"),
            up("Your Profile"),
            // Up at the top of the rail stays put.
            up("Your Profile"),
        )
    }

    @Test
    fun `a grown-up sees its own page and About`() {
        open(SettingsFixture.GUEST)
        walk.assertFocused("Your Profile")
        walk.run(
            // No Admin.
            down("About"),
            down("About"),
            up("Your Profile"),
            up("Your Profile"),
        )
    }
}
