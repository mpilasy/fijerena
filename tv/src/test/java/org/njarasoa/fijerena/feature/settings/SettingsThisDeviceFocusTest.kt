package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Admin → This Device (`scripts/focus-walks/settings.txt`): the device's rows, a
 * picker and the way back to its row in the section, then to the section's row in Admin's list.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsThisDeviceFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks This Device`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAdminSection("This Device"),
            // OK opens the section in place of Admin's list, focus on its first row.
            center("Language / Language for menus and messages"),
            down("Text & Grid Size / Size of text and everything on screen, on every screen"),
            down("Items in Recent / How many films, episodes or channels Recent shows / 25"),
            down("Add a Channel to Recent After / So a channel you only zap past isn't added / 10 s"),
            down("Show Clock / The time at the top right of every screen"),
            // The end of the section: Sources & Guide is a section of its own.
            down("Show Clock"),
            up("Add a Channel to Recent After"),
            // OK opens the picker on the current value; Left closes it, focus back on its row, in
            // the section.
            center("10 s"),
            down("15 s"),
            left("Add a Channel to Recent After"),
            up("Items in Recent"),
            center("25"),
            back("Items in Recent"),
            up("Text & Grid Size"),
            up("Language"),
            // Up at the top of the section stays put; Back closes it, focus on its row.
            up("Language"),
            back("This Device / Language, Recent and the clock, on this device only"),
            back("Admin"),
        )
    }
}
