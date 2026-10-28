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
 * picker and the way back to its row.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsThisDeviceFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks This Device`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Admin"),
            right("Set a PIN for Settings"),
            down("A / Admin"),
            *adminDown("A / Admin", "Content Filters of Each Profile"),
            down("Language / Language for menus and messages"),
            down("Text & Grid Size / Size of text and everything on screen, on every screen"),
            down("Items in Recent / How many films, episodes or channels Recent shows / 25"),
            down("Add a Channel to Recent After / So a channel you only zap past isn't added / 10 s"),
            down("Show Clock / The time at the top right of every screen"),
            // Sources & Guide follows on the same page.
            down("Manage Sources"),
            up("Show Clock"),
            up("Add a Channel to Recent After"),
            // OK opens the picker on the current value; Left closes it, focus back on its row.
            center("10 s"),
            down("15 s"),
            left("Add a Channel to Recent After"),
            up("Items in Recent"),
            center("25"),
            back("Items in Recent"),
            up("Text & Grid Size"),
            up("Language"),
            up("Content Filters of Each Profile"),
            back("Admin"),
        )
    }
}
