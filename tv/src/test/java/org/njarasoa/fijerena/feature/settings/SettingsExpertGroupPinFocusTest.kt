package org.njarasoa.fijerena.feature.settings

import androidx.compose.ui.input.key.Key
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.testing.FocusStep
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings with an Expert whose PIN was not typed yet in this session: entering an Expert
 * group's pane (Right or OK on its rail row) asks the PIN first, Cancel keeps focus on the
 * rail row, and once typed the PIN holds for every Expert group. Playback, Display and About never
 * ask it.
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
    fun `entering an Expert group asks the PIN, and Cancel stays on the rail`() {
        openLocked()
        walk.assertFocused("Profiles")
        walk.run(
            // Right on Profiles (the list of every profile, an Expert group): the PIN pad, on 1.
            right("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            // Cancel: focus back on the rail row, the pane not entered.
            center("Profiles"),
            down("Parental Controls"),
            // OK asks it too.
            center("1"),
            down("4"),
            down("7"),
            down("Erase"),
            down("Cancel"),
            center("Parental Controls"),
            // Playback is everyone's: no PIN.
            down("Playback"),
            right("Play Next Episode Automatically"),
            left("Playback"),
            down("Display"),
            down("This Device"),
            right("1"),
        )
    }

    @Test
    fun `the right PIN enters the group and holds for the others`() {
        openLocked()
        walk.run(
            down("Parental Controls"),
            right("1"),
            FocusStep(Key.One, "1"),
            FocusStep(Key.Two, "1"),
            FocusStep(Key.Three, "1"),
            // The fourth digit checks it: the pane, on the group's first row.
            FocusStep(Key.Four, "Kids' Categories"),
            left("Parental Controls"),
            // The session holds the PIN: no pad again, in this group or another.
            right("Kids' Categories"),
            left("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("This Device"),
            right("Language"),
        )
    }
}
