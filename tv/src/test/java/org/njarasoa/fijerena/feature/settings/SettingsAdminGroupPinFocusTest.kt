package org.njarasoa.fijerena.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.testing.FocusStep
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings with an Admin whose PIN was not typed yet in this session: entering an Admin
 * section's pane (Right or OK on its rail row) asks the PIN first, Cancel keeps focus on the rail
 * row, and once typed the PIN holds for the session, for all five sections. Your Profile and About
 * never ask it. A lock while a section is open sends focus back to its rail row.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAdminGroupPinFocusTest : SettingsFocusTest() {
    /** Admin in use with the PIN [SettingsFixture.PIN], locked as after a long time in the background. */
    private fun openLocked() {
        seed(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN))
        PinSession.onActiveProfile("")
        showSettings()
    }

    /** On the PIN pad, open on 1: types [SettingsFixture.PIN]; the fourth digit lands on [landing]. */
    private fun typePin(landing: String): Array<FocusStep> =
        arrayOf(
            FocusStep(Key.One, "1"),
            FocusStep(Key.Two, "1"),
            FocusStep(Key.Three, "1"),
            FocusStep(Key.Four, landing),
        )

    @Test
    fun `entering an Admin section asks the PIN, and Cancel stays on the rail`() {
        openLocked()
        walk.assertFocused("Your Profile")
        walk.run(
            // Your Profile is everyone's: no PIN.
            right("Edit Name / Name / Admin"),
            left("Your Profile"),
            down("Profiles"),
        )
        // A locked section's pane says only that the PIN is needed: none of its rows (AD-D2).
        rule.onNodeWithText("PIN needed. Press OK to enter it.").assertExists()
        rule.onAllNodesWithText("Add Profile").assertCountEquals(0)
        walk.run(
            // Right on an Admin section: the PIN pad, on 1.
            right("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            // Cancel: focus back on the rail row, the pane not entered.
            center("Profiles"),
            // OK asks it too.
            center("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            center("Profiles"),
            // Every Admin section asks it.
            down("Parental Controls"),
            right("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            center("Parental Controls"),
            // About is everyone's: no PIN.
            down("This Device"),
            down("Sources & Guide"),
            down("Sync & Backup"),
            down("About"),
            right("About Fijerena"),
        )
    }

    @Test
    fun `the right PIN enters the Admin sections and holds for the session`() {
        openLocked()
        walk.run(
            down("Profiles"),
            right("1"),
            // The fourth digit checks it: the section's first row.
            *typePin("admi / Admin / Using on this device"),
            left("Profiles"),
            // The session holds the PIN, for every Admin section: no pad again.
            right("admi / Admin / Using on this device"),
            left("Profiles"),
            down("Parental Controls"),
            right("Kids' Categories"),
            left("Parental Controls"),
            up("Profiles"),
            up("Your Profile"),
        )
    }

    @Test
    fun `a lock closes the open section, and the PIN opens it again`() {
        open(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN))
        walk.run(
            *toAdminSection("This Device"),
            center("Language"),
        )
        // The session locks again (a long time in the background): focus goes to the rail row,
        // and the pane says only that the PIN is needed.
        rule.runOnIdle { PinSession.onActiveProfile("") }
        walk.awaitFocused("This Device")
        walk.run(
            right("1"),
            // Back in, on the section's first row.
            *typePin("Language"),
        )
    }
}
