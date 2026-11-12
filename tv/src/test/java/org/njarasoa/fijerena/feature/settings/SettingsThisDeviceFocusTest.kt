package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
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
 * TV Settings → This Device, an Admin section (`scripts/focus-walks/settings.txt`): the device's
 * rows, a picker and the way back to its row in the section, then to the section's rail row.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsThisDeviceFocusTest : SettingsFocusTest() {
    @Test
    fun `an Admin walks This Device`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAdminSection("This Device"),
            // OK on the rail row enters the section, focus on its first row.
            center("Language / Language for menus and messages"),
            down("Text & Grid Size / Size of text and everything on screen, on every screen"),
            down("Items in Recent / How many films, episodes or channels Recent shows / 25"),
            down("Add a Channel to Recent After / So a channel you only zap past isn't added / 10 s"),
            down("Reminder Time / How long before the start a reminder rings, on this device / 10 minutes before"),
            down("Show Clock / The time at the top right of every screen"),
            down("Night Audio / Quieter loud sounds, clearer speech"),
            down("$MATCH_FRAME_RATE / $MATCH_FRAME_RATE_DESC"),
            down("Show Key Input / The last 10 keys the app received"),
            // The end of the section: Sources & Guide is a section of its own.
            down("Show Key Input"),
            // OK on the switch asks how long, focus on the default; Left closes it on its row,
            // the switch still off.
            center("10 minutes"),
            left("Show Key Input"),
            up(MATCH_FRAME_RATE),
            up("Night Audio"),
            up("Show Clock"),
            up("Reminder Time"),
            // Its picker opens on the current choice; Left closes it on its row.
            center("10 minutes before"),
            down("15 minutes before"),
            left("Reminder Time"),
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
            // Up at the top of the section stays put; Back goes to the section's rail row.
            up("Language"),
            back("This Device"),
        )
    }

    @Test
    fun `an Admin turns Match Frame Rate on and off, and the device keeps it`() {
        open(SettingsFixture.ADMIN)
        assertFalse(AppSettings(context).matchFrameRate)
        walk.run(
            *toAdminSection("This Device"),
            center("Language"),
            *sectionDown(THIS_DEVICE_ROWS, "Language", MATCH_FRAME_RATE),
            // OK turns the switch on; focus stays on its row.
            center(MATCH_FRAME_RATE),
        )
        rule.waitForIdle()
        assertTrue(AppSettings(context).matchFrameRate)

        walk.run(center(MATCH_FRAME_RATE))
        rule.waitForIdle()
        assertFalse(AppSettings(context).matchFrameRate)
    }

    @Test
    fun `Match Frame Rate opens with the saved value`() {
        seed(SettingsFixture.ADMIN)
        AppSettings(context).matchFrameRate = true
        showSettings()
        walk.run(
            *toAdminSection("This Device"),
            center("Language"),
            *sectionDown(THIS_DEVICE_ROWS, "Language", MATCH_FRAME_RATE),
            // The row reads the saved on, so OK turns it off.
            center(MATCH_FRAME_RATE),
        )
        rule.waitForIdle()
        assertFalse(AppSettings(context).matchFrameRate)
    }

    private companion object {
        const val MATCH_FRAME_RATE = "Match Frame Rate"
        const val MATCH_FRAME_RATE_DESC =
            "The screen changes to the frame rate of the video. It goes black for a few seconds while it changes."

        /** Admin → This Device as an Admin sees it on the fixture's data. */
        val THIS_DEVICE_ROWS =
            listOf(
                "Language",
                "Text & Grid Size",
                "Items in Recent",
                "Add a Channel to Recent After",
                "Reminder Time",
                "Show Clock",
                "Night Audio",
                MATCH_FRAME_RATE,
                "Show Key Input",
            )
    }
}
