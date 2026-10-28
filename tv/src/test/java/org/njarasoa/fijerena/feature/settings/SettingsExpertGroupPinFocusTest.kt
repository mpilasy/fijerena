package org.njarasoa.fijerena.feature.settings

import androidx.compose.ui.input.key.Key
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
 * TV Settings with an Expert whose PIN was not typed yet in this session: entering Admin's pane
 * (Right or OK on its rail row) asks the PIN first, Cancel keeps focus on the rail row, and once
 * typed the PIN holds for the session. Your Profile and About never ask it.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsExpertGroupPinFocusTest : SettingsFocusTest() {
    /** Admin in use with the PIN [SettingsFixture.PIN], locked as after a long time in the background. */
    private fun openLocked() {
        seed(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN))
        PinSession.onActiveProfile("")
        showSettings()
    }

    @Test
    fun `entering Admin asks the PIN, and Cancel stays on the rail`() {
        openLocked()
        walk.assertFocused("Your Profile")
        walk.run(
            // Your Profile is everyone's: no PIN.
            right("Edit Name / Name / Admin"),
            left("Your Profile"),
            // Right on Admin: the PIN pad, on 1.
            down("Admin"),
            right("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            // Cancel: focus back on the rail row, the pane not entered.
            center("Admin"),
            // OK asks it too.
            center("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            center("Admin"),
            // About is everyone's: no PIN.
            down("About"),
            right("Fijerena v"),
        )
    }

    @Test
    fun `the right PIN enters Admin and holds for the session`() {
        openLocked()
        walk.run(
            down("Admin"),
            right("1"),
            FocusStep(Key.One, "1"),
            FocusStep(Key.Two, "1"),
            FocusStep(Key.Three, "1"),
            // The fourth digit checks it: the pane, on its first row (an Expert has a PIN: no
            // reminder).
            FocusStep(Key.Four, "A / Admin"),
            left("Admin"),
            // The session holds the PIN: no pad again.
            right("A / Admin"),
            left("Admin"),
            up("Your Profile"),
            down("Admin"),
            right("A / Admin"),
        )
    }
}
